package com.agent.pipeline.controller;

import com.agent.pipeline.service.ScriptCreationAgentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
    public Map<String, Object> createScript(
            @RequestParam String topic,
            @RequestParam(defaultValue = "无特定要求") String requirement,
            @RequestParam(required = false) String sessionId) {
        
        // 如果用户没传 sessionId，我们在这里生成，保证 Service 层的纯净
        String finalSessionId = (sessionId != null && !sessionId.isBlank()) 
                ? sessionId 
                : java.util.UUID.randomUUID().toString();
        
        return scriptService.createScriptBlocking(finalSessionId, topic, requirement);
    }
}
