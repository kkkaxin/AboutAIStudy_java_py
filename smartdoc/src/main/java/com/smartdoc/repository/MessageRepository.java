package com.smartdoc.repository;

import com.smartdoc.entity.Message;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    @Query("SELECT m FROM Message m WHERE m.conversationId = :conversationId ORDER BY m.createdAt DESC")
    List<Message> findTopNByConversationIdOrderByCreatedAtDesc(Long conversationId, PageRequest pageable);

    default List<Message> findTopNByConversationIdOrderByCreatedAtDesc(Long conversationId, int n) {
        return findTopNByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, n));
    }

    @Modifying
    @Transactional
    void deleteByConversationId(Long conversationId);
}
