package com.smartdoc.repository;

import com.smartdoc.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByKbIdOrderByCreatedAtDesc(Long kbId);
}
