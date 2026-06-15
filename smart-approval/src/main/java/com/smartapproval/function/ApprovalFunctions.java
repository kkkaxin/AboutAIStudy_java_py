package com.smartapproval.function;

import com.smartapproval.entity.ApprovalPolicy;
import com.smartapproval.entity.ApprovalRequest;
import com.smartapproval.entity.Department;
import com.smartapproval.repository.ApprovalPolicyRepository;
import com.smartapproval.repository.ApprovalRequestRepository;
import com.smartapproval.repository.DepartmentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI Function Calling 工具函数
 * 将企业内部的业务接口封装为 AI 可调用的 Function
 *
 * 这是【阶段3 AI改造企业系统】的核心能力：
 * "把现有 Java 接口封装成 AI 可调用的函数"
 */
@Configuration
public class ApprovalFunctions {

    private final DepartmentRepository departmentRepository;
    private final ApprovalPolicyRepository policyRepository;
    private final ApprovalRequestRepository requestRepository;

    public ApprovalFunctions(DepartmentRepository departmentRepository,
                             ApprovalPolicyRepository policyRepository,
                             ApprovalRequestRepository requestRepository) {
        this.departmentRepository = departmentRepository;
        this.policyRepository = policyRepository;
        this.requestRepository = requestRepository;
    }

    /**
     * 函数1: 检查部门预算
     * AI在审批时会调用此函数了解部门剩余预算
     */
    @Bean
    @Description("检查部门预算是否足够。输入部门名称和申请金额，返回预算剩余情况。" +
            "在审批费用报销、采购申请等涉及金额的审批时必须调用此函数。")
    public Function<BudgetCheckRequest, BudgetCheckResponse> checkBudget() {
        return request -> {
            Department dept = departmentRepository.findByName(request.department())
                    .orElse(null);

            if (dept == null) {
                return new BudgetCheckResponse(false, BigDecimal.ZERO, BigDecimal.ZERO,
                        "部门「" + request.department() + "」不存在", null, null, null);
            }

            BigDecimal remaining = dept.getRemainingBudget();
            BigDecimal annual = dept.getAnnualBudget();
            BigDecimal used = dept.getUsedBudget();

            boolean sufficient = remaining.compareTo(request.amount()) >= 0;
            double usageRate = annual.compareTo(BigDecimal.ZERO) > 0
                    ? used.divide(annual, 4, BigDecimal.ROUND_HALF_UP)
                      .multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0;

            String suggestion;
            String riskLevel;
            String riskDetail;
            if (sufficient && usageRate < 70) {
                suggestion = "预算充足，建议正常审批";
                riskLevel = "LOW";
                riskDetail = "部门预算使用率 " + String.format("%.1f%%", usageRate) + "，处于健康水平";
            } else if (sufficient && usageRate < 90) {
                suggestion = "预算可覆盖，但建议关注部门整体支出";
                riskLevel = "MEDIUM";
                riskDetail = "部门预算使用率 " + String.format("%.1f%%", usageRate) + "，需要合理把控后续支出";
            } else if (sufficient) {
                suggestion = "预算紧张，建议谨慎审批，可要求补充说明";
                riskLevel = "HIGH";
                riskDetail = "部门预算使用率高达 " + String.format("%.1f%%", usageRate) + "，接近年度预算上限";
            } else {
                suggestion = "预算不足，建议驳回或要求追加预算审批";
                riskLevel = "CRITICAL";
                riskDetail = "部门剩余预算 " + remaining + " 元，不足以覆盖本次申请 " + request.amount() + " 元";
            }

            return new BudgetCheckResponse(sufficient, remaining, annual, suggestion,
                    riskLevel, riskDetail, usageRate);
        };
    }

    /**
     * 函数2: 查询审批政策
     * AI会调用此函数获取对应类型的公司政策和审批规则
     */
    @Bean
    @Description("查询公司的审批政策和规则。根据审批类型返回适用政策，包括金额上限、审批层级要求等。" +
            "在给出审批意见前必须调用此函数以了解公司制度。")
    public Function<PolicyQueryRequest, PolicyQueryResponse> getApprovalPolicy() {
        return request -> {
            List<ApprovalPolicy> policies = policyRepository
                    .findByApprovalTypeAndEnabledTrueOrderByPriorityDesc(
                            ApprovalRequest.ApprovalType.valueOf(request.type()));

            if (policies.isEmpty()) {
                return new PolicyQueryResponse(
                        "未找到「" + request.type() + "」相关的审批政策，请按公司通用流程处理。",
                        null, null, List.of());
            }

            // 按金额匹配最适用的政策
            BigDecimal amount = request.amount() != null ? request.amount() : BigDecimal.ZERO;
            ApprovalPolicy matchedPolicy = policies.stream()
                    .filter(p -> p.getMaxAmount() == null
                            || p.getMaxAmount().compareTo(amount) >= 0)
                    .findFirst()
                    .orElse(policies.get(policies.size() - 1));

            List<PolicyItem> policyItems = policies.stream()
                    .map(p -> new PolicyItem(p.getName(), p.getContent(),
                            p.getMaxAmount(), p.getRequiredLevels(),
                            p.getRequireAttachment()))
                    .collect(Collectors.toList());

            String summary = "适用政策：「" + matchedPolicy.getName() + "」\n"
                    + "金额上限：" + (matchedPolicy.getMaxAmount() != null
                            ? matchedPolicy.getMaxAmount() + "元" : "无限制") + "\n"
                    + "需要审批层级：" + matchedPolicy.getRequiredLevels() + "级\n"
                    + (matchedPolicy.getRequireAttachment() ? "需要附件" : "无需附件");

            return new PolicyQueryResponse(summary, matchedPolicy.getName(),
                    matchedPolicy.getRequiredLevels(), policyItems);
        };
    }

