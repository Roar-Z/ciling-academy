package com.wordspirit.module.wordbook.controller;

import com.wordspirit.common.Result;
import com.wordspirit.common.UserContext;
import com.wordspirit.module.user.service.UserService;
import com.wordspirit.module.wordbook.dto.BatchAddReq;
import com.wordspirit.module.wordbook.dto.WordAddReq;
import com.wordspirit.module.wordbook.entity.WordBook;
import com.wordspirit.module.wordbook.service.WordBookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 生词本接口
 */
@RestController
@RequestMapping("/api/word-book")
@RequiredArgsConstructor
public class WordBookController {

    private final WordBookService wordBookService;
    private final UserService userService;

    /** 分页查询生词本 */
    @GetMapping("/list")
    public Result<Object> list(String keyword, Long page, Long size) {
        return Result.ok(wordBookService.list(UserContext.requireUserId(), keyword,
                page == null ? 1 : page, size == null ? 10 : size));
    }

    /** 加入生词本 */
    @PostMapping("/add")
    public Result<WordBook> add(@Valid @RequestBody WordAddReq req) {
        return Result.ok(wordBookService.add(UserContext.requireUserId(), req));
    }

    /** 批量加入（阅读助手一键收藏） */
    @PostMapping("/batch-add")
    public Result<Map<String, Object>> batchAdd(@Valid @RequestBody BatchAddReq req) {
        int added = wordBookService.batchAdd(UserContext.requireUserId(), req);
        return Result.ok(Map.of("added", added));
    }

    /** 删除生词 */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        wordBookService.remove(UserContext.requireUserId(), id);
        return Result.ok();
    }

    /** 批量删除生词 */
    @PostMapping("/batch-remove")
    public Result<Void> batchRemove(@RequestBody List<Long> ids) {
        wordBookService.batchRemove(UserContext.requireUserId(), ids);
        return Result.ok();
    }

    /** 复习完成 */
    @PostMapping("/{id}/review")
    public Result<Void> review(@PathVariable Long id, Boolean familiar) {
        wordBookService.review(UserContext.requireUserId(), id,
                Boolean.TRUE.equals(familiar));
        return Result.ok();
    }

    /** 今日待复习单词（艾宾浩斯） */
    @GetMapping("/due-review")
    public Result<List<WordBook>> dueReview(Integer limit) {
        return Result.ok(wordBookService.dueReview(UserContext.requireUserId(),
                limit == null ? 10 : limit));
    }

    /** 小游戏随机取词（优先生词本） */
    @GetMapping("/random")
    public Result<List<Map<String, Object>>> random(Integer count) {
        return Result.ok(wordBookService.randomForGame(UserContext.requireUserId(),
                count == null ? 10 : count));
    }

    /**
     * 小游戏取词（艾宾浩斯优先级）：
     * 1) 生词本到期复习词  2) 生词本其他词  3) 词典高频核心词
     * 每条带 source: due/book/core，bookId 仅前两类有值
     * 答对锁定的词不出现；词库抽完后按艾宾浩斯顺序重新放行
     * 错词最多分布在 2 个游戏中（gameId 用于判断当前游戏是否已含该词）
     */
    @GetMapping("/spell-game-words")
    public Result<List<Map<String, Object>>> spellGameWords(Integer count, String level, String gameId) {
        return Result.ok(wordBookService.randomForSpellGame(UserContext.requireUserId(),
                count == null ? 10 : count, level, gameId));
    }

    /**
     * 小游戏答题结果上报（跨游戏去重 + 错词游戏分布）：
     * body: { words: ["abandon", ...], result: "know" | "forget" | "vague", gameId: "game3" }
     * know → 锁定该词不再出现在任何小游戏；forget/vague → 解锁，记录出现在当前游戏（最多 2 个游戏）
     */
    @PostMapping("/game-result")
    public Result<Void> gameResult(@RequestBody Map<String, Object> body) {
        List<String> words = body == null ? List.of() : castWordList(body.get("words"));
        String result = body == null ? null : (String) body.get("result");
        String gameId = body == null ? null : (String) body.get("gameId");
        wordBookService.gameResult(UserContext.requireUserId(), words, result, gameId);
        return Result.ok();
    }

    @SuppressWarnings("unchecked")
    private static List<String> castWordList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
        }
        return List.of();
    }

    /**
     * 字母拼拼乐复习反馈（三态）：know/vague/forget，对应艾宾浩斯曲线
     */
    @PostMapping("/{id}/spell-review")
    public Result<Void> spellReview(@PathVariable Long id, @RequestBody Map<String, String> body) {
        wordBookService.spellReview(UserContext.requireUserId(), id, body == null ? null : body.get("result"));
        return Result.ok();
    }

    /** 检查单词是否已在生词本（阅读助手高亮） */
    @PostMapping("/check-exist")
    public Result<List<String>> checkExist(@RequestBody List<String> words) {
        return Result.ok(wordBookService.existWords(UserContext.requireUserId(), words));
    }

    /** 保存/覆盖单词AI助记内容，单词不在生词本则自动加入 */
    @PostMapping("/{word}/ai-note")
    public Result<WordBook> saveAiNote(@PathVariable String word, @RequestBody Map<String, String> body) {
        String aiNote = body.get("aiNote");
        return Result.ok(wordBookService.saveAiNote(UserContext.requireUserId(), word, aiNote));
    }

    /**
     * 标记单词为"已掌握"（新词学习点击"认识"调用）。
     * 写入 sys_user.mastered_words JSON（去重），不影响生词本，
     * 用于"累计掌握"统计——清空生词本不影响累计掌握数。
     * @return true 表示本次新加入，false 表示已存在
     */
    @PostMapping("/mark-mastered")
    public Result<Map<String, Object>> markMastered(@RequestBody Map<String, String> body) {
        String word = body.get("word");
        boolean added = userService.addMasteredWord(UserContext.requireUserId(), word);
        return Result.ok(Map.of("added", added));
    }
}
