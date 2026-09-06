import request from './request'

/** 生词本接口 */
export const listWordBook = (params) => request.get('/api/word-book/list', { params })
export const addWord = (data) => request.post('/api/word-book/add', data)
export const batchAddWords = (data) => request.post('/api/word-book/batch-add', data)
export const removeWord = (id) => request.delete(`/api/word-book/${id}`)
export const batchRemoveWords = (ids) => request.post('/api/word-book/batch-remove', ids)
export const reviewWord = (id, familiar) => request.post(`/api/word-book/${id}/review`, null, { params: { familiar } })
export const dueReview = (limit = 10) => request.get('/api/word-book/due-review', { params: { limit } })
export const randomWords = (count = 10) => request.get('/api/word-book/random', { params: { count } })
export const checkExist = (words) => request.post('/api/word-book/check-exist', words)
export const saveAiNote = (word, aiNote) =>
  request.post(`/api/word-book/${encodeURIComponent(word)}/ai-note`, { aiNote })

/** 标记单词为"已掌握"（新词学习点"认识"调用，去重，持久化累计掌握） */
export const markMastered = (word) => request.post('/api/word-book/mark-mastered', { word })

/** 小游戏：艾宾浩斯优先级取词（due/book/core），bookId 仅前两类有值；答对锁定的词不会出现；错词最多分布 2 个游戏 */
export const spellGameWords = (count, level = null, gameId = null) =>
  request.get('/api/word-book/spell-game-words', { params: { count, level, gameId } })

/**
 * 小游戏答题结果上报（跨游戏去重 + 错词游戏分布）：
 * know → 锁定，之后不再出现在任何小游戏（词库抽完后才重新放行）
 * forget / vague → 解锁，记录出现在当前游戏（同词最多分布 2 个游戏）
 */
export const reportGameResult = (words, result, gameId = null) =>
  request.post('/api/word-book/game-result', { words, result, gameId }, { silentCodes: [404] })

/** 字母拼拼乐复习反馈：三态 know/vague/forget，回写艾宾浩斯曲线（词已被删除时 404 静默处理） */
export const spellReview = (id, result) =>
  request.post(`/api/word-book/${id}/spell-review`, { result }, { silentCodes: [404] })
