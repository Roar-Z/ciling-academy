import request from './request'

/** 阅读助手（模式A：就地调用）接口 */
export const explainText = (data) => request.post('/api/ai/explain', data)