    /**
     * 函数3: 查询申请人历史
     * AI会调用此函数了解申请人的历史审批情况，用于信用评估
     */
    @Bean
    @Description("查询申请人的历史审批记录。输入用户ID和审批类型，返回近30天内该类型申请的次数。" +
            "在评估申请人信用时要调用此函数。")
    public Function<ApplicantHistoryRequest, ApplicantHistoryResponse> getApplicantHistory() {
        return request -> {
            long recentCount = requestRepository.countRecentByUserAndType(
                    request.userId(),
                    ApprovalRequest.ApprovalType.valueOf(request.type()),
                    java.time.LocalDateTime.now().minusDays(30));

            List<ApprovalRequest> similar = requestRepository.findSimilarApprovals(
                    ApprovalRequest.ApprovalType.valueOf(request.type()));

            long approvedCount = similar.stream()
                    .filter(a -> a.getStatus() == ApprovalRequest.ApprovalStatus.APPROVED)
                    .count();
            long rejectedCount = similar.stream()
                    .filter(a -> a.getStatus() == ApprovalRequest.ApprovalStatus.REJECTED)
                    .count();

            String suggestion;
            String riskLevel;
            if (recentCount == 0) {
                suggestion = "无近期同类申请记录";
                riskLevel = "LOW";
            } else if (rejectedCount > approvedCount) {
                suggestion = "历史驳回率较高，建议重点审核";
                riskLevel = "HIGH";
            } else if (recentCount > 5) {
                suggestion = "近期同类申请频繁，建议核实是否合理";
                riskLevel = "MEDIUM";
            } else {
                suggestion = "历史记录正常";
                riskLevel = "LOW";
            }

            return new ApplicantHistoryResponse(recentCount, approvedCount, rejectedCount,
                    suggestion, riskLevel);
        };
    }

    /**
     * 函数4: 查询相似审批案例
     * AI会调用此函数参考历史相似案例
     */
    @Bean
    @Description("查询相似的历史审批案例作为参考。输入审批类型，返回近期同类审批的结果分布。" +
            "在给出审批建议时参考历史案例的处理方式。")
    public Function<SimilarCaseRequest, SimilarCaseResponse> getSimilarCases() {
        return request -> {
            List<ApprovalRequest> similar = requestRepository.findSimilarApprovals(
                    ApprovalRequest.ApprovalType.valueOf(request.type()));

            List<CaseItem> cases = similar.stream()
                    .limit(5)
                    .map(a -> new CaseItem(
                            a.getTitle(),
                            a.getStatus().name(),
                            a.getAmount() != null ? a.getAmount().toString() : "无",
                            a.getCreatedAt().toString(),
                            a.getAiSuggestion() != null
                                    ? a.getAiSuggestion().substring(0,
                                            Math.min(100, a.getAiSuggestion().length()))
                                    : "无"))
                    .collect(Collectors.toList());

            long approved = similar.stream()
                    .filter(a -> a.getStatus() == ApprovalRequest.ApprovalStatus.APPROVED)
                    .count();
            long rejected = similar.stream()
                    .filter(a -> a.getStatus() == ApprovalRequest.ApprovalStatus.REJECTED)
                    .count();
            long total = similar.size();

            return new SimilarCaseResponse((int) total, (int) approved, (int) rejected, cases);
        };
    }

    // ============ 函数入参/出参 Record ============

    public record BudgetCheckRequest(String department, BigDecimal amount) {}
    public record BudgetCheckResponse(boolean sufficient, BigDecimal remaining,
                                       BigDecimal annualBudget, String suggestion,
                                       String riskLevel, String riskDetail,
                                       Double usageRate) {}

    public record PolicyQueryRequest(String type, BigDecimal amount) {}
    public record PolicyQueryResponse(String summary, String matchedPolicy,
                                       Integer requiredLevels, List<PolicyItem> policies) {}
    public record PolicyItem(String name, String content, BigDecimal maxAmount,
                              Integer requiredLevels, Boolean requireAttachment) {}

    public record ApplicantHistoryRequest(Long userId, String type) {}
    public record ApplicantHistoryResponse(long recentCount, long approvedCount,
                                            long rejectedCount, String suggestion,
                                            String riskLevel) {}

    public record SimilarCaseRequest(String type) {}
    public record SimilarCaseResponse(int total, int approved, int rejected,
                                       List<CaseItem> cases) {}
    public record CaseItem(String title, String status, String amount,
                            String createdAt, String aiSuggestion) {}
}
