package com.smartdoc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdoc.dto.ChatRequest;
import com.smartdoc.entity.Conversation;
import com.smartdoc.entity.Message;
import com.smartdoc.repository.ConversationRepository;
import com.smartdoc.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    @Value("${rag.top-k:5}")
    private int topK;

    // RAG 系统提示词模板
    private static final String RAG_SYSTEM_PROMPT = """
            你是一个专业的知识库助手。请根据以下从知识库中检索到的相关文档片段来回答用户问题。
            
            【检索到的相关内容】
            {context}
            
            【回答要求】
            1. 优先基于检索到的内容回答，保证准确性
            2. 如果检索内容不足以完整回答，可以补充你的知识，但需明确说明
            3. 回答要简洁清晰，条理分明
            4. 如果问题与检索内容完全无关，请如实说明
            """;

    /**
     * RAG 问答（SSE 流式返回）
     */
    public Flux<String> chatWithKnowledge(ChatRequest request, Long userId) {
        Long conversationId = request.getConversationId();
        Long kbId = request.getKbId();
        String userQuestion = request.getMessage();

        // 1. 获取或创建会话
        Conversation conversation = getOrCreateConversation(conversationId, userId, kbId);

        // 2. 保存用户消息
        Message userMsg = new Message();
        userMsg.setConversationId(conversation.getId());
        userMsg.setRole("USER");
        userMsg.setContent(userQuestion != null ? userQuestion : "");
        userMsg.setSources(null); // 用户消息不需要来源
        messageRepository.save(userMsg);

        // 3. 从向量库检索相关文档（按知识库 ID 过滤）
        List<Document> relevantDocs = retrieveRelevantDocs(userQuestion, kbId);

        // 4. 构建上下文
        String context = relevantDocs.stream()
                .map(doc -> String.format("[来源：%s]\n%s",
                        doc.getMetadata().getOrDefault("file_name", "未知"),
                        doc.getText()))
                .collect(Collectors.joining("\n\n---\n\n"));

        // 5. 获取历史消息（最近 6 条，保证上下文连贯）
        List<org.springframework.ai.chat.messages.Message> historyMessages =
                buildHistoryMessages(conversation.getId(), 6);

        // 6. 构建 System Prompt
        String systemContent = new SystemPromptTemplate(RAG_SYSTEM_PROMPT)
                .render(Map.of("context", context.isEmpty() ? "暂无相关文档内容" : context));

        // 7. 构建完整 Prompt（System Prompt 放入消息列表开头）
        List<org.springframework.ai.chat.messages.Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage(systemContent));
        promptMessages.addAll(historyMessages);
        promptMessages.add(new UserMessage(userQuestion != null ? userQuestion : ""));

        Prompt prompt = new Prompt(promptMessages);

        // 8. 收集完整回答用于持久化
        StringBuilder fullResponse = new StringBuilder();
        List<Map<String, Object>> sources = buildSourcesMetadata(relevantDocs);

        // 9. SSE 流式调用
        return chatClient.prompt(prompt)
                .stream()
                .content()
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    // 流结束后保存 AI 回答到数据库
                    try {
                        Message assistantMsg = new Message();
                        assistantMsg.setConversationId(conversation.getId());
                        assistantMsg.setRole("ASSISTANT");
                        assistantMsg.setContent(fullResponse.toString());
                        assistantMsg.setSources(objectMapper.writeValueAsString(sources) != null 
                                ? objectMapper.writeValueAsString(sources) 
                                : "[]");
                        messageRepository.save(assistantMsg);

                        // 自动更新会话标题（取第一条用户消息的前 20 字）
                        if ("新对话".equals(conversation.getTitle()) && userQuestion.length() > 0) {
                            String title = userQuestion.length() > 20
                                    ? userQuestion.substring(0, 20) + "..."
                                    : userQuestion;
                            conversation.setTitle(title);
                            conversationRepository.save(conversation);
                        }
                    } catch (Exception e) {
                        log.error("保存 AI 回答失败", e);
                    }
                });
    }

    /**
     * 从向量库按知识库 ID 过滤检索相关文档
     */
    private List<Document> retrieveRelevantDocs(String query, Long kbId) {
        if (kbId == null) return List.of();
        try {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .filterExpression(b.eq("kb_id", String.valueOf(kbId)).build())
                    .similarityThreshold(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL)
                    .build();  // ChromaDB L2距离转相似度后数值极低，不设阈值
            return vectorStore.similaritySearch(searchRequest);
        } catch (Exception e) {
            log.warn("向量检索失败，降级为无知识库模式", e);
            return List.of();
        }
    }

    /**
     * 构建历史消息列表（用于多轮对话）
     */
    private List<org.springframework.ai.chat.messages.Message> buildHistoryMessages(
            Long conversationId, int limit) {
        List<Message> history = messageRepository
                .findTopNByConversationIdOrderByCreatedAtDesc(conversationId, limit);
        Collections.reverse(history);  // 按时间正序

        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        for (Message msg : history) {
            if ("USER".equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent() != null ? msg.getContent() : ""));
            } else {
                messages.add(new AssistantMessage(msg.getContent() != null ? msg.getContent() : ""));
            }
        }
        return messages;
    }

    /**
     * 获取或创建会话
     */
    private Conversation getOrCreateConversation(Long conversationId, Long userId, Long kbId) {
        if (conversationId != null) {
            return conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new RuntimeException("会话不存在"));
        }
        Conversation conv = new Conversation();
        conv.setUserId(userId);
        conv.setKbId(kbId);
        conv.setTitle("新对话");
        return conversationRepository.save(conv);
    }

    private List<Map<String, Object>> buildSourcesMetadata(List<Document> docs) {
        return docs.stream().map(doc -> {
            Map<String, Object> source = new HashMap<>();
            source.put("fileName", doc.getMetadata().getOrDefault("file_name", "未知"));
            source.put("content", doc.getText().length() > 200
                    ? doc.getText().substring(0, 200) + "..."
                    : doc.getText());
            return source;
        }).collect(Collectors.toList());
    }

    public List<Conversation> listConversations(Long userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public List<Message> getMessages(Long conversationId, Long userId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("会话不存在"));
        if (!conv.getUserId().equals(userId)) throw new RuntimeException("无权访问");
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    public void deleteConversation(Long conversationId, Long userId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("会话不存在"));
        if (!conv.getUserId().equals(userId)) throw new RuntimeException("无权操作");
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
    }
}
