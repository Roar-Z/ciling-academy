package com.wordspirit.module.dict.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wordspirit.module.dict.entity.DictWord;
import com.wordspirit.module.dict.mapper.DictWordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单词发音音频服务
 * <p>
 * 音频文件存放在后端工作目录 uploads/audio/{word}.mp3（与 uploads 静态资源映射同源，
 * 前端可直接通过 /uploads/audio/xxx.mp3 访问），数据库 dict_word.audio_url 只存相对路径。
 * <p>
 * 缓存策略（Redis → 磁盘文件 → MySQL → 懒下载）：
 * <ul>
 *   <li>拉取单词时顺带返回 audio_url，不触发任何下载</li>
 *   <li>点击播放调 /api/dict/word/{word}/audio 时：
 *       Redis 命中（dict:audio:{word}）→ 直接返回路径，不访问数据库；
 *       未命中 → 查 dict_word.audio_url / 磁盘文件；仍无则从有道词典发音源
 *       下载 mp3 → 写入磁盘 → 回写数据库 + Redis，返回相对路径</li>
 *   <li>同一单词的并发请求通过进程内锁去重，避免重复下载</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WordAudioService {

    /** 有道词典发音：type=1 英式 / type=2 美式 */
    private static final String YOUDAO_UK = "https://dict.youdao.com/dictvoice?audio=%s&type=1";
    private static final String YOUDAO_US = "https://dict.youdao.com/dictvoice?audio=%s&type=2";

    /** 音频存放目录（相对后端工作目录，与 WebConfig 的 /uploads/** 静态映射一致） */
    private static final String AUDIO_DIR = "uploads/audio";

    /**
     * 对外音频访问 URL：走 /api 前缀由本服务流式输出。
     * 部署环境（帽子云）只把 /api 代理到后端，/uploads 直链会 404，
     * 因此数据库/Redis/接口一律存这个 API 形式的路径。
     */
    public static final String AUDIO_URL_PREFIX = "/api/dict/audio/";

    public static String audioUrlOf(String wordLower) {
        return AUDIO_URL_PREFIX + wordLower + ".mp3";
    }

    /** 旧数据兼容：把历史存的 /uploads/audio/xxx.mp3 归一为 /api/dict/audio/xxx.mp3 */
    public static String normalizeUrl(String url) {
        if (url != null && url.startsWith("/uploads/audio/")) {
            return AUDIO_URL_PREFIX + url.substring("/uploads/audio/".length());
        }
        return url;
    }

    /** Redis 键前缀：word 发音音频路径缓存（反复播放只走 Redis，不打数据库） */
    private static final String AUDIO_KEY = "dict:audio:";
    private static final Duration AUDIO_TTL = Duration.ofDays(7);

    /** 单词只允许字母（含连字符/撇号），防止路径穿越与非法 URL */
    private static final String WORD_PATTERN = "[A-Za-z][A-Za-z'\\-]{0,63}";

    /** 下载超时：2 秒拿不到就换源/放弃，前端降级为浏览器 TTS 朗读 */
    private static final int DOWNLOAD_TIMEOUT_MS = 2000;

    /** 按单词粒度的进程内锁，避免同一词并发重复下载 */
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    private final DictWordMapper dictWordMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 确保单词音频已缓存，返回可访问的相对路径（如 /uploads/audio/optical.mp3）。
     * 反复播放命中 Redis，不访问 MySQL。
     *
     * @return 相对路径；单词非法或下载失败返回 null（前端自行降级）
     */
    public String ensureAudio(String word) {
        if (StrUtil.isBlank(word) || !word.matches(WORD_PATTERN)) {
            return null;
        }
        String w = word.trim().toLowerCase(Locale.ROOT);
        // 1) Redis 命中：验证磁盘文件仍在（防止手动清理过 uploads），在则直接返回
        String cached = cacheGet(w);
        if (cached != null) {
            if (audioFileExists(w)) {
                return cached;
            }
            cacheEvict(w);
        }
        DictWord dict = dictWordMapper.selectOne(new LambdaQueryWrapper<DictWord>()
                .select(DictWord::getId, DictWord::getWord, DictWord::getAudioUrl)
                .eq(DictWord::getWord, word.trim()));
        // 2) 已缓存：文件仍在 → 回填 Redis 直接返回；文件丢失（手动清理过）→ 重新下载
        if (audioFileExists(w)) {
            if (dict != null && StrUtil.isNotBlank(dict.getAudioUrl())) {
                String url = normalizeUrl(dict.getAudioUrl());
                cachePut(w, url);
                return url;
            }
            // 词典无此词（如手动添加的生词）：文件已在磁盘，直接返回路径并缓存，不落库
            if (dict == null) {
                String url = audioUrlOf(w);
                cachePut(w, url);
                return url;
            }
        }
        Object lock = locks.computeIfAbsent(w, k -> new Object());
        synchronized (lock) {
            try {
                String url = download(w);
                if (url == null) {
                    return null;
                }
                if (dict != null) {
                    DictWord patch = new DictWord();
                    patch.setId(dict.getId());
                    patch.setAudioUrl(url);
                    dictWordMapper.updateById(patch);
                }
                cachePut(w, url);
                return url;
            } finally {
                locks.remove(w, lock);
            }
        }
    }

    private String cacheGet(String wordLower) {
        try {
            Object v = redisTemplate.opsForValue().get(AUDIO_KEY + wordLower);
            return v instanceof String s && StrUtil.isNotBlank(s) ? normalizeUrl(s) : null;
        } catch (Exception e) {
            log.warn("audio cache read failed word={}: {}", wordLower, e.getMessage());
            return null;
        }
    }

    private void cachePut(String wordLower, String url) {
        try {
            redisTemplate.opsForValue().set(AUDIO_KEY + wordLower, url, AUDIO_TTL);
        } catch (Exception e) {
            log.warn("audio cache write failed word={}: {}", wordLower, e.getMessage());
        }
    }

    private void cacheEvict(String wordLower) {
        try {
            redisTemplate.delete(AUDIO_KEY + wordLower);
        } catch (Exception e) {
            log.warn("audio cache evict failed word={}: {}", wordLower, e.getMessage());
        }
    }

    /**
     * 批量填充音频路径（拉取单词时顺带返回）：
     * 只补 dict_word 中已缓存（audio_url 非空）的词，不触发下载。
     *
     * @param getter  从条目取单词
     * @param setter  向条目写音频路径
     * @param entries 待填充的条目列表（原地修改）
     */
    public <T> void fillAudioUrls(java.util.function.Function<T, String> getter,
                                  java.util.function.BiConsumer<T, String> setter,
                                  List<T> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        Set<String> words = new java.util.HashSet<>();
        for (T e : entries) {
            String w = getter.apply(e);
            if (StrUtil.isNotBlank(w)) {
                words.add(w);
            }
        }
        if (words.isEmpty()) {
            return;
        }
        List<DictWord> rows = dictWordMapper.selectList(new LambdaQueryWrapper<DictWord>()
                .select(DictWord::getWord, DictWord::getAudioUrl)
                .in(DictWord::getWord, words));
        Map<String, String> map = new HashMap<>();
        for (DictWord r : rows) {
            if (StrUtil.isNotBlank(r.getAudioUrl())) {
                map.put(r.getWord().toLowerCase(Locale.ROOT), normalizeUrl(r.getAudioUrl()));
            }
        }
        for (T e : entries) {
            String w = getter.apply(e);
            if (w == null) {
                continue;
            }
            String url = map.get(w.toLowerCase(Locale.ROOT));
            if (url != null) {
                setter.accept(e, url);
            }
        }
    }

    /** 下载有道发音（先英后美），成功写入 uploads/audio/{word}.mp3 并返回相对路径 */
    private String download(String wordLower) {
        File dir = new File(AUDIO_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn("audio dir create failed: {}", dir.getAbsolutePath());
            return null;
        }
        File target = new File(dir, wordLower + ".mp3");
        byte[] bytes = fetch(String.format(YOUDAO_UK, wordLower));
        if (bytes == null || bytes.length < 1024) {
            bytes = fetch(String.format(YOUDAO_US, wordLower));
        }
        if (bytes == null || bytes.length < 1024) {
            log.warn("audio download failed word={} (both UK/US)", wordLower);
            return null;
        }
        try {
            FileUtil.writeBytes(bytes, target);
            return audioUrlOf(wordLower);
        } catch (Exception e) {
            log.warn("audio write failed word={}: {}", wordLower, e.getMessage());
            return null;
        }
    }

    private byte[] fetch(String url) {
        try {
            return HttpUtil.createGet(url).timeout(DOWNLOAD_TIMEOUT_MS).execute().bodyBytes();
        } catch (Exception e) {
            log.warn("audio fetch failed url={}: {}", url, e.getMessage());
            return null;
        }
    }

    private boolean audioFileExists(String wordLower) {
        return new File(AUDIO_DIR, wordLower + ".mp3").isFile();
    }

    /**
     * 解析音频文件（供 /api/dict/audio/{filename} 端点流式输出）。
     * 仅接受 {word}.mp3 形式，防路径穿越。
     */
    public org.springframework.core.io.FileSystemResource resolveAudioFile(String filename) {
        if (filename == null || !filename.matches("[A-Za-z][A-Za-z'\\-]{0,63}\\.mp3")) {
            return null;
        }
        File f = new File(AUDIO_DIR, filename);
        if (!f.isFile()) {
            return null;
        }
        return new org.springframework.core.io.FileSystemResource(f);
    }
}
