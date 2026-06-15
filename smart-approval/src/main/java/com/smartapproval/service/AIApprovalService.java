package com.smartapproval.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapproval.entity.ApprovalRequest;
import com.smartapproval.function.ApprovalFunctions;
import com.smartapproval.function.ApprovalFunctions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * AI 审批服务 - 核心 AI 能力
 *
 * 采用"手动 Function Calling"模式：
 *  1. 先调用 4 个业务函数获取真实数据（预算/政策/历史/案例）
 *  2. 将函数返回的结构化数据嵌入 Prompt
 *  3. AI 基于真实数据进行分析并输出 JSON 结论
 *
 * 这样绕过了 Spring AI 1.0.7 中 ChatClient.Builder API 的兼容问题，
 * 同时完整演示了 "AI + 企业系统" 的核心流程。
 */
@Service
public class AIApprovalService {

    private static final Logger log = LoggerFactory.getLogger(AIApprovalService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    // === 四个业务函数 Bean（手动注入） ===
    private final Function<BudgetCheckRequest, BudgetCheckResponse> checkBudget;
    private final Function<PolicyQueryRequest, PolicyQueryResponse> getApprovalPolicy;
    private final Function<ApplicantHistoryRequest, ApplicantHistoryResponse> getApplicantHistory;
    private final Function<SimilarCaseRequest, SimilarCaseResponse> getSimilarCases;

    @Value("${approval.ai-suggestion.enabled:true}")
    private boolean aiEnabled;

    public AIApprovalService(ChatClient chatClient,
                             ObjectMapper objectMapper,
                             ApprovalFunctions approvalFunctions) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        // 从 ApprovalFunctions 配置类中提取 @Bean 方法返回的 Function 实例
        this.checkBudget = approvalFunctions.checkBudget();
        this.getApprovalPolicy = approvalFunctions.getApprovalPolicy();
        this.getApplicantHistory = approvalFunctions.getApplicantHistory();
        this.getSimilarCases = approvalFunctions.getSimilarCases();
    }

    /**
     * 生成 AI 审批建议
     *
     * 流程: 业务数据采集 → 嵌入 Prompt → AI 分析 → 解析 JSON 结论
     */
    public AIAnalysisResult analyzeApproval(ApprovalRequest request,
                                              String applicantDepartment,
                                              Long applicantId) {

        if (!aiEnabled) {
            return AIAnalysisResult.disabled();
        }

        try {
            // === 步骤1: 调用业务函数，采集真实数据 ===
            BusinessContext ctx = collectBusinessData(request, applicantDepartment, applicantId);

            // === 步骤2: 将数据嵌入 Prompt，交给 AI 分析 ===
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(request, applicantDepartment, applicantId, ctx);

            String aiResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            log.info("AI审批分析完成, requestId={}, riskLevel解析中...", request.getId());

            // === 步骤3: 解析 AI 返回的 JSON ===
            return parseAIResponse(aiResponse, request);

        } catch (Exception e) {
            log.error("AI审批分析失败", e);
            return AIAnalysisResult.error("AI分析异常: " + e.getMessage());
        }
    }

    /**
     * 采集业务数据 —— 相当于"手动 Function Calling"
     * 依次调用四个业务函数，将返回值汇总为 BusinessContext
     */
    private BusinessContext collectBusinessData(ApprovalRequest request,
                                                 String department, Long applicantId) {
        BusinessContext ctx = new BusinessContext();

        // 1. 预算检查
        if (request.getAmount() != null && request.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            try {
                BudgetCheckResponse budget = checkBudget.apply(
                        new BudgetCheckRequest(department, request.getAmount()));
                ctx.budgetCheck = budget;
                log.debug("预算检查完成: sufficient={}, riskLevel={}",
                        budget.sufficient(), budget.riskLevel());
            } catch (Exception e) {
                log.warn("预算检查异常: {}", e.getMessage());
                ctx.budgetCheck = null;
            }
        }

        // 2. 政策查询
        try {
            PolicyQueryResponse policy = getApprovalPolicy.apply(
                    new PolicyQueryRequest(request.getType().name(),
                            request.getAmount()));
            ctx.policyQuery = policy;
            log.debug("政策查询完成: matchedPolicy={}", policy.matchedPolicy());
        } catch (Exception e) {
            log.warn("政策查询异常: {}", e.getMessage());
            ctx.policyQuery = null;
        }

        // 3. 申请人历史
        try {
            ApplicantHistoryResponse history = getApplicantHistory.apply(
                    new ApplicantHistoryRequest(applicantId, request.getType().name()));
            ctx.applicantHistory = history;
            log.debug("申请人历史查询完成: recentCount={}", history.recentCount());
        } catch (Exception e) {
            log.warn("申请人历史查询异常: {}", e.getMessage());
            ctx.applicantHistory = null;
        }

        // 4. 相似案例
        try {
            SimilarCaseResponse cases = getSimilarCases.apply(
                    new SimilarCaseRequest(request.getType().name()));
            ctx.similarCases = cases;
            log.debug("相似案例查询完成: total={}", cases.total());
        } catch (Exception e) {
            log.warn("相似案例查询异常: {}", e.getMessage());
            ctx.similarCases = null;
        }

        return ctx;
    }

