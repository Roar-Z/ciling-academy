import { ref } from 'vue'

/**
 * 沉浸模式（模块级单例状态，跨组件共享）
 *
 * 开启后：浏览器全屏（占满整屏、无地址栏）+ 隐藏顶部导航栏与底部页脚 + 锁定页面滚动。
 * 移动端全屏成功后尝试锁定横屏（主流背词 App 全屏态一致）；
 * 全屏被拒绝时（iOS Safari 等不支持元素全屏）回退为「页面内沉浸 + 竖屏旋转横板」。
 * 退出：按 ESC（桌面）或点击「退出沉浸」按钮 / 系统返回手势。
 *
 * 【视口卡死·无条件修复】安卓部分内核（夸克/X5 等）退出全屏后布局视口会永久
 * 卡死在全屏时的横向宽度——内容挤在左半边、右半黑屏、移动端菜单变 PC 导航。
 * 实测该状态下 innerWidth/媒体查询/方向上报全部失真，任何"检测后再刷新"的
 * 条件方案（宽>高、UA 嗅探、基准比对+方向判断）都会被内核的虚假上报绕过。
 * 因此最终方案放弃一切检测：
 *   1. 只要本轮沉浸发生过浏览器全屏，退出时无条件整页刷新（120ms 后），
 *      刷新重建文档 = 视口必然按当前窗口形状重建（唯一被证实有效的恢复手段）。
 *      复习进度走 localStorage 持久化，刷新后自动恢复当轮，不丢数据。
 *   2. 自愈兜底：刷新后若手机类设备仍渲染桌面布局（视口宽度 > 768），
 *      说明该内核连刷新都救不回，标记 ws_fs_broken —— 以后沉浸模式不再调用
 *      全屏 API（页面内沉浸，退出必然干净），彻底规避该内核 bug。
 */

const immersive = ref(false)
// 是否处于浏览器全屏（供 UI 决定是否启用旋转横板回退方案）
const fullscreen = ref(false)
let scrollLocked = false

// 触屏设备（手机/平板）：视口恢复机制仅触屏需要（桌面退出全屏无此 bug）
export const isTouchDevice =
  typeof window !== 'undefined' &&
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(hover: none) and (pointer: coarse)').matches

// 全屏视口已确认坏掉的内核标记（localStorage 持久化）
const FS_BROKEN_KEY = 'ws_fs_broken'
// 「本次刷新是退出沉浸的恢复动作」标记（sessionStorage 跨刷新传递）
const RECOVER_KEY = 'ws_vp_recovering'
// 自动恢复刷新计数（防循环上限）
const RECOVER_COUNT_KEY = 'ws_vp_recover_count'

function isFsBroken() {
  try {
    return localStorage.getItem(FS_BROKEN_KEY) === '1'
  } catch (e) {
    return false
  }
}

/**
 * 开机自检：本次加载若是「退出沉浸 → 自动刷新」的恢复动作，
 * 检查视口是否恢复：手机类设备视口仍 > 768（渲染桌面布局）= 刷新也救不回，
 * 标记坏内核，后续沉浸模式走页面内沉浸；已恢复则清空刷新计数。
 */
function bootSelfCheck() {
  if (!isTouchDevice) return
  let recovering = false
  try {
    recovering = sessionStorage.getItem(RECOVER_KEY) === '1'
    sessionStorage.removeItem(RECOVER_KEY)
  } catch (e) {
    /* 无痕模式等不支持 sessionStorage：跳过自愈标记，无条件刷新仍生效 */
  }
  if (!recovering) return
  const w = document.documentElement.clientWidth
  if (w > 768) {
    try {
      localStorage.setItem(FS_BROKEN_KEY, '1')
    } catch (e) {
      /* 忽略 */
    }
  } else {
    try {
      sessionStorage.removeItem(RECOVER_COUNT_KEY)
    } catch (e) {
      /* 忽略 */
    }
  }
}
bootSelfCheck()

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

/**
 * 退出全屏后的恢复：无条件整页刷新（不做任何检测，条件判断已被证实
 * 会被夸克/X5 的虚假视口/方向上报绕过）。仅触屏 + 本轮确实进过全屏时执行。
 * 防循环：sessionStorage 计数上限 2 次；刷新落地的开机自检恢复后清零。
 */
function scheduleExitReload() {
  if (!isTouchDevice) return
  try {
    const n = Number(sessionStorage.getItem(RECOVER_COUNT_KEY) || '0')
    if (n >= 2) return
    sessionStorage.setItem(RECOVER_KEY, '1')
    sessionStorage.setItem(RECOVER_COUNT_KEY, String(n + 1))
  } catch (e) {
    /* 无痕模式：没有标记也能刷，只是失去防循环保护 */
  }
  setTimeout(() => {
    if (!immersive.value) window.location.reload()
  }, 120)
}

function onFullscreenChange() {
  const active = !!getFullscreenElement()
  const wasActive = fullscreen.value
  fullscreen.value = active
  // 用户按 ESC / 系统返回键 / 浏览器移出全屏：同步退出沉浸模式
  // （此时全屏已退出，exit() 内 wasFullscreen=false 不会重复调度刷新）
  if (!active && immersive.value) exit()
  // 浏览器主动退出全屏路径：这里调度恢复刷新
  if (wasActive && !active) scheduleExitReload()
}

// 全屏激活时浏览器原生处理 ESC；若全屏被拒绝（页面内沉浸），这里兜底
function onKeyDown(e) {
  if (e.key === 'Escape' && immersive.value) exit()
}

/**
 * 进入沉浸模式
 * @returns {Promise<boolean>} 是否成功进入浏览器全屏
 *   - true：占满整屏（移动端已尝试锁定横屏）
 *   - false：全屏被拒绝 / 该内核被标记为 fs-broken（仅页面内沉浸），
 *     调用方应自行提示退出方式
 */
function enter() {
  if (immersive.value) return Promise.resolve(!!getFullscreenElement())
  immersive.value = true
  applyScrollLock(true)
  if (isFsBroken()) {
    // 该内核退出全屏视口必卡死且刷新无法恢复：
    // 降级为页面内沉浸（不调用全屏 API），退出不刷新、必然干净
    return Promise.resolve(false)
  }
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
    // 不会再回调，必须在这里自行调度恢复刷新
    scheduleExitReload()
  }
  // wasFullscreen=false：浏览器已自行退出全屏（返回手势/ESC），
  // onFullscreenChange 会调度刷新，此处不重复
}

export function useImmersive() {
  return { immersive, fullscreen, enter, exit }
}
