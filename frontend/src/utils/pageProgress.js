/**
 * 轻量顶部进度条 + 整页加载层（国内主流刷新/路由切换反馈）
 * 不依赖第三方库，自行创建 DOM 并控制显隐。
 * 由 router 的 beforeEach / afterEach 调用。
 */

let bar = null

function ensureBar() {
  if (bar) return bar
  bar = document.createElement('div')
  bar.className = 'ws-page-progress'
  document.body.appendChild(bar)
  return bar
}

/** 开始：进度条淡入并从 0 增长到约 82% */
export function startProgress() {
  const b = ensureBar()
  b.classList.remove('ws-progress-done')
  // 触发重排以重启过渡动画
  void b.offsetWidth
  b.classList.add('ws-progress-active')
}

/** 结束：进度条补满到 100% 后淡出 */
export function endProgress() {
  const b = ensureBar()
  b.classList.remove('ws-progress-active')
  b.classList.add('ws-progress-done')
  window.setTimeout(() => {
    b.classList.remove('ws-progress-done')
  }, 320)
}

/* =============================================================
   整页加载层（路由进入/刷新时整页居中旋转环"加载中…"）
   - 固定全屏、半透明白底，覆盖所有主页面刷新/跳转
   - 旋转环 + 文案由 reset.scss 的 .ws-route-loading 提供动画
   ============================================================= */
let routeLoadingEl = null

function ensureRouteLoading() {
  if (routeLoadingEl) return routeLoadingEl
  routeLoadingEl = document.createElement('div')
  routeLoadingEl.className = 'ws-route-loading'
  document.body.appendChild(routeLoadingEl)
  return routeLoadingEl
}

/** 开始：显示整页加载动画 */
export function startRouteLoading() {
  ensureRouteLoading().classList.add('ws-route-loading-active')
}

/** 结束：隐藏整页加载动画 */
export function endRouteLoading() {
  if (!routeLoadingEl) return
  routeLoadingEl.classList.remove('ws-route-loading-active')
}
