package com.smartapproval.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapproval.dto.ApprovalActionRequest;
import com.smartapproval.dto.ApprovalSubmitRequest;
import com.smartapproval.entity.*;
import com.smartapproval.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 审批服务 - 业务流程编排
 *
 * 核心职责：
 * 1. 提交审批 → 匹配工作流模板 → 触发 AI 分析 → 创建审批记录
 * 2. 执行审批 → 校验权限 → 记录意见 → 流转下一级 → 完结
 * 3. AI 建议 vs 人工决策的协调
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalRequestRepository requestRepository;
    private final ApprovalRecordRepository recordRepository;
    private final WorkflowTemplateRepository workflowTemplateRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final AIApprovalService aiService;
    private final ObjectMapper objectMapper;

    public ApprovalService(ApprovalRequestRepository requestRepository,
                           ApprovalRecordRepository recordRepository,
                           WorkflowTemplateRepository workflowTemplateRepository,
                           UserRepository userRepository,
                           DepartmentRepository departmentRepository,
                           AIApprovalService aiService,
                           ObjectMapper objectMapper) {
        this.requestRepository = requestRepository;
        this.recordRepository = recordRepository;
        this.workflowTemplateRepository = workflowTemplateRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    /**
     * 提交审批申请
     * 流程: 创建申请 → 匹配工作流 → AI分析 → 创建审批记录
     */
    @Transactional
    public Map<String, Object> submitApproval(ApprovalSubmitRequest req,
                                               Long applicantId,
                                               String applicantName) {

        // 1. 获取申请人信息
        User applicant = userRepository.findById(applicantId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 2. 创建审批申请
        ApprovalRequest request = new ApprovalRequest();
        request.setApplicantId(applicantId);
        request.setApplicantName(applicantName);
        request.setType(req.getType());
        request.setTitle(req.getTitle());
        request.setContent(req.getContent());
        request.setAmount(req.getAmount());
        request.setRemark(req.getRemark());

        // 序列化结构化业务数据
        if (req.getBusinessData() != null && !req.getBusinessData().isEmpty()) {
            try {
                request.setBusinessData(objectMapper.writeValueAsString(req.getBusinessData()));
            } catch (JsonProcessingException e) {
                log.warn("序列化业务数据失败", e);
            }
        }

        // 3. 匹配工作流模板
        WorkflowTemplate template = matchWorkflow(req);
        if (template != null) {
            request.setTotalLevels(parseLevelCount(template.getLevels()));
        } else {
            request.setTotalLevels(2); // 默认2级审批
        }
        request.setCurrentLevel(1);

        requestRepository.save(request);

        // 4. 触发 AI 分析（异步效果更好，这里同步演示）
        AIApprovalService.AIAnalysisResult aiResult = null;
        try {
            String deptName = applicant.getDepartment() != null
                    ? applicant.getDepartment() : "未分配部门";
            aiResult = aiService.analyzeApproval(request, deptName, applicantId);

            request.setRiskLevel(aiResult.riskLevel());
            request.setAiSuggestion(aiResult.summary());
            request.setAiAnalysis(aiResult.rawJson());
            requestRepository.save(request);
            log.info("AI分析完成: riskLevel={}, recommendation={}",
                    aiResult.riskLevel(), aiResult.recommendation());
        } catch (Exception e) {
            log.warn("AI分析失败，继续正常流程", e);
            request.setRiskLevel("N/A");
            request.setAiSuggestion("AI分析暂不可用，请人工审批");
            requestRepository.save(request);
        }

        // 5. 创建第一级审批记录
        createApprovalRecord(request, 1, request.getAiSuggestion(), request.getRiskLevel());

        // 6. 返回结果（包含 AI 分析的各项详细字段）
        Map<String, Object> result = new HashMap<>();
        result.put("requestId", request.getId());
        result.put("status", request.getStatus());
        result.put("totalLevels", request.getTotalLevels());
        result.put("riskLevel", request.getRiskLevel());
        result.put("aiSuggestion", request.getAiSuggestion());
        // 将 AI 分析的各个详细字段也返回给前端
        if (aiResult != null) {
            result.put("budgetAnalysis", aiResult.budgetAnalysis());
            result.put("complianceCheck", aiResult.complianceCheck());
            result.put("riskPoints", aiResult.riskPoints());
            result.put("suggestions", aiResult.suggestions());
        }
        return result;
    }

    /**
     * 执行审批操作（同意/驳回/退回）
     */
    @Transactional
    public Map<String, Object> approve(ApprovalActionRequest actionReq,
                                        Long approverId, String approverName) {

        ApprovalRecord record = recordRepository.findById(actionReq.getRecordId())
                .orElseThrow(() -> new RuntimeException("审批记录不存在"));

        ApprovalRequest request = requestRepository.findById(record.getRequestId())
                .orElseThrow(() -> new RuntimeException("审批申请不存在"));

        // 校验状态
        if (request.getStatus() != ApprovalRequest.ApprovalStatus.PENDING) {
            throw new RuntimeException("该申请已处理完毕");
        }

        // 更新审批记录
        record.setApproverId(approverId);
        record.setApproverName(approverName);
        record.setAction(ApprovalRecord.ApprovalAction.valueOf(actionReq.getAction()));
        record.setOpinion(actionReq.getOpinion());
        record.setAiAccepted(actionReq.getAcceptAiSuggestion() != null
                ? actionReq.getAcceptAiSuggestion() : false);
        recordRepository.save(record);

        ApprovalRequest.ApprovalStatus newStatus;

        switch (actionReq.getAction()) {
            case "APPROVE":
                // 判断是否为最后一级
                if (request.getCurrentLevel() >= request.getTotalLevels()) {
                    // 全部通过
                    newStatus = ApprovalRequest.ApprovalStatus.APPROVED;
                    request.setCompletedAt(LocalDateTime.now());
                } else {
                    // 流转下一级
                    newStatus = ApprovalRequest.ApprovalStatus.PENDING;
                    request.setCurrentLevel(request.getCurrentLevel() + 1);

                    // 为下一级触发AI分析
                    try {
                        triggerNextLevelAIAnalysis(request);
                    } catch (Exception e) {
                        log.warn("下一级AI分析失败", e);
                    }

                    // 创建下一级审批记录
                    createApprovalRecord(request, request.getCurrentLevel(),
                            request.getAiSuggestion(), request.getRiskLevel());
                }
                break;

            case "REJECT":
                newStatus = ApprovalRequest.ApprovalStatus.REJECTED;
                request.setCompletedAt(LocalDateTime.now());
                break;

            case "RETURN":
                newStatus = ApprovalRequest.ApprovalStatus.RETURNED;
                break;

            default:
                throw new RuntimeException("未知的审批动作: " + actionReq.getAction());
        }

        request.setStatus(newStatus);
        request.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(request);

        Map<String, Object> result = new HashMap<>();
        result.put("requestId", request.getId());
        result.put("status", newStatus);
        result.put("currentLevel", request.getCurrentLevel());
        result.put("totalLevels", request.getTotalLevels());
        return result;
    }

    /**
     * 查询待审批列表
     */
    public List<ApprovalRequest> getPendingList() {
        return requestRepository.findByStatusOrderByCreatedAtDesc(
                ApprovalRequest.ApprovalStatus.PENDING);
    }

    /**
     * 查询我的申请
     */
    public Page<ApprovalRequest> getMyRequests(Long userId, int page, int size) {
        return requestRepository.findByApplicantIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
    }

    /**
     * 查询申请的审批记录
     */
    public List<ApprovalRecord> getApprovalRecords(Long requestId) {
        return recordRepository.findByRequestIdOrderByLevelAsc(requestId);
    }

    /**
     * 查询申请详情
     */
    public ApprovalRequest getRequestDetail(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("审批申请不存在"));
    }

    // ============ 私有方法 ============

    /**
     * 匹配工作流模板
     */
    private WorkflowTemplate matchWorkflow(ApprovalSubmitRequest req) {
        BigDecimal amount = req.getAmount() != null ? req.getAmount() : BigDecimal.ZERO;
        return workflowTemplateRepository
                .findFirstByApprovalTypeAndEnabledTrueAndAmountThresholdLessThanEqualOrderByAmountThresholdDesc(
                        req.getType(), amount)
                .orElse(null);
    }

    /**
     * 从模板JSON解析审批层级数
     */
    private int parseLevelCount(String levelsJson) {
        try {
            @SuppressWarnings("unchecked")
            List<Object> levels = objectMapper.readValue(levelsJson, List.class);
            return levels.size();
        } catch (Exception e) {
            return 3;
        }
    }

    /**
     * 创建审批记录
     */
    private void createApprovalRecord(ApprovalRequest request, int level,
                                       String aiSuggestion, String riskLevel) {
        ApprovalRecord record = new ApprovalRecord();
        record.setRequestId(request.getId());
        record.setLevel(level);
        record.setAiSuggestion(aiSuggestion);
        record.setAiRiskLevel(riskLevel);
        recordRepository.save(record);
    }

    /**
     * 触发下一级 AI 分析
     */
    private void triggerNextLevelAIAnalysis(ApprovalRequest request) {
        List<ApprovalRecord> records = recordRepository
                .findByRequestIdOrderByLevelAsc(request.getId());
        // 收集前几级的审批意见，为下一级AI分析提供上下文
        // 这里简化处理，实际可扩展
        log.info("触发第{}级AI分析, requestId={}", request.getCurrentLevel(), request.getId());
    }
}
