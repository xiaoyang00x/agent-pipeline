package com.agent.pipeline.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 剧本资产实体类 (业务增强版)
 * 
 * 映射数据库中的 scripts 表，包含更丰富的业务维度，支持分类、标签和互动数据。
 */
@TableName("scripts")
public class ScriptEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private String topic;

    /**
     * 剧本分类 (例如: 科幻, 奇幻, 现代, 历史)
     */
    private String category;

    /**
     * 剧本标签 (以逗号分隔)
     */
    private String tags;

    private String outline;

    private String content;

    /**
     * 点赞数
     */
    private Integer likes;

    /**
     * 浏览量
     */
    private Integer views;

    /**
     * 状态: DRAFT (草稿), PUBLISHED (已发布)
     */
    private String status;

    private String reviewFeedback;

    private LocalDateTime createdAt;

    // --- 标准 Getter 和 Setter ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getOutline() { return outline; }
    public void setOutline(String outline) { this.outline = outline; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getLikes() { return likes; }
    public void setLikes(Integer likes) { this.likes = likes; }

    public Integer getViews() { return views; }
    public void setViews(Integer views) { this.views = views; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReviewFeedback() { return reviewFeedback; }
    public void setReviewFeedback(String reviewFeedback) { this.reviewFeedback = reviewFeedback; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
