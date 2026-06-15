package com.smartapproval.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 配置 - 创建 ChatClient Bean
 *
 * 函数调用采用"手动注入"模式：由 AIApprovalService 直接调用业务函数获取数据，
 * 将结果嵌入 Prompt，避免对 ChatClient.Builder API 版本的强依赖。
 */
@Configuration
public class AppConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
