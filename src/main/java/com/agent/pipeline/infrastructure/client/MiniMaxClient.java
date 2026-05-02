package com.agent.pipeline.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 真实的 MiniMax 客户端 (WebFlux 流式版)
 */
@Component
@Profile("!mock")
public class MiniMaxClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MiniMaxClient.class);

    @Value("${minimax.base-url:https://api.minimax.chat}")
    private String baseUrl;

    @Value("${minimax.api-key:}")
    private String apiKey;

    @Value("${minimax.model:abab6.5-chat}")
    private String model;

    @Value("${minimax.max-tokens:16384}")
    private int maxTokens;

    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String chat(String prompt) {
        return chatStream(prompt, null);
    }

    @Override
    public String chatStream(String prompt, Consumer<String> onToken) {
        int maxRetries = 3;
        int retryCount = 0;
        long delay = 1000; // 初始延迟 1 秒

        while (retryCount < maxRetries) {
            try {
                return callMiniMaxApiStream(prompt, onToken);
            } catch (Exception e) {
                retryCount++;
                log.warn("⚠️ 调用 MiniMax 失败 (尝试 {}/{}), 错误: {}", retryCount, maxRetries, e.getMessage());
                
                if (retryCount >= maxRetries) {
                    log.error("❌ 达到最大重试次数，调用彻底失败", e);
                    return "Error: " + e.getMessage();
                }

                try {
                    log.info("⏳ 正在等待 {}ms 后进行下一次重试...", delay);
                    Thread.sleep(delay);
                    delay *= 2; // 指数退避
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return "Error: 重试被中断";
                }
            }
        }
        return "Error: 未知错误";
    }

    private String callMiniMaxApiStream(String prompt, Consumer<String> onToken) {
        log.info("🚀 正在调用真实 MiniMax API (流式模式)，Prompt 长度: {}", prompt.length());
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", Collections.singletonList(
                Map.of("role", "user", "content", prompt)
        ));
        requestBody.put("tokens_to_generate", maxTokens);
        requestBody.put("stream", true);

        String fullUrl = baseUrl + "/v1/text/chatcompletion_v2";
        log.info("📡 正在发送 HTTP 请求... URL: {}", fullUrl);

        StringBuilder fullResponse = new StringBuilder();

        Flux<ServerSentEvent<String>> stream = webClient.post()
                .uri(fullUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {});

        stream.doOnNext(event -> {
            String data = event.data();
            if (data != null && !data.equals("[DONE]")) {
                try {
                    JsonNode root = objectMapper.readTree(data);
                    if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                        JsonNode delta = root.get("choices").get(0).get("delta");
                        if (delta != null && delta.has("content")) {
                            String token = delta.get("content").asText();
                            fullResponse.append(token);
                            if (onToken != null) {
                                onToken.accept(token);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析 SSE 数据包异常: {}", e.getMessage());
                }
            }
        }).blockLast();

        return fullResponse.toString();
    }
}
