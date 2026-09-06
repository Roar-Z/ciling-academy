package com.wordspirit.module.learnround.service;

import com.wordspirit.module.learnround.dto.FinishLearnRoundReq;
import com.wordspirit.module.learnround.dto.LearnRoundDetailDto;
import com.wordspirit.module.learnround.dto.LearnRoundDto;

import java.util.List;

public interface LearnRoundService {

    /** 完成一轮学习：持久化本轮词，返回轮次序号（外层重试死锁） */
    LearnRoundDto finishRound(Long userId, FinishLearnRoundReq req);

    /** 单次事务体（@Transactional 包裹）—— 内部方法，外层通过 self 代理调用以触发事务 */
    LearnRoundDto finishRoundInTx(Long userId, FinishLearnRoundReq req);

    /** 历史轮次列表（按完成时间倒序，带第几轮序号） */
    List<LearnRoundDto> history(Long userId);

    /** 某一轮详情（逐词 + 是否掌握） */
    LearnRoundDetailDto detail(Long userId, Long roundId);

    /** 删除单个轮次（同时删 LearnRoundItem） */
    void deleteRound(Long userId, Long roundId);
}
