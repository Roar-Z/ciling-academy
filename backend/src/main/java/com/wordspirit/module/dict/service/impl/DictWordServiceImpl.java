package com.wordspirit.module.dict.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wordspirit.common.PageResult;
import com.wordspirit.module.dict.entity.DictWord;
import com.wordspirit.module.dict.mapper.DictWordMapper;
import com.wordspirit.module.dict.service.DictWordService;
import com.wordspirit.module.learnround.entity.LearnRoundItem;
import com.wordspirit.module.learnround.mapper.LearnRoundItemMapper;
import com.wordspirit.module.user.entity.User;
import com.wordspirit.module.user.mapper.UserMapper;
import com.wordspirit.module.wordbook.entity.WordBook;
import com.wordspirit.module.wordbook.mapper.WordBookMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 词典服务实现：MySQL 词典 + Redis 热点缓存，完全不消耗 AI 额度
 */
@Service
@RequiredArgsConstructor
public class DictWordServiceImpl implements DictWordService {

    private static final String DICT_KEY = "dict:word:{}";
    private static final Duration DICT_TTL = Duration.ofDays(1);

    private final DictWordMapper dictWordMapper;
    private final WordBookMapper wordBookMapper;
    private final LearnRoundItemMapper learnRoundItemMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public DictWord lookup(String word) {
        if (StrUtil.isBlank(word)) {
            return null;
        }
        String trimmed = word.trim();

        // 1. 英文单词精确匹配（含 Redis 缓存）
        String key = cn.hutool.core.text.StrFormatter.format(DICT_KEY, trimmed.toLowerCase(Locale.ROOT));
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof DictWord dw) {
            return dw;
        }
        DictWord dictWord = dictWordMapper.selectOne(new LambdaQueryWrapper<DictWord>()
                .eq(DictWord::getWord, trimmed));
        if (dictWord != null) {
            redisTemplate.opsForValue().set(key, dictWord, DICT_TTL);
            return dictWord;
        }

