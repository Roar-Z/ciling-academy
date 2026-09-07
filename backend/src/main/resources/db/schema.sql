-- =============================================================
-- 词灵学园 数据库建表脚本
-- 数据库：word_spirit  (charset=utf8mb4)
-- =============================================================
CREATE DATABASE IF NOT EXISTS `word_spirit` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `word_spirit`;

-- ----------------------------
-- 1. 用户表
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`      VARCHAR(50)  NOT NULL COMMENT '用户名（唯一）',
  `password`      VARCHAR(100) NOT NULL COMMENT 'BCrypt加密密码',
  `nickname`      VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '昵称',
  `avatar`        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '头像地址',
  `email`         VARCHAR(100) NOT NULL DEFAULT '' COMMENT '邮箱',
  `bio`           VARCHAR(200) NOT NULL DEFAULT '' COMMENT '个人简介/座右铭',
  `today_words`   INT          NOT NULL DEFAULT 0 COMMENT '今日已学单词数',
  `total_words`   INT          NOT NULL DEFAULT 0 COMMENT '累计学习单词数',
  `total_mastered` INT         NOT NULL DEFAULT 0 COMMENT '累计掌握单词数（familiarity>=80 的去重数，持久化，清空生词本不影响）',
  `mastered_words` JSON         DEFAULT NULL COMMENT '已掌握单词集合 JSON 数组（新词学习"认识"直接加入，与生词本无关）',
  `study_days`    INT          NOT NULL DEFAULT 0 COMMENT '连续学习天数',
  `review_batch_size` INT      NOT NULL DEFAULT 10 COMMENT '每轮复习/学习单词数（偏好持久化，跨设备）',
  `daily_goal`       INT      NOT NULL DEFAULT 20 COMMENT '每日学习目标（用户可自定义，5-200）',
  `last_study_date` DATE       DEFAULT NULL COMMENT '最后学习日期（用于连续天数计算）',
  `ai_used_today` INT          NOT NULL DEFAULT 0 COMMENT '今日AI调用次数（异步统计用）',
  `ai_used_total` INT          NOT NULL DEFAULT 0 COMMENT '累计AI调用次数（异步统计用）',
  `notify_review` TINYINT      NOT NULL DEFAULT 1 COMMENT '是否开启今日复习提醒通知（1=开 0=关）',
  `notify_quota`  TINYINT      NOT NULL DEFAULT 1 COMMENT '是否开启AI额度提醒通知（1=开 0=关）',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB COMMENT='用户表';

-- ----------------------------
-- 2. 单词词典表（基础释义来源，不消耗AI额度）
-- ----------------------------
DROP TABLE IF EXISTS `dict_word`;
CREATE TABLE `dict_word` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `word`        VARCHAR(64) NOT NULL COMMENT '单词',
  `phonetic`    VARCHAR(64) NOT NULL DEFAULT '' COMMENT '音标',
  `pos`         VARCHAR(32) NOT NULL DEFAULT '' COMMENT '词性',
  `meaning`     VARCHAR(512) NOT NULL COMMENT '中文释义',
  `example`     VARCHAR(512) NOT NULL DEFAULT '' COMMENT '例句',
  `example_cn`  VARCHAR(512) NOT NULL DEFAULT '' COMMENT '例句翻译',
  `difficulty`  TINYINT     NOT NULL DEFAULT 1 COMMENT '难度等级1-5',
  `level`       VARCHAR(8)  NOT NULL DEFAULT 'other' COMMENT '主等级: cet4(四级)/cet6(六级)/gaokao(高考)/zhongkao(中考)/other(其他)',
  `levels`      VARCHAR(64) NOT NULL DEFAULT '' COMMENT '词库多标签csv(档位序): zhongkao,gaokao,cet4,cet6——选级过滤用 FIND_IN_SET(levels)',
  `is_core`     TINYINT     NOT NULL DEFAULT 0 COMMENT '是否高频核心词 1=是 0=否（优先推送）',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`),
  KEY `idx_difficulty` (`difficulty`),
  KEY `idx_level` (`level`),
  KEY `idx_core` (`is_core`)
) ENGINE=InnoDB COMMENT='单词词典';

