package com.wordspirit.module.sentence.service;

import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wordspirit.ai.AiService;
import com.wordspirit.module.sentence.dto.DailySentenceVO;
import com.wordspirit.module.sentence.entity.DailySentence;
import com.wordspirit.module.sentence.mapper.DailySentenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 句灵日选服务
 *
 * <p>数据流：
 * <ol>
 *   <li>每天 0:05（北京时间）由 {@link DailySentenceScheduler} 调用 {@link #generateToday()}，
 *       调大模型生成 EN/CN/KEY/TAG/NOTE 五行格式日选并写入 daily_sentence（date 唯一幂等）。</li>
 *   <li>{@link #getToday()} 读取流程：Redis 缓存(26h) → DB 当天记录 → 历史随机兜底</li>
 *   <li>历史兜底：当日若未生成（AI 故障/额度耗尽），从历史随机抽一句并标记 fallback=true</li>
 * </ol>
 *
 * <p>大模型调用走 {@link AiService#rawChat} 系统级接口，不占用用户 AI 额度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailySentenceService {

    private static final String CACHE_KEY = "sentence:today:{}";
    private static final Duration CACHE_TTL = Duration.ofHours(26);

    private static final double TEMPERATURE = 0.75;

    /** 系统提示词：固定 + 用户原话，控制输出 EN/CN/KEY/TAG/NOTE 五行格式 */
    private static final String SYSTEM_PROMPT = """
            生成 1 句 CET4‑6 难度、积极治愈的英文佳句。严格按格式输出，**无开场白、结束语、多余装饰、作者名字**，文字尽量凝练。
            EN: 英文原句
            CN: 中文释义
            KEY: 1‑2 个高分实用词块
            TAG: 2 个主题标签（用｜分隔）
            NOTE: 18‑32 字，点明意境或作文适用场景

            规则：
            1. 避开全网泛滥的老套名言，优先选清新耐看的句子
            2. 主题范围：成长｜坚持｜沉淀｜自律｜从容｜希望｜读书｜自我提升
            3. KEY 只给出搭配，不加长篇释义
            4. TAG 从上面主题池里选，不要自创奇怪标签
            5. NOTE 简洁克制，不要抒情堆砌
            6. 整体紧凑，控制总长度
            """;

    private static final String USER_PROMPT = "请生成今天的句灵日选。";

    private final AiService aiService;
    private final DailySentenceMapper sentenceMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取今日日选：缓存 → DB 当天 → 历史兜底
     */
    public DailySentenceVO getToday() {
        LocalDate today = LocalDate.now();
        String key = cacheKey(today);

        // 1. Redis 缓存
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                DailySentenceVO vo = JSONUtil.toBean(JSONUtil.toJsonStr(cached), DailySentenceVO.class);
                if (vo != null && StrUtil.isNotBlank(vo.getEnSentence())) {
                    if (!Boolean.TRUE.equals(vo.getFallback())) {
                        return vo;
                    }
                    // fallback 缓存：今天可能已补生成，再确认真实当天记录
                    DailySentence dbToday = sentenceMapper.selectOne(new LambdaQueryWrapper<DailySentence>()
                            .eq(DailySentence::getDate, today).last("LIMIT 1"));
                    if (dbToday != null) {
                        DailySentenceVO nv = toVO(dbToday, false);
                        cachePut(key, nv);
                        return nv;
                    }
                    return vo;
                }
            } catch (Exception e) {
                log.warn("日选缓存解析失败，重读 DB: {}", e.getMessage());
            }
        }

        // 2. DB 当天记录
        DailySentence dbToday = sentenceMapper.selectOne(new LambdaQueryWrapper<DailySentence>()
                .eq(DailySentence::getDate, today)
                .last("LIMIT 1"));
        if (dbToday != null) {
            DailySentenceVO vo = toVO(dbToday, false);
            cachePut(key, vo);
            return vo;
        }

        // 3. 历史随机兜底
        DailySentence hist = sentenceMapper.selectOne(new LambdaQueryWrapper<DailySentence>()
                .lt(DailySentence::getDate, today)
                .last("ORDER BY RAND() LIMIT 1"));
        if (hist != null) {
            log.info("今日日选未生成，回退到历史 {} 的句子", hist.getDate());
            DailySentenceVO vo = toVO(hist, true);
            cachePut(key, vo);
            return vo;
        }

        log.warn("今日及历史日选均为空");
        return null;
    }

    /**
     * 生成今日日选：调大模型，解析并入库（幂等：当天已存在则跳过）。
     * 失败抛出异常，由调用方（定时任务）记录日志。
     */
    @Transactional(rollbackFor = Exception.class)
    public DailySentenceVO generateToday() {
        LocalDate today = LocalDate.now();

        // 幂等：今天已生成则跳过
        Long exists = sentenceMapper.selectCount(new LambdaQueryWrapper<DailySentence>()
                .eq(DailySentence::getDate, today));
        if (exists != null && exists > 0) {
            log.info("今日日选已存在，跳过生成 date={}", today);
            DailySentence ex = sentenceMapper.selectOne(new LambdaQueryWrapper<DailySentence>()
                    .eq(DailySentence::getDate, today).last("LIMIT 1"));
            return toVO(ex, false);
        }

        String raw = aiService.rawChat(SYSTEM_PROMPT, USER_PROMPT, TEMPERATURE);
        if (StrUtil.isBlank(raw)) {
            throw new RuntimeException("AI 响应为空");
        }
        DailySentence parsed = parseLine(raw, today);
        sentenceMapper.insert(parsed);
        // 清缓存让下次读取重建
        redisTemplate.delete(cacheKey(today));
        log.info("今日日选生成成功 date={} en={}", today, parsed.getEnSentence());
        return toVO(parsed, false);
    }

    /** 解析模型输出的 EN/CN/KEY/TAG/NOTE 多行文本 */
    private DailySentence parseLine(String raw, LocalDate date) {
        Map<String, String> m = new HashMap<>();
        for (String line : raw.split("\\r?\\n")) {
            int idx = line.indexOf(':');
            if (idx > 0 && idx <= 6) {
                String k = line.substring(0, idx).trim().toUpperCase();
                String v = line.substring(idx + 1).trim();
                if (!k.isEmpty() && !v.isEmpty()) {
                    m.put(k, v);
                }
            }
        }
        DailySentence s = new DailySentence();
        s.setDate(date);
        s.setEnSentence(m.getOrDefault("EN", ""));
        s.setCnTrans(m.getOrDefault("CN", ""));
        s.setKeyCollocation(m.getOrDefault("KEY", ""));
        s.setTags(m.getOrDefault("TAG", ""));
        s.setNote(m.getOrDefault("NOTE", ""));
        if (StrUtil.isBlank(s.getEnSentence())) {
            throw new RuntimeException("AI 输出缺少 EN 字段，原文：" + truncate(raw, 200));
        }
        return s;
    }

    private void cachePut(String key, DailySentenceVO vo) {
        try {
            redisTemplate.opsForValue().set(key, vo, CACHE_TTL);
        } catch (Exception e) {
            log.warn("日选缓存写入失败（不影响返回）: {}", e.getMessage());
        }
    }

    private String cacheKey(LocalDate d) {
        return StrFormatter.format(CACHE_KEY, d.toString());
    }

    private DailySentenceVO toVO(DailySentence s, boolean fallback) {
        DailySentenceVO vo = new DailySentenceVO();
        vo.setDate(s.getDate());
        vo.setEnSentence(s.getEnSentence());
        vo.setCnTrans(s.getCnTrans());
        vo.setKeyCollocation(s.getKeyCollocation());
        vo.setTags(s.getTags());
        vo.setNote(s.getNote());
        vo.setFallback(fallback);
        return vo;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}