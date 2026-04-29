package com.agent.pipeline.workflow.config;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.GraphLifecycleListener;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import com.agent.pipeline.client.MiniMaxClient;
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
    public CompiledGraph scriptCreationGraph(MiniMaxClient miniMaxClient, 
                                            DataSource dataSource, 
                                            WorkflowProperties workflowProperties) throws Exception {

        var planner = node_async(new PlannerNode(miniMaxClient, workflowProperties));
        var writer = node_async(new WriterNode(miniMaxClient, workflowProperties));
        var reviewer = node_async(new ReviewerNode(miniMaxClient, workflowProperties));

        StateGraph workflow = new StateGraph(ScriptGraphState.createKeyStrategyFactory())
                .addNode("planner", planner)
                .addNode("writer", writer)
                .addNode("reviewer", reviewer);

        workflow.addEdge(START, "planner");

        // --- 人机协作路由：Planner 阶段 ---
        workflow.addConditionalEdges("planner", edge_async(state -> {
            String feedback = (String) state.value(ScriptGraphState.KEY_HUMAN_INTERVENTION).orElse("");
            if (feedback.contains("重写") || feedback.contains("重做") || feedback.contains("不行")) {
                log.info("🎯 [协作路由] 导演指令：打回重做策划...");
                return "planner";
            }
            return "writer";
        }), Map.of("planner", "planner", "writer", "writer"));

        workflow.addEdge("writer", "reviewer");

        // --- 人机协作路由：Reviewer 阶段 ---
        workflow.addConditionalEdges("reviewer", edge_async(state -> {
            String feedback = (String) state.value(ScriptGraphState.KEY_HUMAN_INTERVENTION).orElse("");
            // 导演最高指示优先
            if (feedback.contains("通过") || feedback.contains("可以") || feedback.contains("完结")) {
                log.info("🎯 [协作路由] 导演指令：强制通过审稿...");
                return "end";
            }
            if (feedback.contains("重写") || feedback.contains("重做")) {
                log.info("🎯 [协作路由] 导演指令：打回修改剧本...");
                return "writer";
            }
            // 默认遵循 Agent 的评审结论
            return (String) state.value(ScriptGraphState.KEY_NEXT_NODE).orElse("end");
        }), Map.of("writer", "writer", "end", END, "planner", "planner"));

        var saver = MysqlSaver.builder()
                .dataSource(dataSource)
                .stateSerializer(new SpringAIJacksonStateSerializer(workflow.getStateFactory()))
                .build();
        
        var compileConfigBuilder = CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(saver).build());

        // 终极捕获网：监控每一个动作
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

        if ("SELECTIVE".equalsIgnoreCase(workflowProperties.getMode()) && workflowProperties.getBreakpoints() != null) {
            compileConfigBuilder.interruptsAfter(workflowProperties.getBreakpoints());
        }

        return workflow.compile(compileConfigBuilder.build());
    }
}
