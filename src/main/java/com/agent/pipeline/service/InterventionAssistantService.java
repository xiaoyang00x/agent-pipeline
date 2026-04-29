package com.agent.pipeline.service;

import com.agent.pipeline.mapper.InterventionMapper;
import com.agent.pipeline.model.InterventionEntity;
import com.agent.pipeline.workflow.state.ScriptGraphState;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 干预记录管理服务
 *
 * 职责单一：只负责干预记录的 CRUD 操作。
 * LLM 调用已移至 AdvisorNode（图引擎内的正式节点）。
 */
@Service
public class InterventionAssistantService {

    private static final Logger log = LoggerFactory.getLogger(InterventionAssistantService.class);
    private final InterventionMapper interventionMapper;

    public InterventionAssistantService(InterventionMapper interventionMapper) {
        this.interventionMapper = interventionMapper;
    }

    /**
     * 在图引擎中断后，将参谋建议（已由 AdvisorNode 写入 State）持久化到数据库
     */
    public void prepareIntervention(String sessionId, String nodeName, OverAllState state) {
        log.info("📢 正在持久化 [{}] 节点的干预记录...", nodeName);

        // 参谋建议已由 AdvisorNode 写入 State，直接读取
        String advice = state.value(ScriptGraphState.KEY_INTERVENTION_ADVICE)
                .map(v -> (String) v).orElse("无建议");

        InterventionEntity intervention = new InterventionEntity();
        intervention.setSessionId(sessionId);
        intervention.setExecutionId(sessionId);
        intervention.setNodeName(nodeName);
        intervention.setAdvice(advice);
        intervention.setStatus("WAITING");
        intervention.setCreatedAt(LocalDateTime.now());
        intervention.setUpdatedAt(LocalDateTime.now());

        interventionMapper.insert(intervention);
        log.info("✅ 干预记录已入库。");
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
