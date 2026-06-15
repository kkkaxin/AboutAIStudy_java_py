package com.smartapproval.dto;

import lombok.Data;

/**
 * 执行审批操作
 */
@Data
public class ApprovalActionRequest {

    /** 审批记录ID */
    private Long recordId;

    /** 审批动作: APPROVE / REJECT / RETURN */
    private String action;

    /** 人工审批意见 */
    private String opinion;

    /** 是否采纳AI建议 */
    private Boolean acceptAiSuggestion;
}