-- ----------------------------
-- 3. 生词本
-- ----------------------------
DROP TABLE IF EXISTS `word_book`;
CREATE TABLE `word_book` (
  `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`          BIGINT      NOT NULL COMMENT '用户ID',
  `word`             VARCHAR(64) NOT NULL COMMENT '单词',
  `phonetic`         VARCHAR(64) NOT NULL DEFAULT '' COMMENT '音标',
  `meaning`          VARCHAR(512) NOT NULL DEFAULT '' COMMENT '释义',
  `source`           VARCHAR(20) NOT NULL DEFAULT 'manual' COMMENT '来源: manual手动/review复习自动加入/reading阅读/ai AI助手',
  `familiarity`      INT         NOT NULL DEFAULT 0 COMMENT '熟悉度0-100',
  `review_count`     INT         NOT NULL DEFAULT 0 COMMENT '复习次数',
  `next_review_at`   DATETIME    DEFAULT NULL COMMENT '艾宾浩斯下次复习时间点（精确到分钟）',
  `last_review_at`   DATETIME    DEFAULT NULL COMMENT '最近复习时间点',
  `mastered_at`      DATETIME    DEFAULT NULL COMMENT '首次掌握时间（熟悉度首次>=80，防重复计数）',
  `ai_note`          TEXT        COMMENT 'AI助记内容JSON',
  `word_usage`       TEXT        COMMENT '阅读解析要点JSON(语法/搭配/近义/释义/来源句)',
  `created_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_word` (`user_id`, `word`),
  KEY `idx_user_review` (`user_id`, `next_review_at`)
) ENGINE=InnoDB COMMENT='生词本';

-- ----------------------------
-- 4. 句灵集（用户收藏的翻译句子）
-- ----------------------------
DROP TABLE IF EXISTS `sentence`;
CREATE TABLE `sentence` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`           BIGINT      NOT NULL COMMENT '用户ID',
  `original_text`     VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '原文',
  `translation_text`  VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '译文',
  `direction`         VARCHAR(10) NOT NULL DEFAULT 'en2zh' COMMENT '翻译方向 en2zh/zh2en',
  `mode`              VARCHAR(10) NOT NULL DEFAULT 'normal' COMMENT '翻译模式 normal/ai',
  `source`            VARCHAR(20) NOT NULL DEFAULT 'translate' COMMENT '来源',
  `created_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `created_at`)
) ENGINE=InnoDB COMMENT='句灵集';

-- ----------------------------
-- 5. AI会话表
-- ----------------------------
DROP TABLE IF EXISTS `ai_chat_session`;
CREATE TABLE `ai_chat_session` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`         BIGINT       NOT NULL COMMENT '用户ID',
  `title`           VARCHAR(100) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
  `mode`            VARCHAR(30)  NOT NULL DEFAULT 'chat' COMMENT '模式: chat答疑/word_review生词巩固/paper生成试卷',
  `source_title`    VARCHAR(200) NOT NULL DEFAULT '' COMMENT '来源标题（来自哪个业务页面的提示）',
  `last_message_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后消息时间',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB COMMENT='AI会话表';

