package com.wordspirit.module.paper.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wordspirit.common.BusinessException;
import com.wordspirit.common.PageResult;
import com.wordspirit.common.ResultCode;
import com.wordspirit.module.dict.entity.DictWord;
import com.wordspirit.module.dict.mapper.DictWordMapper;
import com.wordspirit.module.paper.dto.AnswerItem;
import com.wordspirit.module.paper.dto.QuestionResult;
import com.wordspirit.module.paper.dto.SubmitReq;
import com.wordspirit.module.paper.dto.SubmitResult;
import com.wordspirit.module.paper.entity.ExercisePaper;
import com.wordspirit.module.paper.entity.ExerciseQuestion;
import com.wordspirit.module.paper.mapper.ExercisePaperMapper;
import com.wordspirit.module.paper.mapper.ExerciseQuestionMapper;
import com.wordspirit.module.paper.service.ExerciseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 练习试卷服务实现
 *
 * 判分规则（全部由 Java 后端完成）：
 * - 单选题：答案字符串精确匹配
 * - 拼写填空：忽略大小写与首尾空格
 * - 词义匹配：JSON 数组逐项比对
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExerciseServiceImpl implements ExerciseService {

    /** 允许的 5 种题型 */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "en2cn", "cn2en", "spell_fill", "context_choice", "match");
    /** 每题分值 */
    private static final int SCORE_PER_QUESTION = 10;

    private final ExercisePaperMapper paperMapper;
    private final ExerciseQuestionMapper questionMapper;
    private final DictWordMapper dictWordMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExercisePaper importFromAi(Long userId, String jsonData) {
        // 1. 解析并严格 Schema 校验
        JSONObject json;
        try {
            json = JSONUtil.parseObj(jsonData);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.AI_FORMAT_ERROR, "试卷数据格式非法，请点击按钮重新生成");
        }
        String paperName = json.getStr("paper_name");
        if (StrUtil.isBlank(paperName)) {
            throw new BusinessException(ResultCode.AI_FORMAT_ERROR, "试卷缺少名称，请重新生成");
        }
        JSONArray questionList = json.getJSONArray("question_list");
        if (questionList == null || questionList.size() < 1 || questionList.size() > 30) {
            throw new BusinessException(ResultCode.AI_FORMAT_ERROR, "题目数量异常（应为1-30题），请重新生成");
        }
        validateQuestions(questionList);

        // 2. 内容指纹查重：同一份试卷（题目内容一致）只允许导入一次
        String contentHash = paperContentHash(questionList);
        ExercisePaper existing = paperMapper.selectOne(new LambdaQueryWrapper<ExercisePaper>()
                .eq(ExercisePaper::getUserId, userId)
                .eq(ExercisePaper::getContentHash, contentHash)
                .last("LIMIT 1"));
        if (existing == null) {
            // 历史数据没有指纹，退化为「同名 + 同题量」判重
            existing = paperMapper.selectOne(new LambdaQueryWrapper<ExercisePaper>()
                    .eq(ExercisePaper::getUserId, userId)
                    .eq(ExercisePaper::getPaperName, StrUtil.sub(paperName, 0, 100))
                    .eq(ExercisePaper::getQuestionCount, questionList.size())
                    .isNull(ExercisePaper::getContentHash)
                    .last("LIMIT 1"));
        }
        if (existing != null) {
            throw new BusinessException(ResultCode.PAPER_DUPLICATE,
                    "试卷「" + existing.getPaperName() + "」已导入过，无需重复导入");
        }

        // 3. 创建试卷
        ExercisePaper paper = new ExercisePaper();
        paper.setUserId(userId);
        paper.setPaperName(StrUtil.sub(paperName, 0, 100));
        paper.setPaperIntro(StrUtil.sub(json.getStr("paper_intro", ""), 0, 500));
        paper.setPointSummary(json.getStr("point_summary", ""));
        paper.setSource("ai");
        paper.setContentHash(contentHash);
        paper.setQuestionCount(questionList.size());
        paper.setTotalScore(questionList.size() * SCORE_PER_QUESTION);
        paper.setBestScore(0);
        paper.setDoneCount(0);
        paper.setStatus("normal");
        paperMapper.insert(paper);

        // 4. 批量创建题目
        for (int i = 0; i < questionList.size(); i++) {
            JSONObject q = questionList.getJSONObject(i);
            ExerciseQuestion question = new ExerciseQuestion();
            question.setPaperId(paper.getId());
            question.setUserId(userId);
            question.setQType(q.getStr("q_type"));
            question.setSeq(i + 1);
            question.setStem(cleanSpellStem(q.getStr("q_type"), q.getStr("stem"), q.getStr("ans", "")));
            question.setOpts(extractOpts(q));
            Object ans = q.get("ans");
            question.setAns(ans instanceof JSONArray ? ans.toString() : q.getStr("ans", ""));
            question.setAnalysis(q.getStr("analysis", ""));
            question.setScore(SCORE_PER_QUESTION);
            question.setUserAnswer(null);
            question.setIsCorrect(0);
            questionMapper.insert(question);
        }
        log.info("用户{}导入AI试卷「{}」，共{}题", userId, paperName, questionList.size());
        return paper;
    }

    @Override
    public Object list(Long userId, String keyword, long page, long size) {
        LambdaQueryWrapper<ExercisePaper> wrapper = new LambdaQueryWrapper<ExercisePaper>()
                .eq(ExercisePaper::getUserId, userId);
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(ExercisePaper::getPaperName, keyword);
        }
        wrapper.orderByDesc(ExercisePaper::getCreatedAt);
        return PageResult.of(paperMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @Override
    public Map<String, Object> detail(Long userId, Long paperId, boolean withAnswer) {
        ExercisePaper paper = getOwnedPaper(userId, paperId);
        List<ExerciseQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<ExerciseQuestion>()
                        .eq(ExerciseQuestion::getPaperId, paperId)
                        .orderByAsc(ExerciseQuestion::getSeq));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paper", paper);
        // 兜底清洗：spell_fill 旧数据补横线；match 旧废题用词典自动修复并回写
        questions.forEach(q -> {
            repairMatchEntityIfNeeded(q);
            q.setStem(cleanSpellStem(q.getQType(), q.getStem(), q.getAns()));
        });
        // 判分在Java后端，做卷时不下发答案
        result.put("questions", withAnswer ? questions : questions.stream().peek(q -> {
            q.setAns(null);
            q.setAnalysis(null);
        }).collect(Collectors.toList()));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmitResult submit(Long userId, SubmitReq req) {
        ExercisePaper paper = getOwnedPaper(userId, req.getPaperId());
        List<ExerciseQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<ExerciseQuestion>()
                        .eq(ExerciseQuestion::getPaperId, paper.getId())
                        .orderByAsc(ExerciseQuestion::getSeq));
        questions.forEach(q -> {
            repairMatchEntityIfNeeded(q);
            q.setStem(cleanSpellStem(q.getQType(), q.getStem(), q.getAns()));
        });

        // 答案映射
        Map<Long, String> answerMap = req.getAnswers().stream()
                .collect(Collectors.toMap(AnswerItem::getQuestionId,
                        a -> a.getAnswer() == null ? "" : a.getAnswer().trim(), (a, b) -> a));

        int score = 0;
        int correctCount = 0;
        List<QuestionResult> details = new ArrayList<>();
        List<ExerciseQuestion> updates = new ArrayList<>();

        for (ExerciseQuestion q : questions) {
            String userAnswer = answerMap.getOrDefault(q.getId(), "");
            boolean correct = judge(q, userAnswer);
            if (correct) {
                score += (q.getScore() == null ? SCORE_PER_QUESTION : q.getScore());
                correctCount++;
            }
            // 回写用户作答与判分结果
            ExerciseQuestion upd = new ExerciseQuestion();
            upd.setId(q.getId());
            upd.setUserAnswer(userAnswer);
            upd.setIsCorrect(correct ? 1 : 0);
            upd.setDoneTime(LocalDateTime.now());
            // 重做答错时重新进错题本（清除旧的"已移除"标记）
            if (!correct) {
                upd.setWrongExcluded(0);
            }
            updates.add(upd);

            QuestionResult qr = new QuestionResult();
            qr.setQuestionId(q.getId());
            qr.setSeq(q.getSeq());
            qr.setQType(q.getQType());
            qr.setStem(q.getStem());
            qr.setOpts(q.getOpts());
            qr.setUserAnswer(userAnswer);
            qr.setCorrectAnswer(q.getAns());
            qr.setCorrect(correct);
            qr.setAnalysis(q.getAnalysis());
            details.add(qr);
        }
        for (ExerciseQuestion u : updates) {
            questionMapper.updateById(u);
        }

        // 更新试卷统计
        int best = Math.max(paper.getBestScore() == null ? 0 : paper.getBestScore(), score);
        ExercisePaper upd = new ExercisePaper();
        upd.setId(paper.getId());
        upd.setBestScore(best);
        upd.setDoneCount((paper.getDoneCount() == null ? 0 : paper.getDoneCount()) + 1);
        paperMapper.updateById(upd);

        return buildResult(paper, score, correctCount, questions.size(), details);
    }

    @Override
    public SubmitResult lastResult(Long userId, Long paperId) {
        ExercisePaper paper = getOwnedPaper(userId, paperId);
        List<ExerciseQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<ExerciseQuestion>()
                        .eq(ExerciseQuestion::getPaperId, paperId)
                        .orderByAsc(ExerciseQuestion::getSeq));
        questions.forEach(q -> q.setStem(cleanSpellStem(q.getQType(), q.getStem(), q.getAns())));
        List<QuestionResult> details = new ArrayList<>();
        int score = 0;
        int correctCount = 0;
        for (ExerciseQuestion q : questions) {
            boolean correct = q.getIsCorrect() != null && q.getIsCorrect() == 1;
            if (correct) {
                score += (q.getScore() == null ? SCORE_PER_QUESTION : q.getScore());
                correctCount++;
            }
            QuestionResult qr = new QuestionResult();
            qr.setQuestionId(q.getId());
            qr.setSeq(q.getSeq());
            qr.setQType(q.getQType());
            qr.setStem(q.getStem());
            qr.setOpts(q.getOpts());
            qr.setUserAnswer(q.getUserAnswer());
            qr.setCorrectAnswer(q.getAns());
            qr.setCorrect(correct);
            qr.setAnalysis(q.getAnalysis());
            details.add(qr);
        }
        return buildResult(paper, score, correctCount, questions.size(), details);
    }

    @Override
    public PageResult<Map<String, Object>> wrongList(Long userId, String qType, long page, long size) {
        LambdaQueryWrapper<ExerciseQuestion> wrapper = new LambdaQueryWrapper<ExerciseQuestion>()
                .eq(ExerciseQuestion::getUserId, userId)
                .eq(ExerciseQuestion::getIsCorrect, 0)
                .isNotNull(ExerciseQuestion::getDoneTime)
                .and(w -> w.ne(ExerciseQuestion::getWrongExcluded, 1)
                        .or().isNull(ExerciseQuestion::getWrongExcluded));
        if (StrUtil.isNotBlank(qType)) {
            wrapper.eq(ExerciseQuestion::getQType, qType);
        }
        wrapper.orderByDesc(ExerciseQuestion::getDoneTime);
        Page<ExerciseQuestion> wrongs = questionMapper.selectPage(new Page<>(page, size), wrapper);
        // 组装试卷名
        Set<Long> paperIds = wrongs.getRecords().stream()
                .map(ExerciseQuestion::getPaperId).collect(Collectors.toSet());
        Map<Long, String> paperNames = new HashMap<>();
        if (!paperIds.isEmpty()) {
            paperMapper.selectBatchIds(paperIds).forEach(p -> paperNames.put(p.getId(), p.getPaperName()));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (ExerciseQuestion w : wrongs.getRecords()) {
            repairMatchEntityIfNeeded(w);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questionId", w.getId());
            item.put("paperId", w.getPaperId());
            item.put("paperName", paperNames.getOrDefault(w.getPaperId(), ""));
            item.put("qType", w.getQType());
            item.put("stem", cleanSpellStem(w.getQType(), w.getStem(), w.getAns()));
            item.put("opts", w.getOpts());
            item.put("userAnswer", w.getUserAnswer());
            item.put("correctAnswer", w.getAns());
            item.put("analysis", w.getAnalysis());
            result.add(item);
        }
        return PageResult.of(wrongs.getTotal(), wrongs.getCurrent(), wrongs.getSize(), result);
    }

    @Override
    public void removeWrong(Long userId, Long questionId) {
        ExerciseQuestion q = questionMapper.selectById(questionId);
        if (q == null || !q.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }
        ExerciseQuestion upd = new ExerciseQuestion();
        upd.setId(questionId);
        upd.setWrongExcluded(1);
        questionMapper.updateById(upd);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeWrongBatch(Long userId, List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return;
        }
        if (questionIds.size() > 100) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "单次最多移除100道错题");
        }
        // 一条 UPDATE 完成，限定本人题目，防越权
        ExerciseQuestion upd = new ExerciseQuestion();
        upd.setWrongExcluded(1);
        questionMapper.update(upd, new LambdaQueryWrapper<ExerciseQuestion>()
                .eq(ExerciseQuestion::getUserId, userId)
                .in(ExerciseQuestion::getId, questionIds));
    }

    @Override
    public void remove(Long userId, Long paperId) {
        getOwnedPaper(userId, paperId);
        questionMapper.delete(new LambdaQueryWrapper<ExerciseQuestion>()
                .eq(ExerciseQuestion::getPaperId, paperId));
        paperMapper.deleteById(paperId);
    }

    // ==================== 私有辅助 ====================

    private ExercisePaper getOwnedPaper(Long userId, Long paperId) {
        ExercisePaper paper = paperMapper.selectById(paperId);
        if (paper == null || !paper.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        }
        return paper;
    }

    /**
     * spell_fill 题干清洗（AI 兜底）：
     * 1. 去掉 "(答案词)" 这类括号泄露，替换为横线；
     * 2. 若题干完全没有填空位（不含下划线），把句中孤立的答案词替换为横线；
     * 3. 合并相邻下划线占位、清理标点前多余空格。
     */
    private String cleanSpellStem(String type, String stem, String ans) {
        if (!"spell_fill".equals(type) || StrUtil.isBlank(stem)) {
            return stem;
        }
        String word = ans == null ? "" : ans.trim();
        String out = stem;
        if (StrUtil.isNotBlank(word)) {
            String quoted = java.util.regex.Pattern.quote(word);
            // 1. "(word)" → 横线
            out = out.replaceAll("\\(\\s*" + quoted + "\\s*\\)", " ______ ");
            // 2. 无任何填空位时，把句中独立出现的答案词替换为横线（仅整词、不区分大小写）
            if (!out.contains("_")) {
                out = out.replaceAll("(?i)\\b" + quoted + "\\b", "______");
            }
        }
        // 3. 合并相邻下划线、清理空格
        out = out.replaceAll("_{1,}\\s+_{2,}", "______");
        out = out.replaceAll("\\s+([.,!?;:])", "$1");
        return out.trim();
    }

    /** 题目 Schema 校验 */
    private void validateQuestions(JSONArray list) {
        for (int i = 0; i < list.size(); i++) {
            JSONObject q = list.getJSONObject(i);
            String type = q.getStr("q_type");
            if (!ALLOWED_TYPES.contains(type)) {
                throw new BusinessException(ResultCode.AI_FORMAT_ERROR,
                        "第" + (i + 1) + "题题型非法，仅支持5种题型，请重新生成");
            }
            if (StrUtil.isBlank(q.getStr("stem"))) {
                throw new BusinessException(ResultCode.AI_FORMAT_ERROR,
                        "第" + (i + 1) + "题缺少题干，请重新生成");
            }
            // match 必须给出可解析的配对结构；AI 常生成格式不对的 match，先尝试用词典自动修复
            if ("match".equals(type)) {
                tryRepairMatchInJson(q);
                validateMatch(q, i);
            } else if (!"spell_fill".equals(type)) {
                JSONArray opts = q.getJSONArray("opts");
                if (opts == null || opts.size() < 2 || opts.size() > 4) {
                    throw new BusinessException(ResultCode.AI_FORMAT_ERROR,
                            "第" + (i + 1) + "题选项数量异常，请重新生成");
                }
            } else {
                // spell_fill 兜底清洗后仍无填空位 → 题目本身没有作答空间，判为格式异常
                String cleaned = cleanSpellStem(type, q.getStr("stem"), q.getStr("ans", ""));
                if (!cleaned.contains("_")) {
                    throw new BusinessException(ResultCode.AI_FORMAT_ERROR,
                            "第" + (i + 1) + "题缺少填空位，请重新生成");
                }
            }
            Object ans = q.get("ans");
            if (ans == null || StrUtil.isBlank(ans.toString())) {
                throw new BusinessException(ResultCode.AI_FORMAT_ERROR,
                        "第" + (i + 1) + "题缺少答案，请重新生成");
            }
        }
    }

    /** match 题校验：opts 为释义数组，ans 为下标数组，stem 需含等量可匹配的左列单词 */
    private void validateMatch(JSONObject q, int idx) {
        JSONArray opts = q.getJSONArray("opts");
        if (opts == null || opts.size() < 2 || opts.size() > 6) {
            throw new BusinessException(ResultCode.AI_FORMAT_ERROR,
                    "第" + (idx + 1) + "题（词义匹配）选项数量异常，请重新生成");
        }
        JSONArray ans = q.getJSONArray("ans");
        if (ans == null || ans.size() != opts.size()) {
            throw new BusinessException(ResultCode.AI_FORMAT_ERROR,
                    "第" + (idx + 1) + "题（词义匹配）答案与选项数量不一致，请重新生成");
        }
        if (matchLeftWords(q.getStr("stem")).size() != opts.size()) {
            throw new BusinessException(ResultCode.AI_FORMAT_ERROR,
                    "第" + (idx + 1) + "题（词义匹配）题干缺少待匹配单词，请重新生成");
        }
    }

    /**
     * 导入前尝试用词典修复废掉的 match 题（就地修改 q）。
     * 典型坏数据：stem 只有 "Matching words with meanings"、opts 存的是英文单词本身、ans 是顺序下标。
     * 修复策略：把 opts 当作待匹配英文词，查 dict_word 得中文释义，重建 stem/opts/ans。
     * 词典查不到足够释义时不修复（保持原样，交给 validateMatch 报错）。
     */
    private void tryRepairMatchInJson(JSONObject q) {
        JSONArray opts = q.getJSONArray("opts");
        if (opts == null || opts.size() < 2) {
            return;
        }
        // 已是好题（左列单词数==选项数）则不动
        if (matchLeftWords(q.getStr("stem")).size() == opts.size()) {
            return;
        }
        List<String> words = new ArrayList<>();
        for (Object o : opts) {
            String w = StrUtil.trimToEmpty(String.valueOf(o));
            if (w.matches("[A-Za-z][A-Za-z'-]*")) {
                words.add(w);
            }
        }
        if (words.size() < 2) {
            return;
        }
        Map<String, String> dict = lookupMeanings(words);
        List<String> usable = words.stream().filter(w -> dict.containsKey(w.toLowerCase())).toList();
        if (usable.size() < 2) {
            return;
        }
        rebuildMatch(q, usable, dict);
    }

    /** 读取时修复已入库的废 match 题；修复成功则回写数据库 */
    private void repairMatchEntityIfNeeded(ExerciseQuestion q) {
        if (!"match".equals(q.getQType())) {
            return;
        }
        JSONArray opts;
        try {
            opts = JSONUtil.parseArray(StrUtil.nullToEmpty(q.getOpts()));
        } catch (Exception e) {
            return;
        }
        if (opts.size() < 2 || matchLeftWords(q.getStem()).size() == opts.size()) {
            return;
        }
        List<String> words = new ArrayList<>();
        for (Object o : opts) {
            String w = StrUtil.trimToEmpty(String.valueOf(o));
            if (w.matches("[A-Za-z][A-Za-z'-]*")) {
                words.add(w);
            }
        }
        if (words.size() < 2) {
            return;
        }
        Map<String, String> dict = lookupMeanings(words);
        List<String> usable = words.stream().filter(w -> dict.containsKey(w.toLowerCase())).toList();
        if (usable.size() < 2) {
            return;
        }
        JSONObject tmp = new JSONObject();
        rebuildMatch(tmp, usable, dict);
        q.setStem(tmp.getStr("stem"));
        q.setOpts(tmp.getStr("opts"));
        q.setAns(tmp.getStr("ans"));
        // 回写，避免每次读取重复修复
        ExerciseQuestion upd = new ExerciseQuestion();
        upd.setId(q.getId());
        upd.setStem(q.getStem());
        upd.setOpts(q.getOpts());
        upd.setAns(q.getAns());
        questionMapper.updateById(upd);
        log.info("自动修复废掉的词义匹配题 questionId={}", q.getId());
    }

    /** 用词典释义重建 match 的 stem（单词列表）/opts（打乱释义）/ans（正确下标） */
    private void rebuildMatch(JSONObject q, List<String> words, Map<String, String> dict) {
        List<String> shuffled = new ArrayList<>(words);
        // 以单词列表哈希为种子，保证同一题多次修复结果稳定
        java.util.Collections.shuffle(shuffled, new java.util.Random(words.hashCode()));
        JSONArray optsArr = new JSONArray();
        for (String w : shuffled) {
            optsArr.add(dict.get(w.toLowerCase()));
        }
        JSONArray ansArr = new JSONArray();
        for (String w : words) {
            ansArr.add(shuffled.indexOf(w));
        }
        q.set("stem", String.join(", ", words));
        q.set("opts", optsArr);
        q.set("ans", ansArr);
    }

    /** 批量查词典，返回 word(小写) -> 中文释义 */
    private Map<String, String> lookupMeanings(List<String> words) {
        Map<String, String> map = new HashMap<>();
        if (words.isEmpty()) {
            return map;
        }
        List<DictWord> rows = dictWordMapper.selectList(
                new LambdaQueryWrapper<DictWord>().in(DictWord::getWord, words));
        for (DictWord d : rows) {
            if (StrUtil.isNotBlank(d.getMeaning())) {
                map.put(d.getWord().toLowerCase(), StrUtil.sub(d.getMeaning(), 0, 40));
            }
        }
        return map;
    }

    /** 与前端 parseMatchWords 保持一致：从题干提取左列单词 */
    private List<String> matchLeftWords(String stem) {
        List<String> words = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[A-Za-z][A-Za-z'-]*").matcher(StrUtil.nullToEmpty(stem));
        Set<String> stops = Set.of("the", "a", "an", "and", "or", "to", "of", "with", "for",
                "match", "matching", "each", "word", "words", "their", "meanings", "meaning",
                "left", "right", "correct", "following", "below", "list", "lists", "please", "select");
        while (m.find()) {
            String w = m.group();
            if (!stops.contains(w.toLowerCase())) {
                words.add(w);
            }
        }
        return words;
    }

    /** 选项统一存成 JSON 数组字符串；AI 偶尔把 match 的 opts 写成对象，取其值列表 */
    private String extractOpts(JSONObject q) {
        Object raw = q.get("opts");
        if (raw == null) {
            return null;
        }
        if (raw instanceof JSONArray) {
            return raw.toString();
        }
        if (raw instanceof JSONObject obj) {
            JSONArray arr = new JSONArray();
            obj.values().forEach(arr::add);
            return arr.toString();
        }
        return JSONUtil.parseArray(raw).toString();
    }

    /** 试卷内容指纹：题型+题干+选项+答案，用于阻止同一份试卷重复导入 */
    private String paperContentHash(JSONArray list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            JSONObject q = list.getJSONObject(i);
            sb.append(q.getStr("q_type")).append('|')
                    .append(q.getStr("stem")).append('|')
                    .append(extractOpts(q)).append('|')
                    .append(q.get("ans")).append(';');
        }
        return SecureUtil.md5(sb.toString());
    }

    /** Java 后端判分 */
    private boolean judge(ExerciseQuestion q, String userAnswer) {
        if (StrUtil.isBlank(userAnswer) || StrUtil.isBlank(q.getAns())) {
            return false;
        }
        if ("match".equals(q.getQType())) {
            return judgeMatch(q.getAns(), userAnswer);
        }
        if ("spell_fill".equals(q.getQType())) {
            return userAnswer.trim().equalsIgnoreCase(q.getAns().trim());
        }
        return userAnswer.trim().equals(q.getAns().trim());
    }

    /** 词义匹配：JSON 数组逐项比对 */
    private boolean judgeMatch(String correctJson, String userJson) {
        try {
            JSONArray correct = JSONUtil.parseArray(correctJson);
            JSONArray user = JSONUtil.parseArray(userJson);
            if (correct.size() != user.size()) {
                return false;
            }
            for (int i = 0; i < correct.size(); i++) {
                if (!String.valueOf(correct.get(i)).equals(String.valueOf(user.get(i)))) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private SubmitResult buildResult(ExercisePaper paper, int score, int correctCount,
                                     int totalCount, List<QuestionResult> details) {
        SubmitResult result = new SubmitResult();
        result.setPaperId(paper.getId());
        result.setPaperName(paper.getPaperName());
        result.setScore(score);
        result.setTotalScore(paper.getTotalScore());
        result.setCorrectCount(correctCount);
        result.setTotalCount(totalCount);
        result.setCorrectRate(totalCount == 0 ? 0D
                : BigDecimal.valueOf(correctCount * 100.0 / totalCount).setScale(2, RoundingMode.HALF_UP).doubleValue());
        result.setDetails(details);
        return result;
    }
}
