-- =============================================================
-- 单词发音音频补丁：dict_word 增加 audio_url 列
-- 适用：背单词复习页「播放音标」功能。mp3 文件存服务器磁盘
--       uploads/audio/ 下，本列只存相对访问路径（如 /uploads/audio/optical.mp3）。
--       首次点击播放时后端自动从有道词典发音源下载并缓存、回写本列，
--       之后拉取单词（/api/dict/random、/api/word-book/due-review）时顺带返回。
-- 用法：在 Navicat 选中 word_spirit 库 → 查询 → 粘贴并执行
-- 幂等：列已存在时报错可忽略（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS）
-- =============================================================

ALTER TABLE `dict_word`
  ADD COLUMN `audio_url` VARCHAR(255) NOT NULL DEFAULT ''
  COMMENT '单词发音音频相对路径（/uploads/audio/xxx.mp3，首次播放时自动下载缓存）'
  AFTER `is_core`;

SELECT COUNT(*) AS total, SUM(audio_url <> '') AS with_audio FROM dict_word;
