package com.smartapproval.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sa_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 20)
    private String realName;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private Role role = Role.EMPLOYEE;

    @Column(length = 50)
    private String department;

    @Column(length = 20)
    private String position;

    @Column(length = 100)
    private String email;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Role {
        EMPLOYEE,   // 普通员工
        MANAGER,    // 部门经理
        DIRECTOR,   // 总监
        ADMIN       // 系统管理员
    }
}
