-- 商城再平衡：价格上调 + 称号图标更换 + 新增传奇勋章（2026-09-06）
USE `word_spirit`;

UPDATE `shop_item` SET `price` = 500   WHERE `id` = 1;  -- 萌芽勋章
UPDATE `shop_item` SET `price` = 3000  WHERE `id` = 2;  -- 破茧勋章
UPDATE `shop_item` SET `price` = 8000  WHERE `id` = 3;  -- 登峰勋章

INSERT INTO `shop_item` (`name`, `category`, `icon`, `price`, `description`)
SELECT '传奇勋章', 'medal', '👑', 15000, '以词为剑，以恒为盾，做学习的传奇'
WHERE NOT EXISTS (SELECT 1 FROM `shop_item` WHERE `name` = '传奇勋章');

UPDATE `shop_item` SET `icon` = '🎓', `price` = 1200  WHERE `id` = 10; -- 称号·学无止境
UPDATE `shop_item` SET `icon` = '💡', `price` = 4000  WHERE `id` = 11; -- 称号·记忆大师
UPDATE `shop_item` SET `icon` = '🏆', `price` = 12000 WHERE `id` = 12; -- 称号·一词封神

UPDATE `shop_item` SET `price` = 800  WHERE `id` = 13; -- 词库·CET4
UPDATE `shop_item` SET `price` = 1500 WHERE `id` = 14; -- 词库·CET6
UPDATE `shop_item` SET `price` = 3000 WHERE `id` = 15; -- 词库·考研
