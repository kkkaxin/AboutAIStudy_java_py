package com.smartapproval.repository;

import com.smartapproval.entity.ApprovalRecord;
import com.smartapproval.entity.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {

    /** 按申请ID查询审批记录(按层级排序) */
    List<ApprovalRecord> findByRequestIdOrderByLevelAsc(Long requestId);

    /** 统计审批人的审批记录 */
    long countByApproverIdAndAction(Long approverId, ApprovalRecord.ApprovalAction action);

    /** 统计某申请的AI采纳情况 */
    @Query("SELECT COUNT(r), " +
           "SUM(CASE WHEN r.aiAccepted = true THEN 1 ELSE 0 END) " +
           "FROM ApprovalRecord r WHERE r.requestId = :requestId")
    List<Object[]> getAiAcceptanceStats(@Param("requestId") Long requestId);
}
