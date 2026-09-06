import request from './request'

/** 练习试卷接口 */
export const listPapers = (params) => request.get('/api/paper/list', { params })
export const getPaper = (id, withAnswer = false) => request.get(`/api/paper/${id}`, { params: { withAnswer } })
// 604（重复导入）由页面自行弹引导框，不走全局错误提示
export const importPaper = (data) => request.post('/api/paper/import', data, { silentCodes: [604] })
export const submitPaper = (data) => request.post('/api/paper/submit', data)
export const paperResult = (id) => request.get(`/api/paper/${id}/result`)
export const wrongList = (params) => request.get('/api/paper/wrong', { params })
export const removeWrong = (questionId) => request.delete(`/api/paper/wrong/${questionId}`)
export const removeWrongBatch = (questionIds) => request.delete('/api/paper/wrong', { data: { questionIds } })
export const removePaper = (id) => request.delete(`/api/paper/${id}`)
