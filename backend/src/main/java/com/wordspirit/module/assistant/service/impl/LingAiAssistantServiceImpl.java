package com.wordspirit.module.assistant.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wordspirit.ai.AiPromptConstant;
import com.wordspirit.ai.AiQuotaService;
import com.wordspirit.ai.AiService;
import com.wordspirit.common.BusinessException;
import com.wordspirit.common.ResultCode;
import com.wordspirit.module.assistant.dto.ChatSendReq;
import com.wordspirit.module.assistant.dto.ChatSendResp;
import com.wordspirit.module.assistant.entity.AiChatMessage;
import com.wordspirit.module.assistant.entity.AiChatSession;
import com.wordspirit.module.assistant.entity.AiFavorite;
import com.wordspirit.module.assistant.mapper.AiChatMessageMapper;
import com.wordspirit.module.assistant.mapper.AiChatSessionMapper;
import com.wordspirit.module.assistant.mapper.AiFavoriteMapper;
import com.wordspirit.module.assistant.service.LingAiAssistantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 词灵AI 助手服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LingAiAssistantServiceImpl implements LingAiAssistantService {

    /** 多轮上下文保留的消息条数 */
    private static final int HISTORY_LIMIT = 6;

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final AiFavoriteMapper favoriteMapper;
    private final AiService aiService;
    private final AiQuotaService aiQuotaService;

    @Override
    public List<AiChatSession> listSessions(Long userId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getUserId, userId)
                .orderByDesc(AiChatSession::getLastMessageAt));
    }

    @Override
    public AiChatSession createSession(Long userId, String mode, String title, String sourceTitle) {
        AiChatSession session = new AiChatSession();
        session.setUserId(userId);
        session.setMode(StrUtil.isNotBlank(mode) ? mode : "chat");
        session.setTitle(StrUtil.isNotBlank(title) ? title : "新对话");
        session.setSourceTitle(StrUtil.nullToEmpty(sourceTitle));
        session.setLastMessageAt(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long userId, Long sessionId) {
        AiChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }
        messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId));
        sessionMapper.deleteById(sessionId);
    }

    @Override
    public List<AiChatMessage> listMessages(Long userId, Long sessionId) {
        AiChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId)
                .orderByAsc(AiChatMessage::getCreatedAt));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSendResp send(Long userId, ChatSendReq req) {
        // 1. 会话解析：存在则复用，不存在则创建
        AiChatSession session;
        if (req.getSessionId() == null) {
            session = createSession(userId, req.getMode(), req.getTitle(), req.getSourceTitle());
        } else {
            session = sessionMapper.selectById(req.getSessionId());
            if (session == null || !session.getUserId().equals(userId)) {
                throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
            }
        }
        String mode = StrUtil.isNotBlank(req.getMode()) ? req.getMode() : session.getMode();

        // 2. 保存用户消息
        AiChatMessage userMsg = new AiChatMessage();
        userMsg.setSessionId(session.getId());
        userMsg.setUserId(userId);
        userMsg.setRole("user");
        userMsg.setContent(req.getContent());
        userMsg.setRenderType("text");
        userMsg.setIsFavorited(0);
        messageMapper.insert(userMsg);

        // 3. 构造提示词（含多轮上下文）
        String history = buildHistory(userId, session.getId());
        String system = buildSystemPrompt(mode, req);
        String userPrompt = buildUserPrompt(mode, req, history);

        // 4. 调用词灵AI（额度/限流/缓存/RAG/大模型/JSON清洗 全部在内部完成）
        //    深度思考 / 联网搜索由前端开关透传
        boolean deepThink = Boolean.TRUE.equals(req.getDeepThink());
        boolean webSearch = Boolean.TRUE.equals(req.getWebSearch());
        JSONObject json = aiService.callJson(userId, system, userPrompt, "chat".equals(mode), deepThink, webSearch);

        // 5. 计算渲染类型与预览文本（前端渲染层 + 纯文本兜底）
        String renderType = detectRenderType(json);
        String preview = buildPreview(json, renderType);

        // 6. 保存 AI 消息（原始 JSON 入库）
        AiChatMessage aiMsg = new AiChatMessage();
        aiMsg.setSessionId(session.getId());
        aiMsg.setUserId(userId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(preview);
        aiMsg.setJsonData(json.toString());
        aiMsg.setRenderType(renderType);
        aiMsg.setIsFavorited(0);
        messageMapper.insert(aiMsg);

        // 7. 更新会话：最后消息时间 + 自动命名
        AiChatSession upd = new AiChatSession();
        upd.setId(session.getId());
        upd.setLastMessageAt(LocalDateTime.now());
        if (StrUtil.isBlank(session.getTitle()) || "新对话".equals(session.getTitle())) {
            upd.setTitle(generateTitle(req.getContent()));
        }
        sessionMapper.updateById(upd);

        ChatSendResp resp = new ChatSendResp();
        resp.setSessionId(session.getId());
        resp.setMessage(aiMsg);
        resp.setQuotaRemain(aiQuotaService.getTodayRemain(userId));
        // 本次消耗次数：与 AiService 实际扣减保持一致（降级的增强能力不计费）
        resp.setCost(aiService.calcCost(deepThink, webSearch));
        return resp;
    }

    @Override
    public void favorite(Long userId, Long messageId) {
        AiChatMessage msg = messageMapper.selectById(messageId);
        if (msg == null || !msg.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "消息不存在");
        }
        AiFavorite fav = new AiFavorite();
        fav.setUserId(userId);
        fav.setSessionId(msg.getSessionId());
        fav.setMessageId(msg.getId());
        fav.setTitle(extractTitle(msg));
        fav.setContent(msg.getContent());
        fav.setJsonData(msg.getJsonData());
        fav.setRenderType(msg.getRenderType());
        favoriteMapper.insert(fav);

        AiChatMessage upd = new AiChatMessage();
        upd.setId(msg.getId());
        upd.setIsFavorited(1);
        messageMapper.updateById(upd);
    }

    @Override
    public List<AiFavorite> listFavorites(Long userId) {
        return favoriteMapper.selectList(new LambdaQueryWrapper<AiFavorite>()
                .eq(AiFavorite::getUserId, userId)
                .orderByDesc(AiFavorite::getCreatedAt));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFavorite(Long userId, Long id) {
        AiFavorite fav = favoriteMapper.selectById(id);
        if (fav == null || !fav.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "收藏不存在");
        }
        favoriteMapper.deleteById(id);
        // 同步复位消息的收藏标记，会话里的星标才能恢复未收藏状态
        if (fav.getMessageId() != null) {
            // 防御：同一条消息若还有其他收藏记录，则不复位
            Long remain = favoriteMapper.selectCount(new LambdaQueryWrapper<AiFavorite>()
                    .eq(AiFavorite::getMessageId, fav.getMessageId()));
            if (remain == 0) {
                AiChatMessage upd = new AiChatMessage();
                upd.setId(fav.getMessageId());
                upd.setIsFavorited(0);
                messageMapper.updateById(upd);
            }
        }
    }

    // ==================== 私有辅助 ====================

    /** 构造系统提示词（按模式选择对应提示词模板） */
    private String buildSystemPrompt(String mode, ChatSendReq req) {
        String base = switch (mode) {
            case "word_review" -> AiPromptConstant.GLOBAL_AI_PREFIX + AiPromptConstant.AI_WORD_REVIEW;
            case "paper" -> AiPromptConstant.GLOBAL_AI_PREFIX + AiPromptConstant.AI_PAPER_GENERATE;
            default -> AiPromptConstant.GLOBAL_AI_PREFIX + AiPromptConstant.AI_CHAT_SYSTEM;
        };
        // 将负载 JSON 与用户要求填充进提示词模板
        if (mode.equals("word_review") || mode.equals("paper")) {
            String wordJson = StrUtil.isNotBlank(req.getPayload())
                    ? req.getPayload() : "[]";
            String customReq = StrUtil.isNotBlank(req.getContent()) ? req.getContent() : "请生成";
            base = base.replace("{word_json_list}", wordJson)
                    .replace("{custom_req}", customReq);
        }
        return base;
    }

    /** 构造用户提示词：输入 + 多轮历史上下文 */
    private String buildUserPrompt(String mode, ChatSendReq req, String history) {
        String content = req.getContent();
        // 自由答疑：用户输入即完整问题
        if ("chat".equals(mode)) {
            return StrUtil.isBlank(history) ? content : content + "\n\n【历史对话】\n" + history;
        }
        // 生词巩固/试卷：内容已进入 system，这里仅带历史
        return StrUtil.isBlank(history) ? "请生成。" : "请生成。\n\n【历史对话】\n" + history;
    }

    /** 组装最近对话历史 */
    private String buildHistory(Long userId, Long sessionId) {
        List<AiChatMessage> recent = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId)
                .eq(AiChatMessage::getUserId, userId)
                .orderByDesc(AiChatMessage::getCreatedAt)
                .last("LIMIT " + HISTORY_LIMIT));
        StringBuilder sb = new StringBuilder();
        for (int i = recent.size() - 1; i >= 0; i--) {
            AiChatMessage m = recent.get(i);
            if ("user".equals(m.getRole())) {
                sb.append("用户: ").append(StrUtil.sub(m.getContent(), 0, 200)).append("\n");
            } else {
                sb.append("助手: ").append(StrUtil.sub(m.getContent(), 0, 200)).append("\n");
            }
        }
        return sb.toString();
    }

    /** 根据 JSON 结构识别渲染类型 */
    private String detectRenderType(JSONObject json) {
        if (json.containsKey("question_list")) {
            return "paper";
        }
        if (json.containsKey("word_list")) {
            return "cards";
        }
        if (json.containsKey("sentences")) {
            return "reading";
        }
        if ("refuse".equals(json.getStr("type"))) {
            return "refuse";
        }
        if (json.containsKey("error")) {
            return "error";
        }
        return "text";
    }

    /** 从 JSON 生成干净预览文本（前端渲染失败时兜底） */
    private String buildPreview(JSONObject json, String renderType) {
        try {
            return switch (renderType) {
                case "paper" -> json.getStr("paper_name", "AI试卷")
                        + "\n" + json.getStr("paper_intro", "");
                case "cards" -> json.getStr("group_title", "生词巩固包")
                        + "\n" + json.getStr("group_note", "");
                case "reading" -> "难度：" + json.getStr("difficulty", "unknown")
                        + " " + json.getStr("diff_desc", "");
                case "refuse" -> json.getStr("title", "")
                        + "\n" + json.getStr("content", "");
                default -> json.getStr("title", "") + "\n" + json.getStr("content", "");
            };
        } catch (Exception e) {
            return json.toString();
        }
    }

    /** 会话自动命名 */
    private String generateTitle(String content) {
        String clean = content.replaceAll("\\s+", " ").trim();
        return StrUtil.sub(clean, 0, 12) + (clean.length() > 12 ? "…" : "");
    }

    /** 收藏标题提取 */
    private String extractTitle(AiChatMessage msg) {
        if (StrUtil.isNotBlank(msg.getJsonData())) {
            try {
                JSONObject json = JSONUtil.parseObj(msg.getJsonData());
                String title = json.getStr("title");
                if (StrUtil.isBlank(title)) {
                    title = json.getStr("paper_name");
                }
                if (StrUtil.isBlank(title)) {
                    title = json.getStr("group_title");
                }
                if (StrUtil.isNotBlank(title)) {
                    return StrUtil.sub(title, 0, 100);
                }
            } catch (Exception ignored) {
            }
        }
        return StrUtil.sub(msg.getContent(), 0, 100);
    }
}
