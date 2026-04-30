package com.agent.pipeline.workflow.config;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.GraphLifecycleListener;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import com.agent.pipeline.config.WorkflowProperties;
import com.agent.pipeline.infrastructure.client.LlmClient;
import com.agent.pipeline.workflow.state.ScriptGraphState;
import com.agent.pipeline.workflow.node.PlannerNode;
import com.agent.pipeline.workflow.node.ReviewerNode;
import com.agent.pipeline.workflow.node.WriterNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Configuration
public class ScriptGraphConfig {

    private static final Logger log = LoggerFactory.getLogger(ScriptGraphConfig.class);

    @Bean
    public CompiledGraph scriptCreationGraph(LlmClient llmClient,
                                            DataSource dataSource,
                                            WorkflowProperties workflowProperties) throws Exception {

        var planner  = node_async(new PlannerNode(llmClient, workflowProperties));
        var writer   = node_async(new WriterNode(llmClient, workflowProperties));
        var reviewer = node_async(new ReviewerNode(llmClient, workflowProperties));
        
        // 新增：看门人节点（空节点，仅用于承载路由逻辑，并作为物理断点位）
        var plannerApproval  = node_async(state -> state.data());
        var directorApproval = node_async(state -> state.data());

        StateGraph workflow = new StateGraph(ScriptGraphState.createKeyStrategyFactory())
                .addNode("planner", planner)
                .addNode("planner_approval", plannerApproval)
                .addNode("writer", writer)
                .addNode("reviewer", reviewer)
                .addNode("director_approval", directorApproval);

        // --- 图路由配置 ---
        workflow.addEdge(START, "planner");

        // 策划完活后必须经过策划看门人
        workflow.addEdge("planner", "planner_approval");

        // 策划看门人路由逻辑
        workflow.addConditionalEdges("planner_approval", edge_async(state -> {
            String feedback = (String) state.value(ScriptGraphState.KEY_HUMAN_INTERVENTION).orElse("");
            if (feedback.contains("重写") || feedback.contains("重做") || feedback.contains("不行")) {
                log.info("🎯 [协作路由] 导演下令：策划不通过，打回重写。");
                return "planner";
            }
            log.info("🎯 [协作路由] 导演下令：策划通过，进入编剧环节。");
            return "writer";
        }), Map.of("planner", "planner", "writer", "writer"));

        workflow.addEdge("writer", "reviewer");
        
        // 确保一定会走到导演拍板节点，从而强制触发 reviewer 后的断点
        workflow.addEdge("reviewer", "director_approval");

        // 终审看门人路由逻辑
        workflow.addConditionalEdges("director_approval", edge_async(state -> {
            String feedback = (String) state.value(ScriptGraphState.KEY_HUMAN_INTERVENTION).orElse("");
            
            // 1. 人类绝对优先拍板
            if (feedback.contains("通过")) {
                log.info("🎯 [协作路由] 导演下令：正式通过，项目完结。");
                return "finish";
            } else if (feedback.contains("重写") || feedback.contains("重做") || feedback.contains("不行")) {
                log.info("🎯 [协作路由] 导演下令：剧本不通过，打回重写。");
                return "writer";
            }
            
            // 2. 默认参考机器评审
            boolean approved = (Boolean) state.value(ScriptGraphState.KEY_APPROVED).orElse(false);
            if (approved) {
                log.info("🎯 [协作路由] 默认采纳机器评审：审核通过。");
                return "finish";
            } else {
                log.info("🎯 [协作路由] 默认采纳机器评审：打回重写。");
                return "writer";
            }
        }), Map.of("writer", "writer", "finish", END));

        var saver = MysqlSaver.builder()
                .dataSource(dataSource)
                .stateSerializer(new SpringAIJacksonStateSerializer(workflow.getStateFactory()))
                .build();

        var compileConfigBuilder = CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(saver).build());

        // 生命周期监听：监控图执行全过程
        compileConfigBuilder.withLifecycleListener(new GraphLifecycleListener() {
            @Override
            public void onStart(String executionId, Map<String, Object> inputs, RunnableConfig config) {
                log.info("🔥 [图启动] ExecutionId: {}, ThreadId: {}", executionId, config.threadId());
            }

            @Override
            public void before(String executionId, Map<String, Object> inputs, RunnableConfig config, Long timestamp) {
                log.info("⚡ [节点执行前] ExecutionId: {}, ThreadId: {}", executionId, config.threadId());
            }

            @Override
            public void onComplete(String executionId, Map<String, Object> results, RunnableConfig config) {
                log.info("🏁 [图结束] ExecutionId: {}, ThreadId: {}", executionId, config.threadId());
            }
        });

        if ("SELECTIVE".equalsIgnoreCase(workflowProperties.getMode())) {
            // 还原为最稳健的两个物理断点
            compileConfigBuilder.interruptBefore("planner_approval", "director_approval");
        }

        return workflow.compile(compileConfigBuilder.build());
    }
}
