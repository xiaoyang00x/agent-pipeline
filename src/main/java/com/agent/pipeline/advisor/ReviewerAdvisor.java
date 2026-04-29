package com.agent.pipeline.advisor;

import com.agent.pipeline.workflow.state.ScriptGraphState;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.stereotype.Component;

/**
 * 审稿阶段的建议策略
 */
@Component
public class ReviewerAdvisor implements InterventionAdvisor {
    
    @Override
    public boolean supports(String nodeName) {
        // 当图停留在 reviewer 或最终审批节点时都可以使用此策略
        return "reviewer".equals(nodeName) || "director_approval".equals(nodeName);
    }

    @Override
    public String buildPrompt(OverAllState state) {
        String aiFeedback = state.value(ScriptGraphState.KEY_REVIEW_FEEDBACK).map(v -> (String) v).orElse("未提供");
        String outline = state.value(ScriptGraphState.KEY_OUTLINE).map(v -> (String) v).orElse("无大纲");
        String script = state.value(ScriptGraphState.KEY_SCRIPT).map(v -> (String) v).orElse("无剧本");
        
        return String.format(
            "你是一位坐在导演旁边的副手。目前剧本已经写完，且 AI 审稿人给出了初步意见。\n" +
            "【AI 审稿人意见】：\n%s\n\n" +
            "请结合以下【原始大纲】和【剧本草稿】，客观评估 AI 审稿人的意见是否合理，并给导演一个最终裁决建议（是强制通过，还是同意打回修改）。\n" +
            "--------------------------\n【原始大纲】：\n%s\n\n【剧本草稿】：\n%s", 
            aiFeedback, outline, script);
    }
}
