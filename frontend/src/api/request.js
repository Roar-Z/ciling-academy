import axios from 'axios'

/**
 * axios 统一封装
 * - 请求拦截：自动携带登录令牌
 * - 响应拦截：统一处理 AI 错误（额度耗尽/限流/服务异常/格式错误），给出易懂提示
 * - 绝不向用户暴露原始报错信息
 */

// 词灵AI 统一友好提示（与后端 ResultCode 600~603 对齐）
const AI_ERROR_MESSAGE = {
  600: '词灵AI暂时不可用，请稍后再试，其他学习功能不受影响',
  601: '今日词灵AI额度已用完，明天再来试试吧',
  602: '操作太频繁啦，请稍后再试',
  603: '词灵AI返回内容格式异常，请重新生成'
}

const service = axios.create({
  baseURL: '/',
  timeout: 65000,
  // 携带 cookie（图形验证码 captchaId 通过 cookie 自动传递）
  withCredentials: true
})

// 相同文案 3 秒内只弹一次：页面并发多个请求同时失败时，避免一串相同弹窗刷屏
const lastShownAt = new Map()
function showMessage(type, msg) {
  const now = Date.now()
  if (now - (lastShownAt.get(msg) || 0) < 3000) return
  if (lastShownAt.size > 50) lastShownAt.clear()
  lastShownAt.set(msg, now)
  // grouping: 即使漏网（如跨页面瞬间连续弹出），Element Plus 也会把相同文案合并为一个
  ElMessage({ type, message: msg, grouping: true })
}

// 请求拦截：携带 token + 把图形验证码 captchaId 通过 header 传递
service.interceptors.request.use((config) => {
  const token = localStorage.getItem('ws_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 用 header 而非 body 字段传递：axios 不会省略 header，但 body 里的 undefined 字段会被去掉
  const captchaId = sessionStorage.getItem('ws_captcha_id')
  if (captchaId) {
    config.headers['X-Captcha-Id'] = captchaId
  }
  return config
})

// 响应拦截：统一业务码处理
service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (!res || typeof res.code === 'undefined') {
      return res
    }
    if (res.code === 200) {
      return res.data
    }

    // 401 登录失效：跳登录页
    if (res.code === 401) {
      localStorage.removeItem('ws_token')
      ElMessage.warning(res.message || '登录状态已失效，请重新登录')
      const cur = window.location
      if (!cur.pathname.includes('/login')) {
        window.location.href = '/login?redirect=' + encodeURIComponent(cur.pathname + cur.search)
      }
      return Promise.reject(createError(res))
    }

    const msg = AI_ERROR_MESSAGE[res.code] || res.message || '操作失败'
    // 页面声明静默处理的业务码（如 604 重复导入）不弹全局错误，由页面自行引导
    if (!(response.config?.silentCodes || []).includes(res.code)) {
      ElMessage.error(msg)
    }
    return Promise.reject(createError(res))
  },
  (error) => {
    let msg = '网络开小差了，请稍后再试'
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        localStorage.removeItem('ws_token')
        msg = '登录状态已失效，请重新登录'
        const cur = window.location
        if (!cur.pathname.includes('/login')) {
          window.location.href = '/login?redirect=' + encodeURIComponent(cur.pathname + cur.search)
        }
      } else if (status >= 500) {
        msg = '服务器开小差了，请稍后再试'
      }
    } else if (error.code === 'ECONNABORTED') {
      msg = '请求超时，请稍后再试'
    }
    showMessage('error', msg)
    const err = new Error(msg)
    err.code = -1
    return Promise.reject(err)
  }
)

/** 构造带业务码的错误对象，供页面降级提示使用 */
function createError(res) {
  const err = new Error(res.message || '请求失败')
  err.code = res.code
  err.data = res.data
  return err
}

export default service
