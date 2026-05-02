package com.agent.pipeline.service;

import com.agent.pipeline.advisor.InterventionAdvisor;
import com.agent.pipeline.infrastructure.client.LlmClient;
import com.agent.pipeline.infrastructure.persistence.mapper.InterventionMapper;
import com.agent.pipeline.infrastructure.persistence.entity.InterventionEntity;
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
    private final SseStreamManager sseStreamManager;

    public InterventionAssistantService(InterventionMapper interventionMapper, 
                                      LlmClient llmClient,
                                      List<InterventionAdvisor> advisors,
                                      SseStreamManager sseStreamManager) {
        this.interventionMapper = interventionMapper;
        this.llmClient = llmClient;
        this.advisors = advisors;
        this.sseStreamManager = sseStreamManager;
    }

    /**
     * 在图引擎中断后被触发，动态生成参谋建议并入库 (策略模式实现)
     */
    public void prepareIntervention(String sessionId, String nodeName, OverAllState state) {
        // 0. 提前检查是否支持该节点的干预（过滤掉如 writer 等纯自动化节点），避免无效查询和日志刷屏
        InterventionAdvisor advisor = advisors.stream()
                .filter(a -> a.supports(nodeName))
                .findFirst()
                .orElse(null);

        if (advisor == null) {
            return; // 静默跳过，不需要干预
        }

        // 1. 幂等性检查：只要存在 GENERATING 或 WAITING 状态的建议，直接返回，防止轮询并发调用
        InterventionEntity existing = interventionMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InterventionEntity>()
                .eq(InterventionEntity::getSessionId, sessionId)
                .eq(InterventionEntity::getNodeName, nodeName)
                .in(InterventionEntity::getStatus, "WAITING", "GENERATING")
                .last("LIMIT 1")
        );
        if (existing != null) {
            return; 
        }

        log.info("📢 [全知参谋] 正在为断点节点 [{}] 准备诊断建议...", nodeName);

        // 3. ✨ 关键修复：立即入库一个 GENERATING 状态的占位符，阻断后续轮询的并发触发
        InterventionEntity placeholder = new InterventionEntity();
        placeholder.setSessionId(sessionId);
        placeholder.setExecutionId(sessionId);
        placeholder.setNodeName(nodeName);
        placeholder.setAdvice("正在生成诊断建议，请稍候..."); // 临时文案
        placeholder.setStatus("GENERATING");
        placeholder.setCreatedAt(LocalDateTime.now());
        placeholder.setUpdatedAt(LocalDateTime.now());
        interventionMapper.insert(placeholder);

        // 4. 构建 Prompt 并异步调用大模型 (解决阻塞问题)
        String prompt = advisor.buildPrompt(state);
        log.info("🧠 [全知参谋] 正在提交异步任务以分析局势...");
        
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                String advice = llmClient.chatStream(prompt, token -> {
                    sseStreamManager.sendToken(sessionId, "advisor_chunk", token);
                });
                
                // 5. 成功后更新为 WAITING 状态，前端方可拉取到真实建议
                placeholder.setAdvice(advice);
                placeholder.setStatus("WAITING");
                placeholder.setUpdatedAt(LocalDateTime.now());
                interventionMapper.updateById(placeholder);
                log.info("✅ 参谋建议已准备就绪并入库。策略提供者: {}", advisor.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("❌ 参谋建议生成失败", e);
                // 失败时直接删除占位符，允许下次重试
                interventionMapper.deleteById(placeholder.getId());
            }
        });
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
