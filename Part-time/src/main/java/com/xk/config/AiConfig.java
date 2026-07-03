package com.xk.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultOptions(OllamaOptions.builder()
                        .model("qwen2.5:3b")
                        .temperature(0.0)
                        .topP(0.9)
                        .numPredict(1024)      // 适当增加输出长度
                        .format("json")        // 强制 JSON 输出（Ollama 0.5+ 支持）
                        .build())
                .build();
    }
}