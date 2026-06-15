package com.smartapproval.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 审批记录 - 每级审批的详细记录
 */
@Data
@Entity
@Table(name = "sa_approval_record")
public class ApprovalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的审批申请 */
    @Column(nullable = false)
    private Long requestId;

    /** 审批人ID */
    private Long approverId;

    /** 审批人姓名 */
    @Column(length = 20)
    private String approverName;

    /** 审批层级 */
    @Column(nullable = false)
    private Integer level;

    /** 审批动作 */
    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private ApprovalAction action;

    /** 人工审批意见 */
    @Column(columnDefinition = "TEXT")
    private String opinion;

    /** AI建议(该节点AI给出的意见) */
    @Column(columnDefinition = "TEXT")
    private String aiSuggestion;

    /** AI风险评级 */
    @Column(length = 20)
    private String aiRiskLevel;

    /** 是否采纳AI建议 */
    private Boolean aiAccepted;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum ApprovalAction {
        APPROVE,        // 同意
        REJECT,         // 驳回
        RETURN,         // 退回修改
        DELEGATE        // 转交他人
    }
}
