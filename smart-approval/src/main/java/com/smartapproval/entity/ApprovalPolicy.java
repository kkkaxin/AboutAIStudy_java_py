package com.smartapproval.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审批政策 - 公司各类审批的政策规则，AI分析时作为参考依据
 */
@Data
@Entity
@Table(name = "sa_approval_policy")
public class ApprovalPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 政策名称 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 适用审批类型 */
    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ApprovalRequest.ApprovalType approvalType;

    /** 适用条件(JSON) - 如: {"minAmount":0,"maxAmount":5000,"department":"技术部"} */
    @Column(name = "`condition`", columnDefinition = "JSON")
    private String condition;

    /** 政策内容 */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 金额上限 */
    @Column(precision = 12, scale = 2)
    private BigDecimal maxAmount;

    /** 需要审批层级数 */
    private Integer requiredLevels;

    /** 是否需要附件 */
    private Boolean requireAttachment = false;

    /** 优先级(数值越大越优先) */
    private Integer priority = 0;

    /** 是否启用 */
    private Boolean enabled = true;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}
