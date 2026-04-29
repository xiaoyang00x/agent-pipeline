package com.agent.pipeline.workflow.state;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import java.util.HashMap;

/**
 * 剧本工作流的状态定义 (Graph State)
 * 
 * 我们可以把 State 想象成整个流水线车间的“公共白板”。
 * 每个工人（Agent Node）在干活前，都来看看白板上的需求和上一个工人的产出；
 * 干完活后，把自己产出的内容 write 到白板的特定区域上。
 */
public class ScriptGraphState {

    // --- 以下是我们在“白板”上划分出的各个区域名称 (State Keys) ---

    // 1. 用户输入
    public static final String KEY_TOPIC = "topic";                   // 创作主题
    public static final String KEY_REQUIREMENT = "requirement";       // 具体要求

    // 2. 各个节点的产出物
    public static final String KEY_OUTLINE = "outline";               // 策划节点输出的大纲
    public static final String KEY_SCRIPT = "script";                 // 编剧节点输出的剧本草稿
    public static final String KEY_REVIEW_FEEDBACK = "review_feedback"; // 审阅节点输出的修改意见

    // 3. 流程控制状态
    public static final String KEY_APPROVED = "approved";             // 审阅是否通过 (Boolean)
    public static final String KEY_RETRY_COUNT = "retry_count";       // 打回重写的次数 (Integer)
    public static final String KEY_NEXT_NODE = "next_node";           // 图引擎用于路由判断的下一步节点名称 (String)

    // 4. 人机协同干预相关
    public static final String KEY_HUMAN_INTERVENTION = "human_intervention"; // 人工干预指令 (String)
    public static final String KEY_INTERVENTION_ADVICE = "intervention_advice"; // 辅助 Agent 提供的修改建议 (String)

    /**
     * 配置状态的合并策略
     * 这里的 ReplaceStrategy 表示每次有新数据时，直接覆盖旧数据。
     * 比如编剧修改了剧本，就直接擦掉白板上的老剧本，写上新剧本。
     */
    public static KeyStrategyFactory createKeyStrategyFactory() {
        return () -> {
            HashMap<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put(KEY_TOPIC, new ReplaceStrategy());
            strategies.put(KEY_REQUIREMENT, new ReplaceStrategy());
            
            strategies.put(KEY_OUTLINE, new ReplaceStrategy());
            strategies.put(KEY_SCRIPT, new ReplaceStrategy());
            strategies.put(KEY_REVIEW_FEEDBACK, new ReplaceStrategy());
            
            strategies.put(KEY_APPROVED, new ReplaceStrategy());
            strategies.put(KEY_RETRY_COUNT, new ReplaceStrategy());
            strategies.put(KEY_NEXT_NODE, new ReplaceStrategy());

            strategies.put(KEY_HUMAN_INTERVENTION, new ReplaceStrategy());
            strategies.put(KEY_INTERVENTION_ADVICE, new ReplaceStrategy());
            return strategies;
        };
    }
}
