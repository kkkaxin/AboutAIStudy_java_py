package com.smartdoc.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private Long conversationId;  // 已有会话ID，为空则新建
    private Long kbId;            // 知识库ID，为空则纯对话模式
    private String message;       // 用户输入
}
