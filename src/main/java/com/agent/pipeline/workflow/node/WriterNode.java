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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 编剧节点 (Writer)
 */
public class WriterNode implements NodeAction, InterruptableAction {

    private static final Logger log = LoggerFactory.getLogger(WriterNode.class);
    private final LlmClient llmClient;
    private final WorkflowProperties properties;

    public WriterNode(LlmClient llmClient, WorkflowProperties properties) {
        this.llmClient = llmClient;
        this.properties = properties;
    }

    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeName, OverAllState state, RunnableConfig config) {
        return Optional.empty();
    }

    @Override
    public Optional<InterruptionMetadata> interruptAfter(String nodeName, OverAllState state, Map<String, Object> lastResult, RunnableConfig config) {
        if (properties.shouldInterrupt("writer")) {
            log.info("⏸️ [编剧节点] 任务完成，触发策略中断，等待导演审核剧本草稿...");
            return Optional.of(InterruptionMetadata.builder().nodeId(nodeName).build());
        }
        return Optional.empty();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("✍️ [编剧节点] 开始工作...");

        String outline = state.value(ScriptGraphState.KEY_OUTLINE).map(v -> (String) v).orElse("无大纲");
        Optional<Object> feedbackOpt = state.value(ScriptGraphState.KEY_REVIEW_FEEDBACK);
        Optional<Object> oldScriptOpt = state.value(ScriptGraphState.KEY_SCRIPT);
        
        // 读取导演的最高指示
        String humanFeedback = state.value(ScriptGraphState.KEY_HUMAN_INTERVENTION).map(v -> (String) v).orElse("");

        String prompt;
        if (feedbackOpt.isPresent() && oldScriptOpt.isPresent()) {
            String feedback = (String) feedbackOpt.get();
            String oldScript = (String) oldScriptOpt.get();
            prompt = String.format(
                "你是一位专业的编剧。你之前创作了一版剧本，但是审稿人给出了意见。\n" +
                "【特别注意】：导演也给出了最高指示：%s\n\n" +
                "请你综合【审稿意见】和【导演指示】，在【原有剧本】基础上进行修改完善，输出一版新的完整剧本。\n" +
                "--------------------------\n" +
                "【原始大纲】：\n%s\n\n" +
                "【审稿修改意见】：\n%s\n\n" +
                "【原有剧本】：\n%s",
                humanFeedback.isEmpty() ? "请继续完善" : humanFeedback,
                outline, feedback, oldScript
            );
        } else {
            prompt = String.format(
                "你是一位专业的微电影编剧。请根据以下【剧本大纲】，撰写出具体的【剧本内容】。\n" +
                "【特别注意】：导演给出了关键创作指示：%s\n\n" +
                "剧本需要包含场景描述、角色对白和动作神态提示。\n" +
                "--------------------------\n" +
                "【剧本大纲】：\n%s",
                humanFeedback.isEmpty() ? "无额外指示" : humanFeedback,
                outline
            );
        }

        String script = llmClient.chat(prompt);
        log.info("✅ [编剧节点] 剧本撰写完毕。");

        // 关键：消费完人类反馈后主动清除，防止"幽灵反馈"影响后续路由判断
        Map<String, Object> result = new HashMap<>();
        result.put(ScriptGraphState.KEY_SCRIPT, script);
        result.put(ScriptGraphState.KEY_NEXT_NODE, "reviewer");
        result.put(ScriptGraphState.KEY_HUMAN_INTERVENTION, "");  // 清除幽灵反馈
        return result;
    }
}
