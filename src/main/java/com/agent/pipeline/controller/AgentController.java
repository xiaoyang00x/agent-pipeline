package com.agent.pipeline.controller;

import com.agent.pipeline.service.ScriptCreationAgentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Agent 接口控制器
 *
 * GET /agent/create-script?topic=xxx&requirement=yyy
 * 返回 JSON，包含：大纲、剧本、审稿意见等完整字段
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final ScriptCreationAgentService scriptService;

    public AgentController(ScriptCreationAgentService scriptService) {
        this.scriptService = scriptService;
    }

    /**
     * 执行完整的剧本生成工作流，返回结构化 JSON 结果。
     *
     * @param topic       创作主题
     * @param requirement 附加要求
     * @param sessionId   会话 ID（可选，如果不传则自动生成）
     * @return 包含大纲、剧本、审稿意见等的完整 JSON
     */
    @GetMapping(value = "/create-script", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createScript(
            @RequestParam String topic,
            @RequestParam(defaultValue = "无特定要求") String requirement,
            @RequestParam(required = false) String sessionId) {
        
        String finalSessionId = (sessionId != null && !sessionId.isBlank()) 
                ? sessionId 
                : java.util.UUID.randomUUID().toString();
        
        // 核心改造：Service 内部已处理异步订阅，直接调用即可
        scriptService.createScriptAsync(topic, requirement, finalSessionId);
            
        return ResponseEntity.accepted().body(Map.of(
            "sessionId", finalSessionId,
            "status", "RUNNING",
            "message", "工作流已在后台启动，请轮询状态"
        ));
    }

    /**
     * 查询工作流当前状态与变量数据
     */
    @GetMapping(value = "/{sessionId}/state", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getGraphState(@PathVariable String sessionId) {
        Map<String, Object> stateData = scriptService.getGraphState(sessionId);
        return ResponseEntity.ok(stateData);
    }
}
