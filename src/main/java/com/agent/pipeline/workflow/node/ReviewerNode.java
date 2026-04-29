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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 审稿节点 (Reviewer)
 */
public class ReviewerNode implements NodeAction, InterruptableAction {

    private static final Logger log = LoggerFactory.getLogger(ReviewerNode.class);
    private final MiniMaxClient miniMaxClient;
    private final WorkflowProperties properties;
    private final int MAX_RETRY = 2;

    public ReviewerNode(MiniMaxClient miniMaxClient, WorkflowProperties properties) {
        this.miniMaxClient = miniMaxClient;
        this.properties = properties;
    }

    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeName, OverAllState state, RunnableConfig config) {
        return Optional.empty();
    }

    @Override
    public Optional<InterruptionMetadata> interruptAfter(String nodeName, OverAllState state, Map<String, Object> lastResult, RunnableConfig config) {
        if (properties.shouldInterrupt("reviewer")) {
            log.info("⏸️ [审稿节点] 任务完成，触发策略中断，等待导演终审...");
            return Optional.of(InterruptionMetadata.builder()
                .nodeId(nodeName)
                .build());
        }
        return Optional.empty();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("🧐 [审稿节点] 开始检查剧本...");

        String outline = state.value(ScriptGraphState.KEY_OUTLINE).map(v -> (String) v).orElse("");
        String script = state.value(ScriptGraphState.KEY_SCRIPT).map(v -> (String) v).orElse("");
        int retryCount = state.value(ScriptGraphState.KEY_RETRY_COUNT).map(v -> (Integer) v).orElse(0);

        if (retryCount >= MAX_RETRY) {
            log.warn("⚠️ 剧本已被打回修改超过 {} 次，强制通过审稿！", MAX_RETRY);
            return Map.of(
                ScriptGraphState.KEY_APPROVED, true,
                ScriptGraphState.KEY_NEXT_NODE, "end"
            );
        }

        String prompt = String.format(
            "你是一位严苛的影视项目制片人，负责审阅最终的剧本是否达标。\n" +
            "请比对以下【策划大纲】和【剧本草稿】，判断剧本是否严重偏题、人物是否走样、情节是否有明显漏洞。\n" +
            "请务必以如下 JSON 格式返回你的评审结果（不要包含任何其他额外的文字，只要 JSON）：\n" +
            "{\n" +
            "  \"approved\": true或false,\n" +
            "  \"feedback\": \"如果通过则填无，如果不通过请详细指出修改意见。\"\n" +
            "}\n" +
            "--------------------------\n" +
            "【策划大纲】：\n%s\n\n" +
            "【剧本草稿】：\n%s",
            outline, script
        );

        String response = miniMaxClient.chat(prompt);
        log.info("审稿意见返回：{}", response);

        boolean approved = true;
        String feedback = "无";
        Pattern approvedPattern = Pattern.compile("\"approved\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
        Matcher approvedMatcher = approvedPattern.matcher(response);
        if (approvedMatcher.find()) {
            approved = Boolean.parseBoolean(approvedMatcher.group(1));
        }
        Pattern feedbackPattern = Pattern.compile("\"feedback\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
        Matcher feedbackMatcher = feedbackPattern.matcher(response);
        if (feedbackMatcher.find()) {
            feedback = feedbackMatcher.group(1).replace("\\n", "\n").replace("\\\"", "\"");
        }

        Map<String, Object> stateUpdates = new HashMap<>();
        stateUpdates.put(ScriptGraphState.KEY_APPROVED, approved);

        if (approved) {
            log.info("✅ [审稿节点] 剧本审核通过！");
            stateUpdates.put(ScriptGraphState.KEY_NEXT_NODE, "end");
        } else {
            log.warn("❌ [审稿节点] 剧本审核未通过！打回给编剧修改。当前第 {} 次打回", retryCount + 1);
            stateUpdates.put(ScriptGraphState.KEY_REVIEW_FEEDBACK, feedback);
            stateUpdates.put(ScriptGraphState.KEY_RETRY_COUNT, retryCount + 1);
            stateUpdates.put(ScriptGraphState.KEY_NEXT_NODE, "writer");
        }

        return stateUpdates;
    }
}
