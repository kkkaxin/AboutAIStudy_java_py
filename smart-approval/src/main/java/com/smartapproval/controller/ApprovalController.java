package com.smartapproval.controller;

import com.smartapproval.dto.ApprovalActionRequest;
import com.smartapproval.dto.ApprovalSubmitRequest;
import com.smartapproval.dto.Result;
import com.smartapproval.entity.ApprovalRecord;
import com.smartapproval.entity.ApprovalRequest;
import com.smartapproval.security.SecurityUtils;
import com.smartapproval.service.ApprovalService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/approval")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * 提交审批申请 - 触发AI分析
     */
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@Valid @RequestBody ApprovalSubmitRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        String username = SecurityUtils.getCurrentUsername();
        Map<String, Object> result = approvalService.submitApproval(req, userId, username);
        return Result.success("提交成功，AI已生成审批建议", result);
    }

    /**
     * 执行审批（同意/驳回/退回）
     */
    @PostMapping("/action")
    public Result<Map<String, Object>> approve(@RequestBody ApprovalActionRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        String username = SecurityUtils.getCurrentUsername();
        Map<String, Object> result = approvalService.approve(req, userId, username);
        return Result.success("操作成功", result);
    }

    /**
     * 实时AI分析（单独调用）—— 用于刷新AI建议
     */
    @PostMapping("/ai/analyze/{requestId}")
    public Result<Map<String, String>> aiAnalyze(@PathVariable Long requestId) {
        ApprovalRequest req = approvalService.getRequestDetail(requestId);
        // 重新提交触发AI分析
        ApprovalSubmitRequest dummyReq = new ApprovalSubmitRequest();
        dummyReq.setType(req.getType());
        dummyReq.setTitle(req.getTitle());
        dummyReq.setContent(req.getContent());
        dummyReq.setAmount(req.getAmount());

        Long userId = req.getApplicantId();
        String username = req.getApplicantName();
        Map<String, Object> result = approvalService.submitApproval(dummyReq, userId, username);

        return Result.success("AI分析完成", Map.of(
                "riskLevel", String.valueOf(result.getOrDefault("riskLevel", "N/A")),
                "aiSuggestion", String.valueOf(result.getOrDefault("aiSuggestion", "无"))
        ));
    }

    /**
     * 待审批列表
     */
    @GetMapping("/pending")
    public Result<List<ApprovalRequest>> pendingList() {
        return Result.success(approvalService.getPendingList());
    }

    /**
     * 我的申请
     */
    @GetMapping("/my-requests")
    public Result<Page<ApprovalRequest>> myRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(approvalService.getMyRequests(userId, page, size));
    }

    /**
     * 申请详情（含AI分析结果）
     */
    @GetMapping("/detail/{requestId}")
    public Result<Map<String, Object>> detail(@PathVariable Long requestId) {
        ApprovalRequest req = approvalService.getRequestDetail(requestId);
        List<ApprovalRecord> records = approvalService.getApprovalRecords(requestId);

        return Result.success(Map.of(
                "request", req,
                "records", records,
                "aiAnalysis", req.getAiAnalysis() != null ? req.getAiAnalysis() : "{}"
        ));
    }

    /**
     * 审批记录
     */
    @GetMapping("/records/{requestId}")
    public Result<List<ApprovalRecord>> records(@PathVariable Long requestId) {
        return Result.success(approvalService.getApprovalRecords(requestId));
    }
}
