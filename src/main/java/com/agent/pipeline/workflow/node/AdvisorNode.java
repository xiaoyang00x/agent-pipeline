package com.agent.pipeline.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptableAction;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.agent.pipeline.client.LlmClient;
import com.agent.pipeline.workflow.config.WorkflowProperties;
import com.agent.pipeline.workflow.state.ScriptGraphState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * 参谋节点 (Advisor)
 *
 * 在 Planner 产出大纲之后，由参谋 Agent 对大纲进行专业点评并给出建议。
 * 建议写入 Graph State，供下游 Writer 节点读取。
 * 断点设置在此节点之后，等待导演审阅大纲与参谋建议后注入反馈。
 */
public class AdvisorNode implements NodeAction, InterruptableAction {

    private static final Logger log = LoggerFactory.getLogger(AdvisorNode.class);

    private final LlmClient llmClient;
    private final WorkflowProperties properties;

    public AdvisorNode(LlmClient llmClient, WorkflowProperties properties) {
        this.llmClient = llmClient;
        this.properties = properties;
    }

    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeName, OverAllState state, RunnableConfig config) {
        return Optional.empty();
    }

    @Override
    public Optional<InterruptionMetadata> interruptAfter(String nodeName, OverAllState state, Map<String, Object> lastResult, RunnableConfig config) {
        if (properties.shouldInterrupt("advisor")) {
            log.info("⏸️ [参谋节点] 建议已就绪，触发断点，等待导演审阅大纲与参谋意见...");
            return Optional.of(InterruptionMetadata.builder().nodeId(nodeName).build());
        }
        return Optional.empty();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("💡 [参谋节点] 开始分析大纲并生成专业建议...");

        String topic = state.value(ScriptGraphState.KEY_TOPIC).map(v -> (String) v).orElse("未知主题");
        String outline = state.value(ScriptGraphState.KEY_OUTLINE).map(v -> (String) v).orElse("无大纲");

        String prompt = String.format(
            "你是一位资深的剧本参谋。以下是主题为【%s】的剧本大纲。\n" +
            "请给出简短的专业点评和 3 条核心改进建议。\n" +
            "--------------------------\n" +
            "【大纲内容】：\n%s", topic, outline);

        String advice = llmClient.chat(prompt);
        log.info("✅ [参谋节点] 建议已生成。");

        return Map.of(
            ScriptGraphState.KEY_INTERVENTION_ADVICE, advice
        );
    }
}
