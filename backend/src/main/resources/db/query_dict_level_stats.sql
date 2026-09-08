-- ============================================================
-- 词库档位统计：dict_word 每个阶段（中考/高考/四级/六级）有多少词
-- 在 DB 客户端整体执行，结果为一列指标 + 一列词数
-- ============================================================

SELECT '① 总词数' AS 指标, COUNT(*) AS 词数 FROM dict_word

UNION ALL

-- 2) 主等级 level 分布（每词只归一档，按 difficulty 兜底迁移过的口径）
SELECT CONCAT('② 主等级 level = ', level), COUNT(*) FROM dict_word GROUP BY level

UNION ALL

-- 3) levels 多标签：各阶段词数（一词可同属多档，会在多行各计一次）
SELECT '③ 多标签·中考 zhongkao', COUNT(*) FROM dict_word WHERE FIND_IN_SET('zhongkao', levels)
UNION ALL
SELECT '③ 多标签·高考 gaokao',   COUNT(*) FROM dict_word WHERE FIND_IN_SET('gaokao', levels)
UNION ALL
SELECT '③ 多标签·四级 cet4',     COUNT(*) FROM dict_word WHERE FIND_IN_SET('cet4', levels)
UNION ALL
SELECT '③ 多标签·六级 cet6',     COUNT(*) FROM dict_word WHERE FIND_IN_SET('cet6', levels)
UNION ALL
SELECT '③ 多标签·未标注',        COUNT(*) FROM dict_word WHERE levels IS NULL OR levels = ''

UNION ALL

SELECT '④ 高频核心词 is_core=1', COUNT(*) FROM dict_word WHERE is_core = 1;

-- 5) 附：levels 组合分布 TOP20（看一词多库的具体构成）
SELECT levels AS 组合, COUNT(*) AS 词数
FROM dict_word
WHERE levels IS NOT NULL AND levels <> ''
GROUP BY levels
ORDER BY 词数 DESC
LIMIT 20;
