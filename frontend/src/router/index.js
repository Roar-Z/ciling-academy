import { createRouter, createWebHistory } from 'vue-router'
import { routes } from './routes'
import { startProgress, endProgress, startRouteLoading, endRouteLoading } from '@/utils/pageProgress'

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

/**
 * 全局路由守卫：
 * - 需要登录的页面：未登录 → 跳登录页（带 redirect 参数）
 * - 已登录访问登录页 → 回首页
 * - 进入时启动顶部进度条，离开解析完成后结束（国内主流刷新反馈）
 */
router.beforeEach((to, from, next) => {
  document.title = (to.meta.title ? `${to.meta.title} - ` : '') + '词灵学园'
  startProgress()
  startRouteLoading()
  const token = localStorage.getItem('ws_token')
  if (!to.meta.public && !token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (to.path === '/login' && token) {
    next({ path: '/' })
  } else {
    next()
  }
})

router.afterEach(() => {
  endProgress()
  // 整页加载动画：待组件挂载 + 最小展示时长后收尾，长接口由页面局部 v-loading 接棒
  window.setTimeout(() => {
    endRouteLoading()
  }, 350)
})

export default router
