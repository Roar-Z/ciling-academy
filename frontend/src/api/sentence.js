import request from './request'

/** 句灵日选 */
export const getTodaySentence = () => request.get('/api/sentence/today')