-- ----------------------------
-- 5. AI消息表（原始JSON + 预览文本）
-- ----------------------------
DROP TABLE IF EXISTS `ai_chat_message`;
CREATE TABLE `ai_chat_message` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id`  BIGINT       NOT NULL COMMENT '会话ID',
  `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
  `role`        VARCHAR(10)  NOT NULL COMMENT '角色: user/assistant',
  `content`     TEXT         COMMENT '用户输入原文或AI渲染预览文本',
  `json_data`   LONGTEXT     COMMENT 'AI原始JSON数据（供导入业务库/回看）',
  `render_type` VARCHAR(30)  NOT NULL DEFAULT 'text' COMMENT '渲染类型: text/cards/paper/refuse',
  `is_favorited` TINYINT     NOT NULL DEFAULT 0 COMMENT '是否已收藏',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_created` (`session_id`, `created_at`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='AI消息表';

-- ----------------------------
-- 6. 阅读解析缓存表（同一文本只解析一次）
-- ----------------------------
DROP TABLE IF EXISTS `ai_word_explain`;
CREATE TABLE `ai_word_explain` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`      BIGINT       NOT NULL COMMENT '用户ID',
  `content_hash` CHAR(32)     NOT NULL COMMENT '原文MD5，用于幂等缓存',
  `text_preview` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '原文预览',
  `difficulty`   VARCHAR(10)  NOT NULL DEFAULT '' COMMENT '难度 easy/medium/hard',
  `explain_json` LONGTEXT     COMMENT '解析结果JSON',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_hash` (`user_id`, `content_hash`)
) ENGINE=InnoDB COMMENT='阅读解析缓存表';

-- ----------------------------
-- 7. 生词巩固包表
-- ----------------------------
DROP TABLE IF EXISTS `ai_review_content`;
CREATE TABLE `ai_review_content` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
  `title`       VARCHAR(100) NOT NULL DEFAULT '' COMMENT '巩固包标题',
  `words`       VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '单词逗号分隔，便于检索',
  `review_json` LONGTEXT     COMMENT '巩固包完整JSON',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB COMMENT='生词巩固包表';

-- ----------------------------
-- 8. AI收藏表（我的AI笔记）
-- ----------------------------
DROP TABLE IF EXISTS `ai_favorite`;
CREATE TABLE `ai_favorite` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
  `session_id`  BIGINT       NOT NULL DEFAULT 0 COMMENT '来源会话ID',
  `message_id`  BIGINT       NOT NULL DEFAULT 0 COMMENT '来源消息ID',
  `title`       VARCHAR(200) NOT NULL DEFAULT '' COMMENT '收藏标题',
  `content`     LONGTEXT     COMMENT '收藏内容（渲染预览文本）',
  `json_data`   LONGTEXT     COMMENT '收藏原始JSON',
  `render_type` VARCHAR(30)  NOT NULL DEFAULT 'text' COMMENT '渲染类型',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB COMMENT='AI收藏表（我的AI笔记）';

-- ----------------------------
-- 9. 试卷主表
-- ----------------------------
DROP TABLE IF EXISTS `exercise_paper`;
CREATE TABLE `exercise_paper` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`         BIGINT       NOT NULL COMMENT '用户ID',
  `paper_name`      VARCHAR(100) NOT NULL COMMENT '试卷名称',
  `paper_intro`     VARCHAR(500) NOT NULL DEFAULT '' COMMENT '卷首说明',
  `point_summary`   TEXT         COMMENT '考点小结',
  `source`          VARCHAR(20)  NOT NULL DEFAULT 'manual' COMMENT '来源: ai/manual',
  `content_hash`    CHAR(32)     DEFAULT NULL COMMENT '内容指纹，防同一份试卷重复导入',
  `question_count`  INT          NOT NULL DEFAULT 0 COMMENT '题目数量',
  `total_score`     INT          NOT NULL DEFAULT 0 COMMENT '总分',
  `best_score`      INT          NOT NULL DEFAULT 0 COMMENT '历史最高分',
  `done_count`      INT          NOT NULL DEFAULT 0 COMMENT '作答次数',
  `status`          VARCHAR(10)  NOT NULL DEFAULT 'normal' COMMENT '状态: normal/normal',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB COMMENT='试卷主表';

-- ----------------------------
-- 10. 题目明细表
-- ----------------------------
DROP TABLE IF EXISTS `exercise_question`;
CREATE TABLE `exercise_question` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `paper_id`    BIGINT       NOT NULL COMMENT '试卷ID',
  `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
  `q_type`      VARCHAR(20)  NOT NULL COMMENT '题型: en2cn/cn2en/spell_fill/context_choice/match',
  `seq`         INT          NOT NULL DEFAULT 0 COMMENT '题序',
  `stem`        TEXT         NOT NULL COMMENT '题干',
  `opts`        TEXT         COMMENT '选项JSON数组（match为空）',
  `ans`         TEXT         NOT NULL COMMENT '标准答案JSON（match为数组）',
  `analysis`    TEXT         COMMENT '解析',
  `score`       INT          NOT NULL DEFAULT 10 COMMENT '分值',
  `user_answer` TEXT         COMMENT '用户作答JSON',
  `is_correct`  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否答对',
  `wrong_excluded` TINYINT   NOT NULL DEFAULT 0 COMMENT '已从错题本移除 0/1',
  `done_time`   DATETIME     DEFAULT NULL COMMENT '作答时间',
  PRIMARY KEY (`id`),
  KEY `idx_paper_seq` (`paper_id`, `seq`),
  KEY `idx_user_type` (`user_id`, `q_type`)
) ENGINE=InnoDB COMMENT='题目明细表';

-- ----------------------------
-- 11. 游戏记录表
-- ----------------------------
DROP TABLE IF EXISTS `game_record`;
CREATE TABLE `game_record` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       BIGINT       NOT NULL COMMENT '用户ID',
  `game_id`       VARCHAR(30)  NOT NULL COMMENT '游戏标识 game1~game10',
  `game_name`     VARCHAR(50)  NOT NULL COMMENT '游戏名称',
  `score`         INT          NOT NULL DEFAULT 0 COMMENT '本局得分',
  `coins`         INT          NOT NULL DEFAULT 0 COMMENT '本局获得金币',
  `correct_count` INT          NOT NULL DEFAULT 0 COMMENT '答对题数',
  `total_count`   INT          NOT NULL DEFAULT 0 COMMENT '总题数',
  `correct_rate`  DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '正确率%',
  `duration_sec`  INT          NOT NULL DEFAULT 0 COMMENT '用时（秒）',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '完成时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_game` (`user_id`, `game_id`, `created_at`),
  KEY `idx_game_score` (`game_id`, `score`)
) ENGINE=InnoDB COMMENT='游戏记录表';

