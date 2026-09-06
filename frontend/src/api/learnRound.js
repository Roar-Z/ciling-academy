import request from './request'

/** 完成一轮学习后持久化（保存本轮学过的词 + 是否掌握），返回轮次序号 */
export const finishLearnRound = (payload) => request.post('/api/learn-round/finish', payload)

/** 历史学习轮次列表（已完成，按时间倒序，带第几轮序号） */
export const getLearnRoundHistory = () => request.get('/api/learn-round/history')

/** 某一轮详情（逐词 + 是否掌握） */
export const getLearnRoundDetail = (roundId) => request.get(`/api/learn-round/${roundId}`)

/** 删除一个学习轮次（同时移除对应的词项记录） */
export const deleteLearnRound = (roundId) => request.delete(`/api/learn-round/${roundId}`)
