package com.wordspirit.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wordspirit.common.BusinessException;
import com.wordspirit.common.ResultCode;
import com.wordspirit.module.game.entity.GameRecord;
import com.wordspirit.module.game.mapper.GameRecordMapper;
import com.wordspirit.module.user.entity.User;
import com.wordspirit.module.user.entity.UserAchievement;
import com.wordspirit.module.user.mapper.UserAchievementMapper;
import com.wordspirit.module.user.mapper.UserMapper;
import com.wordspirit.module.user.service.AchievementService;
import com.wordspirit.module.wordbook.entity.WordBook;
import com.wordspirit.module.wordbook.mapper.WordBookMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 用户成就服务实现
 *
 * <p>采用"懒解锁"模式：查询成就列表时统一检测条件，达成即写入解锁记录（含达成时间），
 * 无需在各业务点埋点，成就永久保留、跨设备一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementServiceImpl implements AchievementService {

    private final UserMapper userMapper;
    private final UserAchievementMapper achievementMapper;
    private final GameRecordMapper gameRecordMapper;
    private final WordBookMapper wordBookMapper;

    /** 成就条件判断上下文：用户学习数据 + 生词本数量 + 游戏局数 */
    private record Stats(User u, int wordBookCount, long gameCount) {
        int totalWords() { return nz(u.getTotalWords()); }
        int totalMastered() { return nz(u.getTotalMastered()); }
        int studyDays() { return nz(u.getStudyDays()); }
        int aiUsedTotal() { return nz(u.getAiUsedTotal()); }
        boolean goalReached() {
            int goal = u.getDailyGoal() == null || u.getDailyGoal() <= 0 ? 20 : u.getDailyGoal();
            return nz(u.getTodayWords()) >= goal;
        }
        private static int nz(Integer v) { return v == null ? 0 : v; }
    }

    /** 成就定义（progress/target 用于前端进度条展示，target 支持动态如每日目标） */
    private record AchievementDef(String code, String name, String desc, String icon, String category,
                                  int points, Predicate<Stats> cond,
                                  java.util.function.ToLongFunction<Stats> progress,
                                  java.util.function.Function<Stats, Long> target) {}

    private static AchievementDef of(String code, String name, String desc, String icon, String category,
                                     int points, java.util.function.ToLongFunction<Stats> metric, long threshold) {
        return new AchievementDef(code, name, desc, icon, category, points,
                s -> metric.applyAsLong(s) >= threshold, metric, s -> threshold);
    }

    /** 全量成就定义（顺序即展示顺序；修改条件时保持 code 不变，历史解锁记录不受影响） */
    private static final List<AchievementDef> DEFS = List.of(
            // ---- 词汇积累 ----
            of("FIRST_10",    "初出茅庐",   "累计学习 10 词",       "footprints",    "vocab",   10, Stats::totalWords, 10),
            of("WORDS_100",   "词汇新秀",   "累计学习 100 词",      "book-open",     "vocab",   10, Stats::totalWords, 100),
            of("WORDS_500",   "单词猎人",   "累计学习 500 词",      "target",        "vocab",   20, Stats::totalWords, 500),
            of("WORDS_1000",  "千词斩",     "累计学习 1000 词",     "swords",        "vocab",   30, Stats::totalWords, 1000),
            of("WORDS_2000",  "两千词客",   "累计学习 2000 词",     "layers",        "vocab",   40, Stats::totalWords, 2000),
            of("WORDS_10000", "词汇破万",   "累计学习 10000 词",    "gem",           "vocab",  100, Stats::totalWords, 10000),
            of("MASTER_10",   "学有所成",   "掌握 10 个单词",       "circle-check",  "vocab",   10, Stats::totalMastered, 10),
            of("MASTER_100",  "掌握大师",   "掌握 100 个单词",      "crown",         "vocab",   30, Stats::totalMastered, 100),
            of("MASTER_500",  "融会贯通",   "掌握 500 个单词",      "brain",         "vocab",   50, Stats::totalMastered, 500),
            // ---- 坚持打卡 ----
            of("STREAK_7",    "坚持不懈",   "连续学习 7 天",        "flame",         "streak",  10, Stats::studyDays, 7),
            of("STREAK_21",   "恒心可嘉",   "连续学习 21 天",       "activity",      "streak",  20, Stats::studyDays, 21),
            of("STREAK_100",  "百日筑基",   "连续学习 100 天",      "mountain",      "streak",  40, Stats::studyDays, 100),
            of("STREAK_180",  "半年之约",   "连续学习 180 天",      "calendar-check","streak",  60, Stats::studyDays, 180),
            new AchievementDef("GOAL_TODAY", "今日达标", "完成今日学习目标", "alarm-clock", "streak", 10,
                    Stats::goalReached, s -> s.u().getTodayWords() == null ? 0 : s.u().getTodayWords(),
                    s -> (long) (s.u().getDailyGoal() == null || s.u().getDailyGoal() <= 0 ? 20 : s.u().getDailyGoal())),
            new AchievementDef("GOAL_OVER", "超额完成", "今日学习达目标的 1.5 倍", "trending-up", "streak", 20,
                    s -> {
                        int goal = s.u().getDailyGoal() == null || s.u().getDailyGoal() <= 0 ? 20 : s.u().getDailyGoal();
                        return s.nz(s.u().getTodayWords()) >= (int) Math.ceil(goal * 1.5);
                    },
                    s -> s.nz(s.u().getTodayWords()),
                    s -> (long) Math.ceil((s.u().getDailyGoal() == null || s.u().getDailyGoal() <= 0 ? 20 : s.u().getDailyGoal()) * 1.5)),
            // ---- 多元探索 ----
            of("AI_10",       "AI 探索者",  "累计使用 AI 10 次",    "sparkles",      "explore", 10, Stats::aiUsedTotal, 10),
            of("AI_50",       "AI 常客",    "累计使用 AI 50 次",    "rocket",        "explore", 20, Stats::aiUsedTotal, 50),
            of("AI_200",      "AI 大师",    "累计使用 AI 200 次",   "bot",           "explore", 40, Stats::aiUsedTotal, 200),
            of("BOOK_10",     "生词收藏家", "生词本收藏 10 词",     "bookmark",      "explore", 10, s -> s.wordBookCount(), 10),
            of("BOOK_100",    "生词百宝库", "生词本收藏 100 词",    "library",       "explore", 20, s -> s.wordBookCount(), 100),
            of("GAME_1",      "游戏首胜",   "完成 1 局单词游戏",    "gamepad-2",     "explore", 10, s -> s.gameCount(), 1),
            of("GAME_20",     "游戏达人",   "完成 20 局单词游戏",   "trophy",        "explore", 20, s -> s.gameCount(), 20),
            of("GAME_100",    "游戏大师",   "完成 100 局单词游戏",  "medal",         "explore", 40, s -> s.gameCount(), 100)
    );

    @Override
    public Map<String, Object> achievements(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        long wordBookCount = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId));
        long gameCount = gameRecordMapper.selectCount(new LambdaQueryWrapper<GameRecord>()
                .eq(GameRecord::getUserId, userId));
        Stats stats = new Stats(u, (int) Math.min(wordBookCount, Integer.MAX_VALUE), gameCount);

        Map<String, LocalDateTime> unlockedAtMap = achievementMapper.selectList(new LambdaQueryWrapper<UserAchievement>()
                        .eq(UserAchievement::getUserId, userId))
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        UserAchievement::getCode, UserAchievement::getUnlockedAt, (a, b) -> a));

        // 懒解锁：达成但未记录的成就，补写解锁时间（永久保留）
        LocalDateTime now = LocalDateTime.now();
        List<UserAchievement> toUnlock = new ArrayList<>();
        for (AchievementDef def : DEFS) {
            if (!unlockedAtMap.containsKey(def.code()) && def.cond().test(stats)) {
                UserAchievement ua = new UserAchievement();
                ua.setUserId(userId);
                ua.setCode(def.code());
                ua.setUnlockedAt(now);
                toUnlock.add(ua);
            }
        }
        if (!toUnlock.isEmpty()) {
            try {
                toUnlock.forEach(achievementMapper::insert);
            } catch (DuplicateKeyException dup) {
                log.info("用户{}成就并发解锁去重", userId);
            }
            toUnlock.forEach(ua -> unlockedAtMap.put(ua.getCode(), ua.getUnlockedAt()));
        }

        // 组装返回（定义顺序即展示顺序；progress/target 用于未解锁成就的进度条）
        List<Map<String, Object>> list = new ArrayList<>();
        int unlockedPoints = 0;
        int totalPoints = 0;
        for (AchievementDef def : DEFS) {
            LocalDateTime unlockedAt = unlockedAtMap.get(def.code());
            boolean unlocked = unlockedAt != null;
            if (unlocked) unlockedPoints += def.points();
            totalPoints += def.points();
            long progress = Math.min(def.progress().applyAsLong(stats), Long.MAX_VALUE);
            long target = def.target().apply(stats);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", def.code());
            item.put("name", def.name());
            item.put("desc", def.desc());
            item.put("icon", def.icon());
            item.put("category", def.category());
            item.put("points", def.points());
            item.put("unlocked", unlocked);
            item.put("unlockedAt", unlockedAt);
            item.put("progress", progress);
            item.put("target", target);
            list.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("unlockedCount", unlockedAtMap.size());
        result.put("totalCount", DEFS.size());
        result.put("unlockedPoints", unlockedPoints);
        result.put("totalPoints", totalPoints);
        return result;
    }
}
