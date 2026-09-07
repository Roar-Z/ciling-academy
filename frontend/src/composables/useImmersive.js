import { ref } from 'vue'

/**
 * 沉浸模式（模块级单例状态，跨组件共享）
 *
 * 开启后：浏览器全屏（占满整屏、无地址栏）+ 隐藏顶部导航栏与底部页脚 + 锁定页面滚动。
 * 移动端全屏成功后尝试锁定横屏（主流背词 App 全屏态一致）；
 * 全屏被拒绝时（iOS Safari 等不支持元素全屏）回退为「页面内沉浸 + 竖屏旋转横板」。
 * 退出：按 ESC（桌面）或点击「退出沉浸」按钮 / 系统返回手势。
 *
 * 【视口卡死修复】安卓部分内核（夸克/X5 等）退出全屏后布局视口停留在全屏时的
 * 横向宽度——实测竖屏下视口宽从 ~393 残留到 ~590（宽仍小于高，高度同步缩水），
 * 内容挤在左半边、右半黑屏；重排/重写 viewport meta 等软手段对该内核一律无效。
 * 唯一可靠恢复 = 整页刷新重建视口。
 * 因此采用「基准比对」：进入沉浸前记录布局视口宽度，退出全屏后
 * 若屏幕已回到竖屏而视口仍比基准宽 60px 以上 → 立即刷新。
 * 不做 UA 嗅探、不做「宽>高」猜测——凡视口没回到原样，刷新。
 */
const immersive = ref(false)
// 是否处于浏览器全屏（供 UI 决定是否启用旋转横板回退方案）
const fullscreen = ref(false)
let scrollLocked = false

// 触屏设备（手机/平板）：视口校验仅触屏需要（桌面退出全屏无此 bug）
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

// 进入沉浸前的布局视口宽度——退出后的校验基准
let baselineWidth = 0

/**
 * 退出全屏后的视口校验（核心修复）：
 * 竖屏握持下，视口宽度仍比进入沉浸前明显偏宽 = 内核视口卡死 → 整页刷新。
 * 多轮时点兜底（250ms 足够内核完成过渡；后两轮防偶发抖动）。
 * 复习页进度走 localStorage 持久化，刷新后当轮进度自动恢复，不丢数据。
 */
function scheduleViewportVerify() {
  if (!isTouchDevice || !baselineWidth) return
  const check = (delay) =>
    setTimeout(() => {
      if (immersive.value) return // 已重新进入沉浸，放弃本次校验
      const portrait =
        typeof window.matchMedia === 'function' &&
        window.matchMedia('(orientation: portrait)').matches
      const w = document.documentElement.clientWidth
      if (portrait && w > baselineWidth + 60) {
        window.location.reload()
      }
    }, delay)
  ;[250, 700, 1400].forEach(check)
}

function onFullscreenChange() {
  const active = !!getFullscreenElement()
  const wasActive = fullscreen.value
  fullscreen.value = active
  // 用户按 ESC / 系统返回键 / 浏览器移出全屏：同步退出沉浸模式
  // （此时全屏已退出，exit() 内 wasFullscreen=false 不会重复调度校验）
  if (!active && immersive.value) exit()
  // 浏览器主动退出全屏路径：这里调度视口校验
  if (wasActive && !active) scheduleViewportVerify()
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
  // 必须在请求全屏前采样：这就是退出后的"正常视口"基准
  baselineWidth = document.documentElement.clientWidth
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
  const wasFullscreen = !!getFullscreenElement()
  immersive.value = false
  applyScrollLock(false)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('webkitfullscreenchange', onFullscreenChange)
  document.removeEventListener('keydown', onKeyDown)
  if (wasFullscreen) {
    exitElementFullscreen()
    // 主动退出路径（「退出沉浸」按钮）：监听器已移除，fullscreenchange
    // 不会再回调，必须在这里自行调度视口校验
    scheduleViewportVerify()
  }
  // wasFullscreen=false：浏览器已自行退出全屏（返回手势/ESC），
  // onFullscreenChange 会调度校验，此处不重复
}

export function useImmersive() {
  return { immersive, fullscreen, enter, exit }
}
