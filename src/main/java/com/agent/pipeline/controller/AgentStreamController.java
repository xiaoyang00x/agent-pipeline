package com.agent.pipeline.controller;

import com.agent.pipeline.service.SseStreamManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/agent")
public class AgentStreamController {

    private final SseStreamManager sseStreamManager;

    public AgentStreamController(SseStreamManager sseStreamManager) {
        this.sseStreamManager = sseStreamManager;
    }

    /**
     * 前端建立 SSE 长连接的端点
     */
    @GetMapping(value = "/{sessionId}/stream", produces = "text/event-stream")
    public SseEmitter stream(@PathVariable String sessionId) {
        return sseStreamManager.subscribe(sessionId);
    }
}
