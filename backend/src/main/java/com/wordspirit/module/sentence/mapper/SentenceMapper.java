package com.wordspirit.module.sentence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wordspirit.module.sentence.entity.Sentence;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SentenceMapper extends BaseMapper<Sentence> {
}