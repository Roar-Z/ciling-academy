import request from './request'

/** 小游戏接口 */
export const saveGameRecord = (data) => request.post('/api/game/record', data)
export const myGameRecords = (params) => request.get('/api/game/records', { params })
export const gameRank = (params) => request.get('/api/game/rank', { params })
export const gameStats = (params) => request.get('/api/game/stats', { params })
