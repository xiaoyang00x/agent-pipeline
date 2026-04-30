package com.agent.pipeline.service;

import com.agent.pipeline.workflow.state.ScriptGraphState;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

@Service
public class ScriptCreationAgentService {

    private static final Logger log = LoggerFactory.getLogger(ScriptCreationAgentService.class);

    private final CompiledGraph scriptCreationGraph;
    private final InterventionAssistantService interventionAssistantService;

    public ScriptCreationAgentService(CompiledGraph scriptCreationGraph, 
                                    InterventionAssistantService interventionAssistantService) {
        this.scriptCreationGraph = scriptCreationGraph;
        this.interventionAssistantService = interventionAssistantService;
    }

    public void createScriptAsync(String topic, String requirement, String sessionId) {
        log.info("🚀 [异步模式] 启动开始，SessionID: {}", sessionId);
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(ScriptGraphState.KEY_TOPIC, topic);
        inputs.put(ScriptGraphState.KEY_REQUIREMENT, requirement);
        inputs.put(ScriptGraphState.KEY_RETRY_COUNT, 0);

        RunnableConfig config = RunnableConfig.builder().threadId(sessionId).build();
        scriptCreationGraph.stream(inputs, config)
                .doOnNext(output -> log.info("【进度】Session: {}, 节点: {}", sessionId, output.node()))
                .subscribe();
    }

    public Map<String, Object> getGraphState(String sessionId) {
        RunnableConfig config = RunnableConfig.builder().threadId(sessionId).build();

        try {
            // 直接获取 snapshot，根据报错提示，它返回的不是 Optional
            Object snapshot = scriptCreationGraph.getState(config);
            if (snapshot != null) {
                // 通过反射获取数据，确保极致兼容
                var stateMethod = snapshot.getClass().getMethod("state");
                Object overallState = stateMethod.invoke(snapshot);
                var dataMethod = overallState.getClass().getMethod("data");
                Map<String, Object> data = new HashMap<>((Map<String, Object>) dataMethod.invoke(overallState));
                
                // 1. 数据物证推断进度
                List<String> completedSteps = new ArrayList<>();
                if (data.get("outline") != null && !data.get("outline").toString().isEmpty()) completedSteps.add("planner");
                if (data.get("script") != null && !data.get("script").toString().isEmpty()) completedSteps.add("writer");
                if (data.get("review_feedback") != null && !data.get("review_feedback").toString().isEmpty()) completedSteps.add("reviewer");
                
                data.put("completed_steps", completedSteps);

                // 2. 节点状态推断
                var nextMethod = snapshot.getClass().getMethod("next");
                Object nextNodes = nextMethod.invoke(snapshot);
                String nextStr = (nextNodes != null) ? nextNodes.toString() : "";

                if (nextStr.isEmpty() || nextStr.contains("__END__")) {
                    data.put("is_finished", true);
                    data.put("current_node", "finish");
                    completedSteps.add("finish");
                } else {
                    String nextNode = nextStr;
                    if (nextNodes instanceof List && !((List<?>) nextNodes).isEmpty()) {
                        nextNode = ((List<?>) nextNodes).get(0).toString();
                    }
                    // 剥离中括号 [ ]，确保前端匹配纯净
                    nextNode = nextNode.replace("[", "").replace("]", "").trim();
                    data.put("current_node", nextNode);

                    // --- 唤醒参谋 (带容错) ---
                    if (interventionAssistantService != null) {
                        try {
                            interventionAssistantService.prepareIntervention(sessionId, nextNode, (com.alibaba.cloud.ai.graph.OverAllState)overallState);
                        } catch (Exception e) {
                            log.error("❌ [参谋唤醒异常]: {}", e.getMessage());
                        }
                    }
                }
                return data;
            }
        } catch (Exception e) {
            log.warn("无法获取 Graph 状态 (反射调试): {}", e.getMessage());
        }
        return new HashMap<>();
    }

    public void resumeScriptAsync(String sessionId, String feedback) {
        log.info("▶️ [恢复执行] SessionID: {}, Feedback: {}", sessionId, feedback);
        Map<String, Object> stateUpdate = new HashMap<>();
        stateUpdate.put(ScriptGraphState.KEY_HUMAN_INTERVENTION, feedback);
        RunnableConfig config = RunnableConfig.builder().threadId(sessionId).build();
        
        try {
            // 必须接收 updateState 返回的包含新 Checkpoint 信息的 config
            RunnableConfig newConfig = scriptCreationGraph.updateState(config, stateUpdate);
            
            // 使用新 config 和空输入(null)触发真正的“断点继续”
            scriptCreationGraph.stream(null, newConfig).subscribe();
        } catch (Exception e) {
            log.error("❌ 恢复图执行失败", e);
        }
    }
}
