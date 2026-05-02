package com.agent.pipeline.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptableAction;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.agent.pipeline.infrastructure.client.LlmClient;
import com.agent.pipeline.config.WorkflowProperties;
import com.agent.pipeline.workflow.state.ScriptGraphState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.agent.pipeline.service.SseStreamManager;

import java.util.Map;
import java.util.Optional;

/**
 * 策划节点 (Planner)
 */
public class PlannerNode implements NodeAction, InterruptableAction {

    private static final Logger log = LoggerFactory.getLogger(PlannerNode.class);

    private final LlmClient llmClient;
    private final WorkflowProperties properties;
    private final SseStreamManager sseStreamManager;

    public PlannerNode(LlmClient llmClient, WorkflowProperties properties, SseStreamManager sseStreamManager) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.sseStreamManager = sseStreamManager;
    }

    /**
     * 节点执行前中断
     */
    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeName, OverAllState state, RunnableConfig config) {
        log.info("🔍 [PlannerNode] 执行前中断检查. 节点名: {}, 策略模式: {}", nodeName, properties.getMode());
        if (properties.shouldInterrupt("planner")) {
             log.info("⏸️ [PlannerNode] 触发‘执行前’中断信号！");
             return Optional.of(InterruptionMetadata.builder().nodeId(nodeName).build());
        }
        return Optional.empty();
    }

    /**
     * 节点执行后中断
     */
    @Override
    public Optional<InterruptionMetadata> interruptAfter(String nodeName, OverAllState state, Map<String, Object> lastResult, RunnableConfig config) {
        log.info("🔍 [PlannerNode] 执行后中断检查. 节点名: {}, 策略模式: {}", nodeName, properties.getMode());
        if (properties.shouldInterrupt("planner")) {
            log.info("⏸️ [PlannerNode] 触发‘执行后’中断信号！");
            return Optional.of(InterruptionMetadata.builder().nodeId(nodeName).build());
        }
        return Optional.empty();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("🎬 [策划节点] 开始策划大纲...");

        String topic = state.value(ScriptGraphState.KEY_TOPIC).map(v -> (String) v).orElse("未知主题");
        String requirement = state.value(ScriptGraphState.KEY_REQUIREMENT).map(v -> (String) v).orElse("无");
        String rawFeedback = state.value(ScriptGraphState.KEY_HUMAN_INTERVENTION).map(v -> (String) v).orElse("");
        String humanFeedback = rawFeedback.replace("[REJECT]", "").replace("[APPROVE]", "").trim();
        String oldOutline = state.value(ScriptGraphState.KEY_OUTLINE).map(v -> (String) v).orElse("");

        String prompt;
        if (!humanFeedback.isEmpty() && !oldOutline.isEmpty()) {
            prompt = String.format(
                "你是一位专业的剧本策划。之前你为主题【%s】写了一版大纲，但被导演打回了。\n" +
                "【导演打回的指导意见】：%s\n\n" +
                "请严格按照导演的意见，对以下【原大纲】进行推翻重写或大幅度修改，输出全新的一版大纲：\n" +
                "--------------------------\n%s", 
                topic, humanFeedback, oldOutline);
        } else {
            prompt = String.format("请为主题为【%s】的剧本创作一份大纲。要求：%s", topic, requirement);
        }

        String sessionId = state.value(ScriptGraphState.KEY_SESSION_ID).map(v -> (String) v).orElse("");

        String outline = llmClient.chatStream(prompt, token -> {
            if (!sessionId.isEmpty()) {
                sseStreamManager.sendToken(sessionId, "outline_chunk", token);
            }
        });
        log.info("✅ [策划节点] 大纲已产出。");

        Map<String, Object> result = new java.util.HashMap<>();
        result.put(ScriptGraphState.KEY_OUTLINE, outline);
        result.put(ScriptGraphState.KEY_HUMAN_INTERVENTION, ""); // 清除幽灵反馈，防止路由死循环

        return result;
    }
}
