package com.wordspirit.module.user.service;

import java.util.Map;

/**
 * 用户等级服务：成长值（经验）计算、等级信息、升级奖励领取
 */
public interface LevelService {

    /** 根据用户学习数据计算成长值（经验值） */
    int expOf(Integer totalWords, Integer totalMastered, Integer studyDays, Integer aiUsedTotal);

    /**
     * 等级详情（个人中心弹窗 + 徽章红点）
     * 包含：等级/称号/经验进度/还差多少升级/已领等级/可领奖励等
     */
    Map<String, Object> levelInfo(Long userId);

    /** 领取指定等级的升级奖励（金币 + 里程碑AI额度），每级仅一次 */
    Map<String, Object> claimReward(Long userId, int level);
}
