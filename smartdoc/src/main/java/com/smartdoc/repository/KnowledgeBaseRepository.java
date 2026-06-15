package com.smartdoc.repository;

import com.smartdoc.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
    List<KnowledgeBase> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE KnowledgeBase k SET k.docCount = k.docCount + 1 WHERE k.id = :kbId")
    void incrementDocCount(Long kbId);

    @Modifying
    @Transactional
    @Query("UPDATE KnowledgeBase k SET k.docCount = k.docCount - 1 WHERE k.id = :kbId AND k.docCount > 0")
    void decrementDocCount(Long kbId);
}
