import { ref } from 'vue'

/**
 * 沉浸模式（模块级单例状态，跨组件共享）
 *
 * 开启后：浏览器全屏（占满整屏、无地址栏）+ 隐藏顶部导航栏与底部页脚 + 锁定页面滚动。
 * 移动端全屏成功后尝试锁定横屏（主流背词 App 全屏态一致）；
 * 全屏被拒绝时（iOS Safari 等不支持元素全屏）回退为「页面内沉浸 + 竖屏旋转横板」。
 * 退出：按 ESC（浏览器原生退出全屏，通过 fullscreenchange 同步）或点击「退出沉浸」按钮。
 */
const immersive = ref(false)
// 是否处于浏览器全屏（供 UI 决定是否启用旋转横板回退方案）
const fullscreen = ref(false)
let scrollLocked = false

// 触屏设备（手机/平板）：用于全屏被拒后的横板回退方案与提示文案
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

// 标准与 WebKit 前缀兼容（iPad Safari 等仅实现 webkit 前缀接口）
function getFullscreenElement() {
  return document.fullscreenElement || document.webkitFullscreenElement || null
}

function requestElementFullscreen() {
  const el = document.documentElement
  const request = el.requestFullscreen || el.webkitRequestFullscreen
  if (!request) return Promise.resolve(false) // iOS iPhone 不支持元素全屏
  try {
    const result = request.call(el)
    if (result && typeof result.then === 'function') {
      return result.then(() => true).catch(() => false)
    }
    return Promise.resolve(true) // webkit 前缀接口无返回值
  } catch (e) {
    return Promise.resolve(false)
  }
}

function exitElementFullscreen() {
  const exit = document.exitFullscreen || document.webkitExitFullscreen
  if (!exit) return
  try {
    const result = exit.call(document)
    if (result && typeof result.catch === 'function') result.catch(() => {})
  } catch (e) {
    /* 忽略 */
  }
}

// 部分安卓浏览器（夸克/X5 WebView）退出全屏后布局视口不回调 resize，
// 页面会残留全屏时的横向宽度（内容挤在一侧、另一半黑屏）；
// 通过重写 viewport meta + 触发 resize 强制浏览器重算视口
function restoreViewport() {
  window.dispatchEvent(new Event('resize'))
  window.scrollTo(0, 0)
  const meta = document.querySelector('meta[name="viewport"]')
  if (!meta) return
  const original = meta.getAttribute('content')
  meta.setAttribute('content', `${original}, minimum-scale=1`)
  requestAnimationFrame(() => meta.setAttribute('content', original))
}

function onFullscreenChange() {
  const active = !!getFullscreenElement()
  const wasActive = fullscreen.value
  fullscreen.value = active
  // 用户按 ESC / 系统返回键 / 浏览器移出全屏：同步退出沉浸模式
  if (!active && immersive.value) exit()
  // 全屏退出完成后做一次视口恢复兜底（规避安卓部分浏览器视口不复原 bug）
  if (wasActive && !active) restoreViewport()
}

// 全屏激活时浏览器原生处理 ESC；若全屏被拒绝（页面内沉浸），这里兜底
function onKeyDown(e) {
  if (e.key === 'Escape' && immersive.value) exit()
}

/**
 * 进入沉浸模式
 * @returns {Promise<boolean>} 是否成功进入浏览器全屏
 *   - true：占满整屏（移动端已尝试锁定横屏）
 *   - false：全屏被拒绝（仅页面内沉浸），调用方应自行提示退出方式
 */
function enter() {
  if (immersive.value) return Promise.resolve(!!getFullscreenElement())
  immersive.value = true
  applyScrollLock(true)
  document.addEventListener('fullscreenchange', onFullscreenChange)
  document.addEventListener('webkitfullscreenchange', onFullscreenChange)
  document.addEventListener('keydown', onKeyDown)
  return requestElementFullscreen().then((ok) => {
    if (ok) {
      // 锁定横屏：仅在全屏状态下被允许（Android Chrome 系）；iOS 不支持则静默忽略
      try {
        if (screen.orientation && screen.orientation.lock) {
          screen.orientation.lock('landscape').catch(() => {})
        }
      } catch (e) {
        /* 设备不支持屏幕方向锁定 */
      }
    }
    return ok
  })
}

function exit() {
  if (!immersive.value) return
  immersive.value = false
  applyScrollLock(false)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('webkitfullscreenchange', onFullscreenChange)
  document.removeEventListener('keydown', onKeyDown)
  // 解除横屏锁定（退出全屏时浏览器也会自动释放，这里显式兜底）
  try {
    if (screen.orientation && screen.orientation.unlock) screen.orientation.unlock()
  } catch (e) {
    /* 忽略 */
  }
  if (getFullscreenElement()) exitElementFullscreen()
}

export function useImmersive() {
  return { immersive, fullscreen, enter, exit }
}
