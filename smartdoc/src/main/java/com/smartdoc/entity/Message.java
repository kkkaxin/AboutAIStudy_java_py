package com.smartdoc.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sd_message")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /**
     * USER      - 用户消息
     * ASSISTANT - AI 回答
     */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /**
     * RAG 引用的来源文档片段（JSON 格式存储）
     * 示例：[{"fileName":"xxx.pdf","content":"相关内容片段...","score":0.85}]
     */
    @Column(columnDefinition = "JSON")
    private String sources;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
