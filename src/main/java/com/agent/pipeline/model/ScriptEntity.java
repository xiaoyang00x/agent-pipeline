package com.agent.pipeline.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 剧本资产实体类
 * 
 * 映射数据库中的 scripts 表，用于存储最终产出的优质剧本内容。
 */
@TableName("scripts")
public class ScriptEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的 Agent 会话 ID
     */
    private String sessionId;

    /**
     * 剧本主题
     */
    private String topic;

    /**
     * 剧本大纲
     */
    private String outline;

    /**
     * 剧本正文
     */
    private String content;

    /**
     * 审稿意见
     */
    private String reviewFeedback;

    /**
     * 创作完成时间
     */
    private LocalDateTime createdAt;

    // --- 标准 Getter 和 Setter ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getOutline() {
        return outline;
    }

    public void setOutline(String outline) {
        this.outline = outline;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getReviewFeedback() {
        return reviewFeedback;
    }

    public void setReviewFeedback(String reviewFeedback) {
        this.reviewFeedback = reviewFeedback;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
