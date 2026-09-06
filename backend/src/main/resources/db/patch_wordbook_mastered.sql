-- ============================================================
-- 生词本"掌握"防重复计数补丁
-- ！！重要：不要运行 schema.sql，它会 DROP TABLE 清空你的数据。
-- 本文件：不删数据。MySQL 8.0 无 ADD COLUMN IF NOT EXISTS，
-- 重复执行 ALTER 会报"Duplicate column"错，忽略即可。
--
-- 口径变更：掌握 = 熟悉度首次 >= 80 时打 mastered_at 标记 + total_mastered +1
-- 每个单词一生只计一次（掉回 80 以下再升上来不重复计数）
-- ============================================================
USE `word_spirit`;

-- 1) word_book 增加首次掌握时间列
ALTER TABLE `word_book`
  ADD COLUMN `mastered_at` DATETIME NULL COMMENT '首次掌握时间（熟悉度首次>=80，防重复计数）' AFTER `next_review_at`;

-- 2) 存量校准：历史上熟悉度已达 80 的词统一打标，但【不】追加 total_mastered
--    （total_mastered 保持历史累计口径，打标仅为防止未来复习时重复计数）
UPDATE `word_book`
SET `mastered_at` = NOW()
WHERE `familiarity` >= 80
  AND `mastered_at` IS NULL;