-- ----------------------------
-- 12. 用户金币表
-- ----------------------------
DROP TABLE IF EXISTS `user_coin`;
CREATE TABLE `user_coin` (
  `id`            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       BIGINT   NOT NULL COMMENT '用户ID',
  `coin_balance`  INT      NOT NULL DEFAULT 0 COMMENT '当前金币余额',
  `total_earned`  INT      NOT NULL DEFAULT 0 COMMENT '累计获得',
  `total_spent`   INT      NOT NULL DEFAULT 0 COMMENT '累计消费',
  `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB COMMENT='用户金币表';

-- ----------------------------
-- 13. 商城商品表
-- ----------------------------
DROP TABLE IF EXISTS `shop_item`;
CREATE TABLE `shop_item` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`        VARCHAR(50)  NOT NULL COMMENT '商品名',
  `category`    VARCHAR(20)  NOT NULL COMMENT '分类: medal勋章/avatar_frame头像框/chat_bubble聊天气泡/resource备考资料',
  `icon`        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '图标（emoji或CSS类）',
  `price`       INT          NOT NULL DEFAULT 0 COMMENT '价格（金币）',
  `description` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '商品描述',
  `resource_key` VARCHAR(50) DEFAULT NULL COMMENT '备考资料内容key（resource类商品用）',
  `status`      VARCHAR(10)  NOT NULL DEFAULT 'on_sale' COMMENT '状态: on_sale上架/off_sale下架',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB COMMENT='商城商品表';

-- ----------------------------
-- 14. 用户已拥有装扮表
-- ----------------------------
DROP TABLE IF EXISTS `user_goods`;
CREATE TABLE `user_goods` (
  `id`          BIGINT    NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT    NOT NULL COMMENT '用户ID',
  `item_id`     BIGINT    NOT NULL COMMENT '商品ID',
  `equipped`    TINYINT   NOT NULL DEFAULT 0 COMMENT '是否佩戴(1是0否)',
  `obtained_at` DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获得时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_item` (`user_id`, `item_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='用户已拥有装扮表';

-- ----------------------------
-- 15. 邮箱黑名单表（注销账号后冻结邮箱 7 天，期间不可再注册）
-- ----------------------------
DROP TABLE IF EXISTS `sys_email_blacklist`;
CREATE TABLE `sys_email_blacklist` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `email`         VARCHAR(100) NOT NULL COMMENT '被冻结的邮箱（小写）',
  `user_id`       BIGINT       DEFAULT NULL COMMENT '注销时的用户ID（追溯用）',
  `reason`        VARCHAR(50)  NOT NULL DEFAULT '注销账号' COMMENT '冻结原因',
  `expire_at`     DATETIME     NOT NULL COMMENT '解冻时间',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '冻结时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email` (`email`),
  KEY `idx_expire` (`expire_at`)
) ENGINE=InnoDB COMMENT='邮箱黑名单表';

-- ----------------------------
-- 16. 每日打卡记录表（每日一行，幂等去重）
-- ----------------------------
DROP TABLE IF EXISTS `task_check_in`;
CREATE TABLE `task_check_in` (
  `id`             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`        BIGINT   NOT NULL COMMENT '用户ID',
  `check_in_date`  DATE     NOT NULL COMMENT '打卡日期（用户本地日，按服务器统一记录）',
  `streak_after`   INT      NOT NULL DEFAULT 1 COMMENT '本次打卡后的连续天数',
  `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '打卡时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `check_in_date`),
  KEY `idx_user_date` (`user_id`, `check_in_date`)
) ENGINE=InnoDB COMMENT='每日打卡记录表';

-- ----------------------------
-- 17. 连续打卡奖励领取记录表（每个用户每个里程碑仅可领一次）
-- ----------------------------
DROP TABLE IF EXISTS `task_streak_reward_claim`;
CREATE TABLE `task_streak_reward_claim` (
  `id`            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       BIGINT   NOT NULL COMMENT '用户ID',
  `streak_days`   INT      NOT NULL COMMENT '连续天数里程碑：7/14/21/30',
  `ai_quota_award` INT     NOT NULL DEFAULT 0 COMMENT '本次奖励的AI调用额度',
  `coin_award`    INT      NOT NULL DEFAULT 0 COMMENT '本次奖励的金币',
  `claimed_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_streak` (`user_id`, `streak_days`)
) ENGINE=InnoDB COMMENT='连续打卡奖励领取记录';

-- ----------------------------
-- 18. 任务活跃度奖励领取记录表（活跃度每日重置，同一用户每天每档位可领一次）
-- ----------------------------
DROP TABLE IF EXISTS `task_active_reward_claim`;
CREATE TABLE `task_active_reward_claim` (
  `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`      BIGINT   NOT NULL COMMENT '用户ID',
  `threshold`    INT      NOT NULL COMMENT '活跃度阈值：25/50/75/100',
  `claim_date`   DATE     NOT NULL DEFAULT (CURRENT_DATE) COMMENT '领取日期（按天隔离）',
  `coin_award`   INT      NOT NULL DEFAULT 0 COMMENT '本次奖励的金币',
  `ai_quota_award` INT    NOT NULL DEFAULT 0 COMMENT '本次奖励的AI调用额度',
  `claimed_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_threshold_date` (`user_id`, `threshold`, `claim_date`)
) ENGINE=InnoDB COMMENT='任务活跃度奖励领取记录';

-- ----------------------------
-- 单词测验批次（一次测验的整体记录）
-- ----------------------------
DROP TABLE IF EXISTS `word_test_batch`;
CREATE TABLE `word_test_batch` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       BIGINT      NOT NULL COMMENT '用户ID',
  `mode`          VARCHAR(20) NOT NULL COMMENT '测验模式: spelling看中文拼英文/matching中英连线/sentence翻译句子',
  `source`      VARCHAR(20) NOT NULL COMMENT '来源: new新词/due待复习',
  `total`         INT         NOT NULL DEFAULT 0 COMMENT '本批词数',
  `correct_count` INT         NOT NULL DEFAULT 0 COMMENT '答对题数',
  `time_limit_sec` INT        NOT NULL DEFAULT 30 COMMENT '单题限时(秒)',
  `started_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '批次开始时间',
  `finished_at`   DATETIME    DEFAULT NULL COMMENT '批次完成时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_started` (`user_id`, `started_at`)
) ENGINE=InnoDB COMMENT='单词测验批次';

