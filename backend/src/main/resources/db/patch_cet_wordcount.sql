-- ============================================================
-- 补丁：四六级词库包扩容后同步商城条目描述（500 → 全量大纲词表）
-- 数据文件已扩容：四级 3849 词 / 六级 5805 词（含四级全部词汇）（ECDICT 按 tag 全量 + 词频排序）
-- 执行方式：请在 DB 客户端手动执行（勿在命令行携带凭据）
-- 说明：仅更新描述文案，不涉及商品价格/库存；重复执行无副作用
-- ============================================================

UPDATE shop_item
SET description = 'CET-4 大纲核心词 3849 · 附音标释义'
WHERE resource_key = 'cet4_words';

UPDATE shop_item
SET description = 'CET-6 大纲核心词 5805 · 附音标释义（含四级词汇）'
WHERE resource_key = 'cet6_words';

-- 校验（应各返回 1 行且描述已更新）
SELECT resource_key, title, description FROM shop_item
WHERE resource_key IN ('cet4_words', 'cet6_words');
