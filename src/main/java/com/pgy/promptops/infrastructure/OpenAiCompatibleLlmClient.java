package com.pgy.promptops.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 走 OpenAI 兼容协议的模型客户端。
 * <p>
 * 两个刻意的设计：
 * <ul>
 *   <li>连接超时与读超时分开设 —— 见 llm-dev-pitfalls 里「流式请求设了总超时」那条</li>
 *   <li>响应 DTO 全部标注 ignoreUnknown —— 国内厂商返回的字段比 OpenAI 官方多，
 *       不忽略未知字段会在换厂商时直接炸</li>
 * </ul>
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final RestClient http;
    private final String model;

    public OpenAiCompatibleLlmClient(LlmProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(props.timeoutSeconds() * 1000);

        this.http = RestClient.builder()
                .requestFactory(factory)
                // 注意：base-url 要写到 /v1 为止，/chat/completions 由下面自己拼
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .build();
        this.model = props.model();
    }

    @Override
    public String complete(String prompt) {
        Map<String, Object> body = Map.<String, Object>of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.2
        );

        ChatCompletionResponse response = http.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ChatCompletionResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("模型返回内容为空，无法评估");
        }
        return response.choices().get(0).message().content();
    }

    @Override
    public String modelName() {
        return model;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatCompletionResponse(List<Choice> choices) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Choice(Message message) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Message(String content) {
        }
    }
}