-- ----------------------------
-- 单词测验答题记录（每题一行）
-- ----------------------------
DROP TABLE IF EXISTS `word_test_record`;
CREATE TABLE `word_test_record` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT      NOT NULL COMMENT '用户ID',
  `batch_id`    BIGINT      NOT NULL COMMENT '所属批次',
  `word_id`     BIGINT      NOT NULL COMMENT '生词本单词ID',
  `word`        VARCHAR(64) NOT NULL DEFAULT '' COMMENT '单词(冗余存储,避免词条删除后查不到)',
  `mode`        VARCHAR(20) NOT NULL COMMENT '测验模式',
  `correct`     TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '0答错 1答对',
  `cost_ms`     INT         NOT NULL DEFAULT 0 COMMENT '用时毫秒',
  `user_answer` VARCHAR(512) NOT NULL DEFAULT '' COMMENT '用户作答内容',
  `tested_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '答题时间',
  PRIMARY KEY (`id`),
  KEY `idx_batch` (`batch_id`),
  KEY `idx_user_word` (`user_id`, `word_id`)
) ENGINE=InnoDB COMMENT='单词测验答题记录';

-- ----------------------------
-- 19. 系统通知表（每用户最多保留 30 条，超出自动删除最早；未读会高亮）
-- ----------------------------
DROP TABLE IF EXISTS `sys_notification`;
CREATE TABLE `sys_notification` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT       NOT NULL COMMENT '用户ID',
  `type`       VARCHAR(32)  NOT NULL COMMENT '类型：ai_daily / review_reminder / register_bonus / reward_check_in / reward_streak / reward_active / gen / system',
  `title`      VARCHAR(128) NOT NULL COMMENT '标题',
  `content`    VARCHAR(512) NOT NULL DEFAULT '' COMMENT '详情',
  `is_read`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已读 0=未读 1=已读',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB COMMENT='系统通知表';

-- ----------------------------
-- 21. 每日任务模板库（随机推送的任务池，每类多个变体）
-- ----------------------------
DROP TABLE IF EXISTS `task_template`;
CREATE TABLE `task_template` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `category`    VARCHAR(32) NOT NULL COMMENT '任务类别：review/newWord/aiUse/game/paper',
  `title`       VARCHAR(64) NOT NULL COMMENT '任务标题',
  `description` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '任务描述',
  `icon`        VARCHAR(32) NOT NULL DEFAULT '' COMMENT '图标名（前端 AppIcon）',
  `path`        VARCHAR(64) NOT NULL DEFAULT '' COMMENT '跳转路径',
  `points`      INT         NOT NULL DEFAULT 0 COMMENT '完成可得活跃度',
  `weight`      INT         NOT NULL DEFAULT 1 COMMENT '抽取权重，越大越容易被抽中',
  `enabled`     TINYINT     NOT NULL DEFAULT 1 COMMENT '是否启用 1=启用 0=停用',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB COMMENT='每日任务模板库';

-- ----------------------------
-- 22. 用户每日任务（每天随机推送的记录，保证刷新不重复）
-- ----------------------------
DROP TABLE IF EXISTS `user_daily_task`;
CREATE TABLE `user_daily_task` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT   NOT NULL COMMENT '用户ID',
  `task_date`   DATE     NOT NULL COMMENT '推送日期',
  `template_id` BIGINT   NOT NULL COMMENT '关联任务模板',
  `category`    VARCHAR(32) NOT NULL COMMENT '任务类别',
  `done`        TINYINT  NOT NULL DEFAULT 0 COMMENT '是否完成（展示用，实际完成态由行为判定）',
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date_tpl` (`user_id`, `task_date`, `template_id`),
  KEY `idx_user_date` (`user_id`, `task_date`)
) ENGINE=InnoDB COMMENT='用户每日任务';

