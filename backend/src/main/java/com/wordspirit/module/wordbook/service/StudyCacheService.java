package com.wordspirit.module.wordbook.service;

import cn.hutool.core.util.StrUtil;
import com.wordspirit.module.dict.entity.DictWord;
import com.wordspirit.module.dict.service.DictWordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * 新词学习取词缓存
 * <p>
 * 缓存 key：study:batch:{userId|0}:{level}:{yyyyMMdd}
 * <ul>
 *   <li>首次请求：拉一批 {@link #BATCH_SIZE} 个词（已按"生词本待复习 → 高频核心 → 普通"拼好），写入 Redis，TTL 到当天结束</li>
 *   <li>同一 (userId, level) 当天反复请求：命中缓存，按 count 切片返回，不再访问数据库</li>
 *   <li>切换 level：不同 key → 重新拉一批</li>
 *   <li>隔天：key 过期 → 重新拉一批</li>
 * </ul>
 * 该缓存用于"新词学习"路径；小游戏（randomForGame）走自己的逻辑，不走这里。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudyCacheService {

    /**
     * 一次拉一批的大小。
     * 需大于等于前端可选的最大 count（10/20/30/50），同时能容纳生词本+高频+普通三段拼接。
     */
    private static final int BATCH_SIZE = 200;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String KEY_PREFIX = "study:batch:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final WordBookService wordBookService;
    private final DictWordService dictWordService;

    /**
     * 取 N 个学习单词（带缓存）
     *
     * @param userId 登录用户 ID；游客传 null
     * @param level  cet4 / cet6 / mixed / all / null
     * @param count  本次需要的数量（应 ≤ BATCH_SIZE）
     */
    @SuppressWarnings("unchecked")
    public List<DictWord> take(Long userId, String level, int count) {
        String lv = normalizeLevel(level);
        String key = buildKey(userId, lv);
        List<DictWord> batch = null;
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof DictWord) {
                batch = (List<DictWord>) list;
            }
        } catch (Exception e) {
            log.warn("study cache read failed key={}: {}", key, e.getMessage());
        }
        if (batch == null) {
            batch = loadBatch(userId, lv);
            try {
                redisTemplate.opsForValue().set(key, batch, ttlUntilEndOfDay());
            } catch (Exception e) {
                log.warn("study cache write failed key={}: {}", key, e.getMessage());
            }
        }
        if (count >= batch.size()) {
            return batch;
        }
        return batch.subList(0, count);
    }

    private List<DictWord> loadBatch(Long userId, String level) {
        if (userId != null) {
            return wordBookService.randomStudy(userId, BATCH_SIZE, level);
        }
        List<DictWord> list = dictWordService.randomFromDict(BATCH_SIZE, level);
        return list == null ? Collections.emptyList() : list;
    }

    private static String buildKey(Long userId, String level) {
        return KEY_PREFIX + (userId == null ? "0" : userId) + ":" + level + ":" + LocalDate.now().format(DATE_FMT);
    }

    private static String normalizeLevel(String level) {
        if (StrUtil.isBlank(level) || "all".equalsIgnoreCase(level)) {
            return "all";
        }
        return level.trim().toLowerCase();
    }

    private static Duration ttlUntilEndOfDay() {
        return Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay());
    }
}
