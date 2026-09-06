package com.wordspirit.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wordspirit.module.user.entity.EmailBlacklist;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmailBlacklistMapper extends BaseMapper<EmailBlacklist> {
}