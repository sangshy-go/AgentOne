package com.agentone.agent.config;

import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope Java 2.0 配置
 * 将 Model 注册为 Spring Bean，供 ChatServiceImpl 注入使用
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "agentscope")
public class AgentScopeConfig {

    private OpenAIConfig openai = new OpenAIConfig();

    public OpenAIConfig getOpenai() {
        return openai;
    }

    public void setOpenai(OpenAIConfig openai) {
        this.openai = openai;
    }

    @Bean
    public Model agentScopeModel() {
        log.info("初始化 AgentScope Model: provider=openai, model={}, baseUrl={}",
                openai.getModelName(), openai.getBaseUrl());

        return OpenAIChatModel.builder()
                .modelName(openai.getModelName())
                .apiKey(openai.getApiKey())
                .baseUrl(openai.getBaseUrl())
                .stream(openai.getStream())
                .build();
    }

    public static class OpenAIConfig {
        private boolean enabled = true;
        private String apiKey = "sk-placeholder";
        private String modelName = "gpt-4o-mini";
        private String baseUrl = "https://api.openai.com";
        private boolean stream = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public boolean getStream() { return stream; }
        public void setStream(boolean stream) { this.stream = stream; }
    }
}
