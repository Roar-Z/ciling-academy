package com.wordspirit.module.assistant.dto;

import com.wordspirit.module.assistant.entity.AiChatMessage;
import lombok.Data;

/**
 * 发送消息响应
 */
@Data
public class ChatSendResp {

    private Long sessionId;
    /** AI 消息（含 jsonData 原始JSON与content预览文本） */
    private AiChatMessage message;
    /** 今日剩余AI额度 */
    private Integer quotaRemain;
    /** 本次消耗额度次数：基础1次；开启深度思考/联网搜索为2次（同开不叠加），供前端提示 */
    private Integer cost;
}
