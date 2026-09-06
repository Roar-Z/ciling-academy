package com.wordspirit.module.notification.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wordspirit.config.AiGlobalConfig;
import com.wordspirit.module.notification.NotifyCategory;
import com.wordspirit.module.notification.entity.SysNotification;
import com.wordspirit.module.notification.mapper.SysNotificationMapper;
import com.wordspirit.module.notification.service.NotificationService;
import com.wordspirit.module.user.entity.User;
import com.wordspirit.module.user.mapper.UserMapper;
import com.wordspirit.module.wordbook.entity.WordBook;
import com.wordspirit.module.wordbook.mapper.WordBookMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @see NotificationService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /** 每用户最多保留的通知条数 */
    private static final int MAX_KEEP = 30;

    /** 每日 AI 赠送通知 防重 key */
    private static final String DAILY_KEY = "notify:daily:{}:{}";

    private final SysNotificationMapper notificationMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AiGlobalConfig aiConfig;
    private final UserMapper userMapper;
    private final WordBookMapper wordBookMapper;

    @Override
    @Transactional
    public void push(Long userId, String type, String title, String content) {
        if (userId == null) {
            return;
        }
        SysNotification n = new SysNotification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content == null ? "" : content);
        n.setIsRead(0);
        notificationMapper.insert(n);
        // 超出上限 → 删除最早 N 条
        Long total = notificationMapper.selectCount(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId));
        if (total != null && total > MAX_KEEP) {
            int over = (int) (total - MAX_KEEP);
            List<SysNotification> olds = notificationMapper.selectList(new LambdaQueryWrapper<SysNotification>()
                    .eq(SysNotification::getUserId, userId)
                    .orderByAsc(SysNotification::getCreatedAt)
                    .last("LIMIT " + over));
            if (!olds.isEmpty()) {
                for (SysNotification o : olds) {
                    notificationMapper.deleteById(o.getId());
                }
            }
        }
    }

    @Override
    public List<SysNotification> listRecent(Long userId) {
        return notificationMapper.selectList(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .orderByDesc(SysNotification::getCreatedAt)
                .last("LIMIT " + MAX_KEEP));
    }

    @Override
    public long unreadCount(Long userId) {
        Long c = notificationMapper.selectCount(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getIsRead, 0));
        return c == null ? 0 : c;
    }

    @Override
    public void markRead(Long userId, Long id) {
        if (id == null) {
            return;
        }
        notificationMapper.update(null, new LambdaUpdateWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getId, id)
                .eq(SysNotification::getIsRead, 0)
                .set(SysNotification::getIsRead, 1));
    }

    @Override
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getIsRead, 0)
                .set(SysNotification::getIsRead, 1));
    }

    @Override
    public void pushIfEnabled(Long userId, NotifyCategory category, String type, String title, String content) {
        if (userId == null || category == null) {
            return;
        }
        if (category == NotifyCategory.ALWAYS) {
            push(userId, type, title, content);
            return;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        boolean enabled = switch (category) {
            case REVIEW -> user.getNotifyReview() == null || user.getNotifyReview() == 1;
            case QUOTA -> user.getNotifyQuota() == null || user.getNotifyQuota() == 1;
            default -> true;
        };
        if (!enabled) {
            return;
        }
        push(userId, type, title, content);
    }

    @Override
    @Transactional
    public boolean pushDailyGiftIfNeeded(Long userId) {
        if (userId == null) {
            return false;
        }
        String ymd = DateUtil.format(DateUtil.date(), "yyyyMMdd");
        String key = cn.hutool.core.text.StrFormatter.format(DAILY_KEY, userId, ymd);
        Boolean first = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofDays(2));
        if (Boolean.FALSE.equals(first)) {
            return false;
        }
        // 1) AI 每日赠送（受 AI 额度提醒开关控制）
        if (aiConfig.isEnable()) {
            int dailyQuota = aiConfig.getDailyQuota();
            pushIfEnabled(userId, NotifyCategory.QUOTA, "ai_daily",
                    "每日词灵AI已到账",
                    String.format("今日赠送 %d 次词灵AI调用，请尽情使用～", dailyQuota));
        }
        // 2) 今日复习提醒（受今日复习提醒开关控制，且仅当有待复习单词时推送）
        Long dueCount = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .and(w -> w.isNull(WordBook::getNextReviewAt)
                        .or().le(WordBook::getNextReviewAt, LocalDateTime.now())));
        if (dueCount != null && dueCount > 0) {
            pushIfEnabled(userId, NotifyCategory.REVIEW, "review_reminder",
                    "今日待复习",
                    String.format("你今天还有 %d 个单词待复习，点击查看～", dueCount));
        }
        return true;
    }

    @Override
    public void pushDailyGiftForAll() {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().select(User::getId));
        int ok = 0, fail = 0;
        for (User u : users) {
            try {
                pushDailyGiftIfNeeded(u.getId());
                ok++;
            } catch (Exception e) {
                fail++;
                log.error("定时推送用户{}每日通知失败", u.getId(), e);
            }
        }
        log.info("每日通知定时推送完成：成功 {}，失败 {}", ok, fail);
    }
}