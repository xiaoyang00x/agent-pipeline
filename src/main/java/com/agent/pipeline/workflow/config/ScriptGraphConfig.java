package com.agent.pipeline.workflow.config;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.agent.pipeline.client.MiniMaxClient;
import com.agent.pipeline.workflow.state.ScriptGraphState;
import com.agent.pipeline.workflow.node.PlannerNode;
import com.agent.pipeline.workflow.node.ReviewerNode;
import com.agent.pipeline.workflow.node.WriterNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 剧本工作流配置类 (Graph Config)
 *
 * 职责：负责将"策划"、"编剧"、"审稿"这三个节点连接起来，形成一个完整的工作流，
 * 并配置持久化策略（MysqlSaver），最后暴露为一个 Spring Bean 供 Service 层调用。
 */
@Configuration
public class ScriptGraphConfig {

    @Bean
    public CompiledGraph scriptCreationGraph(MiniMaxClient miniMaxClient, DataSource dataSource) throws Exception {

        // 1. 初始化所有节点，注入我们自己的 MiniMaxClient
        var planner = node_async(new PlannerNode(miniMaxClient));
        var writer = node_async(new WriterNode(miniMaxClient));
        var reviewer = node_async(new ReviewerNode(miniMaxClient));

        // 2. 创建状态图，绑定我们之前定义的 State (白板)
        StateGraph workflow = new StateGraph(ScriptGraphState.createKeyStrategyFactory())
                .addNode("planner", planner)
                .addNode("writer", writer)
                .addNode("reviewer", reviewer);

        // 3. 定义基本的连接边：流程起点 -> planner
        workflow.addEdge(START, "planner");

        // 4. 定义条件边（Conditional Edges）
        // 策划完成后的路由判断：提取状态中的 next_node 值，默认去 writer
        workflow.addConditionalEdges("planner", edge_async(state ->
            (String) state.value(ScriptGraphState.KEY_NEXT_NODE).orElse("writer")
        ), Map.of(
            "writer", "writer"
        ));

        // 编剧完成后的路由判断：去 reviewer
        workflow.addConditionalEdges("writer", edge_async(state ->
            (String) state.value(ScriptGraphState.KEY_NEXT_NODE).orElse("reviewer")
        ), Map.of(
            "reviewer", "reviewer"
        ));

        // 审阅完成后的路由判断：是打回重做去 writer，还是通过了去 END？
        workflow.addConditionalEdges("reviewer", edge_async(state ->
            (String) state.value(ScriptGraphState.KEY_NEXT_NODE).orElse(END)
        ), Map.of(
            "writer", "writer",
            "end", END
        ));

        // 5. 配置记忆持久化引擎（MySQL 模式，后端连接 H2 兼容层）
        var saver = MysqlSaver.builder()
                .dataSource(dataSource)
                .build();
        var compileConfig = CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(saver).build())
                .build();

        // 6. 编译并返回可执行的工作流
        return workflow.compile(compileConfig);
    }
}
