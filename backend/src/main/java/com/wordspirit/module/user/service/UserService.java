package com.wordspirit.module.user.service;

import com.wordspirit.module.user.dto.LoginReq;
import com.wordspirit.module.user.dto.RegisterReq;
import com.wordspirit.module.user.dto.UserVo;

/**
 * 用户服务
 */
public interface UserService {

    /** 注册（需邮箱 + 邮箱验证码；图形验证码已在独立接口校验） */
    UserVo register(RegisterReq req, String email, String emailCode);

    /** 登录 */
    UserVo login(LoginReq req);

    /** 查询当前登录用户信息 */
    UserVo current(Long userId);

    /** 退出登录 */
    void logout(Long userId, String token);

    /** 记录一次学习动作（今日单词数+1、连续天数维护） */
    void recordStudy(Long userId);

    /** 更新个人资料（昵称/头像/简介） */
    UserVo updateProfile(Long userId, String nickname, String avatar, String bio);

    /** 保存每轮复习单词数偏好（跨设备持久化） */
    UserVo saveReviewBatchSize(Long userId, Integer size);

    /** 保存每日学习目标（跨设备持久化，5-200） */
    UserVo saveDailyGoal(Long userId, Integer goal);

    /** 发送邮箱验证码（需先过图形验证，captchaId 已验证）；返回今日剩余发送次数 */
    Integer sendEmailCode(Long userId, String type, String email, String captchaId);

    /** 换绑邮箱（需验证码） */
    UserVo updateEmail(Long userId, String newEmail, String code);

    /** 修改密码（需原密码+邮箱验证码） */
    UserVo changePassword(Long userId, String oldPassword, String newPassword, String code);

    /** 忘记密码重置（公开接口，凭注册邮箱+邮箱验证码，无需原密码） */
    UserVo resetPassword(String email, String code, String newPassword);

    /** 注销账号（需邮箱验证码，删除全部业务数据，邮箱冻结 7 天） */
    void deleteAccount(Long userId, String code);

    /**
     * 获取当前用户的通知偏好（复习提醒、AI额度提醒）
     */
    java.util.Map<String, Boolean> getNotifySettings(Long userId);

    /**
     * 保存当前用户的通知偏好（复习提醒、AI额度提醒）
     */
    java.util.Map<String, Boolean> saveNotifySettings(Long userId, Boolean notifyReview, Boolean notifyQuota);

    /**
     * 把单词加入"已掌握"集合（去重）。新词学习点击"认识"时调用，
     * 该集合持久化到 sys_user.mastered_words（JSON），不受生词本清理影响。
     * @return true 表示本次是新加入，false 表示已存在
     */
    boolean addMasteredWord(Long userId, String word);
}
