package com.agent.pipeline.advisor;

import com.alibaba.cloud.ai.graph.OverAllState;

/**
 * 副手建议提供者接口 (策略模式)
 * 用于针对不同工作流节点生成专业的诊断建议
 */
public interface InterventionAdvisor {
    
    /**
     * 判断该策略是否支持当前的节点名称
     */
    boolean supports(String nodeName);

    /**
     * 根据当前图状态生成建议 Prompt
     */
    String buildPrompt(OverAllState state);
}
