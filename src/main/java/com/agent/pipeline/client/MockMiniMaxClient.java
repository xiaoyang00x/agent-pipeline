package com.agent.pipeline.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mock 版 MiniMax 客户端
 * 暂时移除 @Primary 注解，以切换回真实 API 测试
 */
// @Primary
// @Component
public class MockMiniMaxClient extends MiniMaxClient {

    private static final Logger log = LoggerFactory.getLogger(MockMiniMaxClient.class);

    @Override
    public String chat(String prompt) {
        log.info("🤖 [Mock Mode] 拦截请求，秒回 Mock 数据...");

        if (prompt.contains("大纲")) {
            return "【Mock 剧本大纲】\n1. 发现：主角在数字森林深处找到黄金母根。\n2. 冲突：母根系统开始报错，需要人类意识介入。\n3. 解决：主角通过注入反馈，修复了逻辑链路。";
        }

        if (prompt.contains("点评") || prompt.contains("参谋")) {
            return "【Mock 参谋建议】\n这个点子非常棒！建议在接关后增加一段关于 ID 溯源成功的剧情描写。";
        }

        return "【Mock 通用回复】流程测试中，ID 溯源验证通过。";
    }
}
