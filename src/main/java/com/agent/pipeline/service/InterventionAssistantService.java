package com.agent.pipeline.service;

import com.agent.pipeline.client.MiniMaxClient;
import com.agent.pipeline.mapper.InterventionMapper;
import com.agent.pipeline.model.InterventionEntity;
import com.agent.pipeline.workflow.state.ScriptGraphState;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class InterventionAssistantService {

    private static final Logger log = LoggerFactory.getLogger(InterventionAssistantService.class);
    private final MiniMaxClient miniMaxClient;
    private final InterventionMapper interventionMapper;
    private final JdbcTemplate jdbcTemplate;

    public InterventionAssistantService(MiniMaxClient miniMaxClient, 
                                       InterventionMapper interventionMapper,
                                       JdbcTemplate jdbcTemplate) {
        this.miniMaxClient = miniMaxClient;
        this.interventionMapper = interventionMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void prepareIntervention(String sessionId, String nodeName, OverAllState state) {
        log.info("📢 参谋 Agent 正在分析 [{}] 节点的产出物...", nodeName);
        
        String topic = state.value(ScriptGraphState.KEY_TOPIC).map(v -> (String) v).orElse("未知主题");

        // 提取框架内部的白板执行 ID
        String graphExecId = state.value("_graph_execution_id_").map(v -> (String) v).orElse(null);
        log.info("🔍 获取到内部执行 ID: {}", graphExecId);

        // --- 核心溯源：直接使用 SessionID 作为 ThreadID ---
        String realThreadId = sessionId;
        String checkpointId = ""; // 不指定具体 Checkpoint，让底层自行获取该 threadId 的最新记录
        
        log.info("🎯 溯源成功！ThreadId: {}, CheckpointId: {}", realThreadId, checkpointId);

        String advice = generateAdviceFromContext(topic);
        
        InterventionEntity intervention = new InterventionEntity();
        intervention.setSessionId(sessionId);
        // 我们巧妙地把 checkpointId 也存起来，用逗号分隔，或者存在 executionId 字段里
        intervention.setExecutionId(realThreadId + ":" + (checkpointId != null ? checkpointId : "")); 
        intervention.setNodeName(nodeName);
        intervention.setAdvice(advice);
        intervention.setStatus("WAITING");
        intervention.setCreatedAt(LocalDateTime.now());
        intervention.setUpdatedAt(LocalDateTime.now());
        
        interventionMapper.insert(intervention);
        log.info("✅ 参谋建议已入库。");
    }

    private Map<String, String> findRealIdsByGraphId(String graphExecId) {
        // 已废弃复杂的 SQL 溯源逻辑，直接依赖框架原生的 sessionId
        return Map.of("thread_id", graphExecId, "checkpoint_id", "");
    }

    private String generateAdviceFromContext(String topic) {
        String prompt = String.format("你是剧本参谋。剧本【%s】刚完成大纲。请给出简短的专业点评和 3 条建议。", topic);
        return miniMaxClient.chat(prompt);
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
            interventionMapper.updateById(intervention);
        }
    }
}
