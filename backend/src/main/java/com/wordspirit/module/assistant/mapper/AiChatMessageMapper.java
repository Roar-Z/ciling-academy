package com.wordspirit.module.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wordspirit.module.assistant.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 消息 Mapper
 */
@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {
}
