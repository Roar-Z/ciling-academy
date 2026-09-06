-- =============================================================
-- 今日句灵日选（2026-09-03）补数据
-- 用途：今天 0:00 定时任务如果在部署前没跑/AI 不可用，用这条立即可见
-- 用法：Navicat 选中 word_spirit 库 → 查询 → 粘贴执行
-- 说明：无需重启后端、无需清 Redis（getToday 已对 fallback 缓存做当天二次确认）
-- =============================================================

INSERT INTO daily_sentence (`date`, en_sentence, cn_trans, key_collocation, tags, note)
VALUES ('2026-09-03',
        'The quietest growth happens where no one is watching.',
        '最安静的成长，发生在无人注视的地方。',
        'happen where 发生在……之处',
        '成长｜沉淀',
        '适合写坚持、内修类英语作文的升华句。')
ON DUPLICATE KEY UPDATE
  en_sentence      = VALUES(en_sentence),
  cn_trans         = VALUES(cn_trans),
  key_collocation  = VALUES(key_collocation),
  tags             = VALUES(tags),
  note             = VALUES(note);

SELECT * FROM daily_sentence WHERE `date` = '2026-09-03';
