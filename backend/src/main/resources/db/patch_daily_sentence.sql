-- =============================================================
-- 句灵日选补丁：建 daily_sentence 表
-- 适用：每天 0:00 服务器调大模型生成一句英语佳句存入库，供任务页"句灵日选"展示
-- 用法：在 Navicat 选中 word_spirit 库 → 查询 → 粘贴并执行
-- 幂等（先 DROP 再 CREATE）
-- =============================================================

DROP TABLE IF EXISTS `daily_sentence`;
CREATE TABLE `daily_sentence` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `date`            DATE         NOT NULL                COMMENT '推送日期（唯一）',
  `en_sentence`     VARCHAR(512) NOT NULL DEFAULT ''     COMMENT '英文原句',
  `cn_trans`        VARCHAR(512) NOT NULL DEFAULT ''     COMMENT '中文释义',
  `key_collocation` VARCHAR(256) NOT NULL DEFAULT ''     COMMENT '高分词块',
  `tags`            VARCHAR(128) NOT NULL DEFAULT ''     COMMENT '主题标签（｜分隔）',
  `note`            VARCHAR(256) NOT NULL DEFAULT ''     COMMENT '意境/适用场景备注',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_date` (`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='句灵日选';

-- 兜底初始数据：万一 0:00 定时任务生成失败，可从历史随机抽取；先放 2 条做种子
INSERT INTO `daily_sentence` (`date`, `en_sentence`, `cn_trans`, `key_collocation`, `tags`, `note`) VALUES
('2026-01-01', 'Growth often lies in the quiet persistence no one sees.', '成长往往藏在无人看见的默默坚持里。', 'quiet persistence 默默坚持', '成长｜坚持', '温柔有力量，可用于成长、奋斗类英语作文段落。'),
('2026-01-02', 'Small steps every day still move you miles ahead.', '每天一小步，终将让你领先千里。', 'small steps 小步前进', '坚持｜自我提升', '适合写长期主义、习惯养成的作文段落。');

SELECT DATE(date) AS d, en_sentence FROM daily_sentence ORDER BY date DESC LIMIT 5;