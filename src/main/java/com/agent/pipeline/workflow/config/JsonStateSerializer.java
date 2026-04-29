package com.agent.pipeline.workflow.config;

import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;

/**
 * 自定义 JSON 状态序列化器
 *
 * 职责：将 Graph 的 OverAllState (白板数据) 以 JSON 格式进行序列化/反序列化。
 * 这样存储在数据库里的内容就是人类可读的 JSON 字符串，方便审计和跨系统访问。
 */
public class JsonStateSerializer extends StateSerializer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void writeData(Map<String, Object> data, ObjectOutput out) throws IOException {
        // 将 Map 转为 JSON 字符串并写入输出流
        byte[] bytes = objectMapper.writeValueAsBytes(data);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    @Override
    public Map<String, Object> readData(ObjectInput in) throws IOException, ClassNotFoundException {
        // 从输入流读取长度和字节，还原为 Map
        int len = in.readInt();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return objectMapper.readValue(bytes, new TypeReference<Map<String, Object>>() {});
    }
}
