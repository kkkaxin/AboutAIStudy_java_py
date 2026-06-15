package com.smartapproval.repository;

import com.smartapproval.entity.ApprovalPolicy;
import com.smartapproval.entity.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ApprovalPolicyRepository extends JpaRepository<ApprovalPolicy, Long> {

    /** 按审批类型查找启用的政策 */
    List<ApprovalPolicy> findByApprovalTypeAndEnabledTrue(ApprovalRequest.ApprovalType type);

    /** 按类型和优先级排序 */
    List<ApprovalPolicy> findByApprovalTypeAndEnabledTrueOrderByPriorityDesc(
            ApprovalRequest.ApprovalType type);
}
