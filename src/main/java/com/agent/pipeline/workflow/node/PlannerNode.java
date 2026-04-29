package com.agent.pipeline.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptableAction;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.agent.pipeline.client.MiniMaxClient;
import com.agent.pipeline.workflow.config.WorkflowProperties;
import com.agent.pipeline.workflow.state.ScriptGraphState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * 策划节点 (Planner)
 */
public class PlannerNode implements NodeAction, InterruptableAction {

    private static final Logger log = LoggerFactory.getLogger(PlannerNode.class);

    private final MiniMaxClient miniMaxClient;
    private final WorkflowProperties properties;

    public PlannerNode(MiniMaxClient miniMaxClient, WorkflowProperties properties) {
        this.miniMaxClient = miniMaxClient;
        this.properties = properties;
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

        String prompt = String.format("请为主题为【%s】的剧本创作一份大纲。要求：%s", topic, requirement);

        String outline = miniMaxClient.chat(prompt);
        log.info("✅ [策划节点] 大纲已产出。");

        return Map.of(
            ScriptGraphState.KEY_OUTLINE, outline,
            ScriptGraphState.KEY_NEXT_NODE, "writer"
        );
    }
}
