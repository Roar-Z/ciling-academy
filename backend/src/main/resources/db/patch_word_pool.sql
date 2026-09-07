-- =============================================================
-- 词库完全独立（词书模式）改造：
--   1) learn_round 加 level 列（记录每轮所属词库档位）
--   2) 新表 user_pool_word：各词库持久已学词（轮次满 10 清空后仍是去重依据）
--   3) 存量学习记录迁移为 level='all'（对所有词库生效，保守处理：
--      历史学过的词不会被任何词库重复推送；此后新学习按词库独立记录）
-- 幂等可重复执行；重复执行报 Duplicate column / Table already exists 忽略即可
-- 用法：DBeaver 打开本文件（编码 UTF-8），不选中任何内容，Alt+X 执行整个脚本
-- =============================================================
USE `word_spirit`;

-- 1) learn_round 加 level 列（幂等）
SET @has_lr_level = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'learn_round' AND COLUMN_NAME = 'level');
SET @sql_lr_level = IF(@has_lr_level = 0,
  'ALTER TABLE `learn_round` ADD COLUMN `level` VARCHAR(16) NOT NULL DEFAULT ''all'' COMMENT ''词库档位: cet4/cet6/gaokao/zhongkao/mixed/all(存量)'' AFTER `source`',
  'SELECT 1');
PREPARE stmt_lr_level FROM @sql_lr_level;
EXECUTE stmt_lr_level;
DEALLOCATE PREPARE stmt_lr_level;

-- 2) 词库已学词表（幂等）
CREATE TABLE IF NOT EXISTS `user_pool_word` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT      NOT NULL COMMENT '用户ID',
  `level`      VARCHAR(16) NOT NULL DEFAULT 'all' COMMENT '词库档位: cet4/cet6/gaokao/zhongkao/mixed/all(存量迁移)',
  `word`       VARCHAR(64) NOT NULL COMMENT '单词',
  `mastered`   TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '学习时是否点了认识 1=是 0=否',
  `boosted_at` DATETIME    DEFAULT NULL COMMENT '加深印象重现时间 NULL=未重现过',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_level_word` (`user_id`, `level`, `word`),
  KEY `idx_user_level` (`user_id`, `level`)
) ENGINE=InnoDB COMMENT='词库独立-已学词记录';

-- 2.1) 已建过旧版表（无 mastered/boosted_at）的增量补列（幂等）
SET @has_mst = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_pool_word' AND COLUMN_NAME = 'mastered');
SET @sql_mst = IF(@has_mst = 0,
  'ALTER TABLE `user_pool_word` ADD COLUMN `mastered` TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''学习时是否点了认识 1=是 0=否'' AFTER `word`',
  'SELECT 1');
PREPARE stmt_mst FROM @sql_mst; EXECUTE stmt_mst; DEALLOCATE PREPARE stmt_mst;

SET @has_bst = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_pool_word' AND COLUMN_NAME = 'boosted_at');
SET @sql_bst = IF(@has_bst = 0,
  'ALTER TABLE `user_pool_word` ADD COLUMN `boosted_at` DATETIME DEFAULT NULL COMMENT ''加深印象重现时间 NULL=未重现过'' AFTER `mastered`',
  'SELECT 1');
PREPARE stmt_bst FROM @sql_bst; EXECUTE stmt_bst; DEALLOCATE PREPARE stmt_bst;

-- 3) 存量迁移：历史轮次词 + 生词本词 + 已掌握词 → level='all'（全局排除）
INSERT IGNORE INTO `user_pool_word` (`user_id`, `level`, `word`)
SELECT i.`user_id`, 'all', i.`word` FROM `learn_round_item` i;

INSERT IGNORE INTO `user_pool_word` (`user_id`, `level`, `word`)
SELECT b.`user_id`, 'all', b.`word` FROM `word_book` b;

INSERT IGNORE INTO `user_pool_word` (`user_id`, `level`, `word`)
SELECT u.`id`, 'all', jt.`word`
FROM `sys_user` u
JOIN JSON_TABLE(u.`mastered_words`, '$[*]' COLUMNS (`word` VARCHAR(64) PATH '$')) jt
WHERE u.`mastered_words` IS NOT NULL AND u.`mastered_words` <> ''
  AND JSON_VALID(u.`mastered_words`);

-- 校验
-- SELECT level, COUNT(*) FROM user_pool_word GROUP BY level;
