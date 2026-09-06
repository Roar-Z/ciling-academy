package com.wordspirit.module.game.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wordspirit.common.BusinessException;
import com.wordspirit.common.PageResult;
import com.wordspirit.common.ResultCode;
import com.wordspirit.module.game.dto.GameRecordReq;
import com.wordspirit.module.game.entity.GameRecord;
import com.wordspirit.module.game.mapper.GameRecordMapper;
import com.wordspirit.module.game.service.GameService;
import com.wordspirit.module.shop.entity.UserCoin;
import com.wordspirit.module.shop.mapper.UserCoinMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {

    /** 单局金币上限 */
    private static final int COIN_MAX = 50;

    /** 单词快跑限时局：前端限时 120 秒，后端允许 300 秒（含网络与动画缓冲） */
    private static final int GAME6_DURATION_LIMIT = 300;

    /**
     * 每日小游戏金币收益上限：超出后本局不再发放金币（成绩与记录仍正常保存）。
     * 与任务金币合计约 370/天，保证商城装扮需要按周/按月积累，维持长期目标感。
     */
    private static final int DAILY_GAME_COIN_CAP = 150;

    private final GameRecordMapper gameRecordMapper;
    private final UserCoinMapper userCoinMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveRecord(Long userId, GameRecordReq req) {
        // 限时游戏服务端校验：超时成绩无效
        if ("game6".equals(req.getGameId())) {
            int duration = req.getDurationSec() == null ? 0 : req.getDurationSec();
            if (duration > GAME6_DURATION_LIMIT) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "本局用时已超出限时（" + GAME6_DURATION_LIMIT + " 秒），成绩无效");
            }
        }
        int total = req.getTotalCount() == null ? 0 : req.getTotalCount();
        int correct = req.getCorrectCount() == null ? 0 : req.getCorrectCount();
        BigDecimal rate = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(correct * 100.0 / total).setScale(2, RoundingMode.HALF_UP);

        // 金币由后端按正确率计算：正确率%×0.4 + 满分奖励10，上限50
        int coins = (int) Math.min(COIN_MAX,
                Math.floor(rate.doubleValue() * 0.4) + (total > 0 && correct == total ? 10 : 0));
        coins = Math.max(coins, req.getScore() != null && req.getScore() > 0 ? 1 : 0);

        // 每日游戏金币上限：今日已赚满 DAILY_GAME_COIN_CAP 后本局不再发币
        int earnedToday = sumTodayCoins(userId);
        int grant = Math.max(0, Math.min(coins, DAILY_GAME_COIN_CAP - earnedToday));

        // 保存记录
        GameRecord record = new GameRecord();
        record.setUserId(userId);
        record.setGameId(req.getGameId());
        record.setGameName(StrUtil.isNotBlank(req.getGameName()) ? req.getGameName() : req.getGameId());
        record.setScore(req.getScore() == null ? 0 : req.getScore());
        record.setCoins(grant);
        record.setCorrectCount(correct);
        record.setTotalCount(total);
        record.setCorrectRate(rate);
        record.setDurationSec(req.getDurationSec() == null ? 0 : req.getDurationSec());
        gameRecordMapper.insert(record);

        // 发放金币
        UserCoin coin = getUserCoin(userId);
        UserCoin upd = new UserCoin();
        upd.setId(coin.getId());
        upd.setCoinBalance((coin.getCoinBalance() == null ? 0 : coin.getCoinBalance()) + grant);
        upd.setTotalEarned((coin.getTotalEarned() == null ? 0 : coin.getTotalEarned()) + grant);
        userCoinMapper.updateById(upd);

        Map<String, Object> result = new HashMap<>();
        result.put("coins", grant);
        result.put("correctRate", rate);
        result.put("recordId", record.getId());
        result.put("coinBalance", (coin.getCoinBalance() == null ? 0 : coin.getCoinBalance()) + grant);
        return result;
    }

    /** 今日（服务器自然日）已通过小游戏获得的金币总数 */
    private int sumTodayCoins(Long userId) {
        LocalDateTime dayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        List<GameRecord> today = gameRecordMapper.selectList(new LambdaQueryWrapper<GameRecord>()
                .eq(GameRecord::getUserId, userId)
                .ge(GameRecord::getCreatedAt, dayStart)
                .select(GameRecord::getCoins));
        int sum = 0;
        for (GameRecord r : today) {
            sum += r.getCoins() == null ? 0 : r.getCoins();
        }
        return sum;
    }

    @Override
    public Object myRecords(Long userId, String gameId, long page, long size) {
        LambdaQueryWrapper<GameRecord> wrapper = new LambdaQueryWrapper<GameRecord>()
                .eq(GameRecord::getUserId, userId);
        if (StrUtil.isNotBlank(gameId)) {
            wrapper.eq(GameRecord::getGameId, gameId);
        }
        wrapper.orderByDesc(GameRecord::getCreatedAt);
        return PageResult.of(gameRecordMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @Override
    public List<Map<String, Object>> rank(String gameId, int limit) {
        List<GameRecord> top = gameRecordMapper.selectList(new LambdaQueryWrapper<GameRecord>()
                .eq(StrUtil.isNotBlank(gameId), GameRecord::getGameId, gameId)
                .orderByDesc(GameRecord::getScore)
                .last("LIMIT " + limit));
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            GameRecord r = top.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("rank", i + 1);
            item.put("userId", r.getUserId());
            item.put("gameName", r.getGameName());
            item.put("score", r.getScore());
            item.put("correctRate", r.getCorrectRate());
            item.put("createdAt", r.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> myStats(Long userId, String gameId) {
        List<GameRecord> records = gameRecordMapper.selectList(new LambdaQueryWrapper<GameRecord>()
                .eq(GameRecord::getUserId, userId)
                .eq(StrUtil.isNotBlank(gameId), GameRecord::getGameId, gameId));
        int best = 0;
        int totalCount = records.size();
        double sumRate = 0;
        for (GameRecord r : records) {
            best = Math.max(best, r.getScore() == null ? 0 : r.getScore());
            sumRate += r.getCorrectRate() == null ? 0 : r.getCorrectRate().doubleValue();
        }
        double avgRate = totalCount == 0 ? 0 : BigDecimal.valueOf(sumRate / totalCount)
                .setScale(2, RoundingMode.HALF_UP).doubleValue();
        Map<String, Object> result = new HashMap<>();
        result.put("bestScore", best);
        result.put("playCount", totalCount);
        result.put("avgCorrectRate", avgRate);
        result.put("lastPlayAt", totalCount == 0 ? null : records.get(0).getCreatedAt());
        return result;
    }

    /** 获取用户金币账户（不存在则创建） */
    private UserCoin getUserCoin(Long userId) {
        UserCoin coin = userCoinMapper.selectOne(new LambdaQueryWrapper<UserCoin>()
                .eq(UserCoin::getUserId, userId));
        if (coin == null) {
            coin = new UserCoin();
            coin.setUserId(userId);
            coin.setCoinBalance(0);
            coin.setTotalEarned(0);
            coin.setTotalSpent(0);
            userCoinMapper.insert(coin);
        }
        return coin;
    }
}
