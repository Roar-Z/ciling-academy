package com.wordspirit.module.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wordspirit.module.dict.entity.DictWord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 词典 Mapper
 */
@Mapper
public interface DictWordMapper extends BaseMapper<DictWord> {
}
