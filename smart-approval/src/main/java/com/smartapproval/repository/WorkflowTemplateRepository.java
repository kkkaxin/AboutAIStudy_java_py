package com.smartapproval.repository;

import com.smartapproval.entity.ApprovalRequest;
import com.smartapproval.entity.WorkflowTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WorkflowTemplateRepository extends JpaRepository<WorkflowTemplate, Long> {

    /** 按审批类型和金额阈值查找匹配的模板 */
    Optional<WorkflowTemplate> findFirstByApprovalTypeAndEnabledTrueAndAmountThresholdLessThanEqualOrderByAmountThresholdDesc(
            ApprovalRequest.ApprovalType type, java.math.BigDecimal amount);
}
