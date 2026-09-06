package com.wordspirit.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wordspirit.ai.AiQuotaService;
import com.wordspirit.common.BusinessException;
import com.wordspirit.common.ResultCode;
import com.wordspirit.module.notification.service.NotificationService;
import com.wordspirit.module.shop.entity.UserCoin;
import com.wordspirit.module.shop.mapper.UserCoinMapper;
import com.wordspirit.module.user.entity.User;
import com.wordspirit.module.user.entity.UserLevelReward;
import com.wordspirit.module.user.mapper.UserLevelRewardMapper;
import com.wordspirit.module.user.mapper.UserMapper;
import com.wordspirit.module.user.service.LevelService;
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
import java.util.Set;

/**
 * 用户等级服务实现
 *
 * <p>体系设计（对标国内主流经验值等级）：
 * <ul>
 *   <li>成长值 = 学新词×2 + 掌握词×3 + 连续学习天数×15 + AI探索×2（全部复用已有持久化数据，实时累计）</li>
 *   <li>20 级称号体系，升级曲线前快后慢，长线追求</li>
 *   <li>每级首次达成可领金币奖励，Lv5/10/15/20 里程碑额外 +5 次永久 AI 额度</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LevelServiceImpl implements LevelService {

    private final UserMapper userMapper;
    private final UserLevelRewardMapper levelRewardMapper;
    private final UserCoinMapper coinMapper;
    private final AiQuotaService aiQuotaService;
    private final NotificationService notificationService;

    /** 最大等级 */
    public static final int MAX_LEVEL = 20;

    /** 各等级累计成长值门槛（下标 = 等级-1，前快后慢曲线） */
    private static final int[] LEVEL_EXP = {
            0,      // Lv1
            100,    // Lv2
            250,    // Lv3
            450,    // Lv4
            700,    // Lv5
            1000,   // Lv6
            1400,   // Lv7
            1900,   // Lv8
            2500,   // Lv9
            3200,   // Lv10
            4000,   // Lv11
            5000,   // Lv12
            6200,   // Lv13
            7600,   // Lv14
            9200,   // Lv15
            11000,  // Lv16
            13000,  // Lv17
            15500,  // Lv18
            18500,  // Lv19
            22000   // Lv20
    };

    /** 各等级称号（下标 = 等级-1）：{名称, 图标} */
    private static final String[][] LEVEL_TITLES = {
            {"初入词林", "🌱"}, {"拾词新芽", "🌿"}, {"勤学学徒", "✏️"}, {"词汇行者", "🚶"},
            {"积词能手", "⚡"}, {"词海拾贝", "🐚"}, {"记词巧匠", "🛠️"}, {"词苑园丁", "🌷"},
            {"词海舵手", "🧭"}, {"词汇骑士", "🛡️"}, {"博词学者", "🎓"}, {"词阵先锋", "🚩"},
            {"词林高手", "🥋"}, {"千词法师", "🔮"}, {"词灵使徒", "🕊️"}, {"万词宗师", "🏛️"},
            {"词林泰斗", "⛰️"}, {"词灵贤者", "🦉"}, {"词界传奇", "🌟"}, {"词灵至臻", "👑"}
    };

    /** 里程碑等级 → 额外永久 AI 奖励次数 */
    private static final Map<Integer, Integer> MILESTONE_AI = Map.of(5, 5, 10, 5, 15, 5, 20, 5);

    @Override
    public int expOf(Integer totalWords, Integer totalMastered, Integer studyDays, Integer aiUsedTotal) {
        int words = nz(totalWords) * 2;
        int mastered = nz(totalMastered) * 3;
        int days = nz(studyDays) * 15;
        int ai = nz(aiUsedTotal) * 2;
        return words + mastered + days + ai;
    }

    @Override
    public Map<String, Object> levelInfo(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        int exp = expOf(u.getTotalWords(), u.getTotalMastered(), u.getStudyDays(), u.getAiUsedTotal());

        // 定位当前等级（成长值可能落在最高档之上，按满级兜底）
        int level = 1;
        for (int i = LEVEL_EXP.length - 1; i >= 0; i--) {
            if (exp >= LEVEL_EXP[i]) {
                level = i + 1;
                break;
            }
        }
        boolean isMax = level >= MAX_LEVEL;
        int curStart = LEVEL_EXP[level - 1];
        int nextNeed = isMax ? LEVEL_EXP[MAX_LEVEL - 1] : LEVEL_EXP[level];
        int remain = Math.max(0, nextNeed - exp);
        int percent = isMax ? 100 : (int) Math.min(100L, (long) (exp - curStart) * 100 / Math.max(1, nextNeed - curStart));

        // 已领取的等级奖励
        Set<Integer> claimed = claimedLevels(userId);
        List<Integer> claimable = new ArrayList<>();
        List<Map<String, Object>> claimableDetail = new ArrayList<>();
        // 从 Lv2 开始每级都有奖励；已达成的未领取 → 可领
        for (int lv = 2; lv <= level; lv++) {
            if (!claimed.contains(lv)) {
                claimable.add(lv);
                claimableDetail.add(rewardChip(lv, "claimable"));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("level", level);
        result.put("title", LEVEL_TITLES[level - 1][0]);
        result.put("titleIcon", LEVEL_TITLES[level - 1][1]);
        result.put("maxLevel", MAX_LEVEL);
        result.put("isMax", isMax);
        result.put("exp", exp);
        result.put("levelStartExp", curStart);
        result.put("nextLevelExp", nextNeed);
        result.put("remainExp", remain);
        result.put("percent", percent);
        result.put("claimedLevels", claimed);
        result.put("claimableLevels", claimable);
        result.put("claimableDetail", claimableDetail);
        result.put("hasClaimable", !claimable.isEmpty());
        // 等级奖励一览（弹窗时间轴用）：Lv2 起，含里程碑 AI 奖励
        result.put("rewardList", buildRewardList(level, claimed));
        // 经验获取途径（前端展示引导）
        result.put("expRules", expRules());
        return result;
    }

    @Override
    public Map<String, Object> claimReward(Long userId, int level) {
        if (level < 2 || level > MAX_LEVEL) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的奖励等级");
        }
        Map<String, Object> info = levelInfo(userId);
        int curLevel = (int) info.get("level");
        if (level > curLevel) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "还未达成 Lv." + level + "，继续加油！");
        }

        int coinAward = coinOf(level);
        int aiAward = MILESTONE_AI.getOrDefault(level, 0);
        UserLevelReward claim = new UserLevelReward();
        claim.setUserId(userId);
        claim.setLevel(level);
        claim.setCoinAward(coinAward);
        claim.setAiAward(aiAward);
        claim.setClaimedAt(LocalDateTime.now());
        try {
            levelRewardMapper.insert(claim);
        } catch (DuplicateKeyException dup) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该等级奖励已领取过啦");
        }

        if (coinAward > 0) {
            addCoin(userId, coinAward);
        }
        if (aiAward > 0) {
            aiQuotaService.addBonus(userId, aiAward);
        }
        log.info("用户{}领取等级{}奖励：金币={}, AI={}", userId, level, coinAward, aiAward);

        // 系统通知：等级奖励到账
        StringBuilder content = new StringBuilder("Lv.").append(level).append(" 升级奖励到账：");
        if (coinAward > 0) content.append("+").append(coinAward).append(" 金币 ");
        if (aiAward > 0) content.append("+").append(aiAward).append(" 次词灵AI ");
        content.append("，向下一级进发吧～");
        notificationService.push(userId, "reward_level", "等级奖励已发放", content.toString().trim());

        // 返回最新等级信息（前端直接刷新弹窗）
        Map<String, Object> result = new HashMap<>(levelInfo(userId));
        result.put("coinAward", coinAward);
        result.put("aiAward", aiAward);
        return result;
    }

    /** 升级奖励金币：60 + 等级×20 */
    private int coinOf(int level) {
        return 60 + level * 20;
    }

    private Set<Integer> claimedLevels(Long userId) {
        return levelRewardMapper.selectList(new LambdaQueryWrapper<UserLevelReward>()
                        .eq(UserLevelReward::getUserId, userId))
                .stream()
                .map(UserLevelReward::getLevel)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** 弹窗奖励时间轴：Lv2 → Lv20 全量节点 + 状态 */
    private List<Map<String, Object>> buildRewardList(int curLevel, Set<Integer> claimed) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int lv = 2; lv <= MAX_LEVEL; lv++) {
            String status;
            if (claimed.contains(lv)) {
                status = "claimed";
            } else if (lv <= curLevel) {
                status = "claimable";
            } else {
                status = "locked";
            }
            list.add(rewardChip(lv, status));
        }
        return list;
    }

    private Map<String, Object> rewardChip(int level, String status) {
        Map<String, Object> chip = new LinkedHashMap<>();
        chip.put("level", level);
        chip.put("coin", coinOf(level));
        chip.put("ai", MILESTONE_AI.getOrDefault(level, 0));
        chip.put("milestone", MILESTONE_AI.containsKey(level));
        chip.put("status", status);
        return chip;
    }

    /** 经验获取途径说明（与 expOf 保持一致） */
    private List<Map<String, Object>> expRules() {
        List<Map<String, Object>> rules = new ArrayList<>();
        rules.add(Map.of("name", "学习新词", "desc", "每学 1 个新词", "exp", 2, "icon", "brain"));
        rules.add(Map.of("name", "掌握单词", "desc", "每掌握 1 个词", "exp", 3, "icon", "book-open"));
        rules.add(Map.of("name", "坚持学习", "desc", "连续学习每满 1 天", "exp", 15, "icon", "flame"));
        rules.add(Map.of("name", "AI 探索", "desc", "每使用 1 次词灵AI", "exp", 2, "icon", "sparkles"));
        return rules;
    }

    /** 金币入账（与活跃度奖励同模式） */
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

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
