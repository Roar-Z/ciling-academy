package com.wordspirit.module.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wordspirit.ai.AiQuotaService;
import com.wordspirit.common.BusinessException;
import com.wordspirit.common.ResultCode;
import com.wordspirit.module.assistant.entity.AiChatMessage;
import com.wordspirit.module.assistant.mapper.AiChatMessageMapper;
import com.wordspirit.module.game.entity.GameRecord;
import com.wordspirit.module.game.mapper.GameRecordMapper;
import com.wordspirit.module.notification.service.NotificationService;
import com.wordspirit.module.paper.entity.ExerciseQuestion;
import com.wordspirit.module.paper.mapper.ExerciseQuestionMapper;
import com.wordspirit.module.shop.entity.UserCoin;
import com.wordspirit.module.shop.mapper.UserCoinMapper;
import com.wordspirit.module.task.entity.TaskActiveRewardClaim;
import com.wordspirit.module.task.entity.TaskCheckIn;
import com.wordspirit.module.task.entity.TaskStreakRewardClaim;
import com.wordspirit.module.task.entity.TaskTemplate;
import com.wordspirit.module.task.entity.UserDailyTask;
import com.wordspirit.module.task.mapper.TaskActiveRewardClaimMapper;
import com.wordspirit.module.task.mapper.TaskCheckInMapper;
import com.wordspirit.module.task.mapper.TaskStreakRewardClaimMapper;
import com.wordspirit.module.task.mapper.TaskTemplateMapper;
import com.wordspirit.module.task.mapper.UserDailyTaskMapper;
import com.wordspirit.module.task.service.TaskService;
import com.wordspirit.module.user.entity.User;
import com.wordspirit.module.user.mapper.UserMapper;
import com.wordspirit.module.wordbook.entity.WordBook;
import com.wordspirit.module.wordbook.mapper.WordBookMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 任务中心服务实现
 *
 * <p>积分模型（任务驱动）：完成今日任务即得积分，每个任务固定分值，每日上限 100 分
 *   review=10 + newWord=15 + aiUse=10 + game=20 + paper=25 + checkin=20 = 100
 * <p>连续奖励：7 / 14 / 21 / 30 天，分别给 3 / 5 / 8 / 10 次 AI + 0 / 0 / 200 / 500 金币
 * <p>活跃度奖励：25 / 50 / 75 / 100 分，分别给 20 / 40 / 60 / 100 金币 + 0 / 0 / 1 / 3 次 AI
 *   （每日任务金币合计上限 220，配合小游戏每日 150 上限，控制通胀、保证商城装扮的长期积累目标）
 *
 * <p>所有奖励通过唯一约束（uk_user_streak / uk_user_threshold）保证幂等
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    // ---- 连续天数奖励配置 ----
    private static final Map<Integer, int[]> STREAK_REWARD = new HashMap<>();
    static {
        // key=连续天数, value=[aiAward, coinAward]
        STREAK_REWARD.put(7,  new int[]{3,  0});
        STREAK_REWARD.put(14, new int[]{5,  0});
        STREAK_REWARD.put(21, new int[]{8,  200});
        STREAK_REWARD.put(30, new int[]{10, 500});
    }

    // ---- 活跃度档位奖励配置（每日上限 100 分） ----
    private static final Map<Integer, int[]> ACTIVE_REWARD = new HashMap<>();
    static {
        ACTIVE_REWARD.put(25,  new int[]{20,  0});    // +20 金币
        ACTIVE_REWARD.put(50,  new int[]{40,  0});    // +40 金币
        ACTIVE_REWARD.put(75,  new int[]{60,  1});    // +60 金币 + 1 AI
        ACTIVE_REWARD.put(100, new int[]{100, 3});    // +100 金币 + 3 AI
    }

    // ---- 任务驱动型积分配置：完成某个任务固定获得的活跃度 ----
    private static final Map<String, Integer> TASK_POINTS = new LinkedHashMap<>();
    static {
        TASK_POINTS.put("review",  10);  // 复习今日到期单词
        TASK_POINTS.put("newWord", 15);  // 学习 5 个新单词
        TASK_POINTS.put("aiUse",   10);  // 用一次词灵 AI
        TASK_POINTS.put("game",    20);  // 玩一局单词游戏
        TASK_POINTS.put("paper",   25);  // 做一套练习试卷
        TASK_POINTS.put("checkin", 20);  // 每日签到
    }

    /** 每日签到赠送的永久 AI 额度（写入不设 TTL 的奖励池，不参与每日刷新） */
    private static final int CHECKIN_AI_BONUS = 1;

    private final TaskCheckInMapper checkInMapper;
    private final TaskStreakRewardClaimMapper streakClaimMapper;
    private final TaskActiveRewardClaimMapper activeClaimMapper;
    private final WordBookMapper wordBookMapper;
    private final AiChatMessageMapper aiMsgMapper;
    private final GameRecordMapper gameRecordMapper;
    private final ExerciseQuestionMapper questionMapper;
    private final UserCoinMapper coinMapper;
    private final UserMapper userMapper;
    private final AiQuotaService aiQuotaService;
    private final NotificationService notificationService;
    private final TaskTemplateMapper taskTemplateMapper;
    private final UserDailyTaskMapper userDailyTaskMapper;

    @Override
    public Map<String, Object> todayPanel(Long userId) {
        Map<String, Object> panel = new HashMap<>();
        panel.put("checkedIn", isCheckedInToday(userId));
        panel.put("streak", currentStreak(userId));
        panel.put("streakRewards", streakRewardMeta());
        panel.put("streakClaimed", streakClaimedStreakDays(userId));
        panel.put("activityPoints", todayActivityPoints(userId));
        panel.put("activityBreakdown", activityBreakdown(userId));
        panel.put("activityRewards", activeRewardMeta());
        panel.put("activeClaimed", activeClaimedThresholds(userId));
        panel.put("tasks", buildTodayTasks(userId));
        // 近 90 天已签到日期（前端用于渲染月历）
        LocalDate today = LocalDate.now();
        List<TaskCheckIn> recent = checkInMapper.selectList(new LambdaQueryWrapper<TaskCheckIn>()
                .eq(TaskCheckIn::getUserId, userId)
                .ge(TaskCheckIn::getCheckInDate, today.minusDays(90)));
        List<String> checkedDates = recent.stream()
                .map(c -> c.getCheckInDate().toString())
                .collect(Collectors.toList());
        panel.put("today", today.toString());
        panel.put("checkedDates", checkedDates);
        return panel;
    }

    // ============================ 每日打卡 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> doCheckIn(Long userId) {
        LocalDate today = LocalDate.now();

        // 已存在今日打卡记录 → 直接返回（不抛错，便于前端幂等）
        TaskCheckIn exist = checkInMapper.selectOne(new LambdaQueryWrapper<TaskCheckIn>()
                .eq(TaskCheckIn::getUserId, userId)
                .eq(TaskCheckIn::getCheckInDate, today));
        if (exist != null) {
            return buildCheckInResult(userId, exist.getStreakAfter(), false);
        }

        // 计算本次打卡后的连续天数（向前扫描）
        int streak = 1;
        LocalDate cursor = today.minusDays(1);
        while (true) {
            Long count = checkInMapper.selectCount(new LambdaQueryWrapper<TaskCheckIn>()
                    .eq(TaskCheckIn::getUserId, userId)
                    .eq(TaskCheckIn::getCheckInDate, cursor));
            if (count == null || count == 0) {
                break;
            }
            streak++;
            cursor = cursor.minusDays(1);
        }

        // 落库（依赖 uk_user_date 唯一约束避免并发双打卡）
        TaskCheckIn record = new TaskCheckIn();
        record.setUserId(userId);
        record.setCheckInDate(today);
        record.setStreakAfter(streak);
        try {
            checkInMapper.insert(record);
        } catch (DuplicateKeyException dup) {
            // 并发时另一线程已先插入，回查后返回
            TaskCheckIn re = checkInMapper.selectOne(new LambdaQueryWrapper<TaskCheckIn>()
                    .eq(TaskCheckIn::getUserId, userId)
                    .eq(TaskCheckIn::getCheckInDate, today));
            return buildCheckInResult(userId, re == null ? streak : re.getStreakAfter(), false);
        }

        // 同步更新 sys_user.study_days
        userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getStudyDays, streak)
                .set(User::getLastStudyDate, today));

        // 每日签到赠送永久 AI 额度（叠加进不设 TTL 的奖励池，不参与每日刷新）
        int aiBonus = aiQuotaService.addBonus(userId, CHECKIN_AI_BONUS);
        // 系统通知：签到成功
        String content = String.format("已连续签到 %d 天，+1 次词灵AI已到账，明天再来哦～", streak);
        notificationService.push(userId, "reward_check_in", "签到成功", content);
        return buildCheckInResult(userId, streak, true, aiBonus);
    }

    private Map<String, Object> buildCheckInResult(Long userId, int streak, boolean todayFresh) {
        return buildCheckInResult(userId, streak, todayFresh, 0);
    }

    private Map<String, Object> buildCheckInResult(Long userId, int streak, boolean todayFresh, int aiBonus) {
        Map<String, Object> r = new HashMap<>();
        r.put("checkedIn", true);
        r.put("streak", streak);
        r.put("todayFresh", todayFresh);
        r.put("aiBonus", aiBonus);
        r.put("streakRewards", streakRewardMeta());
        r.put("streakClaimed", streakClaimedStreakDays(userId));
        return r;
    }

    @Override
    public boolean isCheckedInToday(Long userId) {
        Long count = checkInMapper.selectCount(new LambdaQueryWrapper<TaskCheckIn>()
                .eq(TaskCheckIn::getUserId, userId)
                .eq(TaskCheckIn::getCheckInDate, LocalDate.now()));
        return count != null && count > 0;
    }

    @Override
    public int currentStreak(Long userId) {
        TaskCheckIn today = checkInMapper.selectOne(new LambdaQueryWrapper<TaskCheckIn>()
                .eq(TaskCheckIn::getUserId, userId)
                .eq(TaskCheckIn::getCheckInDate, LocalDate.now()));
        if (today != null) {
            return today.getStreakAfter();
        }
        // 今天未打卡：看昨天连续几天（不算今天）
        LocalDate cursor = LocalDate.now().minusDays(1);
        int n = 0;
        while (true) {
            Long c = checkInMapper.selectCount(new LambdaQueryWrapper<TaskCheckIn>()
                    .eq(TaskCheckIn::getUserId, userId)
                    .eq(TaskCheckIn::getCheckInDate, cursor));
            if (c == null || c == 0) {
                break;
            }
            n++;
            cursor = cursor.minusDays(1);
        }
        return n;
    }

    // ============================ 连续奖励 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> claimStreakReward(Long userId, int streakDays) {
        int[] reward = STREAK_REWARD.get(streakDays);
        if (reward == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的连续天数档位");
        }
        int current = currentStreak(userId);
        if (current < streakDays) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "连续打卡 " + streakDays + " 天才能领取，当前连续 " + current + " 天");
        }
        // 依赖 uk_user_streak 保证幂等：第二次插入抛 DuplicateKey
        int aiAward = reward[0];
        int coinAward = reward[1];
        TaskStreakRewardClaim claim = new TaskStreakRewardClaim();
        claim.setUserId(userId);
        claim.setStreakDays(streakDays);
        claim.setAiQuotaAward(aiAward);
        claim.setCoinAward(coinAward);
        try {
            claim.setId(null);
            claim.setClaimedAt(LocalDateTime.now());
            streakClaimMapper.insert(claim);
        } catch (DuplicateKeyException dup) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该档位奖励已领取过啦");
        }
        // 发奖
        if (aiAward > 0) {
            aiQuotaService.addBonus(userId, aiAward);
        }
        if (coinAward > 0) {
            addCoin(userId, coinAward);
        }
        log.info("用户{}领取连续{}天奖励：AI={}, 金币={}", userId, streakDays, aiAward, coinAward);
        // 系统通知：连续奖励到账
        StringBuilder content = new StringBuilder("连续 ").append(streakDays).append(" 天奖励到账：");
        if (aiAward > 0) content.append("+").append(aiAward).append(" 次词灵AI ");
        if (coinAward > 0) content.append("+").append(coinAward).append(" 金币 ");
        content.append("，坚持就是胜利～");
        notificationService.push(userId, "reward_streak",
                "连续打卡奖励已发放", content.toString().trim());

        Map<String, Object> result = new HashMap<>();
        result.put("streakDays", streakDays);
        result.put("aiAward", aiAward);
        result.put("coinAward", coinAward);
        result.put("streakClaimed", streakClaimedStreakDays(userId));
        return result;
    }

    @Override
    public List<TaskStreakRewardClaim> streakClaims(Long userId) {
        return streakClaimMapper.selectList(new LambdaQueryWrapper<TaskStreakRewardClaim>()
                .eq(TaskStreakRewardClaim::getUserId, userId)
                .orderByAsc(TaskStreakRewardClaim::getStreakDays));
    }

    private Set<Integer> streakClaimedStreakDays(Long userId) {
        return streakClaims(userId).stream()
                .map(TaskStreakRewardClaim::getStreakDays)
                .collect(Collectors.toSet());
    }

    private List<Map<String, Object>> streakRewardMeta() {
        List<Map<String, Object>> list = new ArrayList<>();
        STREAK_REWARD.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> {
                Map<String, Object> m = new HashMap<>();
                m.put("streakDays", e.getKey());
                m.put("aiAward", e.getValue()[0]);
                m.put("coinAward", e.getValue()[1]);
                list.add(m);
            });
        return list;
    }

    // ============================ 活跃度 ============================

    @Override
    public int todayActivityPoints(Long userId) {
        return computeActivity(userId).cappedTotal;
    }

    /** 今日活跃度分项明细（按类别的得分明细，供前端展示） */
    public Map<String, Integer> activityBreakdown(Long userId) {
        return computeActivity(userId).points;
    }

    /**
     * 一次性聚合今日活跃度（任务驱动型）：
     *   - 一次性查询 5 类行为计数 + 今日签到状态
     *   - 按 TASK_POINTS 表给"完成的任务"打固定分，未完成不计分
     *   - 总分上限 100
     *
     * 任务完成判定：
     *   review  = 今日待复习单词全部清空（dueCount == 0）
     *   newWord = 今日新学词数 ≥ 5
     *   aiUse   = 今日词灵 AI 用户消息数 ≥ 1
     *   game    = 今日单词游戏局数 ≥ 1
     *   paper   = 今日完成的不同试卷数 ≥ 1
     *   checkin = 今日已签到
     */
    private ActivitySnap computeActivity(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = LocalDateTime.of(today, LocalTime.MIN);

        long dueCount = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .and(w -> w.isNull(WordBook::getNextReviewAt)
                        .or().le(WordBook::getNextReviewAt, LocalDateTime.now())));

        long newWordCount = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .ge(WordBook::getCreatedAt, dayStart));

        long aiUseCount = aiMsgMapper.selectCount(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getUserId, userId)
                .eq(AiChatMessage::getRole, "user")
                .ge(AiChatMessage::getCreatedAt, dayStart));

        long gameCount = gameRecordMapper.selectCount(new LambdaQueryWrapper<GameRecord>()
                .eq(GameRecord::getUserId, userId)
                .ge(GameRecord::getCreatedAt, dayStart));

        List<ExerciseQuestion> doneToday = questionMapper.selectList(new LambdaQueryWrapper<ExerciseQuestion>()
                .eq(ExerciseQuestion::getUserId, userId)
                .ge(ExerciseQuestion::getDoneTime, dayStart)
                .isNotNull(ExerciseQuestion::getDoneTime)
                .select(ExerciseQuestion::getPaperId));
        long paperCount = doneToday.stream()
                .map(ExerciseQuestion::getPaperId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();

        boolean checkinToday = isCheckedInToday(userId);

        // 原始计数（buildTodayTasks 描述文案用）
        Map<String, Long> raw = new LinkedHashMap<>();
        raw.put("dueCount",     dueCount);
        raw.put("newWordCount", newWordCount);
        raw.put("aiUseCount",   aiUseCount);
        raw.put("gameCount",    gameCount);
        raw.put("paperCount",   paperCount);

        // 任务完成判定
        Map<String, Boolean> done = new LinkedHashMap<>();
        done.put("review",  dueCount == 0);
        done.put("newWord", newWordCount >= 5);
        done.put("aiUse",   aiUseCount >= 1);
        done.put("game",    gameCount >= 1);
        done.put("paper",   paperCount >= 1);
        done.put("checkin", checkinToday);

        // 各任务得分：完成才计 TASK_POINTS 里的固定分
        Map<String, Integer> pts = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<String, Integer> e : TASK_POINTS.entrySet()) {
            int p = Boolean.TRUE.equals(done.get(e.getKey())) ? e.getValue() : 0;
            pts.put(e.getKey(), p);
            total += p;
        }
        int cappedTotal = Math.max(0, Math.min(100, total));
        return new ActivitySnap(raw, done, pts, cappedTotal);
    }

    /** 活跃度聚合快照：原始计数 + 任务完成状态 + 各任务得分 + 封顶总分（≤100） */
    private static class ActivitySnap {
        final Map<String, Long> raw;
        final Map<String, Boolean> done;
        final Map<String, Integer> points;
        final int cappedTotal;
        ActivitySnap(Map<String, Long> raw, Map<String, Boolean> done,
                     Map<String, Integer> points, int cappedTotal) {
            this.raw = raw;
            this.done = done;
            this.points = points;
            this.cappedTotal = cappedTotal;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> claimActiveReward(Long userId, int threshold) {
        int[] reward = ACTIVE_REWARD.get(threshold);
        if (reward == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的活跃度档位");
        }
        int cur = todayActivityPoints(userId);
        if (cur < threshold) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "今日活跃度不足 " + threshold + "，当前 " + cur);
        }
        int coinAward = reward[0];
        int aiAward = reward[1];
        TaskActiveRewardClaim claim = new TaskActiveRewardClaim();
        claim.setUserId(userId);
        claim.setThreshold(threshold);
        claim.setClaimDate(LocalDate.now());
        claim.setCoinAward(coinAward);
        claim.setAiQuotaAward(aiAward);
        try {
            claim.setId(null);
            claim.setClaimedAt(LocalDateTime.now());
            activeClaimMapper.insert(claim);
        } catch (DuplicateKeyException dup) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该档位奖励已领取过啦");
        }
        if (coinAward > 0) {
            addCoin(userId, coinAward);
        }
        if (aiAward > 0) {
            aiQuotaService.addBonus(userId, aiAward);
        }
        log.info("用户{}领取活跃度{}奖励：金币={}, AI={}", userId, threshold, coinAward, aiAward);
        // 系统通知：活跃度档位奖励
        StringBuilder content = new StringBuilder("今日活跃度已达 ").append(cur).append(" 分，").append(threshold).append(" 档奖励到账：");
        if (coinAward > 0) content.append("+").append(coinAward).append(" 金币 ");
        if (aiAward > 0) content.append("+").append(aiAward).append(" 次词灵AI ");
        content.append("，继续保持活跃～");
        notificationService.push(userId, "reward_active",
                "活跃度奖励已发放", content.toString().trim());

        Map<String, Object> result = new HashMap<>();
        result.put("threshold", threshold);
        result.put("coinAward", coinAward);
        result.put("aiAward", aiAward);
        result.put("activityPoints", cur);
        result.put("activeClaimed", activeClaimedThresholds(userId));
        return result;
    }

    @Override
    public List<TaskActiveRewardClaim> activeClaims(Long userId) {
        return activeClaimMapper.selectList(new LambdaQueryWrapper<TaskActiveRewardClaim>()
                .eq(TaskActiveRewardClaim::getUserId, userId)
                .orderByAsc(TaskActiveRewardClaim::getThreshold));
    }

    private Set<Integer> activeClaimedThresholds(Long userId) {
        // 活跃度每日重置：只认今天的领取记录，昨天领过今天仍可领
        return activeClaimMapper.selectList(new LambdaQueryWrapper<TaskActiveRewardClaim>()
                .eq(TaskActiveRewardClaim::getUserId, userId)
                .eq(TaskActiveRewardClaim::getClaimDate, LocalDate.now()))
                .stream()
                .map(TaskActiveRewardClaim::getThreshold)
                .collect(Collectors.toSet());
    }

    private List<Map<String, Object>> activeRewardMeta() {
        List<Map<String, Object>> list = new ArrayList<>();
        ACTIVE_REWARD.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> {
                Map<String, Object> m = new HashMap<>();
                m.put("threshold", e.getKey());
                m.put("coinAward", e.getValue()[0]);
                m.put("aiAward", e.getValue()[1]);
                list.add(m);
            });
        return list;
    }

    // ============================ 今日任务清单 ============================

    /**
     * 构建今日任务清单：从任务池随机抽选，每日不重复（同一天刷新不重写，且尽量与昨天措辞不同）。
     * 完成态由对应行为（computeActivity）判定，与抽到的具体措辞无关。
     */
    private List<Map<String, Object>> buildTodayTasks(Long userId) {
        LocalDate today = LocalDate.now();

        // 1. 取今日已推送任务；没有则随机抽选并落库
        List<UserDailyTask> todayTasks = userDailyTaskMapper.selectList(new LambdaQueryWrapper<UserDailyTask>()
                .eq(UserDailyTask::getUserId, userId)
                .eq(UserDailyTask::getTaskDate, today)
                .orderByAsc(UserDailyTask::getId));
        if (todayTasks.isEmpty()) {
            todayTasks = assignTodayTasks(userId, today);
        }

        // 2. 行为完成态（任务池里每类对应一种行为）
        ActivitySnap snap = computeActivity(userId);
        Map<String, Boolean> done = snap.done;

        // 3. 组装任务行
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (UserDailyTask udt : todayTasks) {
            TaskTemplate tpl = taskTemplateMapper.selectById(udt.getTemplateId());
            if (tpl == null) continue;
            String category = tpl.getCategory();
            boolean isDone = Boolean.TRUE.equals(done.get(category));
            tasks.add(taskRow(tpl.getIcon(), tpl.getTitle(), tpl.getDescription(),
                    isDone, tpl.getPath(), tpl.getPoints() == null ? 0 : tpl.getPoints()));
        }
        return tasks;
    }

    /**
     * 为某用户某天随机抽选任务：每个类别抽 1 个变体，尽量避开昨天已推送的措辞。
     * 落库依赖 uk_user_date_tpl 唯一约束保证幂等；并发重复插入时回退查询已有记录。
     */
    private List<UserDailyTask> assignTodayTasks(Long userId, LocalDate today) {
        // 昨天抽过的模板（按类别记下来），尽量避免连日重复同一措辞
        LocalDate yesterday = today.minusDays(1);
        Map<String, Long> yestTplByCat = userDailyTaskMapper.selectList(new LambdaQueryWrapper<UserDailyTask>()
                .eq(UserDailyTask::getUserId, userId)
                .eq(UserDailyTask::getTaskDate, yesterday))
                .stream().collect(Collectors.toMap(
                        UserDailyTask::getCategory,
                        UserDailyTask::getTemplateId,
                        (a, b) -> a));

        // 所有启用模板按类别分组
        List<TaskTemplate> all = taskTemplateMapper.selectList(new LambdaQueryWrapper<TaskTemplate>()
                .eq(TaskTemplate::getEnabled, 1));
        Map<String, List<TaskTemplate>> byCat = all.stream()
                .collect(Collectors.groupingBy(TaskTemplate::getCategory));

        Random rnd = new Random();
        List<UserDailyTask> assigned = new ArrayList<>();
        try {
            for (Map.Entry<String, List<TaskTemplate>> e : byCat.entrySet()) {
                String cat = e.getKey();
                List<TaskTemplate> candidates = e.getValue();
                // 优先选昨天没出现过的措辞
                List<TaskTemplate> fresh = candidates.stream()
                        .filter(t -> !Long.valueOf(t.getId()).equals(yestTplByCat.get(cat)))
                        .collect(Collectors.toList());
                List<TaskTemplate> pool = fresh.isEmpty() ? candidates : fresh;
                TaskTemplate chosen = pool.get(rnd.nextInt(pool.size()));

                UserDailyTask udt = new UserDailyTask();
                udt.setUserId(userId);
                udt.setTaskDate(today);
                udt.setTemplateId(chosen.getId());
                udt.setCategory(chosen.getCategory());
                udt.setDone(0);
                userDailyTaskMapper.insert(udt);
                assigned.add(udt);
            }
        } catch (DuplicateKeyException dup) {
            // 并发下已有其他请求写入，回退读取已有记录
            return userDailyTaskMapper.selectList(new LambdaQueryWrapper<UserDailyTask>()
                    .eq(UserDailyTask::getUserId, userId)
                    .eq(UserDailyTask::getTaskDate, today));
        }
        return assigned;
    }

    /**
     * 每日 0:00（北京时间）定时刷新：为所有用户生成当天随机任务。
     * 活跃度（computeActivity）本身按 LocalDate.now() 实时统计当日行为，天然跨天重置，无需在此处理。
     * 幂等：若某用户当天任务已存在（重复触发或手动补偿）则跳过；单用户失败不影响其他用户。
     */
    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Shanghai")
    @Transactional(rollbackFor = Exception.class)
    public void rotateDailyTasks() {
        LocalDate today = LocalDate.now();
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().select(User::getId));
        int ok = 0, skip = 0, fail = 0;
        for (User u : users) {
            Long uid = u.getId();
            try {
                Long exists = userDailyTaskMapper.selectCount(new LambdaQueryWrapper<UserDailyTask>()
                        .eq(UserDailyTask::getUserId, uid)
                        .eq(UserDailyTask::getTaskDate, today));
                if (exists != null && exists > 0) { skip++; continue; }
                assignTodayTasks(uid, today);
                ok++;
            } catch (Exception e) {
                fail++;
                log.error("0:00 生成用户{}今日任务失败", uid, e);
            }
        }
        // 清理 30 天前的历史任务，避免 user_daily_task 表无限膨胀
        LocalDate expire = today.minusDays(30);
        userDailyTaskMapper.delete(new LambdaQueryWrapper<UserDailyTask>()
                .lt(UserDailyTask::getTaskDate, expire));
        log.info("0:00 每日任务刷新完成：生成={}, 跳过={}, 失败={}, 已清理<{} 的历史任务",
                ok, skip, fail, expire);
    }

    private Map<String, Object> taskRow(String icon, String name, String desc, boolean done, String path, int point) {
        Map<String, Object> m = new HashMap<>();
        m.put("icon", icon);
        m.put("name", name);
        m.put("desc", desc);
        m.put("done", done);
        m.put("path", path);
        m.put("point", point);
        return m;
    }

    // ============================ 金币账户操作 ============================

    private void addCoin(Long userId, int amount) {
        UserCoin coin = coinMapper.selectOne(new LambdaQueryWrapper<UserCoin>()
                .eq(UserCoin::getUserId, userId));
        if (coin == null) {
            coin = new UserCoin();
            coin.setUserId(userId);
            coin.setCoinBalance(amount);
            coin.setTotalEarned(amount);
            coin.setTotalSpent(0);
            coinMapper.insert(coin);
            return;
        }
        int newBalance = (coin.getCoinBalance() == null ? 0 : coin.getCoinBalance()) + amount;
        int newEarned = (coin.getTotalEarned() == null ? 0 : coin.getTotalEarned()) + amount;
        coinMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserCoin>()
                .eq(UserCoin::getId, coin.getId())
                .set(UserCoin::getCoinBalance, newBalance)
                .set(UserCoin::getTotalEarned, newEarned));
    }
}