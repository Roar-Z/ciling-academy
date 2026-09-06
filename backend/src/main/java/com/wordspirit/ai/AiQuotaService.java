package com.wordspirit.ai;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.StrFormatter;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wordspirit.common.ResultCode;
import com.wordspirit.config.AiGlobalConfig;
import com.wordspirit.module.notification.NotifyCategory;
import com.wordspirit.module.notification.service.NotificationService;
import com.wordspirit.module.user.entity.User;
import com.wordspirit.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * AI 每日额度服务
 *
 * <p>额度分为两层（符合主流产品模型）：
 * <ul>
 *   <li><b>每日免费额度</b>：key = {@code ai:quota:{userId}:{yyyyMMdd}}，按自然日重置，不跨天累计。
 *       调用顺序上优先消耗这一层。</li>
 *   <li><b>永久奖励池</b>：key = {@code ai:bonus:{userId}}，<b>不设 TTL、永不随日刷新</b>，
 *       由以下来源叠加：新用户注册赠送、每日签到、连续打卡奖励、活跃度奖励。
 *       仅在每日免费额度用尽后才从此池扣除。</li>
 * </ul>
 *
 * <p>数据库仅做异步统计（ai_used_today / ai_used_total），业务流程绝不依赖库查额度。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuotaService {

    /** 每日已用计数（按自然日，2 天 TTL 实现每日自动归零） */
    private static final String QUOTA_KEY = "ai:quota:{}:{}";
    /** 永久奖励池剩余（不设 TTL，永不随日刷新） */
    private static final String BONUS_KEY = "ai:bonus:{}";
    private static final Duration KEY_TTL = Duration.ofDays(2);

    private final AiGlobalConfig aiConfig;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    /**
     * 检查并扣减一次额度（业务流程统一入口）。
     * 扣减策略：先用尽当日免费额度，再扣永久奖励池。
     *
     * @return 今日剩余可用额度（免费剩余 + 奖励池剩余）
     */
    public int checkAndConsume(Long userId) {
        return checkAndConsume(userId, 1);
    }

    /**
     * 检查并扣减指定次数的额度（业务流程统一入口）。
     * 扣减策略：先用尽当日免费额度，再扣永久奖励池。
     *
     * <p><b>增强能力计费</b>：开启深度思考 / 联网搜索时，业务层传入 cost=2
     * （基础 1 次 + 增强 1 次；两个开关同开不叠加，仍为 2 次）。</p>
     *
     * <p><b>关键点</b>：扣减前先校验剩余是否充足，<b>不足则直接抛出且不产生任何扣减</b>，
     * 避免出现"只扣了一半"导致用户额度异常。</p>
     *
     * @param cost 本次消耗次数（≥1）
     * @return 今日剩余可用额度（免费剩余 + 奖励池剩余）
     */
    public int checkAndConsume(Long userId, int cost) {
        if (!aiConfig.isEnable()) {
            throw new AiException(ResultCode.AI_UNAVAILABLE);
        }
        if (cost <= 0) {
            cost = 1;
        }
        long base = aiConfig.getDailyQuota();

        // 1) 先校验剩余是否足够：不足直接抛出，不做任何扣减
        long freeUsedNow = parseLong(redisTemplate.opsForValue().get(quotaKey(userId)));
        long freeRemain = Math.max(0, base - freeUsedNow);
        long bonusRemain = getBonusRemain(userId);
        if (freeRemain + bonusRemain < cost) {
            log.warn("用户{}额度不足：本次需要{}次，剩余{}次（免费{} + 奖励池{}）",
                    userId, cost, freeRemain + bonusRemain, freeRemain, bonusRemain);
            throw new AiException(ResultCode.AI_QUOTA_EXCEEDED);
        }

        // 2) 扣减：优先扣当日免费额度，剩余部分从永久奖励池扣
        int fromFree = (int) Math.min(cost, freeRemain);
        int fromBonus = cost - fromFree;
        if (fromFree > 0) {
            Long freeUsed = redisTemplate.opsForValue().increment(quotaKey(userId), fromFree);
            if (freeUsed != null && freeUsed == (long) fromFree) {
                redisTemplate.expire(quotaKey(userId), KEY_TTL);
            }
        }
        if (fromBonus > 0) {
            redisTemplate.opsForValue().decrement(bonusKey(userId), fromBonus);
        }
        asyncStat(userId, cost);
        int remain = getTodayRemain(userId);
        // 扣减后额度归零 → 推送"额度用尽"提醒（受 AI 额度提醒开关控制，每日仅一次）
        if (remain <= 0) {
            notifyQuotaExhausted(userId);
        }
        return remain;
    }

    /**
     * 额度用尽通知：Redis 日 key 防重，当天只推一次；
     * 走 pushIfEnabled(QUOTA)，用户关闭「AI额度提醒」开关则不推送。
     */
    private void notifyQuotaExhausted(Long userId) {
        try {
            String ymd = DateUtil.format(DateUtil.date(), "yyyyMMdd");
            String key = StrFormatter.format("notify:quota_exhausted:{}:{}", userId, ymd);
            Boolean first = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofDays(2));
            if (Boolean.TRUE.equals(first)) {
                notificationService.pushIfEnabled(userId, NotifyCategory.QUOTA, "ai_quota_exhausted",
                        "今日词灵AI额度已用完",
                        "今日 AI 额度已全部用完，明天 0 点自动重置；也可以通过签到、完成任务赢取永久额度～");
            }
        } catch (Exception e) {
            log.warn("用户{}额度用尽通知推送失败: {}", userId, e.getMessage());
        }
    }

    /**
     * 退还一次额度（AI 解析失败时调用，保证异常不消耗额度）。
     * <p>仅退还当日免费计数；若本次原本是从奖励池扣的，免费计数为 0 时不再额外退奖励池，
     * 极端情况下用户最多损失 1 次额度，远好于异常仍扣额度。</p>
     */
    public int refund(Long userId) {
        return refund(userId, 1);
    }

    /**
     * 退还指定次数的额度（AI 解析失败时调用，保证异常不消耗额度）。
     *
     * @param cost 需退还的次数，应与 {@link #checkAndConsume(Long, int)} 的 cost 一致
     */
    public int refund(Long userId, int cost) {
        if (cost <= 0) {
            cost = 1;
        }
        String qk = quotaKey(userId);
        Long cur = redisTemplate.opsForValue().decrement(qk, cost);
        if (cur != null && cur < 0) {
            // 防止极端情况下免费计数被减成负数
            redisTemplate.opsForValue().set(qk, 0);
        }
        asyncStatRefund(userId, cost);
        return getTodayRemain(userId);
    }

    /** 查询今日剩余额度（不扣减）= 免费剩余 + 奖励池剩余 */
    public int getTodayRemain(Long userId) {
        if (!aiConfig.isEnable()) {
            return 0;
        }
        long freeUsed = parseLong(redisTemplate.opsForValue().get(quotaKey(userId)));
        long freeRemain = Math.max(0, aiConfig.getDailyQuota() - freeUsed);
        long bonus = getBonusRemain(userId);
        return (int) Math.min(Integer.MAX_VALUE, freeRemain + bonus);
    }

    /**
     * 获取用户 AI 额度详情（每日免费 + 永久奖励），前端弹窗展示用
     *
     * @return 包含 dailyQuota / dailyRemain / dailyUsed / bonusRemain / totalRemain / resetAt
     */
    public java.util.Map<String, Object> getQuotaDetail(Long userId) {
        long dailyQuota = aiConfig.isEnable() ? aiConfig.getDailyQuota() : 0;
        long freeUsed = parseLong(redisTemplate.opsForValue().get(quotaKey(userId)));
        long freeRemain = Math.max(0, dailyQuota - freeUsed);
        long bonusRemain = getBonusRemain(userId);
        long totalRemain = Math.min(Integer.MAX_VALUE, freeRemain + bonusRemain);
        // 下次重置时间：明天 0 点（yyyy-MM-dd HH:mm:ss）
        String resetAt = DateUtil.format(DateUtil.offsetDay(DateUtil.date(), 1), "yyyy-MM-dd") + " 00:00:00";

        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("dailyQuota", (int) dailyQuota);
        m.put("dailyRemain", (int) freeRemain);
        m.put("dailyUsed", (int) freeUsed);
        m.put("bonusRemain", (int) bonusRemain);
        m.put("totalRemain", (int) totalRemain);
        m.put("resetAt", resetAt);
        return m;
    }

    /**
     * 给用户追加永久 AI 奖励额度（注册赠送 / 每日签到 / 连续打卡 / 活跃度奖励）。
     * 关键点：写入<b>不设 TTL 的永久键</b>，不会参与每日刷新，会一直叠加。
     *
     * @param userId 用户 ID
     * @param amount 奖励次数，必须 ≥ 1
     * @return 追加后的永久奖励池剩余
     */
    public int addBonus(Long userId, int amount) {
        if (amount <= 0) {
            return (int) getBonusRemain(userId);
        }
        Long remain = redisTemplate.opsForValue().increment(bonusKey(userId), amount);
        log.info("用户{}获得{}次永久AI奖励额度，当前奖励池剩余{}", userId, amount, remain);
        return remain == null ? 0 : remain.intValue();
    }

    /** 永久奖励池剩余 */
    private long getBonusRemain(Long userId) {
        return parseLong(redisTemplate.opsForValue().get(bonusKey(userId)));
    }

    private long parseLong(Object v) {
        return v == null ? 0 : Long.parseLong(v.toString());
    }

    /** 数据库异步统计，失败不影响主流程 */
    @Async
    protected void asyncStat(Long userId) {
        asyncStat(userId, 1);
    }

    /** 数据库异步统计（按实际消耗次数累加），失败不影响主流程 */
    @Async
    protected void asyncStat(Long userId, int cost) {
        try {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .setSql("ai_used_today = ai_used_today + " + cost)
                    .setSql("ai_used_total = ai_used_total + " + cost));
        } catch (Exception e) {
            log.warn("AI额度异步统计失败: {}", e.getMessage());
        }
    }

    /** 数据库异步退还统计，失败不影响主流程 */
    @Async
    protected void asyncStatRefund(Long userId) {
        asyncStatRefund(userId, 1);
    }

    /** 数据库异步退还统计（按实际消耗次数回退），失败不影响主流程 */
    @Async
    protected void asyncStatRefund(Long userId, int cost) {
        try {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .setSql("ai_used_today = GREATEST(ai_used_today - " + cost + ", 0)")
                    .setSql("ai_used_total = GREATEST(ai_used_total - " + cost + ", 0)"));
        } catch (Exception e) {
            log.warn("AI额度异步退还统计失败: {}", e.getMessage());
        }
    }

    private String quotaKey(Long userId) {
        return StrFormatter.format(QUOTA_KEY, userId, DateUtil.format(DateUtil.date(), "yyyyMMdd"));
    }

    private String bonusKey(Long userId) {
        return StrFormatter.format(BONUS_KEY, userId);
    }
}
