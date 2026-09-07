import { ref, watchEffect } from 'vue'

/**
 * 设备判定（模块级单例，跨组件共享一份 resize 监听）
 *
 * 断点 768：与页面中追加的 @media (max-width: 768px) 语义完全一致，
 * JS（布局壳切换）与 CSS（页面窄屏样式）永不在临界值上打架。
 *
 * is-mobile 类同步到 <html> 根元素：
 * el-dialog / el-message 等 append-to-body 传送节点不在布局组件内部，
 * 挂根元素才能让 mobile.scss 的全局兜底样式覆盖到它们。
 */
export const MOBILE_BREAKPOINT = 768

const isMobile = ref(window.innerWidth <= MOBILE_BREAKPOINT)

let listenerBound = false
let classBound = false

function onResize() {
  isMobile.value = window.innerWidth <= MOBILE_BREAKPOINT
}

export function useDevice() {
  if (!listenerBound) {
    listenerBound = true
    window.addEventListener('resize', onResize, { passive: true })
  }
  if (!classBound) {
    classBound = true
    watchEffect(() => {
      document.documentElement.classList.toggle('is-mobile', isMobile.value)
    })
  }
  return { isMobile }
}
