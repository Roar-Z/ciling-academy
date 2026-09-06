package com.wordspirit.module.wordbook.service;

import com.wordspirit.module.dict.entity.DictWord;
import com.wordspirit.module.wordbook.dto.BatchAddReq;
import com.wordspirit.module.wordbook.dto.WordAddReq;
import com.wordspirit.module.wordbook.entity.WordBook;

import java.util.List;
import java.util.Map;

/**
 * 生词本服务
 */
public interface WordBookService {

    /** 分页查询生词本 */
    Object list(Long userId, String keyword, long page, long size);

    /** 加入生词本（已存在则幂等） */
    WordBook add(Long userId, WordAddReq req);

    /** 批量加入（阅读助手） */
    int batchAdd(Long userId, BatchAddReq req);

    /** 删除 */
    void remove(Long userId, Long id);

    /** 批量删除 */
    void batchRemove(Long userId, List<Long> ids);

    /** 标记复习完成（更新熟悉度 + 艾宾浩斯复习计划） */
    void review(Long userId, Long id, boolean familiar);

    /** 查询今日待复习单词（艾宾浩斯） */
    List<WordBook> dueReview(Long userId, int limit);

    /**
     * 新词学习取词（带优先级）：
     * 1) 生词本待复习词（艾宾浩斯到期，最优先巩固）
     * 2) 高频核心词（dict_word.is_core=1，符合 level）
     * 3) 其余普通词（符合 level）
     * 去重后凑满 count 个，返回 DictWord 列表
     */
    List<DictWord> randomStudy(Long userId, int count, String level);

    /** 随机取 N 个词用于小游戏（优先生词本，不足用词典兜底） */
    List<Map<String, Object>> randomForGame(Long userId, int count);

    /**
     * 小游戏取词（艾宾浩斯复习游戏）：
     * 1) 生词本到期复习词（nextReviewAt <= now，逾期越久越优先，同级按熟悉度升序）—— 最优先
     * 2) 生词本其他词（按 nextReviewAt 升序，越接近复习时间越优先）
     * 3) 词典高频核心词（is_core=1，按 level 过滤）
     * 返回中每条带 source: due / book / core，用于前端按类型调不同复习接口。
     * <p>
     * 答对锁定的词（见 {@link #gameResult}）不参与取词；未锁定的词全部抽完后，
     * 才按艾宾浩斯优先级放行最早答对的词重新出现。
     * <p>
     * 错词游戏分布限制：答错/半对的词最多出现在 2 个游戏中；
     * 已在当前游戏出现过的词不受此限制（可在本游戏反复出现），且优先拉取。
     *
     * @param gameId 当前游戏ID（可为空，为空时不做分布限制）
     */
    List<Map<String, Object>> randomForSpellGame(Long userId, int count, String level, String gameId);

    /**
     * 小游戏答题结果上报（跨游戏去重 + 错词游戏分布记录）：
     * know   → 加入"答对锁定"集合，该词之后不再出现在任何小游戏中（直到词库抽完才重新放行），并清除错词分布记录
     * forget / vague → 解除锁定，记录该词已出现在当前游戏（同词最多分布 2 个游戏）
     *
     * @param gameId 当前游戏ID（可为空）
     */
    void gameResult(Long userId, List<String> words, String result, String gameId);

    /**
     * 字母拼拼乐复习接口（三态），对应艾宾浩斯曲线：
     * - know：熟悉度 +20，下次复习按 EB_DAYS（1d/2d/4d/7d/15d…）
     * - vague：熟悉度 +5，下次复习 +30 min
     * - forget：熟悉度 -10，下次复习 +5 min
     */
    void spellReview(Long userId, Long id, String result);

    /** 检查一批单词中哪些已在生词本（阅读助手高亮用） */
    List<String> existWords(Long userId, List<String> words);

    /** 保存/覆盖单词AI助记内容，单词不在生词本则自动加入 */
    WordBook saveAiNote(Long userId, String word, String aiNote);
}
