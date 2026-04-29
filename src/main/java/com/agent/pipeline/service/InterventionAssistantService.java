package com.agent.pipeline.service;

import com.agent.pipeline.client.LlmClient;
import com.agent.pipeline.mapper.InterventionMapper;
import com.agent.pipeline.model.InterventionEntity;
import com.agent.pipeline.workflow.state.ScriptGraphState;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 独立参谋服务 (Director's Copilot)
 *
 * 独立于图流水线外。在系统发生断点时被唤醒。
 * 根据当前的上下文（只有大纲，或者大纲+剧本+机器审稿意见），
 * 动态提供相应的决策辅助建议给人类导演。
 */
@Service
public class InterventionAssistantService {

    private static final Logger log = LoggerFactory.getLogger(InterventionAssistantService.class);
    private final InterventionMapper interventionMapper;
    private final LlmClient llmClient;

    public InterventionAssistantService(InterventionMapper interventionMapper, LlmClient llmClient) {
        this.interventionMapper = interventionMapper;
        this.llmClient = llmClient;
    }

    /**
     * 在图引擎中断后被触发，动态生成参谋建议并入库
     */
    public void prepareIntervention(String sessionId, String nodeName, OverAllState state) {
        log.info("📢 [全知参谋] 正在为断点节点 [{}] 准备诊断建议...", nodeName);

        String topic = state.value(ScriptGraphState.KEY_TOPIC).map(v -> (String) v).orElse("未知主题");
        String outline = state.value(ScriptGraphState.KEY_OUTLINE).map(v -> (String) v).orElse("无大纲");
        String script = state.value(ScriptGraphState.KEY_SCRIPT).map(v -> (String) v).orElse("");
        String aiFeedback = state.value(ScriptGraphState.KEY_REVIEW_FEEDBACK).map(v -> (String) v).orElse("");
        
        String prompt;
        
        if ("planner".equals(nodeName)) {
            prompt = String.format(
                "你是一位坐在导演旁边的副手。目前编剧刚产出了主题为【%s】的剧本大纲，等待导演审批。\n" +
                "请针对以下大纲，给出简短犀利的专业点评，以及是否需要打回重做的建议。\n" +
                "--------------------------\n【大纲内容】：\n%s", topic, outline);
        } else if ("reviewer".equals(nodeName)) {
            prompt = String.format(
                "你是一位坐在导演旁边的副手。目前剧本已经写完，且 AI 审稿人给出了初步意见。\n" +
                "【AI 审稿人意见】：\n%s\n\n" +
                "请结合以下【原始大纲】和【剧本草稿】，客观评估 AI 审稿人的意见是否合理，并给导演一个最终裁决建议（是强制通过，还是同意打回修改）。\n" +
                "--------------------------\n【原始大纲】：\n%s\n\n【剧本草稿】：\n%s", 
                aiFeedback.isEmpty() ? "未提供" : aiFeedback, outline, script);
        } else {
            prompt = "请对当前流程状态进行综合评估。";
        }

        log.info("🧠 [全知参谋] 正在分析当前局势...");
        String advice = llmClient.chat(prompt);

        InterventionEntity intervention = new InterventionEntity();
        intervention.setSessionId(sessionId);
        intervention.setExecutionId(sessionId);
        intervention.setNodeName(nodeName);
        intervention.setAdvice(advice);
        intervention.setStatus("WAITING");
        intervention.setCreatedAt(LocalDateTime.now());
        intervention.setUpdatedAt(LocalDateTime.now());

        interventionMapper.insert(intervention);
        log.info("✅ 参谋建议已准备就绪并入库。");
    }

    public InterventionEntity getLatestIntervention(String sessionId) {
        return interventionMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InterventionEntity>()
                .eq(InterventionEntity::getSessionId, sessionId)
                .eq(InterventionEntity::getStatus, "WAITING")
                .orderByDesc(InterventionEntity::getCreatedAt)
                .last("LIMIT 1")
        );
    }

    public void completeIntervention(String sessionId, String humanFeedback) {
        InterventionEntity intervention = getLatestIntervention(sessionId);
        if (intervention != null) {
            intervention.setHumanFeedback(humanFeedback);
            intervention.setStatus("COMPLETED");
            intervention.setUpdatedAt(LocalDateTime.now());
            interventionMapper.updateById(intervention);
        }
    }
}
