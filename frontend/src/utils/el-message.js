import 'element-plus/es/components/message/style/css'
import RawMessage from 'element-plus/es/components/message/index.mjs'

/**
 * ElMessage 全局包装：
 * - 相同文案 3 秒内只弹一次（页面并发多个请求同时失败时，避免一串相同弹窗刷屏）
 * - 开启 grouping，即使跨页面瞬间连续弹出，Element Plus 也会把相同文案合并为一个
 * - 其余行为（success/error 方法链、关闭回调等）与原生 ElMessage 完全一致
 */

const lastShownAt = new Map()
const DEDUP_WINDOW = 3000

function shouldShow(text) {
  const key = String(text || '')
  // 无文案的提示不参与去重（极少见，直接放行）
  if (!key) return true
  const now = Date.now()
  if (now - (lastShownAt.get(key) || 0) < DEDUP_WINDOW) return false
  if (lastShownAt.size > 50) lastShownAt.clear()
  lastShownAt.set(key, now)
  return true
}

function normalize(args) {
  if (typeof args === 'string') return { message: args }
  return { ...(args || {}) }
}

function ElMessage(options) {
  const opts = normalize(options)
  if (!shouldShow(opts.message)) return
  return RawMessage({ grouping: true, ...opts })
}

ElMessage.success = (options) => ElMessage({ ...normalize(options), type: 'success' })
ElMessage.warning = (options) => ElMessage({ ...normalize(options), type: 'warning' })
ElMessage.error = (options) => ElMessage({ ...normalize(options), type: 'error' })
ElMessage.info = (options) => ElMessage({ ...normalize(options), type: 'info' })
ElMessage.closeAll = RawMessage.closeAll

export { ElMessage }
