package com.wordspirit.module.learnround.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wordspirit.common.BusinessException;
import com.wordspirit.common.ResultCode;
import com.wordspirit.module.learnround.dto.FinishLearnRoundReq;
import com.wordspirit.module.learnround.dto.LearnRoundDetailDto;
import com.wordspirit.module.learnround.dto.LearnRoundDto;
import com.wordspirit.module.learnround.entity.LearnRound;
import com.wordspirit.module.learnround.entity.LearnRoundItem;
import com.wordspirit.module.learnround.mapper.LearnRoundItemMapper;
import com.wordspirit.module.learnround.mapper.LearnRoundMapper;
import com.wordspirit.module.learnround.service.LearnRoundService;
import com.wordspirit.module.user.entity.User;
import com.wordspirit.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearnRoundServiceImpl implements LearnRoundService {

    private final LearnRoundMapper roundMapper;
    private final LearnRoundItemMapper itemMapper;
    private final UserMapper userMapper;
    /**
     * 自注入（@Lazy 避免循环依赖），用于本类内调用走 Spring 代理，
     * 触发 @Transactional 拦截；finishRound 入口重试时通过它开新事务。
     */
    @Autowired
    @Lazy
    private LearnRoundService self;

    /** 用户保留的学习轮次上限 —— 累计达到此值时一键清空全部（含当前轮），轮次计数归 1 */
    private static final int MAX_ROUNDS_PER_USER = 10;

@Override
public LearnRoundDto finishRound(Long userId, FinishLearnRoundReq req) {
    // 死锁重试：并发 finishRound + 清空全部在同一事务里会触发 MySQL 死锁
    // (DELETE user_id=? 与并发 INSERT 互锁)。重试 3 次，每次间隔递增。
    int maxAttempts = 3;
    long backoffMs = 80L;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        try {
            // 通过 self 代理调用，确保每次都开新事务（避免上一次失败事务的脏状态）
            return self.finishRoundInTx(userId, req);
        } catch (DeadlockLoserDataAccessException e) {
            log.warn("[finishRound] 死锁重试 userId={} attempt={}/{} err={}", userId, attempt, maxAttempts, e.getMessage());
            if (attempt >= maxAttempts) break;
            try { Thread.sleep(backoffMs * attempt); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    throw new BusinessException(ResultCode.SERVER_ERROR,
                    "学习轮次提交失败（数据库忙），请稍后再试");
}

    /**
     * 单次事务体：插入新轮 + 必要时清空全部并把当前轮作为第 1 轮重新插入。
     * 事务内执行 INSERT/DELETE，并发下可能触发死锁，由 finishRound 入口统一重试。
     * 通过 self 代理调用，确保 @Transactional 生效。
     */
    @Override
    @Transactional
    public LearnRoundDto finishRoundInTx(Long userId, FinishLearnRoundReq req) {
        LearnRound round = new LearnRound();
        round.setUserId(userId);
        round.setSource(req.getSource());
        round.setCount(req.getCount());
        round.setMasteredCount(req.getMasteredCount());
        round.setStartedAt(LocalDateTime.now());
        round.setFinishedAt(LocalDateTime.now());
        roundMapper.insert(round);

        List<FinishLearnRoundReq.WordItem> ws = req.getWords();
        if (ws != null) {
            for (FinishLearnRoundReq.WordItem w : ws) {
                LearnRoundItem it = new LearnRoundItem();
                it.setRoundId(round.getId());
                it.setUserId(userId);
                it.setWord(w.getWord());
                it.setWordId(w.getWordId());
                it.setPhonetic(w.getPhonetic());
                it.setMeaning(w.getMeaning());
                it.setExample(w.getExample());
                it.setExampleCn(w.getExampleCn());
                it.setIsMastered(w.getIsMastered() == null ? 0 : w.getIsMastered());
                itemMapper.insert(it);
            }
        }

        // 自动清理：累计达到 MAX_ROUNDS_PER_USER 轮时一键清空全部（含当前轮），
        // 然后再把当前轮作为第 1 轮重新插入，轮次计数归 1。
        Long currentCount = roundMapper.selectCount(new LambdaQueryWrapper<LearnRound>().eq(LearnRound::getUserId, userId));
        boolean cleared = false;
        if (currentCount != null && currentCount >= MAX_ROUNDS_PER_USER) {
            // 清空前先把"已掌握"词回填到 mastered_words（含当前轮），
            // 防止清空后这些词被 randomUnlearned 重新分配。
            backfillMasteredWords(userId);
            // 清空当前用户所有 learn_round + learn_round_item
            itemMapper.delete(new LambdaQueryWrapper<LearnRoundItem>()
                    .eq(LearnRoundItem::getUserId, userId));
            roundMapper.delete(new LambdaQueryWrapper<LearnRound>()
                    .eq(LearnRound::getUserId, userId));
            // 把刚插入的 round 也清掉，下面重新插入作为"第 1 轮"
            itemMapper.delete(new LambdaQueryWrapper<LearnRoundItem>()
                    .eq(LearnRoundItem::getRoundId, round.getId()));
            roundMapper.deleteById(round.getId());
            cleared = true;
        }

        LearnRoundDto dto = new LearnRoundDto();
        if (cleared) {
            // 重新插入当前轮，作为"第 1 轮"
            LearnRound fresh = new LearnRound();
            fresh.setUserId(userId);
            fresh.setSource(req.getSource());
            fresh.setCount(req.getCount());
            fresh.setMasteredCount(req.getMasteredCount());
            fresh.setStartedAt(LocalDateTime.now());
            fresh.setFinishedAt(LocalDateTime.now());
            roundMapper.insert(fresh);
            round.setId(fresh.getId());
            // 重新写入 items（指向新 roundId）
            if (ws != null) {
                for (FinishLearnRoundReq.WordItem w : ws) {
                    LearnRoundItem it = new LearnRoundItem();
                    it.setRoundId(fresh.getId());
                    it.setUserId(userId);
                    it.setWord(w.getWord());
                    it.setWordId(w.getWordId());
                    it.setPhonetic(w.getPhonetic());
                    it.setMeaning(w.getMeaning());
                    it.setExample(w.getExample());
                    it.setExampleCn(w.getExampleCn());
                    it.setIsMastered(w.getIsMastered() == null ? 0 : w.getIsMastered());
                    itemMapper.insert(it);
                }
            }
            dto.setRoundId(fresh.getId());
            dto.setRoundNo(1);
        } else {
            dto.setRoundId(round.getId());
            Long no = roundMapper.selectCount(new LambdaQueryWrapper<LearnRound>().eq(LearnRound::getUserId, userId));
            dto.setRoundNo(no == null ? 1 : no.intValue());
        }
        dto.setSource(round.getSource());
        dto.setCount(round.getCount());
        dto.setMasteredCount(round.getMasteredCount());
        dto.setFinishedAt(round.getFinishedAt());
        return dto;
    }

    @Override
    public List<LearnRoundDto> history(Long userId) {
        List<LearnRound> all = roundMapper.selectList(new LambdaQueryWrapper<LearnRound>()
                .eq(LearnRound::getUserId, userId)
                .orderByDesc(LearnRound::getFinishedAt));
        int total = all.size();
        List<LearnRoundDto> list = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            LearnRound r = all.get(i);
            LearnRoundDto dto = new LearnRoundDto();
            dto.setRoundId(r.getId());
            dto.setRoundNo(total - i);   // 倒序：最新一条为第 total 轮
            dto.setSource(r.getSource());
            dto.setCount(r.getCount());
            dto.setMasteredCount(r.getMasteredCount());
            dto.setFinishedAt(r.getFinishedAt());
            list.add(dto);
        }
        return list;
    }

    @Override
    public LearnRoundDetailDto detail(Long userId, Long roundId) {
        LearnRound round = roundMapper.selectById(roundId);
        if (round == null || !round.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学习轮次不存在");
        }
        // 计算轮次序号
        List<LearnRound> all = roundMapper.selectList(new LambdaQueryWrapper<LearnRound>()
                .eq(LearnRound::getUserId, userId)
                .orderByDesc(LearnRound::getFinishedAt));
        int total = all.size();
        int roundNo = 1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(roundId)) {
                roundNo = total - i;
                break;
            }
        }

        LearnRoundDetailDto dto = new LearnRoundDetailDto();
        dto.setRoundId(round.getId());
        dto.setRoundNo(roundNo);
        dto.setSource(round.getSource());
        dto.setCount(round.getCount());
        dto.setMasteredCount(round.getMasteredCount());
        dto.setFinishedAt(round.getFinishedAt());

        List<LearnRoundItem> items = itemMapper.selectList(new LambdaQueryWrapper<LearnRoundItem>()
                .eq(LearnRoundItem::getRoundId, roundId)
                .orderByAsc(LearnRoundItem::getId));
        List<LearnRoundDetailDto.LearnRoundWordDto> words = new ArrayList<>();
        for (LearnRoundItem it : items) {
            LearnRoundDetailDto.LearnRoundWordDto w = new LearnRoundDetailDto.LearnRoundWordDto();
            w.setWordId(it.getWordId());
            w.setWord(it.getWord());
            w.setPhonetic(it.getPhonetic());
            w.setMeaning(it.getMeaning());
            w.setIsMastered(it.getIsMastered());
            words.add(w);
        }
        dto.setWords(words);
        return dto;
    }

    @Override
    @Transactional
    public void deleteRound(Long userId, Long roundId) {
        LearnRound r = roundMapper.selectById(roundId);
        if (r == null || !r.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学习轮次不存在");
        }
        itemMapper.delete(new LambdaQueryWrapper<LearnRoundItem>()
                .eq(LearnRoundItem::getRoundId, roundId));
        roundMapper.deleteById(roundId);
    }

    /** 把用户当前轮次中 is_mastered=1 的去重词回填到 mastered_words（仅当 mastered_words 为空时执行一次） */
    private void backfillMasteredWords(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) return;
        // 已有内容说明已经回填过（或在用），跳过
        if (StrUtil.isNotBlank(u.getMasteredWords())) return;
        List<String> words = itemMapper.selectObjs(new LambdaQueryWrapper<LearnRoundItem>()
                .select(LearnRoundItem::getWord)
                .eq(LearnRoundItem::getUserId, userId)
                .eq(LearnRoundItem::getIsMastered, 1));
        if (words == null || words.isEmpty()) return;
        Set<String> set = new LinkedHashSet<>();
        for (String w : words) {
            if (StrUtil.isNotBlank(w)) set.add(w.trim().toLowerCase(Locale.ROOT));
        }
        if (set.isEmpty()) return;
        User patch = new User();
        patch.setId(userId);
        patch.setMasteredWords(JSONUtil.toJsonStr(set));
        userMapper.updateById(patch);
    }
}
