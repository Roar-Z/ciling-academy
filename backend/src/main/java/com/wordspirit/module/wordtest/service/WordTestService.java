package com.wordspirit.module.wordtest.service;

import com.wordspirit.module.wordtest.dto.FinishTestResp;
import com.wordspirit.module.wordtest.dto.HistoryDetailDto;
import com.wordspirit.module.wordtest.dto.HistoryItemDto;
import com.wordspirit.module.wordtest.dto.StartTestReq;
import com.wordspirit.module.wordtest.dto.StartTestResp;

import java.util.List;

public interface WordTestService {

    /**
     * 开启一次测验
     * - source = due  ：从生词本取今日待复习词
     * - source = new  ：从生词本取最近学习的词（不足用词典兜底）
     * - reuseBatchId ≠ null：复用某一轮的词表开新一轮（巩固测验）
     */
    StartTestResp start(Long userId, StartTestReq req);

    /** 提交一道题答案：判定 + 更新熟悉度/EB 复习计划 + 记录 */
    void submitAnswer(Long userId, Long batchId, Long wordId, boolean correct, Integer costMs, String userAnswer);

    /** 完成批次：返回统计与错题 */
    FinishTestResp finish(Long userId, Long batchId);

    /** 历史轮次列表（已完成的，按时间倒序，带第几轮序号） */
    List<HistoryItemDto> history(Long userId);

    /** 某一轮详情（逐题单词 + 用户作答 + 对错），用于回看 */
    HistoryDetailDto historyDetail(Long userId, Long batchId);
}