-- =============================================================
-- 学习轮次（每次"新词学习/复习"产生的批次，含该轮学的所有词）
-- 用于"巩固测验"选哪一轮 / "再来一轮换新词" 的核心数据
-- =============================================================

DROP TABLE IF EXISTS `learn_round_item`;
CREATE TABLE `learn_round_item` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `round_id`    BIGINT      NOT NULL COMMENT '所属轮次ID',
  `user_id`     BIGINT      NOT NULL COMMENT '用户ID（冗余便于按用户过滤）',
  `word`        VARCHAR(64) NOT NULL COMMENT '单词（词典词）',
  `word_id`     BIGINT      DEFAULT NULL COMMENT '关联生词本ID（用户翻卡时手动加词后回填）',
  `phonetic`    VARCHAR(128) DEFAULT NULL COMMENT '音标（冗余存，避免词库变动后丢失）',
  `meaning`     VARCHAR(512) DEFAULT NULL COMMENT '释义（冗余存）',
  `example`     VARCHAR(512) DEFAULT NULL COMMENT '英文例句',
  `example_cn`  VARCHAR(512) DEFAULT NULL COMMENT '例句中文翻译',
  `is_mastered` TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '用户是否标记为掌握 1=是 0=否/模糊/不记得',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_round` (`round_id`),
  KEY `idx_user_word` (`user_id`, `word`),
  KEY `idx_user_round` (`user_id`, `round_id`)
) ENGINE=InnoDB COMMENT='学习轮次-单词明细';

-- ---------------------------------------------------------------
-- 若表已存在（先于本补丁建库），增量补充 4 列（MySQL 8 不支持 IF NOT EXISTS，
-- 重复执行前请确认；列已存在会报错可忽略）
-- ---------------------------------------------------------------
ALTER TABLE `learn_round_item`
  ADD COLUMN `phonetic`   VARCHAR(128) DEFAULT NULL COMMENT '音标'      AFTER `word_id`,
  ADD COLUMN `meaning`    VARCHAR(512) DEFAULT NULL COMMENT '释义'      AFTER `phonetic`,
  ADD COLUMN `example`    VARCHAR(512) DEFAULT NULL COMMENT '英文例句'  AFTER `meaning`,
  ADD COLUMN `example_cn` VARCHAR(512) DEFAULT NULL COMMENT '例句中文'  AFTER `example`;

DROP TABLE IF EXISTS `learn_round`;
CREATE TABLE `learn_round` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT      NOT NULL COMMENT '用户ID',
  `source`      VARCHAR(20) NOT NULL COMMENT '来源: new新词/due待复习',
  `count`       INT         NOT NULL DEFAULT 0 COMMENT '本轮学过的词数',
  `mastered_count` INT     NOT NULL DEFAULT 0 COMMENT '标记掌握的词数',
  `started_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `finished_at` DATETIME    DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_started` (`user_id`, `started_at`)
) ENGINE=InnoDB COMMENT='学习轮次';