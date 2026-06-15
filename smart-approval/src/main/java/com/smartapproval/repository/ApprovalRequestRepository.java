package com.smartapproval.repository;

import com.smartapproval.entity.ApprovalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    /** 按申请人查询 */
    Page<ApprovalRequest> findByApplicantIdOrderByCreatedAtDesc(Long applicantId, Pageable pageable);

    /** 按状态查询(当前层审批人查看待审批) */
    List<ApprovalRequest> findByStatusOrderByCreatedAtDesc(ApprovalRequest.ApprovalStatus status);

    /** 按类型和状态统计 */
    long countByTypeAndStatus(ApprovalRequest.ApprovalType type, 
                              ApprovalRequest.ApprovalStatus status);

    /** 查询某人的历史审批统计 */
    @Query("SELECT COUNT(a) FROM ApprovalRequest a WHERE a.applicantId = :userId " +
           "AND a.type = :type AND a.createdAt >= :thirtyDaysAgo")
    long countRecentByUserAndType(@Param("userId") Long userId,
                                   @Param("type") ApprovalRequest.ApprovalType type,
                                   @Param("thirtyDaysAgo") java.time.LocalDateTime thirtyDaysAgo);

    /** 查询相似审批(同类型、相近金额) */
    @Query("SELECT a FROM ApprovalRequest a WHERE a.type = :type " +
           "AND a.status IN ('APPROVED', 'REJECTED') " +
           "ORDER BY a.createdAt DESC")
    List<ApprovalRequest> findSimilarApprovals(@Param("type") ApprovalRequest.ApprovalType type);

    /** 本月部门申请汇总 */
    @Query("SELECT COUNT(a), COALESCE(SUM(a.amount), 0) FROM ApprovalRequest a " +
           "WHERE a.applicantName LIKE CONCAT('%', :deptPrefix, '%') " +
           "AND a.createdAt >= :thirtyDaysAgo AND a.status = 'APPROVED'")
    List<Object[]> getDeptMonthlyStats(@Param("deptPrefix") String deptPrefix,
                                        @Param("thirtyDaysAgo") java.time.LocalDateTime thirtyDaysAgo);
}
