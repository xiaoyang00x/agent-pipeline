package com.agent.pipeline.client;

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
        log.info("🚀 准备调用真实 MiniMax API，Prompt 长度: {}", prompt.length());
        
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

        try {
            String fullUrl = baseUrl + "/v1/text/chatcompletion_v2"; // MiniMax v2/chat 端点
            log.info("📡 正在调用真实 MiniMax... URL: {}", fullUrl);
            Map<String, Object> response = restTemplate.postForObject(fullUrl, entity, Map.class);
            
            log.info("🤖 MiniMax 原始响应: {}", response);
            
            if (response != null) {
                // 判断是否包含 reply (旧版)
                if (response.containsKey("reply") && response.get("reply") != null) {
                    return (String) response.get("reply");
                }
                // 判断是否包含 choices (新版)
                if (response.get("choices") != null) {
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                    if (!choices.isEmpty()) {
                        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                        return (String) message.get("content");
                    }
                }
                // 如果是报错，通常有 base_resp
                if (response.get("base_resp") != null) {
                    return "MiniMax API 报错: " + response.get("base_resp").toString();
                }
            }
            return "MiniMax 返回格式异常或为空: " + response;
        } catch (Exception e) {
            log.error("❌ 调用真实 MiniMax 失败", e);
            return "Error: " + e.getMessage();
        }
    }
}
