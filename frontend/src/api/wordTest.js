import request from './request'

/** 单词测验接口
 *
 *  mode: spelling 看中文拼写英文
 *        matching 中英连线
 *        sentence 翻译句子
 *  source: new 新词测验 / due 待复习测验
 */

/** 开启一次测验，返回 batchId + 题目列表 */
export const startWordTest = (data) => request.post('/api/word-test/start', data)

/** 提交一道题答案（正确与否由前端判定，costMs 用时毫秒，userAnswer 用户作答） */
export const submitWordTestAnswer = (data) => request.post('/api/word-test/answer', data)

/** 完成测验，返回统计与错题 */
export const finishWordTest = (batchId) => request.post('/api/word-test/finish', { batchId })

/** 历史轮次列表（已完成的，按时间倒序，带第几轮序号） */
export const getWordTestHistory = () => request.get('/api/word-test/history')

/** 某一轮详情（逐题单词 + 用户作答 + 对错） */
export const getWordTestHistoryDetail = (batchId) => request.get(`/api/word-test/history/${batchId}`)