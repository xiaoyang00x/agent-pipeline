package com.agent.pipeline.controller;

import com.agent.pipeline.infrastructure.persistence.entity.InterventionEntity;
import com.agent.pipeline.service.InterventionAssistantService;
import com.agent.pipeline.service.ScriptCreationAgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

/**
 * 人机干预控制器
 */
@RestController
@RequestMapping("/intervention")
public class InterventionController {

    private final InterventionAssistantService assistantService;
    private final ScriptCreationAgentService agentService;
    private final ObjectMapper objectMapper;

    public InterventionController(InterventionAssistantService assistantService,
                                  ScriptCreationAgentService agentService,
                                  ObjectMapper objectMapper) {
        this.assistantService = assistantService;
        this.agentService = agentService;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取参谋建议
     */
    @GetMapping("/{sessionId}/advice")
    public InterventionEntity getAdvice(@PathVariable String sessionId) {
        return assistantService.getLatestIntervention(sessionId);
    }

    /**
     * 提交反馈并恢复执行（异步执行，统一轮询架构）
     */
    @PostMapping(value = "/{sessionId}/resume", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> resume(@PathVariable String sessionId,
                                                      @RequestBody Map<String, String> payload) {
        String feedback = payload.get("feedback");

        // 核心改造：Service 内部已处理异步订阅，直接调用即可
        agentService.resumeScriptAsync(sessionId, feedback);

        return ResponseEntity.accepted().body(Map.of(
            "sessionId", sessionId,
            "status", "RUNNING",
            "message", "接关指令已发送，工作流继续在后台执行"
        ));
    }
}
