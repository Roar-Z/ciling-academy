package com.wordspirit.module.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wordspirit.module.assistant.entity.AiChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 会话 Mapper
 */
@Mapper
public interface AiChatSessionMapper extends BaseMapper<AiChatSession> {
}