-- ----------------------------
-- 升级补丁：sys_user 增加通知偏好字段（幂等：仅当列不存在时添加）
-- ----------------------------
SET @has_notify = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'notify_review');
SET @sql_notify = IF(@has_notify = 0, 'ALTER TABLE `sys_user` ADD COLUMN `notify_review` TINYINT NOT NULL DEFAULT 1 COMMENT ''是否开启今日复习提醒通知（1=开 0=关）'', ADD COLUMN `notify_quota` TINYINT NOT NULL DEFAULT 1 COMMENT ''是否开启AI额度提醒通知（1=开 0=关）''', 'SELECT 1');
PREPARE stmt FROM @sql_notify; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------
-- 升级补丁：sys_user 增加 total_mastered 字段（累计掌握，持久化，清空生词本不影响）
-- ----------------------------
SET @has_total_mastered = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'total_mastered');
SET @sql_total_mastered = IF(@has_total_mastered = 0, 'ALTER TABLE `sys_user` ADD COLUMN `total_mastered` INT NOT NULL DEFAULT 0 COMMENT \'累计掌握单词数（familiarity>=80 去重，持久化）\'', 'SELECT 1');
PREPARE stmt FROM @sql_total_mastered;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 升级补丁：sys_user 增加 mastered_words JSON（新词学习"认识"加入该集合，与生词本无关）
SET @has_mastered_words = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'mastered_words');
SET @sql_mastered_words = IF(@has_mastered_words = 0, 'ALTER TABLE `sys_user` ADD COLUMN `mastered_words` JSON DEFAULT NULL COMMENT \'已掌握单词集合 JSON 数组\' ', 'SELECT 1');
PREPARE stmt FROM @sql_mastered_words;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 数据迁移：用 word_book 当前 familiarity>=80 的不同词数回填 total_mastered（避免老用户从 0 起步）
UPDATE `sys_user` u
SET u.total_mastered = (
  SELECT COUNT(DISTINCT wb.word)
  FROM `word_book` wb
  WHERE wb.user_id = u.id AND wb.familiarity >= 80
)
WHERE u.total_mastered = 0;

