package com.wordspirit.module.user.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wordspirit.ai.AiQuotaService;
import com.wordspirit.common.BusinessException;
import com.wordspirit.common.ResultCode;
import com.wordspirit.config.AuthInterceptor;
import com.wordspirit.module.assistant.mapper.AiChatMessageMapper;
import com.wordspirit.module.assistant.mapper.AiChatSessionMapper;
import com.wordspirit.module.assistant.mapper.AiFavoriteMapper;
import com.wordspirit.module.explain.mapper.AiWordExplainMapper;
import com.wordspirit.module.game.mapper.GameRecordMapper;
import com.wordspirit.module.notification.service.NotificationService;
import com.wordspirit.module.paper.mapper.ExercisePaperMapper;
import com.wordspirit.module.paper.mapper.ExerciseQuestionMapper;
import com.wordspirit.module.review.mapper.AiReviewContentMapper;
import com.wordspirit.module.shop.mapper.UserCoinMapper;
import com.wordspirit.module.shop.mapper.UserGoodsMapper;
import com.wordspirit.module.user.dto.LoginReq;
import com.wordspirit.module.user.dto.RegisterReq;
import com.wordspirit.module.user.dto.UserVo;
import com.wordspirit.module.user.entity.EmailBlacklist;
import com.wordspirit.module.user.entity.User;
import com.wordspirit.module.user.mapper.EmailBlacklistMapper;
import com.wordspirit.module.user.mapper.UserMapper;
import com.wordspirit.module.user.service.MailService;
import com.wordspirit.module.user.service.CaptchaService;
import com.wordspirit.module.user.service.UserService;
import com.wordspirit.module.wordbook.mapper.WordBookMapper;
import com.wordspirit.module.longsentence.mapper.AiLongSentenceAnalysisMapper;
import com.wordspirit.module.learnround.mapper.LearnRoundMapper;
import com.wordspirit.module.learnround.mapper.LearnRoundItemMapper;
import com.wordspirit.module.notification.mapper.SysNotificationMapper;
import com.wordspirit.module.task.mapper.TaskActiveRewardClaimMapper;
import com.wordspirit.module.task.mapper.TaskCheckInMapper;
import com.wordspirit.module.task.mapper.TaskStreakRewardClaimMapper;
import com.wordspirit.module.task.mapper.UserDailyTaskMapper;
import com.wordspirit.module.wordtest.mapper.WordTestBatchMapper;
import com.wordspirit.module.wordtest.mapper.WordTestRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Duration TOKEN_TTL = Duration.ofDays(7);
    private static final Duration EMAIL_BLACKLIST_TTL = Duration.ofDays(7);
    private static final DateTimeFormatter DAILY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final UserMapper userMapper;
    private final EmailBlacklistMapper emailBlacklistMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final AiQuotaService aiQuotaService;
    private final NotificationService notificationService;
    private final MailService mailService;
    private final CaptchaService captchaService;
    private final WordBookMapper wordBookMapper;
    private final AiChatSessionMapper aiChatSessionMapper;
    private final AiChatMessageMapper aiChatMessageMapper;
    private final AiWordExplainMapper aiWordExplainMapper;
    private final AiReviewContentMapper aiReviewContentMapper;
    private final AiFavoriteMapper aiFavoriteMapper;
    private final ExercisePaperMapper exercisePaperMapper;
    private final ExerciseQuestionMapper exerciseQuestionMapper;
    private final GameRecordMapper gameRecordMapper;
    private final UserCoinMapper userCoinMapper;
    private final UserGoodsMapper userGoodsMapper;
    private final AiLongSentenceAnalysisMapper aiLongSentenceAnalysisMapper;
    private final LearnRoundMapper learnRoundMapper;
    private final LearnRoundItemMapper learnRoundItemMapper;
    private final SysNotificationMapper sysNotificationMapper;
    private final TaskActiveRewardClaimMapper taskActiveRewardClaimMapper;
    private final TaskCheckInMapper taskCheckInMapper;
    private final TaskStreakRewardClaimMapper taskStreakRewardClaimMapper;
    private final UserDailyTaskMapper userDailyTaskMapper;
    private final WordTestBatchMapper wordTestBatchMapper;
    private final WordTestRecordMapper wordTestRecordMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserVo register(RegisterReq req, String email, String emailCode) {
        // 1. 图形验证码已在独立接口 /api/captcha/verify 校验，注册阶段不再校验

        // 2. 校验邮箱 + 邮箱验证码（必须先发，类型为 bind_email）
        if (StrUtil.isBlank(email) || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邮箱格式不正确");
        }
        String emailLower = email.trim().toLowerCase();
        // 校验邮箱黑名单（注销账号后冻结 7 天，期间不可用该邮箱再次注册）
        EmailBlacklist bl = emailBlacklistMapper.selectOne(new LambdaQueryWrapper<EmailBlacklist>()
                .eq(EmailBlacklist::getEmail, emailLower));
        if (bl != null && bl.getExpireAt() != null && bl.getExpireAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "该邮箱近期被注销冻结，" + bl.getExpireAt().toLocalDate() + " 后解冻，或联系管理员提前解冻");
        }
        checkEmailCode("bind_email", emailLower, emailCode);

        // 3. 校验用户名唯一
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名已被注册");
        }
        // 4. 校验邮箱唯一
        Long emailCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, emailLower));
        if (emailCount != null && emailCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该邮箱已被注册");
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(StrUtil.isNotBlank(req.getNickname()) ? req.getNickname() : req.getUsername());
        user.setEmail(emailLower);
        user.setTodayWords(0);
        user.setTotalWords(0);
        user.setStudyDays(0);
        user.setAiUsedToday(0);
        user.setAiUsedTotal(0);
        userMapper.insert(user);
        // 新用户赠送 20 次永久 AI 调用额度（写入不设 TTL 的奖励池，不参与每日刷新）
        aiQuotaService.addBonus(user.getId(), 20);
        // 注册欢迎通知（系统通知面板可见）
        notificationService.push(user.getId(),
                "register_bonus",
                "欢迎加入词灵学园",
                "新用户专属：20 次词灵AI调用已到账，尽情开启你的学习之旅吧～");
        return buildVo(user, true);
    }

    @Override
    public UserVo login(LoginReq req) {
        // 防爆破：同用户名连错 5 次锁 15 分钟（Redis 计数，成功登录清零）
        String failKey = "login:fail:" + req.getUsername();
        String failStr = stringRedisTemplate.opsForValue().get(failKey);
        if (failStr != null && Integer.parseInt(failStr) >= 5) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "尝试次数过多，请 15 分钟后再试");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            Long fails = stringRedisTemplate.opsForValue().increment(failKey);
            if (fails != null && fails == 1) {
                stringRedisTemplate.expire(failKey, Duration.ofMinutes(15));
            }
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名或密码错误");
        }
        stringRedisTemplate.delete(failKey);
        return buildVo(user, true);
    }

    @Override
    public UserVo current(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        // 进入个人页时若已跨天，把今日单词数重置为 0（避免昨天数据残留在今天的页面上）
        resetDailyIfNeeded(user);
        return buildVo(user, false);
    }

    @Override
    public void logout(Long userId, String token) {
        if (StrUtil.isNotBlank(token)) {
            if (token.startsWith(AuthInterceptor.TOKEN_PREFIX)) {
                token = token.substring(AuthInterceptor.TOKEN_PREFIX.length());
            }
            stringRedisTemplate.delete(AuthInterceptor.REDIS_TOKEN_KEY + token);
        }
        log.info("用户 {} 退出登录", userId);
    }

    @Override
    public void recordStudy(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        int currentToday = user.getTodayWords() == null ? 0 : user.getTodayWords();
        int currentTotal = user.getTotalWords() == null ? 0 : user.getTotalWords();
        int currentStudyDays = user.getStudyDays() == null ? 0 : user.getStudyDays();
        boolean newDay = user.getLastStudyDate() == null || !today.equals(user.getLastStudyDate());

        User update = new User();
        update.setId(userId);
        if (newDay) {
            boolean continuous = user.getLastStudyDate() != null
                    && today.minusDays(1).equals(user.getLastStudyDate());
            update.setTodayWords(1);
            update.setTotalWords(currentTotal + 1);
            update.setStudyDays(continuous ? currentStudyDays + 1 : 1);
            update.setLastStudyDate(today);
        } else {
            update.setTodayWords(currentToday + 1);
            update.setTotalWords(currentTotal + 1);
        }
        userMapper.updateById(update);
    }

    @Override
    public UserVo updateProfile(Long userId, String nickname, String avatar, String bio) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        User update = new User();
        update.setId(userId);
        if (nickname != null) {
            update.setNickname(StrUtil.isBlank(nickname) ? "" : StrUtil.sub(nickname, 0, 20));
        }
        if (avatar != null) {
            update.setAvatar(StrUtil.sub(avatar, 0, 255));
        }
        if (bio != null) {
            update.setBio(StrUtil.sub(bio, 0, 100));
        }
        userMapper.updateById(update);
        return buildVo(userMapper.selectById(userId), false);
    }

    @Override
    public UserVo saveReviewBatchSize(Long userId, Integer size) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (size == null || size < 1) {
            size = 10;
        }
        if (size > 50) {
            size = 50;
        }
        User update = new User();
        update.setId(userId);
        update.setReviewBatchSize(size);
        userMapper.updateById(update);
        return buildVo(userMapper.selectById(userId), false);
    }

    @Override
    public UserVo saveDailyGoal(Long userId, Integer goal) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (goal == null || goal < 5) {
            goal = 5;
        }
        if (goal > 200) {
            goal = 200;
        }
        User update = new User();
        update.setId(userId);
        update.setDailyGoal(goal);
        userMapper.updateById(update);
        return buildVo(userMapper.selectById(userId), false);
    }

    @Override
    public Integer sendEmailCode(Long userId, String type, String email,
                                String captchaId) {
        log.info("[验证码] 收到发送请求: userId={}, type={}, email={}, captchaId={}",
                userId, type, email, captchaId);
        // 校验该 captchaId 已在独立接口 /api/captcha/verify 通过图形验证
        captchaService.validateUsed(captchaId);

        User user = userId == null ? null : userMapper.selectById(userId);
        if (userId != null && user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (StrUtil.isBlank(email) || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邮箱格式不正确");
        }
        // 匿名场景（注册/忘记密码）只允许 bind_email / reset_password
        if (userId == null) {
            if ("reset_password".equals(type)) {
                // 忘记密码：验证码只能发给已注册邮箱
                User byEmail = userMapper.selectOne(new LambdaQueryWrapper<User>()
                        .eq(User::getEmail, email.trim().toLowerCase()));
                if (byEmail == null) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "该邮箱未注册");
                }
            } else if (!"bind_email".equals(type)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的验证码类型");
            }
        } else if ("change_password".equals(type)) {
            if (StrUtil.isBlank(user.getEmail())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "请先绑定邮箱再修改密码");
            }
            if (!user.getEmail().equals(email)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "只能向已绑定邮箱发送验证码");
            }
        } else if ("change_email".equals(type)) {
            if (StrUtil.isNotBlank(user.getEmail()) && !user.getEmail().equals(email)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "换绑邮箱请使用当前已绑定邮箱接收验证码");
            }
        } else if ("bind_email".equals(type)) {
            if (StrUtil.isNotBlank(user.getEmail())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "您已绑定邮箱，请使用换绑功能");
            }
        } else if ("delete_account".equals(type)) {
            if (StrUtil.isBlank(user.getEmail())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "请先绑定邮箱后再注销账号");
            }
            if (!user.getEmail().equals(email)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "只能向已绑定邮箱发送验证码");
            }
        } else {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的验证码类型");
        }
        // 限流：同一邮箱 60 秒内只能发送一次
        String intervalKey = "email:send:interval:" + email.toLowerCase();
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(intervalKey))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "操作过于频繁，请 60 秒后再获取验证码");
        }
        // 限流：同一用户每天最多发送 10 次
        String today = LocalDate.now().format(DAILY_FMT);
        // 注册场景没有 userId，按邮箱统计
        String dailyKey = "email:send:daily:" + (userId == null ? "anon:" + email.toLowerCase() : userId) + ":" + today;
        String dailyVal = stringRedisTemplate.opsForValue().get(dailyKey);
        int dailyCount = dailyVal == null ? 0 : Integer.parseInt(dailyVal);
        if (dailyCount >= 10) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "今日验证码发送次数已达上限（10 次），请明日再试");
        }

        String code = RandomUtil.randomNumbers(6);
        String key = "email:code:" + type + ":" + email.toLowerCase();
        stringRedisTemplate.opsForValue().set(key, code, Duration.ofMinutes(10));
        mailService.sendVerifyCode(email, code);

        // 记录限流计数
        stringRedisTemplate.opsForValue().set(intervalKey, "1", Duration.ofSeconds(60));
        Long totalSent = stringRedisTemplate.opsForValue().increment(dailyKey);
        long ttl = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay()).getSeconds();
        stringRedisTemplate.expire(dailyKey, Duration.ofSeconds(ttl));
        // 返回今日剩余发送次数（上限 10 次）
        return Math.max(0, 10 - (totalSent == null ? 0 : totalSent.intValue()));
    }

    @Override
    public UserVo updateEmail(Long userId, String newEmail, String code) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (StrUtil.isBlank(newEmail) || !newEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邮箱格式不正确");
        }
        // 校验邮箱黑名单（注销账号后冻结 7 天，期间不可绑定/换绑到该邮箱）
        String emailLower = newEmail.trim().toLowerCase();
        EmailBlacklist bl = emailBlacklistMapper.selectOne(new LambdaQueryWrapper<EmailBlacklist>()
                .eq(EmailBlacklist::getEmail, emailLower));
        if (bl != null && bl.getExpireAt() != null && bl.getExpireAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "该邮箱近期被注销冻结，" + bl.getExpireAt().toLocalDate() + " 后解冻，或联系管理员提前解冻");
        }
        boolean hasOld = StrUtil.isNotBlank(user.getEmail());
        String verifyType = hasOld ? "change_email" : "bind_email";
        String verifyEmail = hasOld ? user.getEmail() : newEmail;
        checkEmailCode(verifyType, verifyEmail, code);

        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, newEmail).ne(User::getId, userId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该邮箱已被绑定");
        }
        User update = new User();
        update.setId(userId);
        update.setEmail(newEmail);
        userMapper.updateById(update);
        return buildVo(userMapper.selectById(userId), false);
    }

    @Override
    public UserVo changePassword(Long userId, String oldPassword, String newPassword, String code) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (StrUtil.isBlank(user.getEmail())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先绑定邮箱再修改密码");
        }
        if (StrUtil.isBlank(oldPassword) || StrUtil.isBlank(newPassword) || StrUtil.isBlank(code)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请填写完整信息");
        }
        if (newPassword.length() < 6 || newPassword.length() > 20) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新密码长度需在 6-20 位之间");
        }
        checkEmailCode("change_password", user.getEmail(), code);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "原密码错误");
        }
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
        return buildVo(userMapper.selectById(userId), false);
    }

    @Override
    public UserVo resetPassword(String email, String code, String newPassword) {
        if (StrUtil.isBlank(email) || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邮箱格式不正确");
        }
        String emailLower = email.trim().toLowerCase();
        checkEmailCode("reset_password", emailLower, code);
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 20) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新密码长度需在 6-20 位之间");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, emailLower));
        if (user == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该邮箱未注册");
        }
        User update2 = new User();
        update2.setId(user.getId());
        update2.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update2);
        return buildVo(userMapper.selectById(user.getId()), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long userId, String code) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (StrUtil.isBlank(user.getEmail())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先绑定邮箱后再注销账号");
        }
        // 验证码校验（已发送至已绑定邮箱）
        checkEmailCode("delete_account", user.getEmail(), code);

        String lowerEmail = user.getEmail().trim().toLowerCase();
        LocalDateTime expireAt = LocalDateTime.now().plus(EMAIL_BLACKLIST_TTL);

        // 删除用户关联的所有业务数据（顺序无关，因为都是按 user_id 过滤）
        // word_book
        wordBookMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.wordbook.entity.WordBook>()
                .eq(com.wordspirit.module.wordbook.entity.WordBook::getUserId, userId));
        // AI 聊天：先删消息再删 session
        aiChatMessageMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.assistant.entity.AiChatMessage>()
                .eq(com.wordspirit.module.assistant.entity.AiChatMessage::getUserId, userId));
        aiChatSessionMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.assistant.entity.AiChatSession>()
                .eq(com.wordspirit.module.assistant.entity.AiChatSession::getUserId, userId));
        aiWordExplainMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.explain.entity.AiWordExplain>()
                .eq(com.wordspirit.module.explain.entity.AiWordExplain::getUserId, userId));
        aiReviewContentMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.review.entity.AiReviewContent>()
                .eq(com.wordspirit.module.review.entity.AiReviewContent::getUserId, userId));
        aiFavoriteMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.assistant.entity.AiFavorite>()
                .eq(com.wordspirit.module.assistant.entity.AiFavorite::getUserId, userId));
        // 试卷：先删题目再删试卷
        exerciseQuestionMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.paper.entity.ExerciseQuestion>()
                .eq(com.wordspirit.module.paper.entity.ExerciseQuestion::getUserId, userId));
        exercisePaperMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.paper.entity.ExercisePaper>()
                .eq(com.wordspirit.module.paper.entity.ExercisePaper::getUserId, userId));
        gameRecordMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.game.entity.GameRecord>()
                .eq(com.wordspirit.module.game.entity.GameRecord::getUserId, userId));
        userCoinMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.shop.entity.UserCoin>()
                .eq(com.wordspirit.module.shop.entity.UserCoin::getUserId, userId));
        userGoodsMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.shop.entity.UserGoods>()
                .eq(com.wordspirit.module.shop.entity.UserGoods::getUserId, userId));
        // 长难句分析
        aiLongSentenceAnalysisMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.longsentence.entity.AiLongSentenceAnalysis>()
                .eq(com.wordspirit.module.longsentence.entity.AiLongSentenceAnalysis::getUserId, userId));
        // 巩固测验：先删明细再删轮次
        learnRoundItemMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.learnround.entity.LearnRoundItem>()
                .eq(com.wordspirit.module.learnround.entity.LearnRoundItem::getUserId, userId));
        learnRoundMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.learnround.entity.LearnRound>()
                .eq(com.wordspirit.module.learnround.entity.LearnRound::getUserId, userId));
        // 通知
        sysNotificationMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.notification.entity.SysNotification>()
                .eq(com.wordspirit.module.notification.entity.SysNotification::getUserId, userId));
        // 任务：签到、奖励领取、每日任务
        taskCheckInMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.task.entity.TaskCheckIn>()
                .eq(com.wordspirit.module.task.entity.TaskCheckIn::getUserId, userId));
        taskStreakRewardClaimMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.task.entity.TaskStreakRewardClaim>()
                .eq(com.wordspirit.module.task.entity.TaskStreakRewardClaim::getUserId, userId));
        taskActiveRewardClaimMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.task.entity.TaskActiveRewardClaim>()
                .eq(com.wordspirit.module.task.entity.TaskActiveRewardClaim::getUserId, userId));
        userDailyTaskMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.task.entity.UserDailyTask>()
                .eq(com.wordspirit.module.task.entity.UserDailyTask::getUserId, userId));
        // 单词测试：先删记录再删批次
        wordTestRecordMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.wordtest.entity.WordTestRecord>()
                .eq(com.wordspirit.module.wordtest.entity.WordTestRecord::getUserId, userId));
        wordTestBatchMapper.delete(new LambdaQueryWrapper<com.wordspirit.module.wordtest.entity.WordTestBatch>()
                .eq(com.wordspirit.module.wordtest.entity.WordTestBatch::getUserId, userId));

        // 最后删除用户主表
        userMapper.deleteById(userId);

        // 冻结邮箱：7 天内不可用此邮箱再次注册
        EmailBlacklist bl = new EmailBlacklist();
        bl.setEmail(lowerEmail);
        bl.setUserId(userId);
        bl.setReason("注销账号");
        bl.setExpireAt(expireAt);
        emailBlacklistMapper.insert(bl);

        // 清理 Redis：所有 token、AI 额度、限流键、验证码
        clearUserRedis(userId, lowerEmail);

        log.warn("用户已注销: userId={}, email={}, 解冻时间={}", userId, lowerEmail, expireAt);
    }

    /** 清理注销用户在 Redis 中的所有痕迹 */
    private void clearUserRedis(Long userId, String email) {
        // 1. 清理所有 token（按 token 前缀扫描）
        Set<String> tokenKeys = stringRedisTemplate.keys(AuthInterceptor.REDIS_TOKEN_KEY + "*");
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            // value 是 userId 字符串
            for (String key : tokenKeys) {
                String val = stringRedisTemplate.opsForValue().get(key);
                if (val != null && String.valueOf(userId).equals(val)) {
                    stringRedisTemplate.delete(key);
                }
            }
        }
        // 2. AI 每日额度（按日期，最多保留 7 天都清掉）
        String today = LocalDate.now().format(DAILY_FMT);
        stringRedisTemplate.delete("ai:quota:" + userId + ":" + today);
        for (int i = 1; i <= 6; i++) {
            stringRedisTemplate.delete("ai:quota:" + userId + ":" + LocalDate.now().minusDays(i).format(DAILY_FMT));
        }
        // 3. 邮箱验证码、限流键
        stringRedisTemplate.delete("email:code:delete_account:" + email);
        stringRedisTemplate.delete("email:send:interval:" + email);
        stringRedisTemplate.delete("email:send:daily:" + userId + ":" + today);
        // 4. 游戏跨局去重：答对锁定集合、每日已分配词表
        stringRedisTemplate.delete("game:mastered:" + userId);
        Set<String> gameDailyKeys = stringRedisTemplate.keys("game:words:" + userId + ":*");
        if (gameDailyKeys != null && !gameDailyKeys.isEmpty()) {
            stringRedisTemplate.delete(gameDailyKeys);
        }
    }

    private void checkEmailCode(String type, String email, String code) {
        if (StrUtil.isBlank(code)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "验证码不能为空");
        }
        String key = "email:code:" + type + ":" + email;
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (!code.equals(cached)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "验证码错误或已过期");
        }
        stringRedisTemplate.delete(key);
    }

    @Override
    public Map<String, Boolean> getNotifySettings(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Map<String, Boolean> result = new HashMap<>();
        result.put("notifyReview", user.getNotifyReview() == null || user.getNotifyReview() == 1);
        result.put("notifyQuota", user.getNotifyQuota() == null || user.getNotifyQuota() == 1);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Boolean> saveNotifySettings(Long userId, Boolean notifyReview, Boolean notifyQuota) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        User update = new User();
        update.setId(userId);
        if (notifyReview != null) {
            update.setNotifyReview(Boolean.TRUE.equals(notifyReview) ? 1 : 0);
        }
        if (notifyQuota != null) {
            update.setNotifyQuota(Boolean.TRUE.equals(notifyQuota) ? 1 : 0);
        }
        userMapper.updateById(update);
        return getNotifySettings(userId);
    }

    /** 若已跨天，把 todayWords 重置为 0（studyDays/totalWords 不动，等下一次学习时再处理） */
    private void resetDailyIfNeeded(User user) {
        LocalDate today = LocalDate.now();
        if (user.getLastStudyDate() != null && !today.equals(user.getLastStudyDate())
                && (user.getTodayWords() == null || user.getTodayWords() != 0)) {
            User update = new User();
            update.setId(user.getId());
            update.setTodayWords(0);
            userMapper.updateById(update);
            user.setTodayWords(0);
        }
    }

    /** 组装返回 VO（token 仅在登录/注册时生成） */
    private UserVo buildVo(User user, boolean withToken) {
        UserVo vo = new UserVo();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setEmail(user.getEmail());
        vo.setBio(user.getBio());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setTodayWords(user.getTodayWords());
        vo.setTotalWords(user.getTotalWords());
        // 累计掌握（去重）：生词本中 familiarity>=80 的不同单词数
        vo.setMasteredWords(countMasteredWords(user.getId()));
        vo.setStudyDays(user.getStudyDays());
        vo.setReviewBatchSize(user.getReviewBatchSize());
        vo.setDailyGoal(user.getDailyGoal() == null ? 20 : user.getDailyGoal());
        vo.setAiQuotaRemain(aiQuotaService.getTodayRemain(user.getId()));
        vo.setAiUsedTotal(user.getAiUsedTotal() == null ? 0 : user.getAiUsedTotal());
        vo.setNotifyReview(user.getNotifyReview() == null || user.getNotifyReview() == 1);
        vo.setNotifyQuota(user.getNotifyQuota() == null || user.getNotifyQuota() == 1);
        if (withToken) {
            String token = IdUtil.fastSimpleUUID();
            stringRedisTemplate.opsForValue().set(
                    AuthInterceptor.REDIS_TOKEN_KEY + token,
                    String.valueOf(user.getId()),
                    TOKEN_TTL);
            vo.setToken(token);
        }
        return vo;
    }

    /**
     * 累计掌握（去重）：从 sys_user.mastered_words JSON 数组读取大小。
     * 该集合由新词学习点击"认识"调 addMasteredWord 写入，与生词本无关。
     * 清空生词本不会让累计掌握归零。
     */
    private int countMasteredWords(Long userId) {
        if (userId == null) return 0;
        User u = userMapper.selectById(userId);
        if (u == null || u.getMasteredWords() == null || u.getMasteredWords().isEmpty()) return 0;
        try {
            return JSONUtil.parseArray(u.getMasteredWords()).size();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public boolean addMasteredWord(Long userId, String word) {
        if (userId == null || StrUtil.isBlank(word)) return false;
        String w = word.trim().toLowerCase(Locale.ROOT);
        User u = userMapper.selectById(userId);
        if (u == null) return false;
        java.util.Set<String> set = new java.util.LinkedHashSet<>();
        if (u.getMasteredWords() != null && !u.getMasteredWords().isEmpty()) {
            try {
                List<String> arr = JSONUtil.parseArray(u.getMasteredWords()).toList(String.class);
                set.addAll(arr);
            } catch (Exception ignored) {}
        }
        boolean added = set.add(w);
        if (!added) return false;   // 已存在，不重加
        User patch = new User();
        patch.setId(userId);
        patch.setMasteredWords(JSONUtil.toJsonStr(set));
        // 同步累计掌握 +1：与复习跨80共用 total_mastered 口径（成就/等级成长值读该字段）
        int cur = u.getTotalMastered() == null ? 0 : u.getTotalMastered();
        patch.setTotalMastered(cur + 1);
        userMapper.updateById(patch);
        return true;
    }
}
