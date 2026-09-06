package com.wordspirit.module.assistant.controller;

import com.wordspirit.common.Result;
import com.wordspirit.common.UserContext;
import com.wordspirit.module.assistant.dto.ChatSendReq;
import com.wordspirit.module.assistant.dto.ChatSendResp;
import com.wordspirit.module.assistant.dto.FavoriteReq;
import com.wordspirit.module.assistant.entity.AiChatMessage;
import com.wordspirit.module.assistant.entity.AiChatSession;
import com.wordspirit.module.assistant.entity.AiFavorite;
import com.wordspirit.module.assistant.service.LingAiAssistantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 词灵AI 助手接口（模式B：对话归集）
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class LingAiAssistantController {

    private final LingAiAssistantService aiAssistantService;

    /** 会话列表 */
    @GetMapping("/session/list")
    public Result<List<AiChatSession>> listSessions() {
        return Result.ok(aiAssistantService.listSessions(UserContext.requireUserId()));
    }

    /** 新建会话 */
    @PostMapping("/session")
    public Result<AiChatSession> createSession(@RequestParam(defaultValue = "chat") String mode,
                                               @RequestParam(required = false) String title,
                                               @RequestParam(required = false) String sourceTitle) {
        return Result.ok(aiAssistantService.createSession(
                UserContext.requireUserId(), mode, title, sourceTitle));
    }

    /** 删除会话 */
    @DeleteMapping("/session/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        aiAssistantService.deleteSession(UserContext.requireUserId(), id);
        return Result.ok();
    }

    /** 会话消息列表 */
    @GetMapping("/session/{id}/messages")
    public Result<List<AiChatMessage>> listMessages(@PathVariable Long id) {
        return Result.ok(aiAssistantService.listMessages(UserContext.requireUserId(), id));
    }

    /** 发送消息（核心） */
    @PostMapping("/chat/send")
    public Result<ChatSendResp> send(@Valid @RequestBody ChatSendReq req) {
        return Result.ok(aiAssistantService.send(UserContext.requireUserId(), req));
    }

    /** 收藏消息 */
    @PostMapping("/favorite")
    public Result<Void> favorite(@Valid @RequestBody FavoriteReq req) {
        aiAssistantService.favorite(UserContext.requireUserId(), req.getMessageId());
        return Result.ok();
    }

    /** 收藏列表（我的AI笔记） */
    @GetMapping("/favorite/list")
    public Result<List<AiFavorite>> listFavorites() {
        return Result.ok(aiAssistantService.listFavorites(UserContext.requireUserId()));
    }

    /** 取消收藏 */
    @DeleteMapping("/favorite/{id}")
    public Result<Void> removeFavorite(@PathVariable Long id) {
        aiAssistantService.removeFavorite(UserContext.requireUserId(), id);
        return Result.ok();
    }
}
