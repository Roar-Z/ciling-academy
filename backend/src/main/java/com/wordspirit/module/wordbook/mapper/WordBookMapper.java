package com.wordspirit.module.wordbook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wordspirit.module.wordbook.entity.WordBook;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生词本 Mapper
 */
@Mapper
public interface WordBookMapper extends BaseMapper<WordBook> {
}
