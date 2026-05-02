package com.agent.pipeline.infrastructure.client;

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

    /**
     * 向大模型发送提示词，并在收到每个 Token 时触发回调（用于 SSE 流式传输），最终返回完整回复
     */
    default String chatStream(String prompt, java.util.function.Consumer<String> onToken) {
        // 默认实现为不支持流式，直接返回完整结果并调用一次回调
        String response = chat(prompt);
        if (onToken != null) {
            onToken.accept(response);
        }
        return response;
    }
}
