import { ref, watchEffect } from 'vue'

/**
 * 设备判定（模块级单例，跨组件共享一份 resize 监听）
 *
 * 断点 768：与页面中追加的 @media (max-width: 768px) 语义完全一致，
 * JS（布局壳切换）与 CSS（页面窄屏样式）永不在临界值上打架。
 *
 * 触屏设备（手机/平板）一律强制移动端布局：夸克/X5 等内核退出全屏后
 * 视口宽度可能永久虚报为横屏值（> 768），若按宽度判断手机会被误判成 PC
 * 而丢失底部 TabBar。改为按输入能力判定（无悬停 + 粗主指针 = 触屏），
 * 手机永远渲染移动端壳，PC 触屏笔记本主指针为鼠标不会误判。
 *
 * is-mobile 类同步到 <html> 根元素：
 * el-dialog / el-message 等 append-to-body 传送节点不在布局组件内部，
 * 挂根元素才能让 mobile.scss 的全局兜底样式覆盖到它们。
 */
export const MOBILE_BREAKPOINT = 768

export const isTouchDevice =
  typeof window !== 'undefined' &&
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(hover: none) and (pointer: coarse)').matches

const isMobile = ref(isTouchDevice || window.innerWidth <= MOBILE_BREAKPOINT)

let listenerBound = false
let classBound = false

function onResize() {
  // 触屏设备不信任 innerWidth 上报（可能被坏内核虚报），始终为移动端布局
  if (isTouchDevice) return
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
