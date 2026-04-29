package com.agent.pipeline.controller;

import com.agent.pipeline.mapper.ScriptMapper;
import com.agent.pipeline.model.ScriptEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 剧本资产管理控制器
 * 
 * 提供剧本的列表查询、模糊搜索、详情查看及互动功能。
 */
@RestController
@RequestMapping("/scripts")
public class ScriptController {

    private final ScriptMapper scriptMapper;

    public ScriptController(ScriptMapper scriptMapper) {
        this.scriptMapper = scriptMapper;
    }

    /**
     * 分页查询剧本列表
     * 
     * GET /scripts/list?current=1&size=10&keyword=赛博
     */
    @GetMapping("/list")
    public Map<String, Object> listScripts(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {

        Page<ScriptEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<ScriptEntity> wrapper = new LambdaQueryWrapper<>();
        
        // 模糊搜索主题
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(ScriptEntity::getTopic, keyword);
        }
        
        // 按时间倒序排序
        wrapper.orderByDesc(ScriptEntity::getCreatedAt);

        Page<ScriptEntity> resultPage = scriptMapper.selectPage(page, wrapper);

        return Map.of(
            "total", resultPage.getTotal(),
            "pages", resultPage.getPages(),
            "data", resultPage.getRecords()
        );
    }

    /**
     * 获取剧本详情
     */
    @GetMapping("/{id}")
    public ScriptEntity getDetail(@PathVariable Long id) {
        // 每次查看，增加一次浏览量
        ScriptEntity entity = scriptMapper.selectById(id);
        if (entity != null) {
            entity.setViews(entity.getViews() + 1);
            scriptMapper.updateById(entity);
        }
        return entity;
    }

    /**
     * 点赞剧本
     */
    @PostMapping("/{id}/like")
    public String likeScript(@PathVariable Long id) {
        ScriptEntity entity = scriptMapper.selectById(id);
        if (entity != null) {
            entity.setLikes(entity.getLikes() + 1);
            scriptMapper.updateById(entity);
            return "点赞成功，当前赞数: " + entity.getLikes();
        }
        return "剧本不存在";
    }
}
