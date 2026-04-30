package com.agent.pipeline;

import com.agent.pipeline.infrastructure.persistence.entity.InterventionEntity;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AgentPipelineEndToEndTest {

    private static final Logger log = LoggerFactory.getLogger(AgentPipelineEndToEndTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testFullHitlWorkflow() throws Exception {
        String sessionId = "auto-test-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("▶️ [1/4] 开始自动化端到端测试，SessionID: {}", sessionId);

        // 1. 触发图执行（会在 Planner 节点后被拦截挂起）
        log.info("▶️ [2/4] 触发 /agent/create-script (Topic: Cyberpunk)");
        MvcResult createResult = mockMvc.perform(get("/agent/create-script")
                        .param("topic", "Cyberpunk")
                        .param("requirement", "Test")
                        .param("sessionId", sessionId))
                .andExpect(status().isOk())
                .andReturn();
        
        log.info("✅ 初始阶段执行完成，输出: {}", createResult.getResponse().getContentAsString());

        // 等待一下，让数据库和参谋服务落库
        Thread.sleep(1000);

        // 2. 获取参谋建议
        log.info("▶️ [3/4] 验证参谋 Agent 建议...");
        MvcResult adviceResult = mockMvc.perform(get("/intervention/" + sessionId + "/advice"))
                .andExpect(status().isOk())
                .andReturn();
        
        String adviceResponse = adviceResult.getResponse().getContentAsString();
        log.info("✅ 成功获取参谋建议: {}", adviceResponse);

        // 3. 注入人类反馈，接关恢复执行
        log.info("▶️ [4/4] 提交导演反馈并接关 (Resume)...");
        String payload = "{\"feedback\": \"建议很好，请继续增加赛博朋克元素\"}";
        
        MvcResult resumeResult = mockMvc.perform(post("/intervention/" + sessionId + "/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        // 因为是 SSE (Flux)，我们获取异步结果或者读取流
        String resumeResponse = resumeResult.getResponse().getContentAsString();
        log.info("✅ 接关执行完成，SSE 输出: {}", resumeResponse);
        
        log.info("🎉 自动化端到端 HITL 工作流测试全部通过！");
    }
}
