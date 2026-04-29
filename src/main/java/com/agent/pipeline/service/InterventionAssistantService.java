package com.agent.pipeline.service;

import com.agent.pipeline.advisor.InterventionAdvisor;
import com.agent.pipeline.client.LlmClient;
import com.agent.pipeline.mapper.InterventionMapper;
import com.agent.pipeline.model.InterventionEntity;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 独立参谋服务 (Director's Copilot)
 *
 * 独立于图流水线外。在系统发生断点时被唤醒。
 * 采用策略模式，通过不同的 Advisor 为不同节点提供定制化的建议。
 */
@Service
public class InterventionAssistantService {

    private static final Logger log = LoggerFactory.getLogger(InterventionAssistantService.class);
    private final InterventionMapper interventionMapper;
    private final LlmClient llmClient;
    private final List<InterventionAdvisor> advisors;

    public InterventionAssistantService(InterventionMapper interventionMapper, 
                                      LlmClient llmClient,
                                      List<InterventionAdvisor> advisors) {
        this.interventionMapper = interventionMapper;
        this.llmClient = llmClient;
        this.advisors = advisors;
    }

    /**
     * 在图引擎中断后被触发，动态生成参谋建议并入库 (策略模式实现)
     */
    public void prepareIntervention(String sessionId, String nodeName, OverAllState state) {
        log.info("📢 [全知参谋] 正在为断点节点 [{}] 准备诊断建议...", nodeName);

        // 1. 寻找匹配的建议策略 (策略模式)
        InterventionAdvisor advisor = advisors.stream()
                .filter(a -> a.supports(nodeName))
                .findFirst()
                .orElse(null);

        if (advisor == null) {
            log.warn("⚠️ 未找到支持节点 [{}] 的参谋策略，跳过建议生成。", nodeName);
            return;
        }

        // 2. 构建 Prompt 并调用大模型
        String prompt = advisor.buildPrompt(state);
        log.info("🧠 [全知参谋] 正在分析当前局势...");
        String advice = llmClient.chat(prompt);

        // 3. 入库持久化
        InterventionEntity intervention = new InterventionEntity();
        intervention.setSessionId(sessionId);
        intervention.setExecutionId(sessionId);
        intervention.setNodeName(nodeName);
        intervention.setAdvice(advice);
        intervention.setStatus("WAITING");
        intervention.setCreatedAt(LocalDateTime.now());
        intervention.setUpdatedAt(LocalDateTime.now());

        interventionMapper.insert(intervention);
        log.info("✅ 参谋建议已准备就绪并入库。策略提供者: {}", advisor.getClass().getSimpleName());
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
