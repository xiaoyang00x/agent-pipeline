package com.agent.pipeline.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.agent.pipeline.workflow.state.ScriptGraphState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 剧本生成服务
 *
 * 驱动 Graph Core 执行 Planner → Writer → Reviewer 工作流。
 * 通过收集每个节点的输出 State，最终拼装出完整的结构化结果返回给前端。
 */
@Service
public class ScriptCreationAgentService {

    private static final Logger log = LoggerFactory.getLogger(ScriptCreationAgentService.class);
    private final CompiledGraph scriptCreationGraph;

    public ScriptCreationAgentService(CompiledGraph scriptCreationGraph) {
        this.scriptCreationGraph = scriptCreationGraph;
    }

    /**
     * 以流式方式运行剧本创作工作流。
     *
     * @param sessionId   会话唯一标识（用于追踪和记忆）
     * @param topic       创作主题
     * @param requirement 附加要求
     * @return Flux 流，每个元素是一个节点执行完毕后的输出
     */
    public Flux<NodeOutput> createScriptStream(String sessionId, String topic, String requirement) {
        // 1. 初始化白板上的输入数据
        Map<String, Object> initialState = new HashMap<>();
        initialState.put(ScriptGraphState.KEY_TOPIC, topic);
        initialState.put(ScriptGraphState.KEY_REQUIREMENT, requirement);
        initialState.put(ScriptGraphState.KEY_RETRY_COUNT, 0);

        // 2. 使用传入的 sessionId 区分不同会话
        RunnableConfig config = RunnableConfig.builder()
                .threadId(sessionId)
                .build();

        log.info("🚀 开始启动图流式执行，SessionID: {}", sessionId);

        try {
            return scriptCreationGraph.stream(initialState, config)
                    .doOnNext(output -> log.info("节点输出: [{}]", output.node()))
                    .doOnError(error -> log.error("执行错误: {}", error.getMessage()))
                    .doOnComplete(() -> log.info("✅ 流式执行结束！"));
        } catch (Exception e) {
            log.error("启动工作流失败", e);
            return Flux.error(e);
        }
    }

    /**
     * 阻塞执行完整工作流，收集所有节点的输出内容，返回结构化的 Map。
     *
     * @param sessionId   会话唯一标识
     * @param topic       创作主题
     * @param requirement 附加要求
     * @return 包含大纲、剧本、审稿意见等的完整结果
     */
    public Map<String, Object> createScriptBlocking(String sessionId, String topic, String requirement) {
        // 用于收集每个节点执行后的最新状态
        List<String> nodesExecuted = new ArrayList<>();

        // 通过 reduce 聚合所有节点输出，最后取到的就是最终状态
        NodeOutput finalOutput = createScriptStream(sessionId, topic, requirement)
                .doOnNext(output -> {
                    String nodeName = output.node();
                    // 排除掉内部的 START/END 伪节点
                    if (!nodeName.startsWith("__")) {
                        nodesExecuted.add(nodeName);
                    }
                })
                .blockLast(); // 阻塞等到最后一个节点执行完毕

        // 从最后一个节点输出中取出白板中的所有数据
        Map<String, Object> result = new HashMap<>();
        result.put("topic", topic);
        result.put("requirement", requirement);
        result.put("nodes_executed", nodesExecuted);

        if (finalOutput != null && finalOutput.state() != null) {
            // OverAllState 不是 Map，需用 .value(key) 方法逐个读取
            var state = finalOutput.state();
            result.put("outline",         state.value(ScriptGraphState.KEY_OUTLINE).orElse("（未生成）"));
            result.put("script",          state.value(ScriptGraphState.KEY_SCRIPT).orElse("（未生成）"));
            result.put("approved",        state.value(ScriptGraphState.KEY_APPROVED).orElse(false));
            result.put("review_feedback", state.value(ScriptGraphState.KEY_REVIEW_FEEDBACK).orElse("无"));
            result.put("retry_count",     state.value(ScriptGraphState.KEY_RETRY_COUNT).orElse(0));
        }

        log.info("📦 最终结果已收集完毕，共执行节点: {}", nodesExecuted);
        return result;
    }
}
