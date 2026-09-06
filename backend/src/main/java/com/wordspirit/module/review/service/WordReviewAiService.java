package com.wordspirit.module.review.service;

import com.wordspirit.module.review.dto.SaveReviewReq;
import com.wordspirit.module.review.entity.AiReviewContent;

import java.util.List;

/**
 * 生词巩固服务
 */
public interface WordReviewAiService {

    /** 保存巩固包（严格 Schema 校验，非法返回提示重新生成） */
    AiReviewContent save(Long userId, SaveReviewReq req);

    /** 巩固包列表 */
    List<AiReviewContent> list(Long userId);

    /** 巩固包详情 */
    AiReviewContent detail(Long userId, Long id);

    /** 删除巩固包 */
    void remove(Long userId, Long id);
}
