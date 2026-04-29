package com.agent.pipeline.service;

import com.agent.pipeline.model.InterventionEntity;
import com.agent.pipeline.workflow.state.ScriptGraphState;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
     * 阻塞式启动工作流（兼容 AgentController）
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

        AtomicBoolean isInterrupted = new AtomicBoolean(false);
        NodeOutput lastOutput = scriptCreationGraph.stream(inputs, config)
                .doOnNext(output -> log.info("节点输出: {}", output.node()))
                .blockLast();

        // 准确判断：如果最后一个节点不是 __END__，说明中途被断点拦截了
        if (lastOutput != null && !"__END__".equals(lastOutput.node())) {
            isInterrupted.set(true);
        }

        // 关键：如果中断了，立刻启动参谋 Agent
        if (isInterrupted.get() && lastOutput != null) {
            log.warn("🚨 阻塞模式下检测到中断，启动参谋 Agent...");
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

        InterventionEntity intervention = interventionAssistantService.getLatestIntervention(sessionId);
        if (intervention == null || intervention.getExecutionId() == null) {
            log.error("🚨 无法接关：找不到有效的干预记录或 ID。SessionID: {}", sessionId);
            return Flux.error(new RuntimeException("Resume failed: No executionId found"));
        }

        String rawExecId = intervention.getExecutionId();
        String realThreadId = rawExecId.contains(":") ? rawExecId.split(":")[0] : rawExecId;
        String checkpointId = rawExecId.contains(":") && rawExecId.split(":").length > 1 ? rawExecId.split(":")[1] : null;

        log.info("🎯 准备接关 - ThreadId: [{}](len:{}), Checkpoint: [{}](len:{})", 
                realThreadId, realThreadId.length(), 
                checkpointId, (checkpointId != null ? checkpointId.length() : 0));

        interventionAssistantService.completeIntervention(sessionId, humanFeedback);

        Map<String, Object> interventionState = new java.util.HashMap<>();
        interventionState.put(ScriptGraphState.KEY_HUMAN_INTERVENTION, humanFeedback);

        RunnableConfig config = RunnableConfig.builder()
                .threadId(realThreadId)
                .build();
        
        if (checkpointId != null && !checkpointId.isEmpty()) {
            config = config.withCheckPointId(checkpointId);
        }
        
        config = config.withResume();
        
        try {
            return scriptCreationGraph.stream(interventionState, config)
                    .doOnNext(output -> {
                        log.info("接关后节点输出: {}", output.node());
                        // 如果后续节点又中断了（比如 Reviewer），可以继续在这里递归处理
                        // 目前简化逻辑，只处理 Planner 到 Writer 的跳转
                    })
                    .doOnComplete(() -> log.info("✅ 接关任务执行完毕。"))
                    .doOnError(e -> log.error("❌ 接关过程中出错", e));
        } catch (Exception e) {
            log.error("💥 发起接关请求失败", e);
            return Flux.error(e);
        }
    }
}