    /**
     * 系统提示词
     */
    private String buildSystemPrompt() {
        return """
                # 身份
                你是一个专业的企业审批系统AI助手，负责对员工的审批申请给出专业、客观的分析意见。

                # 核心原则
                1. **基于数据做判断**: 用户会在消息中提供结构化的业务数据，你必须基于这些数据给出分析
                2. **风险优先**: 识别潜在风险点，给出风险等级(LOW/MEDIUM/HIGH/CRITICAL)
                3. **合规检查**: 对照公司政策判断是否符合规定
                4. **有理有据**: 每个结论都要引用具体数据

                # 输出格式
                必须严格以 JSON 格式输出（不要包含 ```json 代码块标记）:
                {
                  "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
                  "recommendation": "APPROVE|REJECT|REVIEW",
                  "summary": "一段完整的审批意见(100-200字)，包含具体数据支撑",
                  "riskPoints": ["风险点1", "风险点2"],
                  "complianceCheck": "合规检查结论",
                  "budgetAnalysis": "预算分析结论",
                  "suggestions": ["改进建议1", "改进建议2"]
                }
                """;
    }

    /**
     * 构建用户 Prompt —— 嵌入业务数据
     */
    private String buildUserPrompt(ApprovalRequest request,
                                    String department, Long applicantId,
                                    BusinessContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下审批申请:\n\n");

        sb.append("【申请信息】\n");
        sb.append("- 申请ID: ").append(request.getId()).append("\n");
        sb.append("- 审批类型: ").append(request.getType()).append("\n");
        sb.append("- 标题: ").append(request.getTitle()).append("\n");
        sb.append("- 申请内容: ").append(request.getContent()).append("\n");
        if (request.getAmount() != null && request.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            sb.append("- 涉及金额: ").append(request.getAmount()).append(" 元\n");
        }
        if (request.getBusinessData() != null) {
            sb.append("- 业务数据: ").append(request.getBusinessData()).append("\n");
        }
        sb.append("- 提交时间: ").append(
                request.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .append("\n");

        sb.append("\n【申请人信息】\n");
        sb.append("- 申请人: ").append(request.getApplicantName()).append("\n");
        sb.append("- 用户ID: ").append(applicantId).append("\n");
        sb.append("- 所属部门: ").append(department).append("\n");

        // === 嵌入业务函数返回的数据 ===
        sb.append("\n--- 以下为系统自动采集的业务数据 ---\n");

        // 1. 预算数据
        if (ctx.budgetCheck != null) {
            BudgetCheckResponse b = ctx.budgetCheck;
            sb.append("\n【预算检查结果】\n");
            sb.append("- 预算是否充足: ").append(b.sufficient() ? "是" : "否").append("\n");
            sb.append("- 部门剩余预算: ").append(b.remaining()).append(" 元\n");
            sb.append("- 部门年度预算: ").append(b.annualBudget()).append(" 元\n");
            sb.append("- 预算使用率: ").append(String.format("%.1f%%",
                    b.usageRate() != null ? b.usageRate() : 0)).append("\n");
            sb.append("- 预算建议: ").append(b.suggestion()).append("\n");
            sb.append("- 风险等级: ").append(b.riskLevel()).append("\n");
            sb.append("- 风险说明: ").append(b.riskDetail()).append("\n");
        }

        // 2. 政策数据
        if (ctx.policyQuery != null) {
            PolicyQueryResponse p = ctx.policyQuery;
            sb.append("\n【适用政策】\n");
            sb.append(p.summary()).append("\n");
        }

        // 3. 申请人历史
        if (ctx.applicantHistory != null) {
            ApplicantHistoryResponse h = ctx.applicantHistory;
            sb.append("\n【申请人历史记录】\n");
            sb.append("- 近30天同类申请: ").append(h.recentCount()).append(" 次\n");
            sb.append("- 历史通过: ").append(h.approvedCount()).append(" 次\n");
            sb.append("- 历史驳回: ").append(h.rejectedCount()).append(" 次\n");
            sb.append("- 信用评估: ").append(h.suggestion()).append("\n");
            sb.append("- 信用风险: ").append(h.riskLevel()).append("\n");
        }

        // 4. 相似案例
        if (ctx.similarCases != null) {
            SimilarCaseResponse s = ctx.similarCases;
            sb.append("\n【相似案例参考】\n");
            sb.append("- 同类申请总数: ").append(s.total()).append("\n");
            sb.append("- 通过: ").append(s.approved()).append(" | 驳回: ")
                    .append(s.rejected()).append("\n");
            if (s.cases() != null && !s.cases().isEmpty()) {
                sb.append("- 最近案例:\n");
                for (CaseItem c : s.cases()) {
                    sb.append("  • ").append(c.title())
                            .append(" [").append(c.status()).append("] ")
                            .append(c.amount()).append("元\n");
                }
            }
        }

        sb.append("\n请基于以上数据给出审批分析结论（JSON格式）。");
        return sb.toString();
    }

    /**
     * 解析AI返回的JSON结果
     */
    private AIAnalysisResult parseAIResponse(String aiResponse, ApprovalRequest request) {
        try {
            String cleaned = aiResponse;
            if (cleaned.contains("```json")) {
                cleaned = cleaned.substring(
                        cleaned.indexOf("```json") + 7,
                        cleaned.lastIndexOf("```"));
            } else if (cleaned.contains("```")) {
                cleaned = cleaned.substring(
                        cleaned.indexOf("```") + 3,
                        cleaned.lastIndexOf("```"));
            }
            cleaned = cleaned.trim();

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(cleaned, Map.class);

            return new AIAnalysisResult(
                    String.valueOf(result.getOrDefault("riskLevel", "MEDIUM")),
                    String.valueOf(result.getOrDefault("recommendation", "REVIEW")),
                    String.valueOf(result.getOrDefault("summary", "详见系统记录")),
                    objectMapper.writeValueAsString(result),
                    safeList(result, "riskPoints"),
                    String.valueOf(result.getOrDefault("complianceCheck", "")),
                    String.valueOf(result.getOrDefault("budgetAnalysis", "")),
                    safeList(result, "suggestions")
            );
        } catch (Exception e) {
            log.warn("解析AI响应失败，使用原始文本。response={}", aiResponse);
            return new AIAnalysisResult(
                    "MEDIUM", "REVIEW",
                    aiResponse.substring(0, Math.min(500, aiResponse.length())),
                    "{}",
                    List.of("AI分析格式解析异常"),
                    "无法解析", "无法解析",
                    List.of("建议人工审核")
            );
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> safeList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List) return (List<String>) val;
        return List.of();
    }

    // ============ 内部类型 ============

    /**
     * 业务数据上下文 —— 汇总四个函数返回值
     */
    private static class BusinessContext {
        BudgetCheckResponse budgetCheck;
        PolicyQueryResponse policyQuery;
        ApplicantHistoryResponse applicantHistory;
        SimilarCaseResponse similarCases;
    }

    /**
     * AI 分析结果 VO
     */
    public record AIAnalysisResult(
            String riskLevel,
            String recommendation,
            String summary,
            String rawJson,
            List<String> riskPoints,
            String complianceCheck,
            String budgetAnalysis,
            List<String> suggestions
    ) {
        public static AIAnalysisResult disabled() {
            return new AIAnalysisResult("N/A", "N/A", "AI审批未启用",
                    "{}", List.of(), "", "", List.of());
        }

        public static AIAnalysisResult error(String msg) {
            return new AIAnalysisResult("ERROR", "REVIEW", msg,
                    "{}", List.of(msg), "", "", List.of("请人工审批"));
        }
    }
}
