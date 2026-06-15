package com.smartdoc.controller;

import com.smartdoc.dto.ChatRequest;
import com.smartdoc.dto.Result;
import com.smartdoc.service.ChatService;
import com.smartdoc.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 核心：RAG 问答接口（SSE 流式）
     * 前端通过 EventSource 或 fetch + ReadableStream 接收
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody @Valid ChatRequest request) {
        Long userId = getCurrentUserIdOrDefault();
        return chatService.chatWithKnowledge(request, userId);
    }

    /**
     * 非流式聊天接口（用于简单的前端）
     */
    @PostMapping
    public Result<?> chat(@RequestBody @Valid ChatRequest request) {
        Long userId = getCurrentUserIdOrDefault();
        String answer = chatService.chatWithKnowledge(request, userId)
                .collectList()
                .map(list -> String.join("", list))
                .block();
        return Result.success(Map.of("answer", answer));
    }

    /**
     * 获取当前用户ID，如果未登录则返回默认ID
     */
    private Long getCurrentUserIdOrDefault() {
        try {
            return SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            return 1L; // 默认用户ID
        }
    }

    /**
     * 获取当前用户的所有会话列表
     */
    @GetMapping("/conversations")
    public Result<?> listConversations() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(chatService.listConversations(userId));
    }

    /**
     * 获取指定会话的历史消息
     */
    @GetMapping("/conversations/{id}/messages")
    public Result<?> getMessages(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(chatService.getMessages(id, userId));
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/conversations/{id}")
    public Result<?> deleteConversation(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        chatService.deleteConversation(id, userId);
        return Result.success("删除成功");
    }
}
