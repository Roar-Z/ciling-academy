package com.wordspirit.module.longsentence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wordspirit.module.longsentence.entity.AiLongSentenceAnalysis;
import org.apache.ibatis.annotations.Mapper;

/**
 * 长难句分析缓存 Mapper
 */
@Mapper
public interface AiLongSentenceAnalysisMapper extends BaseMapper<AiLongSentenceAnalysis> {
}