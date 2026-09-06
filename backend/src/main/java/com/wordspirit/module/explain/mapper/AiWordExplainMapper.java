package com.wordspirit.module.explain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wordspirit.module.explain.entity.AiWordExplain;
import org.apache.ibatis.annotations.Mapper;

/**
 * 阅读解析缓存 Mapper
 */
@Mapper
public interface AiWordExplainMapper extends BaseMapper<AiWordExplain> {
}
