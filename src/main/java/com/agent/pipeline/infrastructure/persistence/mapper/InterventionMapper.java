package com.agent.pipeline.infrastructure.persistence.mapper;

import com.agent.pipeline.infrastructure.persistence.entity.InterventionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InterventionMapper extends BaseMapper<InterventionEntity> {
}
