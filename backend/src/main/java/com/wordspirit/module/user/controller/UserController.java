package com.wordspirit.module.user.controller;

import com.wordspirit.common.BusinessException;
import com.wordspirit.common.Result;
import com.wordspirit.common.ResultCode;
import com.wordspirit.common.UserContext;
import com.wordspirit.config.OssService;
import com.wordspirit.module.user.dto.LoginReq;
import com.wordspirit.module.user.dto.RegisterReq;
import com.wordspirit.module.user.dto.UserVo;
import com.wordspirit.module.user.service.AchievementService;
import com.wordspirit.module.user.service.LevelService;
import com.wordspirit.module.user.service.UserService;
import com.wordspirit.ai.AiQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 用户接口
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final OssService ossService;
    private final AiQuotaService aiQuotaService;
    private final LevelService levelService;
    private final AchievementService achievementService;

    /** 注册（需邮箱 + 邮箱验证码；图形验证码已在 /api/captcha/verify 独立校验） */
    @PostMapping("/register")
    public Result<UserVo> register(@RequestBody Map<String, String> body) {
        if (body == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不完整");
        }
        RegisterReq req = new RegisterReq();
        req.setUsername(body.get("username"));
        req.setPassword(body.get("password"));
        req.setNickname(body.get("nickname"));
        return Result.ok(userService.register(
                req,
                body.get("email"),
                body.get("emailCode")));
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<UserVo> login(@Valid @RequestBody LoginReq req) {
        return Result.ok(userService.login(req));
    }

    /** 当前登录用户信息 */
    @GetMapping("/info")
    public Result<UserVo> info() {
        return Result.ok(userService.current(UserContext.requireUserId()));
    }

    /** 退出登录 */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        userService.logout(UserContext.requireUserId(), request.getHeader("Authorization"));
        return Result.ok();
    }

    /** 今日剩余AI额度（导航栏定时刷新） */
    @GetMapping("/ai-quota")
    public Result<Map<String, Object>> aiQuota() {
        Long userId = UserContext.requireUserId();
        // 每日免费额度 + 永久奖励额度详情（前端弹窗用）
        Map<String, Object> detail = aiQuotaService.getQuotaDetail(userId);
        // 兼容老字段（导航栏徽标 + 翻译/工具页等仍在用）
        detail.put("aiQuotaRemain", detail.get("totalRemain"));
        return Result.ok(detail);
    }

    /** 记录学习动作（背单词/游戏答题后调用） */
    @PostMapping("/record-study")
    public Result<Void> recordStudy() {
        userService.recordStudy(UserContext.requireUserId());
        return Result.ok();
    }

    /** 等级详情（成长值/称号/进度/升级奖励） */
    @GetMapping("/level-info")
    public Result<Map<String, Object>> levelInfo() {
        return Result.ok(levelService.levelInfo(UserContext.requireUserId()));
    }

    /** 领取等级升级奖励（每级一次） */
    @PostMapping("/level-claim")
    public Result<Map<String, Object>> claimLevelReward(@RequestBody Map<String, Integer> body) {
        int level = body == null || body.get("level") == null ? 0 : body.get("level");
        return Result.ok(levelService.claimReward(UserContext.requireUserId(), level));
    }

    /** 成就列表（查询时自动解锁已达成的成就并记录达成时间） */
    @GetMapping("/achievements")
    public Result<Map<String, Object>> achievements() {
        return Result.ok(achievementService.achievements(UserContext.requireUserId()));
    }

    /** 更新个人资料（昵称/头像/简介） */
    @PutMapping("/profile")
    public Result<UserVo> updateProfile(@RequestBody Map<String, String> body) {
        return Result.ok(userService.updateProfile(UserContext.requireUserId(),
                body.get("nickname"), body.get("avatar"), body.get("bio")));
    }

    /** 保存每轮复习单词数偏好（跨设备持久化） */
    @PutMapping("/review-batch")
    public Result<UserVo> saveReviewBatchSize(@RequestBody Map<String, Integer> body) {
        return Result.ok(userService.saveReviewBatchSize(UserContext.requireUserId(), body.get("size")));
    }

    /** 保存每日学习目标（5-200 之间，跨设备持久化） */
    @PutMapping("/daily-goal")
    public Result<UserVo> saveDailyGoal(@RequestBody Map<String, Integer> body) {
        return Result.ok(userService.saveDailyGoal(UserContext.requireUserId(), body.get("goal")));
    }

    /** 发送邮箱验证码（需图形验证已通过，captchaId 需已验证）；返回今日剩余发送次数 */
    @PostMapping("/email-code")
    public Result<Integer> sendEmailCode(@RequestBody Map<String, String> body,
                                         jakarta.servlet.http.HttpServletRequest request) {
        return Result.ok(userService.sendEmailCode(UserContext.requireUserId(),
                body.get("type"), body.get("email"),
                pickCaptchaId(body, request)));
    }

    /** 注册时发送邮箱验证码（公开接口，无需登录）；返回今日剩余发送次数 */
    @PostMapping("/email-code-public")
    public Result<Integer> sendRegisterEmailCode(@RequestBody Map<String, String> body,
                                                  jakarta.servlet.http.HttpServletRequest request) {
        return Result.ok(userService.sendEmailCode(null, "bind_email", body.get("email"),
                pickCaptchaId(body, request)));
    }

    /** 忘记密码发送验证码（公开接口）：验证码只能发到已注册邮箱 */
    @PostMapping("/email-code-reset")
    public Result<Integer> sendResetPasswordEmailCode(@RequestBody Map<String, String> body,
                                                      jakarta.servlet.http.HttpServletRequest request) {
        return Result.ok(userService.sendEmailCode(null, "reset_password", body.get("email"),
                pickCaptchaId(body, request)));
    }

    /** 忘记密码重置（公开接口，凭注册邮箱+邮箱验证码） */
    @PostMapping("/reset-password")
    public Result<UserVo> resetPassword(@RequestBody Map<String, String> body) {
        return Result.ok(userService.resetPassword(body.get("email"), body.get("code"),
                body.get("newPassword")));
    }

    /** captchaId 优先从 body 读，其次 cookie，最后 X-Captcha-Id header（axios 拦截器自动塞） */
    private static String pickCaptchaId(Map<String, String> body, jakarta.servlet.http.HttpServletRequest req) {
        String a = body.get("captchaId");
        if (a != null && !a.isBlank()) return a;
        if (req.getCookies() != null) {
            for (jakarta.servlet.http.Cookie c : req.getCookies()) {
                if ("captcha_id".equals(c.getName())) return c.getValue();
            }
        }
        return req.getHeader("X-Captcha-Id");
    }

    /** 换绑邮箱（需验证码） */
    @PutMapping("/email")
    public Result<UserVo> updateEmail(@RequestBody Map<String, String> body) {
        return Result.ok(userService.updateEmail(UserContext.requireUserId(),
                body.get("email"), body.get("code")));
    }

    /** 修改密码（需原密码+邮箱验证码） */
    @PutMapping("/password")
    public Result<UserVo> changePassword(@RequestBody Map<String, String> body) {
        return Result.ok(userService.changePassword(UserContext.requireUserId(),
                body.get("oldPassword"), body.get("newPassword"), body.get("code")));
    }

    /** 注销账号（需邮箱验证码，删除全部数据，邮箱冻结 7 天） */
    @DeleteMapping("/account")
    public Result<Void> deleteAccount(@RequestBody Map<String, String> body) {
        userService.deleteAccount(UserContext.requireUserId(), body.get("code"));
        return Result.ok();
    }

    /** 获取通知偏好（今日复习提醒 / AI额度提醒） */
    @GetMapping("/notify-settings")
    public Result<Map<String, Boolean>> getNotifySettings() {
        return Result.ok(userService.getNotifySettings(UserContext.requireUserId()));
    }

    /** 保存通知偏好（今日复习提醒 / AI额度提醒） */
    @PutMapping("/notify-settings")
    public Result<Map<String, Boolean>> saveNotifySettings(@RequestBody Map<String, Boolean> body) {
        return Result.ok(userService.saveNotifySettings(UserContext.requireUserId(),
                body == null ? null : body.get("notifyReview"),
                body == null ? null : body.get("notifyQuota")));
    }

    /** 上传头像 */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择头像文件");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        boolean valid = original.endsWith(".jpg") || original.endsWith(".jpeg")
                || original.endsWith(".png") || original.endsWith(".gif");
        if (!valid) {
            return Result.fail("仅支持 jpg/png/gif 格式");
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            return Result.fail("头像大小不能超过 2MB");
        }
        try {
            String url = ossService.uploadAvatar(file, UserContext.requireUserId());
            return Result.ok(url);
        } catch (Exception e) {
            log.error("头像上传失败", e);
            return Result.fail("头像上传失败");
        }
    }
}
