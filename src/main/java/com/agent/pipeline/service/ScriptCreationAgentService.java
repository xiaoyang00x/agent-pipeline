package com.agent.pipeline.service;


import com.agent.pipeline.workflow.state.ScriptGraphState;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 图执行服务
 *
 * 职责精简为两项：启动图执行 + 恢复图执行。
 * 中断检测和参谋调度的 LLM 逻辑已移至图引擎内部的 AdvisorNode。
 */
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

    /**
     * 阻塞式启动工作流
     */
    public Map<String, Object> createScriptBlocking(String topic, String requirement, String sessionId) {
        log.info("🚀 [阻塞模式] 开始启动，SessionID: {}", sessionId);

        Map<String, Object> inputs = new java.util.HashMap<>();
        inputs.put(ScriptGraphState.KEY_TOPIC, topic);
        inputs.put(ScriptGraphState.KEY_REQUIREMENT, requirement);
        inputs.put(ScriptGraphState.KEY_RETRY_COUNT, 0);

        RunnableConfig config = RunnableConfig.builder()
                .threadId(sessionId)
                .build();

        NodeOutput lastOutput = scriptCreationGraph.stream(inputs, config)
                .doOnNext(output -> log.info("节点输出: {}", output.node()))
                .blockLast();

        // 如果最后一个节点不是 __END__，说明中途被断点拦截了
        if (lastOutput != null && !"__END__".equals(lastOutput.node())) {
            log.warn("🚨 检测到图引擎中断于 [{}] 节点，持久化干预记录...", lastOutput.node());
            interventionAssistantService.prepareIntervention(sessionId, lastOutput.node(), lastOutput.state());
        }

        return lastOutput != null ? lastOutput.state().data() : Map.of();
    }

    /**
     * 流式启动工作流
     */
    public Flux<NodeOutput> createScriptStream(String topic, String requirement, String sessionId) {
        log.info("🚀 开始启动图流式执行，SessionID: {}", sessionId);

        Map<String, Object> inputs = new java.util.HashMap<>();
        inputs.put(ScriptGraphState.KEY_TOPIC, topic);
        inputs.put(ScriptGraphState.KEY_REQUIREMENT, requirement);
        inputs.put(ScriptGraphState.KEY_RETRY_COUNT, 0);

        RunnableConfig config = RunnableConfig.builder()
                .threadId(sessionId)
                .build();

        return scriptCreationGraph.stream(inputs, config)
                .doOnNext(output -> log.info("节点输出: {}", output.node()))
                .doOnComplete(() -> log.info("✅ 流式执行结束。"));
    }

    /**
     * 恢复执行（接关）
     */
    public Flux<NodeOutput> resumeScriptStream(String sessionId, String humanFeedback) {
        log.info("▶️ 收到导演指令，准备恢复执行。SessionID: {}", sessionId);

        interventionAssistantService.completeIntervention(sessionId, humanFeedback);

        Map<String, Object> interventionState = new java.util.HashMap<>();
        interventionState.put(ScriptGraphState.KEY_HUMAN_INTERVENTION, humanFeedback);

        RunnableConfig config = RunnableConfig.builder()
                .threadId(sessionId)
                .build()
                .withResume();

        try {
            return scriptCreationGraph.stream(interventionState, config)
                    .doOnNext(output -> log.info("接关后节点输出: {}", output.node()))
                    .doOnComplete(() -> log.info("✅ 接关任务执行完毕。"))
                    .doOnError(e -> log.error("❌ 接关过程中出错", e));
        } catch (Exception e) {
            log.error("💥 发起接关请求失败", e);
            return Flux.error(e);
        }
    }
}
