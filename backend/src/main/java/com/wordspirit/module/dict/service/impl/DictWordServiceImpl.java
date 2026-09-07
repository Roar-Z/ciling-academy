package com.wordspirit.module.dict.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wordspirit.common.PageResult;
import com.wordspirit.module.dict.entity.DictWord;
import com.wordspirit.module.dict.mapper.DictWordMapper;
import com.wordspirit.module.dict.service.DictWordService;
import com.wordspirit.module.learnround.entity.UserPoolWord;
import com.wordspirit.module.learnround.mapper.UserPoolWordMapper;
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
    private final UserPoolWordMapper userPoolWordMapper;
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
                // levels 为多标签 csv（如 zhongkao,gaokao,cet4），按 FIND_IN_SET 命中完整词库池
                case "cet4" -> wrapper.apply("FIND_IN_SET('cet4', levels) > 0");
                case "cet6" -> wrapper.apply("FIND_IN_SET('cet6', levels) > 0");
                case "mixed" -> wrapper.and(w -> w.apply("FIND_IN_SET('cet4', levels) > 0")
                        .or().apply("FIND_IN_SET('cet6', levels) > 0"));
                case "gaokao" -> wrapper.apply("FIND_IN_SET('gaokao', levels) > 0");
                case "zhongkao" -> wrapper.apply("FIND_IN_SET('zhongkao', levels) > 0");
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
        String lv = StrUtil.isBlank(level) ? "all" : level.trim().toLowerCase(Locale.ROOT);
        switch (lv) {
            // levels 为多标签 csv（如 zhongkao,gaokao,cet4），按 FIND_IN_SET 命中完整词库池
            case "cet4" -> wrapper.apply("FIND_IN_SET('cet4', levels) > 0");
            case "cet6" -> wrapper.apply("FIND_IN_SET('cet6', levels) > 0");
            case "mixed" -> wrapper.and(w -> w.apply("FIND_IN_SET('cet4', levels) > 0")
                    .or().apply("FIND_IN_SET('cet6', levels) > 0"));
            case "gaokao" -> wrapper.apply("FIND_IN_SET('gaokao', levels) > 0");
            case "zhongkao" -> wrapper.apply("FIND_IN_SET('zhongkao', levels) > 0");
            default -> { /* no-op */ }
        }
        // 词库完全独立（词书模式）：只排除"本词库学过"的词（user_pool_word 按 level 记录）。
        // 生词本/历史轮次/已掌握不再参与跨词库排除——换词库后学过的词会照常出现，各词库进度独立。
        // level='all' 为存量迁移数据（历史学习记录），对所有词库生效；选择"全部(all)"时排除任何词库学过的词。
        Set<String> exclude = new HashSet<>();
        if (userId != null) {
            List<UserPoolWord> poolRows = userPoolWordMapper.selectList(new LambdaQueryWrapper<UserPoolWord>()
                    .select(UserPoolWord::getWord, UserPoolWord::getLevel)
                    .eq(UserPoolWord::getUserId, userId));
            for (UserPoolWord p : poolRows) {
                String pLevel = p.getLevel() == null ? "all" : p.getLevel();
                if ("all".equals(lv) || "all".equals(pLevel) || pLevel.equals(lv)) {
                    exclude.add(p.getWord().toLowerCase(Locale.ROOT));
                }
            }
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
        // 加深印象：本词库「认识满 60 天且从未重现过」的词混入批次，再出现一次（一次性）
        if (userId != null && !"all".equals(lv)) {
            List<DictWord> boost = pickBoostWords(userId, lv, count);
            if (!boost.isEmpty()) {
                int remain = Math.max(0, count - boost.size());
                List<DictWord> result = new ArrayList<>(boost);
                result.addAll(all.subList(0, Math.min(remain, all.size())));
                // 重现词与新词随机穿插，不集中在开头
                java.util.Collections.shuffle(result);
                return result;
            }
        }
        if (all.size() <= count) {
            return all;
        }
        return new ArrayList<>(all.subList(0, count));
    }

    /** 加深印象触发天数：认识满 60 天未重现 → 再出现一次 */
    private static final int BOOST_AFTER_DAYS = 60;
    /** 每批最多混入的重现词数 */
    private static final int BOOST_MAX_PER_BATCH = 5;

    /**
     * 取「本词库已认识、满 {@link #BOOST_AFTER_DAYS} 天、且从未重现过」的词，
     * 最多 {@link #BOOST_MAX_PER_BATCH} 个；取到即标记 boosted_at，保证只重现一次。
     */
    private List<DictWord> pickBoostWords(Long userId, String level, int count) {
        List<UserPoolWord> rows = userPoolWordMapper.selectList(new LambdaQueryWrapper<UserPoolWord>()
                .select(UserPoolWord::getWord)
                .eq(UserPoolWord::getUserId, userId)
                .eq(UserPoolWord::getLevel, level)
                .eq(UserPoolWord::getMastered, 1)
                .isNull(UserPoolWord::getBoostedAt)
                .le(UserPoolWord::getCreatedAt, java.time.LocalDateTime.now().minusDays(BOOST_AFTER_DAYS)));
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        java.util.Collections.shuffle(rows);
        int cap = Math.min(BOOST_MAX_PER_BATCH, Math.max(1, count / 4));
        List<String> words = new ArrayList<>();
        for (UserPoolWord p : rows) {
            if (words.size() >= cap) {
                break;
            }
            words.add(p.getWord());
        }
        if (words.isEmpty()) {
            return new ArrayList<>();
        }
        List<DictWord> dict = dictWordMapper.selectList(new LambdaQueryWrapper<DictWord>()
                .in(DictWord::getWord, words));
        if (dict == null || dict.isEmpty()) {
            return new ArrayList<>();
        }
        // 标记实际重现的词（词典已清理的词不标，下次再试）
        Set<String> served = dict.stream().map(DictWord::getWord).collect(Collectors.toSet());
        UserPoolWord mark = new UserPoolWord();
        mark.setBoostedAt(java.time.LocalDateTime.now());
        userPoolWordMapper.update(mark, new LambdaQueryWrapper<UserPoolWord>()
                .eq(UserPoolWord::getUserId, userId)
                .eq(UserPoolWord::getLevel, level)
                .in(UserPoolWord::getWord, served)
                .isNull(UserPoolWord::getBoostedAt));
        for (DictWord dw : dict) {
            dw.setBoosted(true);
        }
        return dict;
    }

    // v4：序列化配置调整后旧缓存格式不兼容，升版本号让旧键自然过期
    private static final String STATS_KEY = "platform:stats:v4";
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

