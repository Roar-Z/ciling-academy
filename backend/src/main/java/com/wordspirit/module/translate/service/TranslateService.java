package com.wordspirit.module.translate.service;

import com.wordspirit.module.translate.dto.TranslateReq;
import com.wordspirit.module.translate.dto.TranslateResp;

/**
 * 翻译服务
 */
public interface TranslateService {
    TranslateResp translate(Long userId, TranslateReq req);
}