package com.agent.pipeline.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Mock 版 LLM 客户端
 *
 * 通过 spring.profiles.active=mock 激活，无需修改任何源码。
 * 用于本地开发和自动化测试，秒回 Mock 数据。
 */
@Component
@Profile("mock")
public class MockMiniMaxClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MockMiniMaxClient.class);

    @Override
    public String chat(String prompt) {
        log.info("🤖 [Mock Mode] 拦截请求，秒回 Mock 数据...");

        if (prompt.contains("大纲")) {
            return "【Mock 剧本大纲】\n1. 发现：主角在数字森林深处找到黄金母根。\n2. 冲突：母根系统开始报错，需要人类意识介入。\n3. 解决：主角通过注入反馈，修复了逻辑链路。";
        }

        if (prompt.contains("参谋") || prompt.contains("点评")) {
            return "【Mock 参谋建议】\n1. 建议加强角色动机线。\n2. 建议增加反转情节。\n3. 建议优化结尾节奏。";
        }

        if (prompt.contains("审阅") || prompt.contains("审稿")) {
            return "{\"approved\": true, \"feedback\": \"无\"}";
        }

        return "【Mock 通用回复】流程测试中，Mock 验证通过。";
    }
}