        // 2. 中文释义模糊匹配（无缓存，返回最相关的一条）
        if (containsChinese(trimmed)) {
            List<DictWord> list = dictWordMapper.selectList(new LambdaQueryWrapper<DictWord>()
                    .like(DictWord::getMeaning, trimmed)
                    .orderByAsc(DictWord::getWord)
                    .last("LIMIT 1"));
            if (!list.isEmpty()) {
                return list.get(0);
            }
        }
        return null;
    }

    private boolean containsChinese(String s) {
        if (s == null) {
            return false;
        }
        for (char c : s.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<DictWord> lookupBatch(List<String> words) {
        List<DictWord> result = new ArrayList<>();
        for (String w : words) {
            DictWord dw = lookup(w);
            if (dw != null) {
                result.add(dw);
            }
        }
        return result;
    }

    @Override
    public Object search(String keyword, long page, long size) {
        LambdaQueryWrapper<DictWord> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(DictWord::getWord, keyword)
                    .or().like(DictWord::getMeaning, keyword));
        }
        // 排序：1) 纯字母关键词时以该词开头的优先 2) 高频核心词（is_core=1）优先 3) 单词升序。
        // keyword 已校验为纯字母，拼入 ORDER BY 无注入风险；MySQL 默认排序规则不区分大小写。
        if (StrUtil.isNotBlank(keyword) && keyword.matches("[A-Za-z]+")) {
            wrapper.last("ORDER BY CASE WHEN word LIKE '" + keyword + "%' THEN 0 ELSE 1 END, "
                    + "is_core DESC, word ASC");
        } else {
            wrapper.orderByDesc(DictWord::getIsCore).orderByAsc(DictWord::getWord);
        }
        Page<DictWord> result = dictWordMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result);
    }

    @Override
    public List<DictWord> randomFromDict(int count) {
        return randomFromDict(count, null);
    }

    @Override
    public List<DictWord> randomFromDict(int count, String level) {
        return randomFromDict(count, level, null);
    }

    @Override
    public List<DictWord> randomFromDict(int count, String level, Boolean coreOnly) {
        LambdaQueryWrapper<DictWord> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(level)) {
            String lv = level.trim().toLowerCase(Locale.ROOT);
            switch (lv) {
                case "cet4" -> wrapper.eq(DictWord::getDifficulty, 2);
                case "cet6" -> wrapper.eq(DictWord::getDifficulty, 4);
                case "mixed" -> wrapper.in(DictWord::getDifficulty, 2, 4);
                // "all" 或未知值：不加过滤
                default -> { /* no-op */ }
            }
        }
        if (coreOnly != null && coreOnly) {
            wrapper.eq(DictWord::getIsCore, 1);
        }
        List<DictWord> list = dictWordMapper.selectList(wrapper);
        if (list.isEmpty()) {
            return list;
        }
        if (list.size() <= count) {
            return list;
        }
        java.util.Collections.shuffle(list);
        return new ArrayList<>(list.subList(0, count));
    }

    @Override
    public List<DictWord> randomUnlearned(int count, String level, Long userId) {
        LambdaQueryWrapper<DictWord> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(level)) {
            String lv = level.trim().toLowerCase(Locale.ROOT);
            switch (lv) {
                case "cet4" -> wrapper.eq(DictWord::getDifficulty, 2);
                case "cet6" -> wrapper.eq(DictWord::getDifficulty, 4);
                case "mixed" -> wrapper.in(DictWord::getDifficulty, 2, 4);
                default -> { /* no-op */ }
            }
        }
        // 排除用户已学过的词：
        // 1) 生词本里的词（不记得/模糊历史加入）
        // 2) 历史学习轮次出现过的词（无论认识/模糊/不记得）
        // 3) sys_user.mastered_words 中的词（新词学习"认识"过的词，长期持久化，不受轮次清理影响）
        Set<String> exclude = new HashSet<>();
        if (userId != null) {
            List<Object> learned = wordBookMapper.selectObjs(new LambdaQueryWrapper<WordBook>()
                    .select(WordBook::getWord)
                    .eq(WordBook::getUserId, userId));
            if (learned != null) {
                for (Object o : learned) if (o != null) exclude.add(o.toString());
            }
            List<Object> roundWords = learnRoundItemMapper.selectObjs(new LambdaQueryWrapper<LearnRoundItem>()
                    .select(LearnRoundItem::getWord)
                    .eq(LearnRoundItem::getUserId, userId));
            if (roundWords != null) {
                for (Object o : roundWords) if (o != null) exclude.add(o.toString());
            }
            // 排除"已掌握"集合（新词学习点认识过的词），
            // 防止 trimOldRounds 清掉旧 round_item 后这些词被重新分配
            User u = userMapper.selectById(userId);
            if (u != null && StrUtil.isNotBlank(u.getMasteredWords())) {
                try {
                    List<String> mw = cn.hutool.json.JSONUtil.parseArray(u.getMasteredWords()).toList(String.class);
                    if (mw != null) exclude.addAll(mw);
                } catch (Exception ignored) {}
            }
            org.slf4j.LoggerFactory.getLogger(getClass()).info(
                    "[randomUnlearned] userId={} level={} count={} wordBookLearned={} roundLearned={} excludeSize={} sql={}",
                    userId, level, count,
                    learned == null ? 0 : learned.size(),
                    roundWords == null ? 0 : roundWords.size(),
                    exclude.size(),
                    wrapper.getCustomSqlSegment());
            if (!exclude.isEmpty()) {
                wrapper.notIn(DictWord::getWord, exclude);
            }
        }
        // 不使用 MySQL 的 ORDER BY RAND()（性能差且 NOT IN 大列表时不稳定），
        // 改为：一次性把候选词取回，Java 端 shuffle 再截取。
        List<DictWord> all = dictWordMapper.selectList(wrapper);
        // 二次防御：万一 NOT IN 被 SQL 优化器改写丢失条件，
        // Java 端再用 exclude 过滤一遍，保证返回值绝不包含已学词
        if (!exclude.isEmpty()) {
            all.removeIf(dw -> exclude.contains(dw.getWord()));
        }
        if (all.isEmpty()) {
            return all;
        }
        java.util.Collections.shuffle(all);
        org.slf4j.LoggerFactory.getLogger(getClass()).info(
                "[randomUnlearned] after-filter={}",
                all.stream().limit(count).map(c -> c.getWord()).collect(java.util.stream.Collectors.toList()));
        if (all.size() <= count) {
            return all;
        }
        return new ArrayList<>(all.subList(0, count));
    }

    private static final String STATS_KEY = "platform:stats:v3";
    private static final Duration STATS_TTL = Duration.ofMinutes(10);

    @Override
    public Map<String, Long> platformStats() {
        // Redis 缓存 10 分钟，避免首页每次访问都打 COUNT 聚合
        Object cached = redisTemplate.opsForValue().get(STATS_KEY);
        if (cached instanceof Map<?, ?> m && m.get("wordCount") != null) {
            Map<String, Long> r = new HashMap<>();
            r.put("wordCount", ((Number) m.get("wordCount")).longValue());
            return r;
        }
        long wordCount = dictWordMapper.selectCount(null);
        Map<String, Long> stats = new HashMap<>();
        stats.put("wordCount", wordCount);
        redisTemplate.opsForValue().set(STATS_KEY, stats, STATS_TTL);
        return stats;
    }
}

