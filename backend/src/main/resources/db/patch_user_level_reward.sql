-- ============================================================
-- 用户等级系统 v2.0 补丁：等级升级奖励领取表
-- ！！重要：不要运行 schema.sql，它会 DROP TABLE 清空你的数据。
-- 本文件：幂等、不删数据、可重复执行。直接在现有数据库上运行即可。
-- ============================================================
USE `word_spirit`;

CREATE TABLE IF NOT EXISTS `user_level_reward` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT       NOT NULL COMMENT '用户ID',
  `level`      INT          NOT NULL COMMENT '达成的等级（2-20）',
  `coin_award` INT          NOT NULL DEFAULT 0 COMMENT '奖励金币数',
  `ai_award`   INT          NOT NULL DEFAULT 0 COMMENT '奖励永久AI次数',
  `claimed_at` DATETIME     NULL COMMENT '领取时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_level` (`user_id`, `level`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户等级升级奖励领取记录（每级仅可领一次）';