-- ----------------------------
-- 升级补丁：word_book 复习时间从 DATE 升级到 DATETIME（幂等：仅当旧列仍为 DATE 时改）
-- ----------------------------
SET @has_old_date = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'word_book' AND COLUMN_NAME = 'next_review_date' AND DATA_TYPE = 'date');
SET @sql_review_at = IF(@has_old_date > 0, 'ALTER TABLE `word_book` CHANGE COLUMN `next_review_date` `next_review_at` DATETIME DEFAULT NULL COMMENT ''艾宾浩斯下次复习时间点'', CHANGE COLUMN `last_review_date` `last_review_at` DATETIME DEFAULT NULL COMMENT ''最近复习时间点''', 'SELECT 1');
PREPARE stmt FROM @sql_review_at; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------
-- 升级补丁：dict_word 增加 level / is_core（四六级等级 + 高频核心词标记）
-- 幂等：仅当列不存在时添加；全新库 CREATE 已含列则自动跳过，避免重复加列报错
-- ----------------------------
SET @has_level = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dict_word' AND COLUMN_NAME = 'level');
SET @has_core  = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dict_word' AND COLUMN_NAME = 'is_core');
SET @sql_lvl = IF(@has_level = 0, 'ALTER TABLE `dict_word` ADD COLUMN `level` VARCHAR(8) NOT NULL DEFAULT \'other\' COMMENT \'等级: cet4/cet6/other\'', 'SELECT 1');
SET @sql_core = IF(@has_core = 0, 'ALTER TABLE `dict_word` ADD COLUMN `is_core` TINYINT NOT NULL DEFAULT 0 COMMENT \'是否高频核心词 1=是 0=否\'', 'SELECT 1');
PREPARE stmt FROM @sql_lvl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
PREPARE stmt FROM @sql_core; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 按 difficulty 回填 level（CET4=2 → cet4，CET6=4 → cet6，其余 → other）
UPDATE `dict_word` SET `level` = 'cet4' WHERE `difficulty` = 2 AND `level` = 'other';
UPDATE `dict_word` SET `level` = 'cet6' WHERE `difficulty` = 4 AND `level` = 'other';

