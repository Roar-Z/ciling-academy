import { ref } from 'vue'

/**
 * 沉浸模式（模块级单例状态，跨组件共享）
 *
 * 开启后：桌面端浏览器全屏 + 隐藏顶部导航栏与底部页脚 + 锁定页面滚动。
 * 退出：按 ESC（浏览器原生退出全屏，通过 fullscreenchange 同步）或点击「退出沉浸」按钮。
 */
const immersive = ref(false)
let scrollLocked = false

// 触屏设备（手机/平板）不请求浏览器全屏：部分安卓浏览器（夸克/X5 WebView 等）
// 退出全屏后布局视口不会复原，页面会残留全屏时的横向宽度（内容挤在一侧、另一半黑屏）。
// 移动端改用「页面内沉浸」（隐藏导航 + 锁定滚动），与主流移动端 Web 全屏学习页一致。
export const isTouchDevice =
  typeof window !== 'undefined' &&
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(hover: none) and (pointer: coarse)').matches

function applyScrollLock(on) {
  if (on === scrollLocked) return
  scrollLocked = on
  document.documentElement.style.overflow = on ? 'hidden' : ''
  document.body.style.overflow = on ? 'hidden' : ''
}

function onFullscreenChange() {
  // 用户按 ESC 或浏览器被移出全屏：同步退出沉浸模式
  if (!document.fullscreenElement && immersive.value) exit()
}

// 全屏激活时浏览器原生处理 ESC；若 requestFullscreen 被拒绝，这里兜底
function onKeyDown(e) {
  if (e.key === 'Escape' && immersive.value) exit()
}

/**
 * 进入沉浸模式
 * @returns {Promise<boolean>} 是否成功进入浏览器全屏
 *   - true：浏览器会显示原生「按 Esc 退出全屏」横幅，无需额外提示
 *   - false：全屏被拒绝（仅页面内沉浸），调用方应自行提示退出方式
 */
function enter() {
  if (immersive.value) return Promise.resolve(!!document.fullscreenElement)
  immersive.value = true
  applyScrollLock(true)
  // 移动端跳过浏览器全屏，直接页面内沉浸（规避退出全屏视口不复原的浏览器 bug）
  if (isTouchDevice) return Promise.resolve(false)
  document.addEventListener('fullscreenchange', onFullscreenChange)
  document.addEventListener('keydown', onKeyDown)
  // 全屏可能被浏览器策略拒绝，失败时仍保留"隐藏导航 + 锁定滚动"的沉浸效果
  return document.documentElement
    .requestFullscreen()
    .then(() => true)
    .catch(() => false)
}

function exit() {
  if (!immersive.value) return
  immersive.value = false
  applyScrollLock(false)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('keydown', onKeyDown)
  if (document.fullscreenElement) {
    document.exitFullscreen().catch(() => {})
  }
}

export function useImmersive() {
  return { immersive, enter, exit }
}
