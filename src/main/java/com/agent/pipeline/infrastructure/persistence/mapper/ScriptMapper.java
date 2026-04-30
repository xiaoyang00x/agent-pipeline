package com.agent.pipeline.infrastructure.persistence.mapper;

import com.agent.pipeline.infrastructure.persistence.entity.ScriptEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 剧本资产 Mapper 接口
 * 
 * 继承 BaseMapper 后，自动获得所有的 CRUD 能力。
 */
@Mapper
public interface ScriptMapper extends BaseMapper<ScriptEntity> {
    // 工业级扩展提示：如果以后有超复杂的查询，可以在这里定义方法并在 XML 中写 SQL。
}