-- 高频核心词打标（来源：exam-data/CETVocabulary，考纲5278词按真题试卷词频排序的 TOP 核心词）
-- 仅词库 dict_word 中真实存在的词会被标记（the/a/to 等基础虚词不在四六级实词库，自动忽略）
-- 幂等：可重复执行
UPDATE `dict_word` SET `is_core` = 1 WHERE `word` IN (
  'the','a','to','and','in','have','that','for','on','they','you','with','as','their','by','not','he','from','at','will','more','do','we','passage','this','or','can','i','one','but','question','people','what','there','well','about','answer','make','than','his','time','say','work','which','when','should','part','your','use','all','follow','she','who','each','some','other','if','year','write','new','section','its','word','mark','may','take','many','most','read','up','her','only','would','go','hear','give','base','no','so','get','two','student','our','out','just','child','how','find','way','into','because','sheet','like','through','woman','school','think','world','choice','change','much','life','long','study','need','first','help','four','job','learn','high','now','good','over','then','minute','after','live','know','line','these','become','author','could','even','also','come','mean','such','letter','see','three','those','end','once','company','problem','want','must','food','country','choose','any','my','day','less','accord','show','both','very','great','thing','look','too','business','between','research','before','number','single','own','feel','family','often','old','parent','increase','paragraph','provide','few','home','while','last','point','text','correspond','place','pay','language','still','keep','where','book','college','second','listen','why','system','put','blank','might','result','try','money','ask','seem','speak','news','example','same','important','right','report','believe','public','health','far','young','call','large','city','develop','start','another','during','idea','allow','science','age','every','leave','talk','require','car','society','short','cause','down','technology','grow','sentence','without','hour','big','begin','lead','build','early','off','spend','little','bank','hard','class','cost','group','price','effect','today','tell','set','something','play','course','buy','understand','offer','small','product','experience','suggest','decide','test','bring','since','however','person','around','whether','future','reason','view','water','improve','care','benefit','low','program','market','teach','support','among','kind','friend','skill','yet','form','include','move','create','never','rather','century','bad','eat','always','least','face','plan','consider','industry','themselves','process','rate','fact','house','culture','power','share','area','million','lot','sense','lose','against','rise','energy','control','space','case','hold','possible','animal','enough','value','role','week','individual','hand','open','ten','term','away','happen','fall','art','self','drive','interest','issue','produce','complete','under','brain','level','able','run','past','win','present','quality','difficult','easy','reduce','tend','five','expect','order','environment','law','paper','kid','mind','economy','percent','sleep','recent','half','close','attention','free','poor','body','measure','office','travel','major','professor','remain','though','next','design','history','nation','real','almost','customer','subject','ago','focus','local','certain','ever','success','late','true','name','matter','stress','identify','involve','raise','online','stay','population','risk','medium','fill','lack','strong','patient','rule','until','continue','receive','already','record','sell','month','note','community','demand','instead','challenge','store','check','deal','opportunity','list','average','although','break','eye','fast','common','condition','within','light','meet','shop','concern','task','effort','plant','encourage','career','fail','avoid','influence','music','add','item','policy','force','member','cut','enjoy','explain','side','adult','general','international','nature','type','doctor','mother','save','several','situation','essay','game','graduate','decade','medical','miss','train','interview','third','stop','reach','sign','earth','project','generation','rich','standard','story','war','again','air','contain','night','main','love','soon','sound','account','affect','return','sure','translate','describe','full','purpose','room','appear','center','impact','period','quite','television','position','attitude','machine','clear','field','pass','below','here','protect','lecture','factor','top','draw','firm','memory','whole','watch','death','white','bear','claim','decline','model','wrong','expert','act','carry','send','accept','degree','potential','pause','amount','income','opinion','disease','stand','popular','argue','physical','team','material','compare','prove','nothing','perhaps','please','private','head','street','available','boy','current','goal','speed','together','along','middle','sale','grade','visit','content','source','across','gap','let','depend','especially','farm','modern','approach','correct','hope','someone','special','picture','survey','event','everyone','either','step','habit','similar','chance','necessary','apply','father','foreign','access','anything','oil','sport','exist','advantage','agree','newspaper','remember','land','solve','function','phrase','everything','experiment','sit','structure','message','seek','per','promote','figure','road','limit','sometimes','fear','supply','relate','prevent','baby','pattern','itself','search','beyond','near','regard','serious','black','climate','heart','president','serve','traffic','walk','award','determine','gain','speech','prepare','except','publish','suffer','mental','positive','outside','party','contribute','dream','drink','trade','himself','therefore','achieve','conflict','trouble','girl','exercise','status','various','above','basic','fit','occur','refer','die','attend','maintain','town','wait','alone','meeting','feature','tax','express','hundred','shift','simple','thousand','couple','image','key','theory','card','catch','realize','select','whose','cover','earn','attract','clean','trend','desire','lie','method','promise','network','billion','cook','daily','detail','normal','post','reward','upon','thus','toward','device','discover','drop','else','introduce','court','drug','range','warm','accident','fashion','indicate','particular','attach','six','slow','credit','finish','happy','bill','deep','fire','belief','moment','museum','site','address','concept','crime','damage','restaurant','size','federal','progress','mistake','robot','weather','charge','enter','infer','medicine','engage','object','race','wide','hospital','manage','huge','official','hire','hotel','perform','treat','article','profit','trip','aspect','due','wear','balance','define','enable','character','crisis','dollar','green','cold','contact','department','math','advice','board','comment','consequence','fight','meal','style','worry','tool','waste','despite','morning','predict','kill','stage','purchase','specific','print','replace','shape','significant','thirty','yourself','ground','screen','lay','sea','struggle','cross','expand','forget','multiple','wife','conduct','direct','ensure','fund','intellectual','region','aim','husband','legal','link','reflect','underline','worth','reveal','suppose','advance','practical','smoke','south','attack','holiday','male','peer','film','police','push','twenty','associate','attempt','prefer','review','wealth','title','trust','box','cry','ill','china','derive','hardly','textbook','total','track','warn','bird','extra','heavy','ignore','survive','fuel','magazine','summer','complex','danger','library','piece','throughout','institution','mention','appeal','background','colleague','exact','intend','paint','recognize','strategy','arrive','authority','cheap','factory','grant','journal','ready','tree','expense','patent','spread','chief','establish','foot','indeed','nor','observe','safe','compete','debate','educate','staff','wish','adapt','boss','fix','unless'
);

-- =============================================================
-- 词库独立-已学词记录（词书模式：各词库进度互不影响）
-- level: cet4/cet6/gaokao/zhongkao/mixed；all=存量迁移（对所有词库生效）
-- =============================================================
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
