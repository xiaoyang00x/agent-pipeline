package com.agent.pipeline.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseStreamManager {

    private static final Logger log = LoggerFactory.getLogger(SseStreamManager.class);
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String sessionId) {
        // 设置30分钟超时，防止大图运行断开
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitters.put(sessionId, emitter);

        emitter.onCompletion(() -> emitters.remove(sessionId));
        emitter.onTimeout(() -> emitters.remove(sessionId));
        emitter.onError(e -> emitters.remove(sessionId));

        try {
            // 发送连接成功事件
            emitter.send(SseEmitter.event().name("connected").data("connected"));
            log.info("📡 SSE 连接已建立: {}", sessionId);
        } catch (IOException e) {
            emitters.remove(sessionId);
        }
        return emitter;
    }

    public void sendToken(String sessionId, String type, String token) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter != null) {
            try {
                // 将 token 包装为 JSON 数据结构并推送
                Map<String, String> payload = Map.of("type", type, "chunk", token);
                emitter.send(SseEmitter.event().name("message").data(payload));
            } catch (IOException e) {
                emitters.remove(sessionId);
                log.warn("⚠️ 向 {} 发送 SSE 推流失败: {}", sessionId, e.getMessage());
            }
        }
    }
}
