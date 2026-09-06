package com.wordspirit.module.sentence.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wordspirit.module.sentence.dto.SentenceAddReq;
import com.wordspirit.module.sentence.entity.Sentence;

import java.util.Set;

/**
 * 句灵集服务
 */
public interface SentenceService {

    /** 分页查询我的句灵集（keyword 可为空） */
    Page<Sentence> list(Long userId, String keyword, long page, long size);

    /** 加入句灵集（同一用户同一原文同一方向已存在则直接返回旧记录） */
    Sentence add(Long userId, SentenceAddReq req);

    /** 删除（仅本人可删） */
    void remove(Long userId, Long id);

    /** 当前用户已收藏的原文集合（用于前端"已收藏"按钮状态） */
    Set<String> listCollectedOriginals(Long userId);
}