package com.wordspirit.module.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送消息请求
 */
@Data
public class ChatSendReq {

    /** 会话ID，为空则自动新建 */
    private Long sessionId;

    /** 模式: chat/word_review/paper */
    private String mode = "chat";

    /** 新会话标题 */
    private String title;

    /** 来源提示标题（业务页跳转时携带） */
    private String sourceTitle;

    /** 业务参数负载（生词/单词池的 JSON 数组字符串） */
    private String payload;

    /** 用户输入内容 */
    @NotBlank(message = "发送内容不能为空")
    private String content;

    /** 是否开启深度思考：true 时切换为思考模型并启用思维链（enable_thinking） */
    private Boolean deepThink = false;

    /** 是否开启联网搜索：true 时切换为支持联网的模型并启用内置搜索（enable_search） */
    private Boolean webSearch = false;
}
