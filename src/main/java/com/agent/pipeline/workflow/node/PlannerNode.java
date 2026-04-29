package com.agent.pipeline.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.agent.pipeline.client.MiniMaxClient;
import com.agent.pipeline.workflow.state.ScriptGraphState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 策划节点 (Planner)
 *
 * 职责：作为工作流的第一站，它读取用户的原始要求，负责思考并输出一份"剧本大纲"。
 * 它就像剧组里的"策划"或"总导演"，先定下故事的基调和框架。
 */
public class PlannerNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(PlannerNode.class);

    // 使用自定义的 MiniMaxClient，绕开有版本 Bug 的 Spring AI MiniMaxChatModel
    private final MiniMaxClient miniMaxClient;

    public PlannerNode(MiniMaxClient miniMaxClient) {
        this.miniMaxClient = miniMaxClient;
    }

    /**
     * apply 方法是节点被执行时的入口。
     *
     * @param state 工作流的共享状态（白板）
     * @return 返回需要更新到状态上的新数据（写回白板的内容）
     */
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("🎬 [策划节点] 开始工作...");

        // 1. 从状态中读取前置数据，没取到则给默认值防空指针
        String topic = state.value(ScriptGraphState.KEY_TOPIC).map(v -> (String) v).orElse("未知主题");
        String requirement = state.value(ScriptGraphState.KEY_REQUIREMENT).map(v -> (String) v).orElse("无具体要求");

        log.info("收到主题: [{}], 附加要求: [{}]", topic, requirement);

        // 2. 组装发给大模型的 Prompt
        String prompt = String.format(
            "你是一位经验丰富的影视策划人。\n" +
            "请根据以下主题和附加要求，为一部微电影设计一份详尽的【剧本大纲】。\n" +
            "大纲需要包含：1. 故事背景 2. 主要人物设定 3. 故事起因、经过、高潮、结局。\n" +
            "--------------------------\n" +
            "主题：%s\n" +
            "附加要求：%s",
            topic, requirement
        );

        // 3. 调用大模型生成大纲
        String outline = miniMaxClient.chat(prompt);
        log.info("✅ [策划节点] 大纲生成完毕:\n{}", outline);

        // 4. 将产出结果（大纲）写回白板，并指定下一步去往"编剧节点"
        return Map.of(
            ScriptGraphState.KEY_OUTLINE, outline,
            ScriptGraphState.KEY_NEXT_NODE, "writer"
        );
    }
}
