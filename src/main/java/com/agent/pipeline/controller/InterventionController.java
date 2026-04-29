package com.agent.pipeline.controller;

import com.agent.pipeline.model.InterventionEntity;
import com.agent.pipeline.service.InterventionAssistantService;
import com.agent.pipeline.service.ScriptCreationAgentService;
import com.alibaba.cloud.ai.graph.NodeOutput;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 人机干预控制器
 */
@RestController
@RequestMapping("/intervention")
public class InterventionController {

    private final InterventionAssistantService assistantService;
    private final ScriptCreationAgentService agentService;

    public InterventionController(InterventionAssistantService assistantService, 
                                  ScriptCreationAgentService agentService) {
        this.assistantService = assistantService;
        this.agentService = agentService;
    }

    /**
     * 获取参谋建议
     */
    @GetMapping("/{sessionId}/advice")
    public InterventionEntity getAdvice(@PathVariable String sessionId) {
        return assistantService.getLatestIntervention(sessionId);
    }

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 提交反馈并恢复执行
     * 
     * 使用 SSE (Server-Sent Events) 返回后续节点的流式输出
     */
    @PostMapping(value = "/{sessionId}/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> resume(@PathVariable String sessionId, 
                               @RequestBody Map<String, String> payload) {
        String feedback = payload.get("feedback");
        
        return agentService.resumeScriptStream(sessionId, feedback)
                .map(nodeOutput -> {
                    try {
                        return objectMapper.writeValueAsString(nodeOutput);
                    } catch (Exception e) {
                        return "{\"error\": \"序列化失败: " + e.getMessage() + "\"}";
                    }
                });
    }
}
