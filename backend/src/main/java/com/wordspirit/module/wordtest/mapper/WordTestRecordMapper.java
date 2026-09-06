package com.wordspirit.module.wordtest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wordspirit.module.wordtest.entity.WordTestRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WordTestRecordMapper extends BaseMapper<WordTestRecord> {
}