package com.wordspirit.module.wordtest.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wordspirit.common.BusinessException;
import com.wordspirit.common.ResultCode;
import com.wordspirit.module.dict.entity.DictWord;
import com.wordspirit.module.dict.service.DictWordService;
import com.wordspirit.module.learnround.entity.LearnRound;
import com.wordspirit.module.learnround.entity.LearnRoundItem;
import com.wordspirit.module.user.entity.User;
import com.wordspirit.module.user.mapper.UserMapper;
import com.wordspirit.module.wordbook.entity.WordBook;
import com.wordspirit.module.wordbook.mapper.WordBookMapper;
import com.wordspirit.module.wordtest.dto.FinishTestResp;
import com.wordspirit.module.wordtest.dto.HistoryDetailDto;
import com.wordspirit.module.wordtest.dto.HistoryItemDto;
import com.wordspirit.module.wordtest.dto.StartTestReq;
import com.wordspirit.module.wordtest.dto.StartTestResp;
import com.wordspirit.module.wordtest.dto.TestQuestionDto;
import com.wordspirit.module.wordtest.entity.WordTestBatch;
import com.wordspirit.module.wordtest.entity.WordTestRecord;
import com.wordspirit.module.wordtest.mapper.WordTestBatchMapper;
import com.wordspirit.module.wordtest.mapper.WordTestRecordMapper;
import com.wordspirit.module.wordtest.service.WordTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 单词测验服务实现
 *
 * 模式：
 *   spelling   看中文 → 输入英文（前端判断拼写，后端二次确认）
 *   matching   中英连线（后端发 4-5 组候选，前端连线）
 *   sentence   给出英文例句 → 用户写中文翻译（前端判断包含关键词）
 *
 * 答对 → 复习计数 +1，下次复习按 EB_DAYS 表推进（最长 30 天）
 * 答错 → 复习计数重置为 0，下次复习明天（1 天后）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WordTestServiceImpl implements WordTestService {

    /** 艾宾浩斯间隔（天），按累计答对次数推进。答对+1，错则归零 */
    private static final int[] EB_DAYS = {1, 2, 4, 7, 15, 30};

    /** 长掌握线：连续答对 ≥5 次（走到 EB_DAYS 末位 30 天），视为已掌握 */
    private static final int MASTERED_STREAK = 5;

    /** 答错后推到下次复习的天数 */
    private static final int WRONG_NEXT_DAYS = 1;

    private final WordTestBatchMapper batchMapper;
    private final WordTestRecordMapper recordMapper;
    private final WordBookMapper wordBookMapper;
    private final UserMapper userMapper;
    private final DictWordService dictWordService;
    private final com.wordspirit.module.learnround.mapper.LearnRoundMapper learnRoundMapper;
    private final com.wordspirit.module.learnround.mapper.LearnRoundItemMapper learnRoundItemMapper;

    private static final Random RAND = new Random();

    @Override
    @Transactional
    public StartTestResp start(Long userId, StartTestReq req) {
        String mode = req.getMode();
        String source = req.getSource();
        int count = Math.min(Math.max(req.getCount(), 5), 50);
        int timeLimit = req.getTimeLimitSec() != null ? req.getTimeLimitSec() : defaultTimeLimit(mode);

        // 取词优先级：learnRoundId（巩固测验选学习轮）> reuseBatchId（重做测验轮）> source 随机
        List<WordBook> picked;
        if (req.getLearnRoundId() != null) {
            picked = pickFromLearnRound(userId, req.getLearnRoundId(), count);
        } else if (req.getReuseBatchId() != null) {
            picked = pickFromBatch(userId, req.getReuseBatchId(), count);
        } else {
            picked = pickWords(userId, source, count);
        }

        // 写 batch
        WordTestBatch batch = new WordTestBatch();
        batch.setUserId(userId);
        batch.setMode(mode);
        batch.setSource(source);
        batch.setTotal(picked.size());
        batch.setCorrectCount(0);
        batch.setTimeLimitSec(timeLimit);
        batch.setStartedAt(LocalDateTime.now());
        batchMapper.insert(batch);

        // 补齐词典字段（pos/example/exampleCn）
        Map<String, DictWord> dictMap = lookupDictFor(picked);

        // 组装题目
        List<TestQuestionDto> questions = new ArrayList<>();
        List<DictWord> distractors = dictWordService.randomFromDict(Math.max(count * 2, 20));
        for (WordBook w : picked) {
            TestQuestionDto q = new TestQuestionDto();
            q.setWordId(w.getId());
            q.setWord(w.getWord());
            q.setPhonetic(w.getPhonetic());
            q.setMeaning(w.getMeaning());
            DictWord dw = dictMap.get(w.getWord());
            if (dw != null) {
                q.setPos(dw.getPos());
                q.setExample(dw.getExample());
                q.setExampleCn(dw.getExampleCn());
                if (StrUtil.isBlank(q.getPhonetic())) q.setPhonetic(dw.getPhonetic());
            }
            // matching 模式需要候选集
            if ("matching".equals(mode)) {
                q.setChoicesEn(buildChoices(picked, distractors, w.getWord(), true));
                q.setChoicesCn(buildChoices(picked, distractors, w.getMeaning(), false));
            }
            questions.add(q);
        }

        StartTestResp resp = new StartTestResp();
        resp.setBatchId(batch.getId());
        resp.setMode(mode);
        resp.setSource(source);
        resp.setTotal(questions.size());
        resp.setTimeLimitSec(timeLimit);
        resp.setQuestions(questions);
        return resp;
    }

    @Override
    @Transactional
    public void submitAnswer(Long userId, Long batchId, Long wordId, boolean correct, Integer costMs, String userAnswer) {
        WordTestBatch batch = batchMapper.selectById(batchId);
        if (batch == null || !batch.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "测验批次不存在");
        }
        if (batch.getFinishedAt() != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "测验已结束");
        }
        WordBook book = wordBookMapper.selectById(wordId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "单词不存在");
        }

        // 写 record
        WordTestRecord rec = new WordTestRecord();
        rec.setUserId(userId);
        rec.setBatchId(batchId);
        rec.setWordId(wordId);
        rec.setWord(book.getWord());
        rec.setMode(batch.getMode());
        rec.setCorrect(correct ? 1 : 0);
        rec.setCostMs(costMs == null ? 0 : costMs);
        rec.setUserAnswer(StrUtil.nullToEmpty(userAnswer));
        rec.setTestedAt(LocalDateTime.now());
        recordMapper.insert(rec);

        // 更新 batch 计数
        if (correct) {
            batch.setCorrectCount((batch.getCorrectCount() == null ? 0 : batch.getCorrectCount()) + 1);
            batchMapper.updateById(batch);
        }

        // 推进艾宾浩斯：答对 +1 推进、答错归零重排到 1 天后
        updateEbbinghaus(book, correct);
        wordBookMapper.updateById(book);
    }

    @Override
    @Transactional
    public FinishTestResp finish(Long userId, Long batchId) {
        WordTestBatch batch = batchMapper.selectById(batchId);
        if (batch == null || !batch.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "测验批次不存在");
        }

        // 幂等保护：仅在尚未 finished 时才写入 finishedAt，
        // 这样"再来一轮"按钮多次触发、或客户端异常补调 finish 时，
        // 不会覆盖真实完成时间，也不会打乱 EB / 统计。
        if (batch.getFinishedAt() == null) {
            batch.setFinishedAt(LocalDateTime.now());
            batchMapper.updateById(batch);
        }

        // 拉所有 record
        List<WordTestRecord> records = recordMapper.selectList(new LambdaQueryWrapper<WordTestRecord>()
                .eq(WordTestRecord::getBatchId, batchId)
                .orderByAsc(WordTestRecord::getTestedAt));

        int totalMs = 0;
        Map<Long, WordTestRecord> wrongLatest = new HashMap<>();
        for (WordTestRecord r : records) {
            totalMs += r.getCostMs() == null ? 0 : r.getCostMs();
            // 一词可能有重复提交（前端重复点击），取最后一次
            wrongLatest.put(r.getWordId(), r);
        }

        FinishTestResp resp = new FinishTestResp();
        resp.setBatchId(batchId);
        resp.setTotal(batch.getTotal());
        resp.setCorrectCount(batch.getCorrectCount() == null ? 0 : batch.getCorrectCount());
        resp.setAccuracy(batch.getTotal() == null || batch.getTotal() == 0
                ? 0
                : Math.round((float) resp.getCorrectCount() * 100 / batch.getTotal()));
        resp.setCostMsTotal(totalMs);

        List<FinishTestResp.WrongItem> wrongList = new ArrayList<>();
        for (WordTestRecord r : wrongLatest.values()) {
            if (r.getCorrect() != null && r.getCorrect() == 0) {
                WordBook b = wordBookMapper.selectById(r.getWordId());
                FinishTestResp.WrongItem wi = new FinishTestResp.WrongItem();
                wi.setWordId(r.getWordId());
                wi.setWord(r.getWord());
                wi.setPhonetic(b != null ? b.getPhonetic() : "");
                wi.setMeaning(b != null ? b.getMeaning() : "");
                wi.setUserAnswer(r.getUserAnswer());
                wi.setNextReviewAt(b != null && b.getNextReviewAt() != null
                        ? b.getNextReviewAt().toString()
                        : LocalDateTime.now().plusDays(1).toString());
                wrongList.add(wi);
            }
        }
        resp.setWrongList(wrongList);
        return resp;
    }

    @Override
    public List<HistoryItemDto> history(Long userId) {
        // 仅统计已完成的批次，按开始时间升序，再计算"第几轮"，最后倒序（新轮在前）
        List<WordTestBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<WordTestBatch>()
                .eq(WordTestBatch::getUserId, userId)
                .isNotNull(WordTestBatch::getFinishedAt)
                .orderByAsc(WordTestBatch::getStartedAt));
        List<HistoryItemDto> list = new ArrayList<>();
        int round = 1;
        for (WordTestBatch b : batches) {
            list.add(toItem(b, round));
            round++;
        }
        Collections.reverse(list);
        return list;
    }

    @Override
    public HistoryDetailDto historyDetail(Long userId, Long batchId) {
        WordTestBatch batch = batchMapper.selectById(batchId);
        if (batch == null || !batch.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "测验批次不存在");
        }

        // 计算该轮序号
        List<WordTestBatch> all = batchMapper.selectList(new LambdaQueryWrapper<WordTestBatch>()
                .eq(WordTestBatch::getUserId, userId)
                .isNotNull(WordTestBatch::getFinishedAt)
                .orderByAsc(WordTestBatch::getStartedAt));
        int roundNo = 1;
        for (WordTestBatch b : all) {
            if (b.getId().equals(batchId)) break;
            roundNo++;
        }

        HistoryDetailDto dto = new HistoryDetailDto();
        copyBase(dto, batch, roundNo);

        List<WordTestRecord> records = recordMapper.selectList(new LambdaQueryWrapper<WordTestRecord>()
                .eq(WordTestRecord::getBatchId, batchId)
                .orderByAsc(WordTestRecord::getTestedAt));
        List<HistoryDetailDto.HistoryDetailItem> items = new ArrayList<>();
        for (WordTestRecord r : records) {
            HistoryDetailDto.HistoryDetailItem it = new HistoryDetailDto.HistoryDetailItem();
            it.setWordId(r.getWordId());
            it.setWord(r.getWord());
            it.setMode(r.getMode());
            it.setCorrect(r.getCorrect());
            it.setUserAnswer(r.getUserAnswer());
            it.setCostMs(r.getCostMs());
            WordBook bk = wordBookMapper.selectById(r.getWordId());
            if (bk != null) {
                it.setPhonetic(bk.getPhonetic());
                it.setMeaning(bk.getMeaning());
            }
            items.add(it);
        }
        dto.setItems(items);
        return dto;
    }

    /** 复用某一轮的词表（用于"巩固测验"重做） */
    private List<WordBook> pickFromBatch(Long userId, Long batchId, int count) {
        WordTestBatch batch = batchMapper.selectById(batchId);
        if (batch == null || !batch.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "原测验批次不存在");
        }
        List<WordTestRecord> recs = recordMapper.selectList(new LambdaQueryWrapper<WordTestRecord>()
                .eq(WordTestRecord::getBatchId, batchId)
                .orderByAsc(WordTestRecord::getTestedAt));
        Set<Long> ids = new LinkedHashSet<>();
        for (WordTestRecord r : recs) ids.add(r.getWordId());
        if (ids.isEmpty()) return new ArrayList<>();
        List<WordBook> books = wordBookMapper.selectBatchIds(new ArrayList<>(ids)).stream()
                .filter(b -> b.getUserId().equals(userId))
                .collect(Collectors.toList());
        if (books.size() > count) books = books.subList(0, count);
        Collections.shuffle(books);
        return books;
    }

    /** 复用某一学习轮次（learn_round）的词表，用于"巩固测验"选轮次测试/重测 */
    private List<WordBook> pickFromLearnRound(Long userId, Long roundId, int count) {
        LearnRound round = learnRoundMapper.selectById(roundId);
        if (round == null || !round.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学习轮次不存在");
        }
        List<LearnRoundItem> items = learnRoundItemMapper.selectList(new LambdaQueryWrapper<LearnRoundItem>()
                .eq(LearnRoundItem::getRoundId, roundId)
                .orderByAsc(LearnRoundItem::getId));
        List<WordBook> books = new ArrayList<>();
        for (LearnRoundItem it : items) {
            WordBook w = new WordBook();
            w.setId(it.getWordId() == null ? 0L : it.getWordId());
            w.setWord(it.getWord());
            w.setPhonetic(it.getPhonetic());
            w.setMeaning(it.getMeaning());
            books.add(w);
        }
        if (books.size() > count) books = books.subList(0, count);
        Collections.shuffle(books);
        return books;
    }

    private HistoryItemDto toItem(WordTestBatch b, int roundNo) {
        HistoryItemDto dto = new HistoryItemDto();
        dto.setBatchId(b.getId());
        dto.setRoundNo(roundNo);
        dto.setMode(b.getMode());
        dto.setSource(b.getSource());
        dto.setTotal(b.getTotal());
        dto.setCorrectCount(b.getCorrectCount());
        dto.setAccuracy(b.getTotal() == null || b.getTotal() == 0 ? 0
                : Math.round((float) (b.getCorrectCount() == null ? 0 : b.getCorrectCount()) * 100 / b.getTotal()));
        dto.setStartedAt(b.getStartedAt());
        dto.setFinishedAt(b.getFinishedAt());
        return dto;
    }

    private void copyBase(HistoryDetailDto dto, WordTestBatch b, int roundNo) {
        dto.setBatchId(b.getId());
        dto.setRoundNo(roundNo);
        dto.setMode(b.getMode());
        dto.setSource(b.getSource());
        dto.setTotal(b.getTotal());
        dto.setCorrectCount(b.getCorrectCount());
        dto.setAccuracy(b.getTotal() == null || b.getTotal() == 0 ? 0
                : Math.round((float) (b.getCorrectCount() == null ? 0 : b.getCorrectCount()) * 100 / b.getTotal()));
        dto.setStartedAt(b.getStartedAt());
        dto.setFinishedAt(b.getFinishedAt());
    }

    // ============== 内部工具 ==============

    /**
     * 取 N 个测验词
     * - due  ：生词本里 nextReviewDate <= 今天 范围内随机抽 N 个
     * - new  ：生词本里随机抽 N 个（不足时用词典补齐）
     *
     * 注意：必须 ORDER BY RAND()，否则按 nextReviewDate ASC + LIMIT count 永远是同一批
     * 词——"再来一轮"按时间排序取前 N 个的子集，集合不会变，只是顺序不同，
     * 用户感知就是"每次都是同样的单词"。改为 RAND() 后每次都从候选池随机抽，
     * 真正实现"再来一轮换一批"。
     */
    private List<WordBook> pickWords(Long userId, String source, int count) {
        List<WordBook> books;
        if ("due".equals(source)) {
            LocalDateTime now = LocalDateTime.now();
            books = wordBookMapper.selectList(new LambdaQueryWrapper<WordBook>()
                    .eq(WordBook::getUserId, userId)
                    .and(w -> w.isNull(WordBook::getNextReviewAt)
                            .or().le(WordBook::getNextReviewAt, now))
                    .last("ORDER BY RAND() LIMIT " + count));
        } else {
            books = wordBookMapper.selectList(new LambdaQueryWrapper<WordBook>()
                    .eq(WordBook::getUserId, userId)
                    .last("ORDER BY RAND() LIMIT " + count));
        }
        if (books.size() >= count || "due".equals(source)) {
            Collections.shuffle(books);
            return books;
        }
        // new 源不够则用词典补齐
        int need = count - books.size();
        Set<String> have = books.stream().map(WordBook::getWord).collect(Collectors.toSet());
        List<DictWord> fill = dictWordService.randomFromDict(need * 3);
        for (DictWord dw : fill) {
            if (need <= 0) break;
            if (have.contains(dw.getWord())) continue;
            WordBook b = new WordBook();
            b.setUserId(userId);
            b.setWord(dw.getWord());
            b.setPhonetic(dw.getPhonetic());
            b.setMeaning((StrUtil.nullToEmpty(dw.getPos()) + " " + StrUtil.nullToEmpty(dw.getMeaning())).trim());
            b.setSource("test");
            b.setFamiliarity(0);
            b.setReviewCount(0);
            b.setNextReviewAt(LocalDateTime.now().plusDays(1));
            wordBookMapper.insert(b);
            books.add(b);
            have.add(dw.getWord());
            need--;
        }
        Collections.shuffle(books);
        return books;
    }

    /** 批量从词典补齐字段 */
    private Map<String, DictWord> lookupDictFor(List<WordBook> books) {
        if (books.isEmpty()) return Map.of();
        List<String> words = books.stream().map(WordBook::getWord).collect(Collectors.toList());
        return dictWordService.lookupBatch(words).stream()
                .collect(Collectors.toMap(DictWord::getWord, d -> d, (a, b) -> a));
    }

    /**
     * matching 模式生成候选：正确答案 + 干扰项
     * choicesEn 用单词，choicesCn 用中文释义
     */
    private List<String> buildChoices(List<WordBook> picked, List<DictWord> distractors, String correct, boolean isEn) {
        Set<String> used = new HashSet<>();
        used.add(correct);
        List<String> result = new ArrayList<>();
        result.add(correct);
        // 优先从其他生词本里抽
        List<WordBook> others = picked.stream()
                .filter(w -> !w.getWord().equals(correct))
                .collect(Collectors.toList());
        Collections.shuffle(others);
        for (WordBook o : others) {
            if (result.size() >= 5) break;
            String v = isEn ? o.getWord() : o.getMeaning();
            if (StrUtil.isBlank(v) || used.contains(v)) continue;
            used.add(v);
            result.add(v);
        }
        // 再用随机词典补足到 5 个
        for (DictWord d : distractors) {
            if (result.size() >= 5) break;
            String v = isEn ? d.getWord() : (StrUtil.nullToEmpty(d.getPos()) + " " + StrUtil.nullToEmpty(d.getMeaning())).trim();
            if (StrUtil.isBlank(v) || used.contains(v)) continue;
            used.add(v);
            result.add(v);
        }
        Collections.shuffle(result);
        return result;
    }

    /**
     * 答对/答错时更新 EB 计划
     * - 答对：reviewCount++，按 EB_DAYS 表取下次日期；累计 ≥ MASTERED_STREAK 次推到 30 天后（用户视角"掌握"）
     * - 答错：reviewCount=0，familiarity 降低，nextReviewDate=1 天后
     */
    private void updateEbbinghaus(WordBook book, boolean correct) {
        int reviewCount = book.getReviewCount() == null ? 0 : book.getReviewCount();
        int oldFamiliarity = book.getFamiliarity() == null ? 0 : book.getFamiliarity();
        int familiarity = oldFamiliarity;
        if (correct) {
            reviewCount++;
            familiarity = Math.min(100, familiarity + 20);
            int idx = Math.min(reviewCount - 1, EB_DAYS.length - 1);
            int days = EB_DAYS[Math.max(idx, 0)];
            // 累计答对 ≥ MASTERED_STREAK，直接钉到 30 天后
            if (reviewCount >= MASTERED_STREAK) days = 30;
            book.setReviewCount(reviewCount);
            book.setFamiliarity(familiarity);
            book.setLastReviewAt(LocalDateTime.now());
            book.setNextReviewAt(LocalDateTime.now().plusDays(days));
            // 首次达到 80 熟悉度：打标记 + 累计掌握 +1（每词一生只计一次）
            if (familiarity >= 80 && book.getMasteredAt() == null) {
                book.setMasteredAt(LocalDateTime.now());
                incrementTotalMastered(book.getUserId());
            }
        } else {
            // 答错：复习计数归零，熟悉度扣 15，安排明天
            book.setReviewCount(0);
            book.setFamiliarity(Math.max(0, familiarity - 15));
            book.setLastReviewAt(LocalDateTime.now());
            book.setNextReviewAt(LocalDateTime.now().plusDays(WRONG_NEXT_DAYS));
        }
    }

    /** 累计掌握 +1（持久化到 sys_user.total_mastered） */
    private void incrementTotalMastered(Long userId) {
        if (userId == null) return;
        User u = userMapper.selectById(userId);
        int cur = (u == null || u.getTotalMastered() == null) ? 0 : u.getTotalMastered();
        User patch = new User();
        patch.setId(userId);
        patch.setTotalMastered(cur + 1);
        userMapper.updateById(patch);
    }

    /** 默认单题限时（秒）：拼写 25 / 连线 20 / 翻译 35 */
    private int defaultTimeLimit(String mode) {
        return switch (mode) {
                case "matching" -> 20;
                case "sentence" -> 35;
                default -> 25;
            };
    }
}