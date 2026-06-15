package com.smartapproval.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 部门 - 存储部门预算等信息，供AI调用
 */
@Data
@Entity
@Table(name = "sa_department")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 部门名称 */
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    /** 部门编码 */
    @Column(unique = true, length = 20)
    private String code;

    /** 年度总预算 */
    @Column(precision = 14, scale = 2)
    private BigDecimal annualBudget;

    /** 已使用预算 */
    @Column(precision = 14, scale = 2)
    private BigDecimal usedBudget = BigDecimal.ZERO;

    /** 部门经理ID */
    private Long managerId;

    /** 部门经理姓名 */
    @Column(length = 20)
    private String managerName;

    @Column(length = 200)
    private String description;

    private LocalDateTime createdAt = LocalDateTime.now();

    /** 计算剩余预算 */
    public BigDecimal getRemainingBudget() {
        if (annualBudget == null) return BigDecimal.ZERO;
        if (usedBudget == null) return annualBudget;
        return annualBudget.subtract(usedBudget);
    }
}
