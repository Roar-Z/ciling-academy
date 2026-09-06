import request from './request'

/** 生成图形验证码（captchaId + base64 图片） */
export const generateCaptcha = () => request.get('/api/captcha/generate')

/** 图形验证码独立校验接口 */
export const verifyCaptcha = (captchaId, code) =>
  request.post('/api/captcha/verify', { captchaId, code })

/** 用户与词典接口 */
export const register = (data) => request.post('/api/user/register', data)
export const login = (data) => request.post('/api/user/login', data)
export const getInfo = () => request.get('/api/user/info')
export const logout = () => request.post('/api/user/logout')
export const getAiQuota = () => request.get('/api/user/ai-quota')
export const recordStudy = () => request.post('/api/user/record-study')
/** 等级详情（成长值/称号/进度/升级奖励） */
export const getLevelInfo = () => request.get('/api/user/level-info')
/** 领取等级升级奖励（每级一次） */
export const claimLevelReward = (level) => request.post('/api/user/level-claim', { level })
/** 成就列表（查询时自动解锁已达成的成就并记录达成时间） */
export const getAchievements = () => request.get('/api/user/achievements')
export const updateProfile = (data) => request.put('/api/user/profile', data)
export const saveReviewBatchSize = (size) => request.put('/api/user/review-batch', { size })
export const saveDailyGoal = (goal) => request.put('/api/user/daily-goal', { goal })
export const sendEmailCode = (type, email, captchaId) =>
  request.post('/api/user/email-code', { type, email, captchaId })
/** 注册阶段发送邮箱验证码（公开接口，无需登录） */
export const sendRegisterEmailCode = (email, captchaId) =>
  request.post('/api/user/email-code-public', { type: 'bind_email', email, captchaId })
export const updateEmail = (email, code) => request.put('/api/user/email', { email, code })
export const changePassword = (oldPassword, newPassword, code) =>
  request.put('/api/user/password', { oldPassword, newPassword, code })
export const deleteAccount = (code) => request.delete('/api/user/account', { data: { code } })

/** 通知偏好（今日复习提醒 / AI 额度提醒） */
export const getNotifySettings = () => request.get('/api/user/notify-settings')
export const saveNotifySettings = (data) => request.put('/api/user/notify-settings', data)
export const uploadAvatar = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/api/user/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 词典查询（基础释义，不消耗AI额度）
export const dictLookup = (word) => request.get(`/api/dict/word/${encodeURIComponent(word)}`)
export const dictSearch = (params) => request.get('/api/dict/search', { params })
// 随机单词：level 可选 cet4 / cet6 / mixed / all
// excludeLearned=true 时排除已学过的词，且每次真随机（用于"学习新词 —— 再来一轮换新词"）
export const dictRandom = (count = 10, level, excludeLearned = false) =>
  request.get('/api/dict/random', { params: { count, level, excludeLearned } })
// 首页平台统计（游客可用）：收录单词数 + 全站累计掌握单词数
export const getPlatformStats = () => request.get('/api/dict/stats', { silentCodes: [404, 500] })
