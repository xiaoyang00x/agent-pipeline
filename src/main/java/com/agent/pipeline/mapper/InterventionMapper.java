package com.agent.pipeline.mapper;

import com.agent.pipeline.model.InterventionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 人机干预记录 Mapper
 */
@Mapper
public interface InterventionMapper extends BaseMapper<InterventionEntity> {
}
