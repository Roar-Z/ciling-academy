-- ============================================================
-- 金币商城：词库包（dict_pack）下架，新增备考资料下载商品（resource）
-- ！！重要：不要运行 schema.sql，它会 DROP TABLE 清空你的数据。
-- 本文件不删数据，仅下架旧词库 + 新增资料商品，可安全执行。
-- 资料内容：词表基于 MIT 协议 ECDICT 开源词库，句型/模板为原创，无版权风险
-- 下载走后端鉴权接口（需登录 + 已兑换 + Redis 限速），无外部直链，防抓包
-- ============================================================
USE `word_spirit`;

-- 1) shop_item 增加资料内容 key 列
ALTER TABLE `shop_item`
  ADD COLUMN `resource_key` VARCHAR(50) NULL COMMENT '备考资料内容key（resource类商品用）' AFTER `description`;

-- 2) 下架旧的三个词库包（不删除，历史订单不受影响）
UPDATE `shop_item` SET `status` = 'off_sale' WHERE `category` = 'dict_pack';

-- 3) 新增备考资料商品（金币定价 250-600，配合每日 150 金币上限形成 2-4 天攒币节奏）
INSERT INTO `shop_item` (`name`, `category`, `icon`, `price`, `description`, `status`, `resource_key`) VALUES
('中考核心词汇速记', 'resource', '📙', 250, '中考词频 TOP300 · 音标+释义速记', 'on_sale', 'zk_words'),
('四级高频词速记手册', 'resource', '📘', 300, 'CET-4 大纲核心词 3849 · 附音标释义', 'on_sale', 'cet4_words'),
('六级高频词速记手册', 'resource', '📗', 350, 'CET-6 大纲核心词 5407 · 附音标释义', 'on_sale', 'cet6_words'),
('四六级高频短语速查', 'resource', '🔖', 400, '130+ 必背短语 · 阅读写作双高频', 'on_sale', 'phrases'),
('四六级写作万能句型', 'resource', '✍️', 450, '60+ 高分句型 · 开头论证结尾全覆盖', 'on_sale', 'writing'),
('高考高频词+满分作文模板', 'resource', '📚', 500, 'TOP500 高频词 + 读后续写/应用文模板', 'on_sale', 'gk_pack'),
('考研英语核心高频词汇', 'resource', '📕', 600, '大纲词频 TOP800 · 按真题词频排序', 'on_sale', 'kaoyan_words');
