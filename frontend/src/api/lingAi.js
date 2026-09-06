import request from './request'

/** 词灵AI 接口（对话 / 收藏 / 生词巩固） */

// 会话
export const listSessions = () => request.get('/api/ai/session/list')
export const createSession = (params) => request.post('/api/ai/session', null, { params })
export const deleteSession = (id) => request.delete(`/api/ai/session/${id}`)
export const listMessages = (id) => request.get(`/api/ai/session/${id}/messages`)

// 对话
export const sendMessage = (data) => request.post('/api/ai/chat/send', data)

// 收藏（我的AI笔记）
export const favoriteMessage = (data) => request.post('/api/ai/favorite', data)
export const listFavorites = () => request.get('/api/ai/favorite/list')
export const removeFavorite = (id) => request.delete(`/api/ai/favorite/${id}`)

// 生词巩固包
export const saveReviewContent = (data) => request.post('/api/review/save', data)
export const listReviewContents = () => request.get('/api/review/list')
export const getReviewContent = (id) => request.get(`/api/review/${id}`)
export const deleteReviewContent = (id) => request.delete(`/api/review/${id}`)
