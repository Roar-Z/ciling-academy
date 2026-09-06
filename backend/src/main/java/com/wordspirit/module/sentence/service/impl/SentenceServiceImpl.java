package com.wordspirit.module.sentence.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wordspirit.common.BusinessException;
import com.wordspirit.common.ResultCode;
import com.wordspirit.module.sentence.dto.SentenceAddReq;
import com.wordspirit.module.sentence.entity.Sentence;
import com.wordspirit.module.sentence.mapper.SentenceMapper;
import com.wordspirit.module.sentence.service.SentenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SentenceServiceImpl implements SentenceService {

    private final SentenceMapper sentenceMapper;

    @Override
    public Page<Sentence> list(Long userId, String keyword, long page, long size) {
        LambdaQueryWrapper<Sentence> wrapper = new LambdaQueryWrapper<Sentence>()
                .eq(Sentence::getUserId, userId);
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(Sentence::getOriginalText, keyword)
                    .or().like(Sentence::getTranslationText, keyword));
        }
        wrapper.orderByDesc(Sentence::getCreatedAt);
        return sentenceMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public Sentence add(Long userId, SentenceAddReq req) {
        String original = req.getOriginalText().trim();
        String direction = req.getDirection();
        // 同一用户同一原文同一方向已收藏则直接返回旧记录（幂等，避免重复收藏）
        Sentence exist = sentenceMapper.selectOne(new LambdaQueryWrapper<Sentence>()
                .eq(Sentence::getUserId, userId)
                .eq(Sentence::getOriginalText, original)
                .eq(Sentence::getDirection, direction)
                .last("LIMIT 1"));
        if (exist != null) return exist;
        Sentence s = new Sentence();
        s.setUserId(userId);
        s.setOriginalText(original);
        s.setTranslationText(req.getTranslationText().trim());
        s.setDirection(direction);
        s.setMode(req.getMode());
        // source 可选：默认 translate，长难句分析场景前端传 long_sentence
        s.setSource(StrUtil.isBlank(req.getSource()) ? "translate" : req.getSource().trim());
        s.setSentenceTranslation(req.getSentenceTranslation());
        s.setNormalWords(req.getNormalWords());
        s.setAnalysisJson(req.getAnalysisJson());
        s.setCreatedAt(LocalDateTime.now());
        sentenceMapper.insert(s);
        return s;
    }

    @Override
    public void remove(Long userId, Long id) {
        Sentence s = sentenceMapper.selectById(id);
        if (s == null || !s.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "句子不存在");
        }
        sentenceMapper.deleteById(id);
    }

    @Override
    public Set<String> listCollectedOriginals(Long userId) {
        List<Sentence> list = sentenceMapper.selectList(
                new LambdaQueryWrapper<Sentence>()
                        .eq(Sentence::getUserId, userId)
                        .select(Sentence::getOriginalText));
        Set<String> set = new HashSet<>();
        for (Sentence s : list) {
            String t = s.getOriginalText();
            if (StrUtil.isNotBlank(t)) set.add(t.trim());
        }
        return set;
    }
}