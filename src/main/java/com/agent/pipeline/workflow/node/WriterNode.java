package com.agent.pipeline.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.agent.pipeline.client.MiniMaxClient;
import com.agent.pipeline.workflow.state.ScriptGraphState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * 编剧节点 (Writer)
 *
 * 职责：根据"策划"写出的大纲，或者根据"审稿"给出的反馈意见，进行具体的剧本创作。
 * 如果是第一次创作，它只看大纲；如果是被打回重写，它会参考之前的剧本和审稿反馈。
 */
public class WriterNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(WriterNode.class);
    private final MiniMaxClient miniMaxClient;

    public WriterNode(MiniMaxClient miniMaxClient) {
        this.miniMaxClient = miniMaxClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("✍️ [编剧节点] 开始工作...");

        // 1. 获取基础上下文
        String outline = state.value(ScriptGraphState.KEY_OUTLINE).map(v -> (String) v).orElse("无大纲");

        // 2. 检查是否有修改意见和历史剧本
        Optional<Object> feedbackOpt = state.value(ScriptGraphState.KEY_REVIEW_FEEDBACK);
        Optional<Object> oldScriptOpt = state.value(ScriptGraphState.KEY_SCRIPT);

        String prompt;

        if (feedbackOpt.isPresent() && oldScriptOpt.isPresent()) {
            // 被审稿人打回重写的情况
            log.info("接收到审稿意见，开始重写修改剧本...");
            String feedback = (String) feedbackOpt.get();
            String oldScript = (String) oldScriptOpt.get();

            prompt = String.format(
                "你是一位专业的编剧。你之前根据大纲创作了一版剧本，但是审稿人给出了一些修改意见。\n" +
                "请你根据这些【修改意见】，在【原有剧本】的基础上进行修改完善，输出一版新的完整剧本。\n" +
                "--------------------------\n" +
                "【原始大纲】：\n%s\n\n" +
                "【审稿修改意见】：\n%s\n\n" +
                "【原有剧本】：\n%s",
                outline, feedback, oldScript
            );
        } else {
            // 第一次创作的情况
            log.info("首次创作，根据大纲撰写剧本...");
            prompt = String.format(
                "你是一位专业的微电影编剧。请根据以下【剧本大纲】，撰写出具体的【剧本内容】。\n" +
                "剧本需要包含明确的场景描述（如：日/内，咖啡厅）、角色对白和动作神态提示。\n" +
                "--------------------------\n" +
                "【剧本大纲】：\n%s",
                outline
            );
        }

        // 3. 调用大模型生成剧本
        String script = miniMaxClient.chat(prompt);
        log.info("✅ [编剧节点] 剧本撰写/修改完毕！内容详情:\n{}", script);

        // 4. 将最新剧本存入白板，并前往下一步：审稿人(reviewer)
        return Map.of(
            ScriptGraphState.KEY_SCRIPT, script,
            ScriptGraphState.KEY_NEXT_NODE, "reviewer"
        );
    }
}
