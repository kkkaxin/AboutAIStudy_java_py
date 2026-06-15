package com.smartdoc.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AppConfig {

    /**
     * 配置 Spring AI ChatClient（基于 ChatModel 自动配置）
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是 SmartDoc 智能助手，专注于帮助用户从上传的文档中获取信息。
                        请用中文回答，保持专业、准确、简洁的风格。
                        """)
                .build();
    }
}
