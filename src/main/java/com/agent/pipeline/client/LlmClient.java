package com.agent.pipeline.client;

/**
 * 大语言模型客户端统一接口
 *
 * 所有 LLM 提供商（MiniMax、Claude、GPT 等）都应实现此接口。
 * 通过 Spring Profile 机制在运行时选择具体实现，无需修改代码。
 */
public interface LlmClient {

    /**
     * 向大模型发送提示词并获取回复
     */
    String chat(String prompt);
}
