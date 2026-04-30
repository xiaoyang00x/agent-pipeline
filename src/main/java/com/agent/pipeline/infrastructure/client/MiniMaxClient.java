package com.agent.pipeline.infrastructure.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 真实的 MiniMax 客户端
 */
@Component
@Profile("!mock")
public class MiniMaxClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MiniMaxClient.class);

    @Value("${minimax.base-url:https://api.minimax.chat}")
    private String baseUrl;

    @Value("${minimax.api-key:}")
    private String apiKey;

    @Value("${minimax.group.id:}")
    private String groupId;

    @Value("${minimax.model:abab6.5-chat}")
    private String model;

    @Value("${minimax.max-tokens:16384}")
    private int maxTokens;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String chat(String prompt) {
        int maxRetries = 3;
        int retryCount = 0;
        long delay = 1000; // 初始延迟 1 秒

        while (retryCount < maxRetries) {
            try {
                return callMiniMaxApi(prompt);
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

    private String callMiniMaxApi(String prompt) {
        log.info("🚀 正在调用真实 MiniMax API，Prompt 长度: {}", prompt.length());
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", Collections.singletonList(
                Map.of("role", "user", "content", prompt)
        ));
        requestBody.put("tokens_to_generate", maxTokens);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String fullUrl = baseUrl + "/v1/text/chatcompletion_v2";
        log.info("📡 正在发送 HTTP 请求... URL: {}", fullUrl);
        
        Map<String, Object> response = restTemplate.postForObject(fullUrl, entity, Map.class);
        
        if (response != null) {
            // 兼容新旧版返回格式
            if (response.containsKey("reply") && response.get("reply") != null) {
                return (String) response.get("reply");
            }
            if (response.get("choices") != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }
            if (response.get("base_resp") != null) {
                throw new RuntimeException("MiniMax 业务报错: " + response.get("base_resp").toString());
            }
        }
        throw new RuntimeException("MiniMax 返回格式异常或为空");
    }
}
