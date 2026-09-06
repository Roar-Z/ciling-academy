package com.wordspirit.module.paper.service;

import com.wordspirit.common.PageResult;
import com.wordspirit.module.paper.dto.SubmitReq;
import com.wordspirit.module.paper.dto.SubmitResult;
import com.wordspirit.module.paper.entity.ExercisePaper;
import com.wordspirit.module.paper.entity.ExerciseQuestion;

import java.util.List;
import java.util.Map;

/**
 * 练习试卷服务
 */
public interface ExerciseService {

    /** AI 试卷导入（严格 Schema 校验，非法则提示重新生成） */
    ExercisePaper importFromAi(Long userId, String jsonData);

    /** 试卷分页列表 */
    Object list(Long userId, String keyword, long page, long size);

    /** 试卷详情（题目列表，可带答案） */
    Map<String, Object> detail(Long userId, Long paperId, boolean withAnswer);

    /** 交卷批改（Java 后端判分，绝不交给AI） */
    SubmitResult submit(Long userId, SubmitReq req);

    /** 最近一次作答结果回看 */
    SubmitResult lastResult(Long userId, Long paperId);

    /** 错题收录（分页） */
    PageResult<Map<String, Object>> wrongList(Long userId, String qType, long page, long size);

    /** 从错题本移除一道错题（软移除，不删题目本身） */
    void removeWrong(Long userId, Long questionId);

    /** 批量从错题本移除（整页选择删除） */
    void removeWrongBatch(Long userId, List<Long> questionIds);

    /** 删除试卷 */
    void remove(Long userId, Long paperId);
}
