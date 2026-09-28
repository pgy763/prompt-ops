package com.pgy.promptops.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 根据有没有配 API Key，决定用真实模型还是 Mock。
 * <p>
 * 用显式的 @Bean 方法而不是条件注解，是因为这个判断逻辑值得写在一眼能看到的地方：
 * <b>没配 key 不是错误，是「离线试用模式」。</b>
 */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    LlmClient llmClient(LlmProperties props) {
        if (props.hasApiKey()) {
            log.info("模型接入就绪：{} / {}", props.baseUrl(), props.model());
            return new OpenAiCompatibleLlmClient(props);
        }
        log.warn("未检测到 LLM_API_KEY —— 已切换为 MOCK 模式。"
                + "流程可以完整跑通，但输出是占位内容，评估结果不代表真实效果。");
        return new MockLlmClient();
    }
}
