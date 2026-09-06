package com.wordspirit.module.assistant.service;

import com.wordspirit.module.assistant.dto.ChatSendReq;
import com.wordspirit.module.assistant.dto.ChatSendResp;
import com.wordspirit.module.assistant.entity.AiChatMessage;
import com.wordspirit.module.assistant.entity.AiChatSession;
import com.wordspirit.module.assistant.entity.AiFavorite;

import java.util.List;

/**
 * 词灵AI 助手服务（对话会话、消息、收藏）
 */
public interface LingAiAssistantService {

    /** 会话列表 */
    List<AiChatSession> listSessions(Long userId);

    /** 新建会话 */
    AiChatSession createSession(Long userId, String mode, String title, String sourceTitle);

    /** 删除会话（级联删除消息） */
    void deleteSession(Long userId, Long sessionId);

    /** 会话消息列表 */
    List<AiChatMessage> listMessages(Long userId, Long sessionId);

    /**
     * 发送消息（模式B核心流程）：
     * 登录校验 → 额度校验 → 限流 → 缓存 → RAG → 大模型 → JSON清洗 → 存库 → 返回
     */
    ChatSendResp send(Long userId, ChatSendReq req);

    /** 收藏 AI 消息 → 我的AI笔记 */
    void favorite(Long userId, Long messageId);

    /** 收藏列表 */
    List<AiFavorite> listFavorites(Long userId);

    /** 取消收藏 */
    void removeFavorite(Long userId, Long id);
}
