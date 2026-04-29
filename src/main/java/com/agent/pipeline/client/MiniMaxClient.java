package com.agent.pipeline.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * MiniMax HTTP 直连客户端
 *
 * 【为什么不用 Spring AI 的 MiniMaxChatModel？】
 * spring-ai-alibaba-agent-framework:1.1.2.0 内部依赖 spring-ai-core:1.0.0-M2，
 * 该版本 Message 接口移除了 getContent() 方法（挪到了父接口 Content 中），
 * 但 MiniMaxChatModel/OpenAiChatModel 的编译字节码还在调旧接口，运行时报 NoSuchMethodError。
 *
 * 解决思路：绕过有 Bug 的框架代码，直接用 Java 原生 HttpClient 调用
 * MiniMax 的 OpenAI 兼容接口（/v1/chat/completions），完全稳定可靠。
 */
@Service
public class MiniMaxClient {

    private static final Logger log = LoggerFactory.getLogger(MiniMaxClient.class);

    // 从 application.yml 读取，保持配置集中管理
    @Value("${minimax.api-key}")
    private String apiKey;

    @Value("${minimax.base-url}")
    private String baseUrl;

    @Value("${minimax.model}")
    private String model;

    // Java 11+ 自带的 HttpClient，不需要引入额外依赖
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 发送一条用户消息给 MiniMax，返回模型的文本回复。
     *
     * @param userPrompt 用户的提问 / 指令
     * @return 模型生成的文本内容
     */
    public String chat(String userPrompt) {
        log.info("🚀 准备调用 MiniMax API，Prompt 长度: {}", userPrompt.length());
        log.debug("Prompt 内容: {}", userPrompt);

        // 构造符合 OpenAI 标准的请求体 JSON
        String requestBody = String.format("""
                {
                  "model": "%s",
                  "messages": [
                    {
                      "role": "user",
                      "content": "%s"
                    }
                  ]
                }
                """, model, escapeJson(userPrompt));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", "Java/HttpClient")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            log.info("📡 正在通过 RestTemplate 调用 MiniMax... URL: {}", baseUrl + "/v1/chat/completions");
            
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            headers.set("User-Agent", "Java/RestTemplate");
            
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(requestBody, headers);
            
            String responseBody = restTemplate.postForObject(baseUrl + "/v1/chat/completions", entity, String.class);
            log.info("📥 收到 MiniMax 响应，Body长度: {}", responseBody != null ? responseBody.length() : 0);

            if (responseBody == null) {
                throw new RuntimeException("MiniMax API 返回内容为空");
            }

            String content = extractContent(responseBody);
            log.info("✅ MiniMax API 调用成功，返回内容长度: {}", content.length());
            return content;

        } catch (Exception e) {
            throw new RuntimeException("调用 MiniMax API 时发生异常", e);
        }
    }

    /**
     * 从 OpenAI 格式的 JSON 响应中提取 content 字段文本。
     * 响应格式: {"choices": [{"message": {"content": "..."}}]}
     */
    private String extractContent(String jsonBody) {
        // 简单字符串提取，避免引入 Jackson 依赖
        // 格式: "content": "actual text"
        String key = "\"content\":";
        int start = jsonBody.indexOf(key);
        if (start == -1) {
            throw new RuntimeException("MiniMax 响应格式异常，找不到 content 字段: " + jsonBody.substring(0, Math.min(200, jsonBody.length())));
        }
        start += key.length();

        // 跳过空白和开头引号
        while (start < jsonBody.length() && (jsonBody.charAt(start) == ' ' || jsonBody.charAt(start) == '\n')) {
            start++;
        }
        if (jsonBody.charAt(start) == '"') {
            start++;
        }

        // 找到结尾引号（需要处理转义的引号 \"）
        StringBuilder result = new StringBuilder();
        int i = start;
        while (i < jsonBody.length()) {
            char c = jsonBody.charAt(i);
            if (c == '\\' && i + 1 < jsonBody.length()) {
                char next = jsonBody.charAt(i + 1);
                switch (next) {
                    case '"' -> result.append('"');
                    case 'n' -> result.append('\n');
                    case 't' -> result.append('\t');
                    case '\\' -> result.append('\\');
                    default -> result.append(next);
                }
                i += 2;
            } else if (c == '"') {
                break;
            } else {
                result.append(c);
                i++;
            }
        }

        return result.toString();
    }

    /**
     * 对 prompt 文本进行 JSON 转义，防止引号等特殊字符破坏请求 JSON 结构。
     */
    private String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
