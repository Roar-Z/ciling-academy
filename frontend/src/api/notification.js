import request from './request'

/** 系统通知 API */

/** 最近 30 条通知 */
export const listNotifications = () => request.get('/api/notification/list')

/** 未读数量 */
export const unreadCount = () => request.get('/api/notification/unread-count')

/** 标记单条已读 */
export const markRead = (id) => request.post(`/api/notification/read/${id}`)

/** 全部标记已读 */
export const markAllRead = () => request.post('/api/notification/read-all')

/** 每日 AI 赠送通知（每日首次调用生效） */
export const touchDailyGift = () => request.post('/api/notification/daily-gift')