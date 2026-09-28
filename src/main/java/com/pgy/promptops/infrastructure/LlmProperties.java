package com.pgy.promptops.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模型接入配置。
 * <p>
 * 只支持 OpenAI 兼容协议 —— 因为 DeepSeek、通义、智谱、月之暗面都实现了它，
 * 换厂商只需要改 base-url 和 model 两行。
 */
@ConfigurationProperties(prefix = "prompt-ops.llm")
public record LlmProperties(
        String baseUrl,
        String apiKey,
        String model,
        int timeoutSeconds,
        boolean mockWhenNoKey
) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
