-- =============================================================
-- 任务模块补丁：建 task_template / user_daily_task，并初始化任务池
-- 适用：之前已用 schema.sql 建库但任务模块是后加的，导致 user_daily_task 缺失
-- 用法：在 Navicat 选中 word_spirit 库 → 查询 → 粘贴并执行
-- 可重复执行（先 DROP 再 CREATE，幂等）
-- =============================================================

-- 1. 用户每日任务（先 DROP，避免顺序依赖）
DROP TABLE IF EXISTS `user_daily_task`;

-- 2. 任务模板表
DROP TABLE IF EXISTS `task_template`;
CREATE TABLE `task_template` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `category`    VARCHAR(32)  NOT NULL COMMENT '任务类别：review/newWord/aiUse/game/paper',
  `title`       VARCHAR(64)  NOT NULL COMMENT '任务标题',
  `description` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '任务描述',
  `icon`        VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '图标名（前端 AppIcon）',
  `path`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '跳转路径',
  `points`      INT          NOT NULL DEFAULT 0 COMMENT '完成可得活跃度',
  `weight`      INT          NOT NULL DEFAULT 1 COMMENT '抽取权重，越大越容易被抽中',
  `enabled`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用 1=启用 0=停用',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日任务模板库';

-- 3. 用户每日任务
CREATE TABLE `user_daily_task` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT   NOT NULL COMMENT '用户ID',
  `task_date`   DATE     NOT NULL COMMENT '推送日期',
  `template_id` BIGINT   NOT NULL COMMENT '关联任务模板 id',
  `category`    VARCHAR(32) NOT NULL COMMENT '任务类别',
  `done`        TINYINT  NOT NULL DEFAULT 0 COMMENT '是否完成（展示用，实际完成态由行为判定）',
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date_tpl` (`user_id`, `task_date`, `template_id`),
  KEY `idx_user_date` (`user_id`, `task_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户每日任务';

-- 4. 初始化任务池（每类 3 个变体，每天随机抽 1 个，3 天不重复）
INSERT INTO `task_template` (`category`,`title`,`description`,`icon`,`path`,`points`,`weight`,`enabled`) VALUES
('review',  '复习今日到期单词', '艾宾浩斯计划安排的单词，今日记得回顾',     'book-open', '/review',       10, 1, 1),
('review',  '清空今日复习计划', '把待复习清单全部过一遍更扎实',             'book-open', '/review',       10, 1, 1),
('review',  '巩固待复习词',     '温故而知新，别让单词溜走',                 'book-open', '/review',       10, 1, 1),
('newWord', '学习 5 个新单词',  '认识的词会自动加入生词本',                 'brain',     '/review',       15, 1, 1),
('newWord', '认识 5 个生词',    '每天扩充一点词汇量',                       'brain',     '/review',       15, 1, 1),
('newWord', '积累 5 个新词汇',  '积少成多，词汇量悄悄上涨',                 'brain',     '/review',       15, 1, 1),
('aiUse',   '用一次词灵AI',     '阅读解析 / 生词巩固 / 自由答疑任选',       'sparkles',  '/ai-assistant', 10, 1, 1),
('aiUse',   '让词灵解析一段阅读','把难句交给 AI 拆解',                       'sparkles',  '/ai-assistant', 10, 1, 1),
('aiUse',   '向词灵 AI 提个问题','有不懂的就问词灵',                         'sparkles',  '/ai-assistant', 10, 1, 1),
('game',    '玩一局单词游戏',   '在游戏中巩固单词还能赚金币',               'gamepad-2', '/game-park',    20, 1, 1),
('game',    '挑战一局拼写游戏', '手脑并用记得更牢',                         'gamepad-2', '/game-park',    20, 1, 1),
('game',    '用游戏巩固单词',   '寓教于乐，轻松记词',                       'gamepad-2', '/game-park',    20, 1, 1),
('paper',   '做一套练习试卷',   '检验学习成果，错题自动收录',               'file-text', '/paper-list',   25, 1, 1),
('paper',   '完成一份单元测试', '看看今天学得怎么样',                       'file-text', '/paper-list',   25, 1, 1),
('paper',   '做卷子检验成果',   '以测促学，查漏补缺',                       'file-text', '/paper-list',   25, 1, 1);

-- 5. 验证
SELECT category, COUNT(*) AS cnt FROM task_template GROUP BY category;
SELECT COUNT(*) AS daily_task_cnt FROM user_daily_task;
