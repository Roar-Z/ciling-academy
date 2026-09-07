-- ============================================================
-- 修复词典部分行 UTF-8 二次编码乱码（导入时按 latin1/cp1252 误读源文件）
-- 现象：音标 /ˈɪnəˈprəʊpriət/ 显示为 /ˈEªnÉ™propÉªÉ™t/，
--       中文释义出现 ä¸‰ å­¦ä¹  等假字符；同行英文单词本身正常
-- 原理：乱码字符串的每个字符是原始 UTF-8 字节按 cp1252 误解码的结果，
--       将字段按 latin1 还原为原始字节序列，再按 utf8mb4 重新解释即复原
--       （MySQL 的 latin1 即 cp1252，™ªš 等第二字节字符可正确映射回字节）
-- 安全性：修复条件只命中含 latin1 音帽字母（ÃÂÄÅÆÇÈÉÊËäåæçèé、â€）的行，
--         合法 IPA（əɪʊɔːθð）、中文、英文不含这些字符，不会误伤；
--         纯 ASCII 行不命中，重复执行为 0 行（幂等）
-- 注意：
--   1. 部分行导入时可能被 VARCHAR 长度截断（乱码比原文长约 1.5~2 倍），
--      截断导致还原字节不是合法 UTF-8、CONVERT 结果为 NULL——
--      本脚本对这类行自动跳过（不写入），并在第四步列出供人工处理
--   2. 若上次执行中途报错，本脚本可安全重新执行（幂等）
--   3. DataGrip 若遇错即停，请逐条语句执行，或先跑第一步统计
-- 执行：在数据库客户端（连接需使用 utf8mb4）手动执行
-- ============================================================

-- ---------- 第一步：查看受影响范围（只读） ----------
SELECT
  SUM(phonetic   REGEXP 'Ã|Â|Ä|Å|Æ|Ç|È|É|Ê|Ë')            AS bad_phonetic,
  SUM(meaning    REGEXP 'Ã|Â|Ä|Å|Æ|Ç|È|É|Ê|Ë|ä|å|æ|ç|è|é') AS bad_meaning,
  SUM(example    REGEXP 'â€|Ã|Â')                          AS bad_example,
  SUM(example_cn REGEXP 'Ã|Â|Ä|Å|Æ|Ç|È|É|Ê|Ë|ä|å|æ|ç|è|é') AS bad_example_cn,
  COUNT(*) AS total
FROM dict_word;

-- 抽样预览（执行第二步前可先人工核对几行）
SELECT word, phonetic, meaning
FROM dict_word
WHERE phonetic REGEXP 'Ã|Â|Ä|Å|Æ|Ç|È|É|Ê|Ë'
   OR meaning  REGEXP 'Ã|Â|Ä|Å|Æ|Ç|È|É|Ê|Ë|ä|å|æ|ç|è|é'
LIMIT 20;

-- ---------- 第二步：执行修复（仅写入能无损还原的行；转换失败自动跳过） ----------
-- 1) 音标（合法 IPA 不含带音帽拉丁字母；ð U+00F0 不在标记内不受影响）
UPDATE dict_word
SET phonetic = CONVERT(CAST(CONVERT(phonetic USING latin1) AS BINARY) USING utf8mb4)
WHERE phonetic REGEXP 'Ã|Â|Ä|Å|Æ|Ç|È|É|Ê|Ë'
  AND CONVERT(CAST(CONVERT(phonetic USING latin1) AS BINARY) USING utf8mb4) IS NOT NULL;

-- 2) 中文释义
UPDATE dict_word
SET meaning = CONVERT(CAST(CONVERT(meaning USING latin1) AS BINARY) USING utf8mb4)
WHERE meaning REGEXP 'Ã|Â|Ä|Å|Æ|Ç|È|É|Ê|Ë|ä|å|æ|ç|è|é'
  AND CONVERT(CAST(CONVERT(meaning USING latin1) AS BINARY) USING utf8mb4) IS NOT NULL;

-- 3) 英文例句（合法弯引号 “ ” ’ … 不含 â€ 序列，可安全匹配）
UPDATE dict_word
SET example = CONVERT(CAST(CONVERT(example USING latin1) AS BINARY) USING utf8mb4)
WHERE example REGEXP 'â€|Ã|Â'
  AND CONVERT(CAST(CONVERT(example USING latin1) AS BINARY) USING utf8mb4) IS NOT NULL;

-- 4) 例句中文翻译
UPDATE dict_word
SET example_cn = CONVERT(CAST(CONVERT(example_cn USING latin1) AS BINARY) USING utf8mb4)
WHERE example_cn REGEXP 'Ã|Â|Ä|Å|Æ|Ç|È|É|Ê|Ë|ä|å|æ|ç|è|é'
  AND CONVERT(CAST(CONVERT(example_cn USING latin1) AS BINARY) USING utf8mb4) IS NOT NULL;

-- 5) 生词本同步：用户收藏时从 dict_word 复制了坏音标/释义，按单词回填修复后的值
--    （只更新仍含乱码标记的行，用户手输的正常内容不受影响）
UPDATE word_book wb
JOIN dict_word d ON d.word = wb.word
SET wb.phonetic = d.phonetic,
    wb.meaning  = d.meaning
WHERE wb.phonetic REGEXP 'Ã|Â|Ä|Å|Æ|Ç|È|É|Ê|Ë'
   OR wb.meaning  REGEXP 'Ã|Â|Ä|Å|Æ|Ç|È|É|Ê|Ë|ä|å|æ|ç|è|é';

-- ---------- 第三步：复查 ----------
-- 残余应为 0；若 bad_* 不为 0，说明存在截断等无法自动还原的行，见第四步
SELECT
  SUM(phonetic   REGEXP 'Ã|Â|Ä|Å|Æ|Ç|È|É|Ê|Ë')            AS bad_phonetic,
  SUM(meaning    REGEXP 'Ã|Â|Ä|Å|Æ|Ç|È|É|Ê|Ë|ä|å|æ|ç|è|é') AS bad_meaning,
  SUM(example    REGEXP 'â€|Ã|Â')                          AS bad_example,
  SUM(example_cn REGEXP 'Ã|Â|Ä|Å|Æ|Ç|È|É|Ê|Ë|ä|å|æ|ç|è|é') AS bad_example_cn
FROM dict_word;

-- 验证之前乱码的词
SELECT word, phonetic, meaning FROM dict_word WHERE word = 'inappropriate';

-- ---------- 第四步：列出无法自动还原的行（人工处理） ----------
-- 这些行导入时被截断，字节序列已不完整，只能人工重填或从源词表重新导入
SELECT word, phonetic, meaning
FROM dict_word
WHERE phonetic REGEXP 'Ã|Â|Ä|Å|Æ|Ç|È|É|Ê|Ë'
   OR meaning  REGEXP 'Ã|Â|Ä|Å|Æ|Ç|È|É|Ê|Ë|ä|å|æ|ç|è|é'
   OR example  REGEXP 'â€|Ã|Â'
   OR example_cn REGEXP 'Ã|Â|Ä|Å|Æ|Ç|È|É|Ê|Ë|ä|å|æ|ç|è|é';
