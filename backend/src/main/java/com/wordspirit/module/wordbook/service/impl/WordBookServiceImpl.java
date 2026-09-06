package com.wordspirit.module.wordbook.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wordspirit.common.BusinessException;
import com.wordspirit.common.PageResult;
import com.wordspirit.common.ResultCode;
import com.wordspirit.module.dict.entity.DictWord;
import com.wordspirit.module.dict.service.DictWordService;
import com.wordspirit.module.wordbook.dto.BatchAddReq;
import com.wordspirit.module.wordbook.dto.WordAddReq;
import com.wordspirit.module.user.entity.User;
import com.wordspirit.module.user.mapper.UserMapper;
import com.wordspirit.module.wordbook.entity.WordBook;
import com.wordspirit.module.wordbook.mapper.WordBookMapper;
import com.wordspirit.module.wordbook.service.WordBookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 生词本服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WordBookServiceImpl implements WordBookService {

    /** 艾宾浩斯复习间隔（天），按复习次数递增 */
    private static final int[] EB_DAYS = {1, 2, 4, 7, 15, 30, 60};

    /** 字母拼拼乐三态：know 拼对；vague 半对；forget 完全错（依据艾宾浩斯调整下次复习） */
    private static final String SPELL_RESULT_KNOW = "know";
    private static final String SPELL_RESULT_VAGUE = "vague";
    private static final String SPELL_RESULT_FORGET = "forget";

    /**
     * 入册时的初始熟悉度（按来源）。
     * 不记得=10（很弱）；模糊=40（半熟）；其他=0
     */
    private static int initialFamiliarityFor(String source) {
        if (source == null) return 0;
        return switch (source) {
            case "new_forget" -> 10;
            case "new_vague" -> 40;
            default -> 0;
        };
    }

    /**
     * 入册时的下次复习时间点（艾宾浩斯早期精确到分钟）。
     * - new_forget（不记得）：5 分钟后（艾宾浩斯短期记忆临界点）
     * - new_vague（模糊）：30 分钟后（艾宾浩斯短期记忆巩固）
     * - 其他来源：24 小时后（首次复习）
     */
    private static LocalDateTime initialNextReviewAt(String source) {
        LocalDateTime now = LocalDateTime.now();
        if ("new_forget".equals(source)) return now.plus(5, ChronoUnit.MINUTES);
        if ("new_vague".equals(source)) return now.plus(30, ChronoUnit.MINUTES);
        return now.plus(1, ChronoUnit.DAYS);
    }

    private final WordBookMapper wordBookMapper;
    private final UserMapper userMapper;
    private final DictWordService dictWordService;
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @Override
    public Object list(Long userId, String keyword, long page, long size) {
        LambdaQueryWrapper<WordBook> wrapper = new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId);
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(WordBook::getWord, keyword);
        }
        wrapper.orderByDesc(WordBook::getCreatedAt);
        Page<WordBook> result = wordBookMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result);
    }

    @Override
    public WordBook add(Long userId, WordAddReq req) {
        String word = req.getWord().trim().toLowerCase(Locale.ROOT);
        WordBook exist = wordBookMapper.selectOne(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .eq(WordBook::getWord, word));
        if (exist != null) {
            return exist; // 幂等
        }
        // 释义缺失时从词典自动补全
        String meaning = req.getMeaning();
        String phonetic = req.getPhonetic();
        if (StrUtil.isBlank(meaning)) {
            DictWord dw = dictWordService.lookup(word);
            if (dw != null) {
                meaning = dw.getPos() + " " + dw.getMeaning();
                if (StrUtil.isBlank(phonetic)) {
                    phonetic = dw.getPhonetic();
                }
            } else {
                meaning = StrUtil.isBlank(meaning) ? "暂无释义" : meaning;
            }
        }
        WordBook book = new WordBook();
        book.setUserId(userId);
        book.setWord(word);
        book.setPhonetic(StrUtil.nullToEmpty(phonetic));
        book.setMeaning(StrUtil.nullToEmpty(meaning));
        book.setWordUsage(StrUtil.nullToEmpty(req.getUsage()));
        String source = StrUtil.isNotBlank(req.getSource()) ? req.getSource() : "manual";
        book.setSource(source);
        // 初始熟悉度：
        // - 新词学习"认识"传 80（一次即视为掌握，会进入累计掌握统计）；
        // - 新词学习"不记得/模糊"按 source 自动给 10 / 40；
        // - 其他来源默认 0（待艾宾浩斯复习累计）
        int initFam = req.getInitialFamiliarity() != null
                ? req.getInitialFamiliarity()
                : initialFamiliarityFor(source);
        book.setFamiliarity(initFam);
        book.setReviewCount(0);
        // 艾宾浩斯首次复习时间：不记得=5分钟后，模糊=30分钟后，其他=1天后
        book.setNextReviewAt(initialNextReviewAt(source));
        wordBookMapper.insert(book);
        return book;
    }

    @Override
    public int batchAdd(Long userId, BatchAddReq req) {
        int added = 0;
        for (WordAddReq w : req.getWords()) {
            if (StrUtil.isBlank(w.getWord())) {
                continue;
            }
            add(userId, w);
            added++;
        }
        return added;
    }

    @Override
    public void remove(Long userId, Long id) {
        WordBook book = wordBookMapper.selectById(id);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "单词不存在");
        }
        wordBookMapper.deleteById(id);
    }

    @Override
    public void batchRemove(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            remove(userId, id);
        }
    }

    @Override
    public void review(Long userId, Long id, boolean familiar) {
        WordBook book = wordBookMapper.selectById(id);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "单词不存在");
        }
        int count = (book.getReviewCount() == null ? 0 : book.getReviewCount()) + 1;
        int oldFamiliarity = book.getFamiliarity() == null ? 0 : book.getFamiliarity();
        int familiarity = familiar
                ? Math.min(100, oldFamiliarity + 20)
                : Math.max(0, oldFamiliarity - 10);
        // 按艾宾浩斯间隔安排下次复习
        int days = EB_DAYS[Math.min(count, EB_DAYS.length) - 1];
        WordBook update = new WordBook();
        update.setId(id);
        update.setReviewCount(count);
        update.setFamiliarity(familiarity);
        update.setLastReviewAt(LocalDateTime.now());
        update.setNextReviewAt(LocalDateTime.now().plusDays(days));
        // 首次达到 80 熟悉度：打 mastered_at 标记 + 累计掌握 +1（每词一生只计一次）
        // 该值持久化到 sys_user.total_mastered，清空生词本不影响
        if (familiarity >= 80 && book.getMasteredAt() == null) {
            update.setMasteredAt(LocalDateTime.now());
            User u = userMapper.selectById(userId);
            int cur = (u == null || u.getTotalMastered() == null) ? 0 : u.getTotalMastered();
            User patch = new User();
            patch.setId(userId);
            patch.setTotalMastered(cur + 1);
            userMapper.updateById(patch);
        }
        wordBookMapper.updateById(update);
    }

    @Override
    public List<WordBook> dueReview(Long userId, int limit) {
        LocalDateTime now = LocalDateTime.now();
        return wordBookMapper.selectList(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .and(w -> w.isNull(WordBook::getNextReviewAt)
                        .or().le(WordBook::getNextReviewAt, now))
                // 到期词按时间优先级取一批，再随机顺序返回 ——"再来一轮"换序不换词，
                // 但避免连续两轮完全相同顺序让用户感到没变化。
                .last("ORDER BY next_review_at IS NULL DESC, next_review_at ASC, RAND() LIMIT " + limit));
    }

    @Override
    public List<DictWord> randomStudy(Long userId, int count, String level) {
        List<DictWord> result = new ArrayList<>();
        Set<String> used = new HashSet<>();
        // 1) 优先：生词本待复习词（艾宾浩斯到期）
        if (userId != null) {
            List<WordBook> due = dueReview(userId, count);
            for (WordBook b : due) {
                if (result.size() >= count) {
                    break;
                }
                DictWord dw = new DictWord();
                dw.setWord(b.getWord());
                dw.setPhonetic(StrUtil.nullToEmpty(b.getPhonetic()));
                dw.setMeaning(StrUtil.nullToEmpty(b.getMeaning()));
                dw.setExample("");
                dw.setExampleCn("");
                dw.setLevel(level);
                result.add(dw);
                used.add(b.getWord().toLowerCase(Locale.ROOT));
            }
        }
        // 2) 高频核心词（is_core=1，符合 level）
        int remain = count - result.size();
        if (remain > 0) {
            for (DictWord dw : dictWordService.randomFromDict(remain * 3 + remain, level, Boolean.TRUE)) {
                if (result.size() >= count) {
                    break;
                }
                String w = dw.getWord().toLowerCase(Locale.ROOT);
                if (used.contains(w)) {
                    continue;
                }
                result.add(dw);
                used.add(w);
            }
        }
        // 3) 其余普通词（符合 level）
        remain = count - result.size();
        if (remain > 0) {
            for (DictWord dw : dictWordService.randomFromDict(remain * 3 + remain, level, Boolean.FALSE)) {
                if (result.size() >= count) {
                    break;
                }
                String w = dw.getWord().toLowerCase(Locale.ROOT);
                if (used.contains(w)) {
                    continue;
                }
                result.add(dw);
                used.add(w);
            }
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> randomForGame(Long userId, int count) {
        List<WordBook> books = wordBookMapper.selectList(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .orderByDesc(WordBook::getCreatedAt));
        java.util.Collections.shuffle(books);
        List<Map<String, Object>> result = new ArrayList<>();
        int need = count;
        // 先取生词本单词
        for (WordBook b : books) {
            if (need <= 0) {
                break;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("word", b.getWord());
            item.put("phonetic", b.getPhonetic());
            item.put("meaning", b.getMeaning());
            result.add(item);
            need--;
        }
        // 不足则用词典兜底
        if (need > 0) {
            for (DictWord dw : dictWordService.randomFromDict(Math.max(need * 3, need))) {
                if (need <= 0) {
                    break;
                }
                boolean dup = result.stream().anyMatch(r -> r.get("word").equals(dw.getWord()));
                if (dup) {
                    continue;
                }
                Map<String, Object> item = new HashMap<>();
                item.put("word", dw.getWord());
                item.put("phonetic", dw.getPhonetic());
                item.put("meaning", (dw.getPos() + " " + dw.getMeaning()).trim());
                item.put("pos", dw.getPos());
                item.put("example", dw.getExample());
                item.put("exampleCn", dw.getExampleCn());
                result.add(item);
                need--;
            }
        }
        // 用词典补充词性/例句字段（生词本词条可能缺失）
        enrichFromDict(result);
        return result;
    }

    @Override
    public List<Map<String, Object>> randomForSpellGame(Long userId, int count, String level, String gameId) {
        String lockKey = gameMasteredKey(userId);
        Set<String> locked = loadGameMastered(lockKey);
        Map<String, Set<String>> wrongGames = loadWrongGames(userId);

        // 错词分布限制：已答错且已分布在 ≥2 个游戏、且不含当前游戏的词，对本游戏不可见。
        // 当前游戏自己的错词不在排除集合内，且因答错后 nextReviewAt 被排到最近（+5min/+30min），
        // 会在艾宾浩斯排序下最先被拉取，天然实现"按当前游戏优先拉取错词"。
        Set<String> exclude = new HashSet<>(locked);
        if (StrUtil.isNotBlank(gameId)) {
            for (Map.Entry<String, Set<String>> e : wrongGames.entrySet()) {
                if (!e.getValue().contains(gameId) && e.getValue().size() >= 2) {
                    exclude.add(e.getKey());
                }
            }
        }

        // 常规取词：跳过"已在游戏中答对"的词与"已分布在 2 个其他游戏"的错词
        List<Map<String, Object>> result = new ArrayList<>(pickSpellWords(userId, count, level, exclude));

        // 未答对的词已全部抽完 → 放宽锁定：按艾宾浩斯优先级放行最先到期的词（即最早答对的词），
        // 并把重新出现的词移出锁定集合，让它们回到流通池
        if (result.size() < count && !locked.isEmpty()) {
            Set<String> have = usedWords(result);
            Set<String> back = new HashSet<>();
            for (Map<String, Object> item : pickSpellWords(userId, count - result.size(), level, Set.of())) {
                if (result.size() >= count) break;
                String w = (String) item.get("word");
                if (w == null || have.contains(w)) continue;
                result.add(item);
                have.add(w);
                if (locked.contains(w.toLowerCase(Locale.ROOT))) back.add(w);
            }
            removeGameMastered(lockKey, back);
        }

        // 用词典补齐词性/例句字段（生词本词条可能缺失）
        enrichFromDict(result);
        return result;
    }

    /**
     * 按艾宾浩斯优先级取词，exclude 中的单词跳过：
     * 1) 生词本到期词（逾期越久越优先，同级熟悉度越低越优先）
     * 2) 生词本未来词（越接近计划复习点越优先）
     * 3) 词典高频核心词兜底
     * 注：到期词 + 未来词已覆盖生词本全部词条（nextReviewAt 为空视为到期）。
     */
    private List<Map<String, Object>> pickSpellWords(Long userId, int count, String level, Set<String> exclude) {
        List<Map<String, Object>> result = new ArrayList<>();
        int need = count;

        // 1) 生词本到期复习词（nextReviewAt <= now）—— 最高优先级
        //    艾宾浩斯排序：逾期越久越优先（nextReviewAt ASC），同级按熟悉度升序（越生疏越先复习）
        List<WordBook> due = wordBookMapper.selectList(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .and(w -> w.isNull(WordBook::getNextReviewAt)
                        .or().le(WordBook::getNextReviewAt, LocalDateTime.now()))
                .orderByAsc(WordBook::getNextReviewAt)
                .orderByAsc(WordBook::getFamiliarity)
                .last("LIMIT " + Math.max(count * 3, count)));
        for (WordBook b : due) {
            if (need <= 0) break;
            if (excluded(exclude, b.getWord())) continue;
            result.add(toSpellItem(b, "due"));
            need--;
        }

        // 2) 生词本其他词（nextReviewAt 在未来）—— 第二优先级，越接近复习时间越优先
        if (need > 0) {
            List<WordBook> future = wordBookMapper.selectList(new LambdaQueryWrapper<WordBook>()
                    .eq(WordBook::getUserId, userId)
                    .gt(WordBook::getNextReviewAt, LocalDateTime.now())
                    .orderByAsc(WordBook::getNextReviewAt)
                    .last("LIMIT " + Math.max(count * 3, count)));
            for (WordBook b : future) {
                if (need <= 0) break;
                if (excluded(exclude, b.getWord())) continue;
                result.add(toSpellItem(b, "book"));
                need--;
            }
        }

        // 3) 词典高频核心词（is_core=1，按 level 过滤）—— 兜底
        if (need > 0) {
            Set<String> taken = usedWords(result);
            for (DictWord dw : dictWordService.randomFromDict(Math.max(need * 3, need), level, true)) {
                if (need <= 0) break;
                String w = dw.getWord();
                if (w == null || taken.contains(w) || excluded(exclude, w)) continue;
                result.add(toCoreItem(dw));
                taken.add(w);
                need--;
            }
        }
        return result;
    }

    /** 游戏答对锁定集合（Redis ZSet：word -> 答对时间戳）：game:mastered:{userId} */
    private static final String GAME_MASTERED_PREFIX = "game:mastered:";
    /** 锁定集合保留 180 天，每次答对续期；词库抽完时由取词逻辑主动放行 */
    private static final Duration GAME_MASTERED_TTL = Duration.ofDays(180);

    private static String gameMasteredKey(Long userId) {
        return GAME_MASTERED_PREFIX + (userId == null ? "0" : userId);
    }

    private Set<String> loadGameMastered(String key) {
        try {
            Set<Object> raw = redisTemplate.opsForZSet().range(key, 0, -1);
            Set<String> set = new HashSet<>();
            if (raw != null) {
                for (Object o : raw) {
                    if (o != null) set.add(String.valueOf(o).toLowerCase(Locale.ROOT));
                }
            }
            return set;
        } catch (Exception e) {
            log.warn("game mastered read failed key={}: {}", key, e.getMessage());
            return Set.of();
        }
    }

    private void removeGameMastered(String key, java.util.Collection<String> words) {
        if (words == null || words.isEmpty()) return;
        try {
            for (String w : words) {
                if (StrUtil.isNotBlank(w)) {
                    redisTemplate.opsForZSet().remove(key, w.trim().toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception e) {
            log.warn("game mastered remove failed key={}: {}", key, e.getMessage());
        }
    }

    @Override
    public void gameResult(Long userId, List<String> words, String result, String gameId) {
        if (userId == null || words == null || words.isEmpty() || StrUtil.isBlank(result)) {
            return;
        }
        String key = gameMasteredKey(userId);
        try {
            if (SPELL_RESULT_KNOW.equals(result)) {
                // 答对：加入锁定集合，之后不再出现在任何小游戏的取词结果里；
                // 同时清除错词游戏分布记录（该词已掌握，重新进入流通时不受旧分布约束）
                double now = System.currentTimeMillis();
                for (String w : words) {
                    if (StrUtil.isNotBlank(w)) {
                        redisTemplate.opsForZSet().add(key, w.trim().toLowerCase(Locale.ROOT), now);
                        redisTemplate.opsForHash().delete(wrongGamesKey(userId), w.trim().toLowerCase(Locale.ROOT));
                    }
                }
                redisTemplate.expire(key, GAME_MASTERED_TTL);
            } else {
                // 答错 / 半对：解除锁定，并记录该词已出现在当前游戏（分布上限 2 个游戏）
                removeGameMastered(key, words);
                if (StrUtil.isNotBlank(gameId)) {
                    recordWrongGame(userId, words, gameId);
                }
            }
        } catch (Exception e) {
            log.warn("game mastered write failed key={}: {}", key, e.getMessage());
        }
    }

    /** 错词游戏分布 Hash（field=word，value=逗号分隔的 gameId 列表）：game:wronggames:{userId} */
    private static final String GAME_WRONG_PREFIX = "game:wronggames:";
    /** 与答对锁定集合一致的保留期 */
    private static final Duration GAME_WRONG_TTL = Duration.ofDays(180);

    private static String wrongGamesKey(Long userId) {
        return GAME_WRONG_PREFIX + (userId == null ? "0" : userId);
    }

    /** 读取错词游戏分布：word(小写) -> gameId 集合 */
    private Map<String, Set<String>> loadWrongGames(Long userId) {
        Map<String, Set<String>> map = new HashMap<>();
        try {
            Map<Object, Object> raw = redisTemplate.opsForHash().entries(wrongGamesKey(userId));
            if (raw != null) {
                for (Map.Entry<Object, Object> e : raw.entrySet()) {
                    if (e.getKey() == null || e.getValue() == null) continue;
                    String w = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
                    Set<String> games = new HashSet<>();
                    for (String g : String.valueOf(e.getValue()).split(",")) {
                        if (StrUtil.isNotBlank(g)) games.add(g.trim());
                    }
                    map.put(w, games);
                }
            }
        } catch (Exception e) {
            log.warn("game wronggames read failed userId={}: {}", userId, e.getMessage());
        }
        return map;
    }

    /** 记录错词出现在当前游戏；同一词分布达 2 个游戏后不再扩张 */
    private void recordWrongGame(Long userId, List<String> words, String gameId) {
        String key = wrongGamesKey(userId);
        for (String w : words) {
            if (StrUtil.isBlank(w)) continue;
            String word = w.trim().toLowerCase(Locale.ROOT);
            Object cur = redisTemplate.opsForHash().get(key, word);
            Set<String> games = new HashSet<>();
            if (cur != null) {
                for (String g : String.valueOf(cur).split(",")) {
                    if (StrUtil.isNotBlank(g)) games.add(g.trim());
                }
            }
            if (games.contains(gameId) || games.size() >= 2) {
                continue; // 本游戏已记录 / 已达 2 游戏上限，不再扩张
            }
            games.add(gameId);
            redisTemplate.opsForHash().put(key, word, String.join(",", games));
        }
        redisTemplate.expire(key, GAME_WRONG_TTL);
    }

    private static boolean excluded(Set<String> exclude, String word) {
        return !exclude.isEmpty() && word != null && exclude.contains(word.toLowerCase(Locale.ROOT));
    }

    private static Set<String> usedWords(List<Map<String, Object>> picked) {
        return picked.stream().map(r -> (String) r.get("word"))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static Map<String, Object> toCoreItem(DictWord dw) {
        Map<String, Object> item = new HashMap<>();
        item.put("word", dw.getWord());
        item.put("phonetic", dw.getPhonetic());
        item.put("meaning", (dw.getPos() == null ? "" : dw.getPos() + ". ") + dw.getMeaning());
        item.put("pos", dw.getPos());
        item.put("example", dw.getExample());
        item.put("exampleCn", dw.getExampleCn());
        item.put("bookId", null);   // 核心词不在生词本，bookId=null
        item.put("source", "core");
        return item;
    }

    private Map<String, Object> toSpellItem(WordBook b, String source) {
        Map<String, Object> item = new HashMap<>();
        item.put("word", b.getWord());
        item.put("phonetic", b.getPhonetic());
        item.put("meaning", b.getMeaning());
        item.put("bookId", b.getId());
        item.put("source", source);
        return item;
    }

    @Override
    @Transactional
    public void spellReview(Long userId, Long id, String result) {
        WordBook book = wordBookMapper.selectById(id);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "单词不存在");
        }
        if (result == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "result 不能为空");
        }
        int count = (book.getReviewCount() == null ? 0 : book.getReviewCount()) + 1;
        int oldFamiliarity = book.getFamiliarity() == null ? 0 : book.getFamiliarity();
        int familiarity;
        LocalDateTime nextReviewAt;
        switch (result) {
            case SPELL_RESULT_KNOW -> {
                // 拼对：熟悉度 +20，下次按 EB_DAYS 排（艾宾浩斯）
                familiarity = Math.min(100, oldFamiliarity + 20);
                int days = EB_DAYS[Math.min(count, EB_DAYS.length) - 1];
                nextReviewAt = LocalDateTime.now().plusDays(days);
            }
            case SPELL_RESULT_VAGUE -> {
                // 半对：熟悉度 +5，下次 +30 min（艾宾浩斯短期巩固）
                familiarity = Math.min(100, oldFamiliarity + 5);
                nextReviewAt = LocalDateTime.now().plus(30, ChronoUnit.MINUTES);
            }
            case SPELL_RESULT_FORGET -> {
                // 拼错：熟悉度 -10，下次 +5 min（艾宾浩斯即时复现）
                familiarity = Math.max(0, oldFamiliarity - 10);
                nextReviewAt = LocalDateTime.now().plus(5, ChronoUnit.MINUTES);
            }
            default -> throw new BusinessException(ResultCode.BAD_REQUEST,
                    "result 必须是 know/vague/forget");
        }
        WordBook update = new WordBook();
        update.setId(id);
        update.setReviewCount(count);
        update.setFamiliarity(familiarity);
        update.setLastReviewAt(LocalDateTime.now());
        update.setNextReviewAt(nextReviewAt);
        // 首次达到 80 熟悉度：打标记 + 累计掌握 +1（每词一生只计一次）
        if (familiarity >= 80 && book.getMasteredAt() == null) {
            update.setMasteredAt(LocalDateTime.now());
            User u = userMapper.selectById(userId);
            int cur = (u == null || u.getTotalMastered() == null) ? 0 : u.getTotalMastered();
            User patch = new User();
            patch.setId(userId);
            patch.setTotalMastered(cur + 1);
            userMapper.updateById(patch);
        }
        wordBookMapper.updateById(update);
    }
    private void enrichFromDict(List<Map<String, Object>> items) {
        if (items.isEmpty()) {
            return;
        }
        List<String> words = items.stream().map(i -> (String) i.get("word")).collect(Collectors.toList());
        Map<String, DictWord> dictMap = dictWordService.lookupBatch(words).stream()
                .collect(Collectors.toMap(DictWord::getWord, d -> d, (a, b) -> a));
        for (Map<String, Object> item : items) {
            DictWord dw = dictMap.get(item.get("word"));
            if (dw == null) {
                continue;
            }
            item.putIfAbsent("pos", dw.getPos());
            item.putIfAbsent("example", dw.getExample());
            item.putIfAbsent("exampleCn", dw.getExampleCn());
            if (StrUtil.isBlank((String) item.get("phonetic"))) {
                item.put("phonetic", dw.getPhonetic());
            }
        }
    }

    @Override
    public List<String> existWords(Long userId, List<String> words) {
        if (words == null || words.isEmpty()) {
            return List.of();
        }
        List<String> lower = words.stream()
                .map(w -> w.trim().toLowerCase(Locale.ROOT))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
        if (lower.isEmpty()) {
            return List.of();
        }
        return wordBookMapper.selectList(new LambdaQueryWrapper<WordBook>()
                        .eq(WordBook::getUserId, userId)
                        .in(WordBook::getWord, lower))
                .stream().map(WordBook::getWord).collect(Collectors.toList());
    }

    @Override
    public WordBook saveAiNote(Long userId, String word, String aiNote) {
        String w = word.trim().toLowerCase(Locale.ROOT);
        WordBook exist = wordBookMapper.selectOne(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .eq(WordBook::getWord, w));
        if (exist == null) {
            WordAddReq req = new WordAddReq();
            req.setWord(w);
            req.setSource("ai_note");
            exist = add(userId, req);
        }
        WordBook update = new WordBook();
        update.setId(exist.getId());
        update.setAiNote(aiNote);
        wordBookMapper.updateById(update);
        return wordBookMapper.selectById(exist.getId());
    }
}
