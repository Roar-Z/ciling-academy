import request from './request'

/** 翻译（普通 / AI） */
export const translate = (data) => request.post('/api/translate', data)

/** 加入句灵集 */
export const addSentence = (data) => request.post('/api/sentence/add', data)

/** 分页查询我的句灵集 */
export const listSentence = (params) => request.get('/api/sentence/list', { params })

/** 我已收藏的原文集合（用于判断按钮"已收藏"状态） */
export const listCollectedOriginals = () => request.get('/api/sentence/collected-originals')

/** 删除句灵集 */
export const removeSentence = (id) => request.delete(`/api/sentence/${id}`)

/** 批量删除 */
export const batchRemoveSentence = (ids) => request.post('/api/sentence/batch-remove', ids)