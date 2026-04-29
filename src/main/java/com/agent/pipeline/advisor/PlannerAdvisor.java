package com.agent.pipeline.advisor;

import com.agent.pipeline.workflow.state.ScriptGraphState;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.stereotype.Component;

/**
 * 策划阶段的建议策略
 */
@Component
public class PlannerAdvisor implements InterventionAdvisor {
    
    @Override
    public boolean supports(String nodeName) {
        return "planner".equals(nodeName) || "planner_approval".equals(nodeName);
    }

    @Override
    public String buildPrompt(OverAllState state) {
        String topic = state.value(ScriptGraphState.KEY_TOPIC).map(v -> (String) v).orElse("未知主题");
        String outline = state.value(ScriptGraphState.KEY_OUTLINE).map(v -> (String) v).orElse("无大纲");
        
        return String.format(
            "你是一位坐在导演旁边的副手。目前编剧刚产出了主题为【%s】的剧本大纲，等待导演审批。\n" +
            "请针对以下大纲，给出简短犀利的专业点评，以及是否需要打回重做的建议。\n" +
            "--------------------------\n【大纲内容】：\n%s", 
            topic, outline);
    }
}
