import request from './request'

/** 任务中心接口 */

export const todayPanel = () => request.get('/api/task/today')

export const checkIn = () => request.post('/api/task/check-in')

export const claimStreakReward = (streakDays) =>
  request.post('/api/task/streak-reward/claim', null, { params: { streakDays } })

export const claimActiveReward = (threshold) =>
  request.post('/api/task/active-reward/claim', null, { params: { threshold } })