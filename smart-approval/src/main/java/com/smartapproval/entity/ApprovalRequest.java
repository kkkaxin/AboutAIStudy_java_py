package com.smartapproval.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审批申请 - 核心实体
 * 支持多种审批类型，包含结构化业务数据
 */
@Data
@Entity
@Table(name = "sa_approval_request")
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 申请人ID */
    @Column(nullable = false)
    private Long applicantId;

    /** 申请人姓名(冗余) */
    @Column(length = 20)
    private String applicantName;

    /** 审批类型 */
    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ApprovalType type;

    /** 标题 */
    @Column(nullable = false, length = 200)
    private String title;

    /** 申请内容(文本描述) */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 结构化业务数据(JSON) - 如金额、天数、物品明细等 */
    @Column(columnDefinition = "JSON")
    private String businessData;

    /** 涉及金额 */
    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    /** 审批状态 */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    /** 当前审批层级(从1开始) */
    private Integer currentLevel = 1;

    /** 总审批层级 */
    private Integer totalLevels = 2;

    /** AI风险评级 */
    @Column(length = 20)
    private String riskLevel;

    /** AI综合意见 */
    @Column(columnDefinition = "TEXT")
    private String aiSuggestion;

    /** AI分析详情(JSON) */
    @Column(columnDefinition = "JSON")
    private String aiAnalysis;

    @Column(length = 500)
    private String remark;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
    private LocalDateTime completedAt;

    /**
     * 审批类型枚举
     */
    public enum ApprovalType {
        LEAVE,          // 请假审批
        EXPENSE,        // 费用报销
        PURCHASE,       // 采购申请
        TRAVEL,         // 出差申请
        OVERTIME,       // 加班申请
        CONTRACT,       // 合同审批
        BUDGET,         // 预算调整
        OTHER           // 其他
    }

    /**
     * 审批状态枚举
     */
    public enum ApprovalStatus {
        PENDING,        // 待审批
        APPROVED,       // 已通过
        REJECTED,       // 已驳回
        RETURNED,       // 已退回修改
        CANCELLED       // 已撤销
    }
}
