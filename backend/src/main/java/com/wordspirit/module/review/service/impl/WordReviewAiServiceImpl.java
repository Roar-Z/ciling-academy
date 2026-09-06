package com.wordspirit.module.review.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wordspirit.common.BusinessException;
import com.wordspirit.common.ResultCode;
import com.wordspirit.module.review.dto.SaveReviewReq;
import com.wordspirit.module.review.entity.AiReviewContent;
import com.wordspirit.module.review.mapper.AiReviewContentMapper;
import com.wordspirit.module.review.service.WordReviewAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 生词巩固服务实现
 *
 * 只有用户主动点击"保存这份助记内容"才会提交原始JSON到此接口；
 * 后端对 JSON 进行严格 Schema 校验，非法直接拒绝并提示重新生成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WordReviewAiServiceImpl implements WordReviewAiService {

    private final AiReviewContentMapper reviewMapper;

    @Override
    public AiReviewContent save(Long userId, SaveReviewReq req) {
        // 严格 Schema 校验
        JSONObject json = parseAndValidate(req.getJsonData());
        JSONArray wordList = json.getJSONArray("word_list");
        String title = json.getStr("group_title");
        if (StrUtil.isBlank(title)) {
            title = "生词巩固包";
        }

        List<String> words = new ArrayList<>();
        for (int i = 0; i < wordList.size(); i++) {
            JSONObject w = wordList.getJSONObject(i);
            String word = w.getStr("word");
            if (StrUtil.isNotBlank(word)) {
                words.add(word);
            }
        }

        AiReviewContent content = new AiReviewContent();
        content.setUserId(userId);
        content.setTitle(StrUtil.sub(title, 0, 100));
        content.setWords(String.join(",", words));
        content.setReviewJson(json.toString());
        reviewMapper.insert(content);
        log.info("用户{}保存生词巩固包 {}，共{}个单词", userId, title, words.size());
        return content;
    }

    @Override
    public List<AiReviewContent> list(Long userId) {
        return reviewMapper.selectList(new LambdaQueryWrapper<AiReviewContent>()
                .eq(AiReviewContent::getUserId, userId)
                .orderByDesc(AiReviewContent::getCreatedAt));
    }

    @Override
    public AiReviewContent detail(Long userId, Long id) {
        AiReviewContent content = reviewMapper.selectById(id);
        if (content == null || !content.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "巩固包不存在");
        }
        return content;
    }

    @Override
    public void remove(Long userId, Long id) {
        AiReviewContent content = reviewMapper.selectById(id);
        if (content == null || !content.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "巩固包不存在");
        }
        reviewMapper.deleteById(id);
    }

    /** 解析 + Schema 校验 */
    private JSONObject parseAndValidate(String jsonData) {
        JSONObject json;
        try {
            json = JSONUtil.parseObj(jsonData);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.AI_FORMAT_ERROR, "巩固包数据格式非法，请点击按钮重新生成");
        }
        JSONArray wordList = json.getJSONArray("word_list");
        if (wordList == null || wordList.isEmpty()) {
            throw new BusinessException(ResultCode.AI_FORMAT_ERROR, "巩固包缺少单词列表，请重新生成");
        }
        if (wordList.size() > 30) {
            throw new BusinessException(ResultCode.AI_FORMAT_ERROR, "巩固包单词数量异常，请重新生成");
        }
        for (int i = 0; i < wordList.size(); i++) {
            JSONObject w = wordList.getJSONObject(i);
            if (w == null || StrUtil.isBlank(w.getStr("word"))
                    || StrUtil.isBlank(w.getStr("cn_meaning"))) {
                throw new BusinessException(ResultCode.AI_FORMAT_ERROR, "巩固包内容不完整，请重新生成");
            }
        }
        return json;
    }
}
