-- 勋章/称号图标从 emoji 换为 lucide 开源图标（MIT，离线包渲染）
UPDATE `shop_item` SET `icon` = 'sprout'          WHERE `name` = '萌芽勋章';
UPDATE `shop_item` SET `icon` = 'feather'         WHERE `name` = '破茧勋章';
UPDATE `shop_item` SET `icon` = 'mountain'        WHERE `name` = '登峰勋章';
UPDATE `shop_item` SET `icon` = 'crown'           WHERE `name` = '传奇勋章';
UPDATE `shop_item` SET `icon` = 'graduation-cap'  WHERE `name` = '称号·学无止境';
UPDATE `shop_item` SET `icon` = 'lightbulb'       WHERE `name` = '称号·记忆大师';
UPDATE `shop_item` SET `icon` = 'trophy'          WHERE `name` = '称号·词汇破万';
