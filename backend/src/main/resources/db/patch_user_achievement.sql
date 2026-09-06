-- ============================================================
-- 用户成就系统补丁：成就解锁记录表
-- ！！重要：不要运行 schema.sql，它会 DROP TABLE 清空你的数据。
-- 本文件：幂等、不删数据、可重复执行。直接在现有数据库上运行即可。
-- ============================================================
USE `word_spirit`;

CREATE TABLE IF NOT EXISTS `user_achievement` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT      NOT NULL COMMENT '用户ID',
  `code`        VARCHAR(32) NOT NULL COMMENT '成就编码（后端定义）',
  `unlocked_at` DATETIME    NOT NULL COMMENT '达成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_code` (`user_id`, `code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户成就解锁记录（达成条件时由后端懒解锁写入）';
