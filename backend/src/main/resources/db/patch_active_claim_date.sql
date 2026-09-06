-- 活跃度领取记录改为按天隔离：新增 claim_date 列并重建唯一键
-- 注：MySQL 的 DATE 列不支持 DEFAULT CURRENT_DATE（无括号形式），故先建为可空再回填后置为 NOT NULL
ALTER TABLE `task_active_reward_claim`
  ADD COLUMN `claim_date` DATE NULL COMMENT '领取日期（按天隔离）' AFTER `threshold`;

UPDATE `task_active_reward_claim` SET `claim_date` = DATE(`claimed_at`) WHERE `claim_date` IS NULL;

ALTER TABLE `task_active_reward_claim`
  MODIFY COLUMN `claim_date` DATE NOT NULL DEFAULT (CURRENT_DATE) COMMENT '领取日期（按天隔离）',
  DROP INDEX `uk_user_threshold`,
  ADD UNIQUE KEY `uk_user_threshold_date` (`user_id`, `threshold`, `claim_date`);
