package com.smartapproval.config;

import com.smartapproval.entity.*;
import com.smartapproval.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 数据初始化 - 首次启动时插入种子数据
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final ApprovalPolicyRepository policyRepository;
    private final WorkflowTemplateRepository workflowTemplateRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           DepartmentRepository departmentRepository,
                           ApprovalPolicyRepository policyRepository,
                           WorkflowTemplateRepository workflowTemplateRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.policyRepository = policyRepository;
        this.workflowTemplateRepository = workflowTemplateRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("数据已存在，跳过初始化");
            return;
        }

        log.info("=== 开始初始化种子数据 ===");

        initUsers();
        initDepartments();
        initPolicies();
        initWorkflows();

        log.info("=== 种子数据初始化完成 ===");
    }

    private void initUsers() {
        // 普通员工
        User emp = new User();
        emp.setUsername("zhangsan");
        emp.setPassword(passwordEncoder.encode("123456"));
        emp.setRealName("张三");
        emp.setDepartment("技术部");
        emp.setPosition("高级开发工程师");
        emp.setRole(User.Role.EMPLOYEE);
        userRepository.save(emp);

        // 部门经理
        User mgr = new User();
        mgr.setUsername("lisi");
        mgr.setPassword(passwordEncoder.encode("123456"));
        mgr.setRealName("李四");
        mgr.setDepartment("技术部");
        mgr.setPosition("技术经理");
        mgr.setRole(User.Role.MANAGER);
        userRepository.save(mgr);

        // 总监
        User dir = new User();
        dir.setUsername("wangwu");
        dir.setPassword(passwordEncoder.encode("123456"));
        dir.setRealName("王五");
        dir.setDepartment("技术部");
        dir.setPosition("技术总监");
        dir.setRole(User.Role.DIRECTOR);
        userRepository.save(dir);

        // 管理员
        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("123456"));
        admin.setRealName("管理员");
        admin.setDepartment("管理部");
        admin.setPosition("系统管理员");
        admin.setRole(User.Role.ADMIN);
        userRepository.save(admin);

        log.info("创建4个用户: zhangsan(员工), lisi(经理), wangwu(总监), admin(管理员)");
    }

    private void initDepartments() {
        Department tech = new Department();
        tech.setName("技术部");
        tech.setCode("TECH");
        tech.setAnnualBudget(new BigDecimal("500000"));
        tech.setUsedBudget(new BigDecimal("320000"));
        tech.setManagerId(2L);
        tech.setManagerName("李四");
        departmentRepository.save(tech);

        Department market = new Department();
        market.setName("市场部");
        market.setCode("MKT");
        market.setAnnualBudget(new BigDecimal("300000"));
        market.setUsedBudget(new BigDecimal("150000"));
        departmentRepository.save(market);

        Department hr = new Department();
        hr.setName("人力资源部");
        hr.setCode("HR");
        hr.setAnnualBudget(new BigDecimal("200000"));
        hr.setUsedBudget(new BigDecimal("80000"));
        departmentRepository.save(hr);

        log.info("创建3个部门: 技术部(50万/已用32万), 市场部(30万/已用15万), 人力资源部(20万/已用8万)");
    }

    private void initPolicies() {
        // 费用报销政策
        ApprovalPolicy expense1 = new ApprovalPolicy();
        expense1.setName("小额费用报销规则");
        expense1.setApprovalType(ApprovalRequest.ApprovalType.EXPENSE);
        expense1.setContent("金额≤500元的费用报销，只需1级审批(部门经理)；需提供发票。");
        expense1.setMaxAmount(new BigDecimal("500"));
        expense1.setRequiredLevels(1);
        expense1.setRequireAttachment(true);
        expense1.setPriority(10);
        policyRepository.save(expense1);

        ApprovalPolicy expense2 = new ApprovalPolicy();
        expense2.setName("常规费用报销规则");
        expense2.setApprovalType(ApprovalRequest.ApprovalType.EXPENSE);
        expense2.setContent("金额500-5000元的费用报销，需2级审批(部门经理→总监)；需提供发票和费用明细。");
        expense2.setMaxAmount(new BigDecimal("5000"));
        expense2.setRequiredLevels(2);
        expense2.setRequireAttachment(true);
        expense2.setPriority(5);
        policyRepository.save(expense2);

        ApprovalPolicy expense3 = new ApprovalPolicy();
        expense3.setName("大额费用报销规则");
        expense3.setApprovalType(ApprovalRequest.ApprovalType.EXPENSE);
        expense3.setContent("金额>5000元的费用报销，需3级审批(部门经理→总监→财务复核)；需提供正式申请报告。");
        expense3.setMaxAmount(new BigDecimal("100000"));
        expense3.setRequiredLevels(3);
        expense3.setRequireAttachment(true);
        expense3.setPriority(1);
        policyRepository.save(expense3);

        // 请假政策
        ApprovalPolicy leave1 = new ApprovalPolicy();
        leave1.setName("短期请假规则");
        leave1.setApprovalType(ApprovalRequest.ApprovalType.LEAVE);
        leave1.setContent("3天以内(含)请假，1级审批(部门经理)；提前1天申请。");
        leave1.setMaxAmount(new BigDecimal("3"));
        leave1.setRequiredLevels(1);
        leave1.setPriority(10);
        policyRepository.save(leave1);

        ApprovalPolicy leave2 = new ApprovalPolicy();
        leave2.setName("中长期请假规则");
        leave2.setApprovalType(ApprovalRequest.ApprovalType.LEAVE);
        leave2.setContent("3-7天请假，2级审批(部门经理→总监)；提前3天申请。");
        leave2.setMaxAmount(new BigDecimal("7"));
        leave2.setRequiredLevels(2);
        leave2.setPriority(5);
        policyRepository.save(leave2);

        // 采购政策
        ApprovalPolicy purchase1 = new ApprovalPolicy();
        purchase1.setName("常规采购规则");
        purchase1.setApprovalType(ApprovalRequest.ApprovalType.PURCHASE);
        purchase1.setContent("采购金额≤10000元，2级审批(部门经理→总监)；需提供三方比价。");
        purchase1.setMaxAmount(new BigDecimal("10000"));
        purchase1.setRequiredLevels(2);
        purchase1.setRequireAttachment(true);
        purchase1.setPriority(10);
        policyRepository.save(purchase1);

        log.info("创建6条审批政策: 费用报销(3级)、请假(2级)、采购(1级)");
    }

    private void initWorkflows() {
        // 费用报销 - 2级审批
        WorkflowTemplate wf1 = new WorkflowTemplate();
        wf1.setName("费用报销标准流程");
        wf1.setApprovalType(ApprovalRequest.ApprovalType.EXPENSE);
        wf1.setLevels("""
                [
                    {"level":1,"role":"MANAGER","name":"部门经理审批"},
                    {"level":2,"role":"DIRECTOR","name":"总监审批"}
                ]""");
        wf1.setAmountThreshold(new BigDecimal("500"));
        workflowTemplateRepository.save(wf1);

        // 采购 - 3级审批
        WorkflowTemplate wf2 = new WorkflowTemplate();
        wf2.setName("采购审批流程");
        wf2.setApprovalType(ApprovalRequest.ApprovalType.PURCHASE);
        wf2.setLevels("""
                [
                    {"level":1,"role":"MANAGER","name":"部门经理审批"},
                    {"level":2,"role":"DIRECTOR","name":"总监审批"},
                    {"level":3,"role":"ADMIN","name":"财务复核"}
                ]""");
        wf2.setAmountThreshold(new BigDecimal("0"));
        workflowTemplateRepository.save(wf2);

        log.info("创建2个工作流模板: 费用报销(2级)、采购(3级)");
    }
}
