package com.smartapproval.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审批工作流模板 - 定义不同类型审批的流程规则
 */
@Data
@Entity
@Table(name = "sa_workflow_template")
public class WorkflowTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 模板名称 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 适用审批类型 */
    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ApprovalRequest.ApprovalType approvalType;

    /** 审批层级定义(JSON数组) 
     * 例: [{"level":1,"role":"MANAGER","name":"部门经理"},
     *       {"level":2,"role":"DIRECTOR","name":"总监审批"},
     *       {"level":3,"role":"ADMIN","name":"财务复核","condition":"amount>10000"}]
     * */
    @Column(columnDefinition = "JSON", nullable = false)
    private String levels;

    /** 触发金额阈值(超过此金额才走该流程) */
    @Column(precision = 12, scale = 2)
    private BigDecimal amountThreshold;

    /** 是否启用 */
    private Boolean enabled = true;

    /** 描述 */
    @Column(length = 500)
    private String description;

    private LocalDateTime createdAt = LocalDateTime.now();
}
