<template>
  <div class="mobile-layout">
    <!-- ============ 顶部栏（52px，登录态：铃铛+更多；游客态：登录+更多） ============ -->
    <header v-if="!hideChrome" class="m-topbar" :class="{ 'is-scrolled': scrolled }">
      <router-link to="/" class="m-logo">
        <img src="/logo.png" alt="词灵学园" class="m-logo-mark" />
      </router-link>
      <span class="m-title">{{ pageTitle }}</span>
      <div class="m-top-actions">
        <template v-if="userStore.isLogin">
          <button class="m-icon-btn" aria-label="搜索" @click="openSearch">
            <AppIcon name="search" :size="20" />
          </button>
          <AiQuotaDropdown />
          <NotificationBell />
        </template>
        <template v-else>
          <el-button size="small" type="primary" class="m-login-btn" @click="go('/login')">
            登录
          </el-button>
        </template>
        <button class="m-icon-btn" aria-label="更多" @click="drawerOpen = true">
          <AppIcon name="menu" :size="22" />
        </button>
      </div>
    </header>

    <!-- ============ 内容区 ============ -->
    <main class="m-content" :class="{ 'no-chrome': hideChrome }">
      <router-view />
    </main>

    <!-- ============ 底部 TabBar ============ -->
    <nav v-if="!hideChrome" class="m-tabbar">
      <template v-if="userStore.isLogin">
        <router-link
          v-for="tab in tabs"
          :key="tab.path"
          :to="tab.path"
          class="m-tab"
          :class="{ 'is-active': isTabActive(tab) }"
        >
          <AppIcon :name="tab.icon" :size="22" />
          <span class="m-tab-label">{{ tab.label }}</span>
        </router-link>
      </template>
      <template v-else>
        <router-link
          v-for="tab in guestTabs"
          :key="tab.path"
          :to="tab.path"
          class="m-tab"
          :class="{ 'is-active': isTabActive(tab) }"
        >
          <AppIcon :name="tab.icon" :size="22" />
          <span class="m-tab-label">{{ tab.label }}</span>
        </router-link>
      </template>
    </nav>

    <!-- ============ 更多抽屉（宫格菜单） ============ -->
    <el-drawer
      v-model="drawerOpen"
      direction="rtl"
      size="78%"
      class="m-drawer"
      :with-header="false"
    >
      <div class="m-drawer-body">
        <!-- 头部：用户信息 / 登录入口 -->
        <div class="m-drawer-head">
          <template v-if="userStore.isLogin">
            <el-avatar :size="44" :src="userStore.userInfo?.avatar || ''" class="m-drawer-avatar">
              {{ avatarText }}
            </el-avatar>
            <div class="m-drawer-user">
              <p class="m-drawer-name">{{ userStore.userInfo?.nickname || '同学' }}</p>
              <router-link to="/profile" class="m-drawer-sub" @click="drawerOpen = false">
                进入个人中心
                <AppIcon name="chevron-right" :size="12" />
              </router-link>
            </div>
          </template>
          <template v-else>
            <div class="m-drawer-user">
              <p class="m-drawer-name"> Hi，快来开始学习吧 </p>
              <el-button type="primary" round class="m-drawer-login" @click="go('/login?tab=register')">
                登录 / 注册
              </el-button>
            </div>
          </template>
        </div>

        <!-- 宫格分组 -->
        <div v-for="group in menuGroups" :key="group.title" class="m-menu-group">
          <p class="m-menu-title">{{ group.title }}</p>
          <div class="m-menu-grid">
            <button
              v-for="item in group.items"
              :key="item.path"
              class="m-menu-cell"
              @click="handleMenuClick(item)"
            >
              <AppIcon :name="item.icon" :size="22" class="m-menu-icon" />
              <span>{{ item.label }}</span>
            </button>
          </div>
        </div>

        <!-- 底部退出 -->
        <button v-if="userStore.isLogin" class="m-logout-btn" @click="handleLogout">
          <AppIcon name="log-out" :size="18" />
          退出登录
        </button>
      </div>
    </el-drawer>

    <!-- ============ 全屏搜索层（对应 PC 导航搜索框，京东/淘宝式搜索页） ============ -->
    <Transition name="m-search">
      <div v-if="searchOpen" class="m-search-layer">
        <div class="m-search-row">
          <div class="m-search-field">
            <AppIcon name="search" :size="16" class="m-search-ico" />
            <input
              ref="searchInputRef"
              v-model="searchWord"
              class="m-search-input"
              placeholder="搜索功能 / 查单词"
              enterkeyhint="search"
              @keydown.enter="handleSearchEnter"
            />
          </div>
          <button class="m-search-cancel" @click="closeSearch">取消</button>
        </div>
        <div class="m-search-list">
          <button
            v-for="(item, i) in searchSuggestions"
            :key="item.value + '-' + i"
            class="m-search-item"
            @click="handleSearchSelect(item)"
          >
            <span class="m-search-item-label">{{ item.label }}</span>
            <span v-if="item.hot" class="m-search-tag">热门</span>
            <span v-else-if="item.ai" class="m-search-tag ai">AI</span>
          </button>
          <p v-if="!searchSuggestions.length" class="m-search-empty">没有匹配的功能，试试输入单词直接查询</p>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useImmersive } from '@/composables/useImmersive'
import NotificationBell from '@/components/common/NotificationBell.vue'
import AiQuotaDropdown from '@/components/ai/AiQuotaDropdown.vue'
import AppIcon from '@/components/common/AppIcon.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { immersive } = useImmersive()

/* ---------- 顶栏 / TabBar 显隐：登录页、沉浸模式下全隐藏 ---------- */
const hideChrome = computed(() => route.meta?.hideChrome || immersive.value)
const pageTitle = computed(() => route.meta?.title || '词灵学园')

/* ---------- 滚动阴影 ---------- */
const scrolled = ref(false)
function onScroll() {
  scrolled.value = window.scrollY > 4
}
onMounted(() => window.addEventListener('scroll', onScroll, { passive: true }))
onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  userStore.clearQuotaTimer()
})

/* ---------- 顶栏 AI 额度：登录后立即拉取 + 60s 轮询（对齐 PC 端 MainLayout 行为） ---------- */
watch(
  () => userStore.isLogin,
  (login) => {
    if (login) {
      userStore.refreshQuota().catch(() => {})
      userStore.startQuotaTimer()
    } else {
      userStore.clearQuotaTimer()
    }
  },
  { immediate: true }
)

/* ---------- 底部 TabBar ---------- */
const tabs = [
  { label: '首页', path: '/', icon: 'house', match: (p) => p === '/' },
  { label: '学习', path: '/review', icon: 'book-open', match: (p) => p.startsWith('/review') || p.startsWith('/word-test') },
  { label: '词灵AI', path: '/ai-assistant', icon: 'sparkles', match: (p) => p.startsWith('/ai-assistant') },
  { label: '乐园', path: '/game-park', icon: 'gamepad-2', match: (p) => p.startsWith('/game-park') || p.startsWith('/game') },
  { label: '我的', path: '/profile', icon: 'user', match: (p) => p.startsWith('/profile') }
]

function isTabActive(tab) {
  return tab.match(route.path)
}

/* ---------- 抽屉宫格菜单 ---------- */
const drawerOpen = ref(false)
const menuGroups = [
  {
    title: '学习',
    items: [
      { label: '生词本', path: '/word-book', icon: 'book-marked' },
      { label: '句灵集', path: '/sentence', icon: 'messages-square' },
      { label: '练习试卷', path: '/paper-list', icon: 'file-text' },
      { label: 'AI生成试卷', path: '/paper-generate', icon: 'file-plus-2' }
    ]
  },
  {
    title: '工具',
    items: [
      { label: '词灵工具', path: '/tools', icon: 'wrench' },
      { label: 'AI笔记', path: '/ai-note', icon: 'notebook-pen' },
      { label: '长难句', path: '/long-sentence', icon: 'text' },
      { label: '翻译', path: '/translate', icon: 'languages' },
      { label: '阅读助手', path: '/reading-helper', icon: 'glasses' }
    ]
  },
  {
    title: '成长',
    items: [
      { label: '今日任务', path: '/task', icon: 'calendar-check' },
      { label: '成就殿堂', path: '/achievements', icon: 'trophy' },
      { label: '学习报告', path: '/report', icon: 'bar-chart-3' }
    ]
  },
  {
    title: '更多',
    items: [
      { label: '金币商城', path: '/shop', icon: 'shopping-bag' },
      { label: '设置', path: '/settings', icon: 'settings' },
      { label: '帮助中心', path: '/help', icon: 'circle-help' },
      { label: '关于我们', path: '/about', icon: 'info' }
    ]
  }
]

// 路由变化自动收起抽屉
watch(
  () => route.path,
  () => {
    drawerOpen.value = false
    closeSearch()
  }
)

/* ---------- 全屏搜索层：功能直达 + 查单词（与 PC 导航搜索同源逻辑） ---------- */
const searchOpen = ref(false)
const searchWord = ref('')
const searchInputRef = ref(null)

const searchFeatures = [
  { label: '查单词', value: 'dict', path: '/tools', hot: true },
  { label: '今日任务', value: 'task', path: '/task', hot: false },
  { label: '背单词复习', value: 'review', path: '/review', hot: true },
  { label: '生词本', value: 'wordbook', path: '/word-book', hot: true },
  { label: '练习试卷', value: 'paper', path: '/paper-list', hot: true },
  { label: '词灵AI', value: 'ai', path: '/ai-assistant', hot: true },
  { label: '长难句分析助手', value: 'long-sentence', path: '/long-sentence', keywords: 'ai 长难句 分析 语法', ai: true },
  { label: '阅读助手', value: 'reading', path: '/reading-helper', keywords: 'ai 阅读 文章 段落', ai: true },
  { label: '翻译助手', value: 'translate', path: '/translate', keywords: 'ai 翻译 中英', ai: true },
  { label: '趣味乐园', value: 'game', path: '/game-park', hot: true },
  { label: '词灵工具', value: 'tools', path: '/tools', hot: false },
  { label: '我的AI笔记', value: 'note', path: '/ai-note', hot: false },
  { label: '个人中心', value: 'profile', path: '/profile', hot: false },
  { label: '金币商城', value: 'shop', path: '/shop', hot: false },
  { label: '学习报告', value: 'report', path: '/report', hot: false },
  { label: '设置', value: 'settings', path: '/settings', hot: false }
]

function fuzzyMatch(text, query) {
  const t = text.toLowerCase()
  const q = query.toLowerCase()
  let i = 0
  for (const ch of q) {
    i = t.indexOf(ch, i)
    if (i === -1) return false
    i++
  }
  return true
}

const searchSuggestions = computed(() => {
  const q = searchWord.value.trim()
  if (!q) return searchFeatures.filter((f) => f.hot)
  const matched = searchFeatures.filter(
    (f) =>
      fuzzyMatch(f.label, q) ||
      fuzzyMatch(f.value, q) ||
      fuzzyMatch(f.path, q) ||
      (f.keywords && fuzzyMatch(f.keywords, q))
  )
  // 任意输入都兜底一个「查单词」直达项
  matched.unshift({ label: `查单词：${q}`, value: `dict:${q}`, path: '/tools', query: { word: q } })
  return matched
})

function openSearch() {
  searchOpen.value = true
  nextTick(() => searchInputRef.value?.focus())
}

function closeSearch() {
  searchOpen.value = false
  searchWord.value = ''
}

function handleSearchEnter() {
  const first = searchSuggestions.value[0]
  if (first) handleSearchSelect(first)
}

function handleSearchSelect(item) {
  closeSearch()
  router.push({ path: item.path, query: item.query })
}

function go(path) {
  drawerOpen.value = false
  router.push(path)
}

function handleMenuClick(item) {
  // 受保护页面（无 meta.public）由全局路由守卫自动重定向到登录页，无需重复处理
  router.push(item.path)
}

const avatarText = computed(() => (userStore.userInfo?.nickname || '同').slice(0, 1))

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '再想想',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  drawerOpen.value = false
  await userStore.logout()
  ElMessage.success('已退出登录，期待下次见面～')
  router.push('/')
}
</script>

<style lang="scss" scoped>
.mobile-layout {
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  background: $bg-page;
}

/* ---------- 顶部栏 ---------- */
.m-topbar {
  position: sticky;
  top: 0;
  z-index: 100;
  height: 52px;
  display: flex;
  align-items: center;
  gap: $sp-2;
  padding: 0 $sp-3;
  background: $bg-card;
  border-bottom: 1px solid $border-light;
  transition: box-shadow 0.25s ease;

  &.is-scrolled {
    box-shadow: $shadow-sm;
  }
}

.m-logo {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.m-logo-mark {
  width: 30px;
  height: 30px;
  border-radius: $radius-sm;
}

.m-title {
  flex: 1;
  min-width: 0;
  font-size: $fs-xl;
  font-weight: 600;
  color: $text-title;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.m-top-actions {
  display: flex;
  align-items: center;
  gap: $sp-2;
  flex-shrink: 0;
}

.m-login-btn {
  padding: 0 $sp-4;
  height: 32px;
}

.m-icon-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: $text-body;
  border-radius: $radius-base;
  cursor: pointer;

  &:active {
    background: $bg-hover;
  }
}

/* ---------- 内容区：为 TabBar 预留高度（含 iOS 安全区） ---------- */
.m-content {
  flex: 1;
  padding-bottom: calc(62px + env(safe-area-inset-bottom));

  &.no-chrome {
    padding-bottom: 0;
  }
}

/* ---------- 底部 TabBar ---------- */
.m-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  height: calc(58px + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  display: flex;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(12px);
  border-top: 1px solid $border-light;
}

.m-tab {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  text-decoration: none;
  color: $text-secondary;
  transition: color $transition-fast;

  .m-tab-label {
    font-size: 10px;
    line-height: 1;
  }

  &.is-active {
    color: $primary-5;
    font-weight: 600;
  }

  &:active {
    transform: scale(0.94);
  }
}

/* ---------- 全屏搜索层 ---------- */
.m-search-layer {
  position: fixed;
  inset: 0;
  z-index: 110;
  display: flex;
  flex-direction: column;
  background: $bg-card;
  padding: calc(env(safe-area-inset-top) + $sp-2) $sp-3 $sp-3;
}

.m-search-row {
  display: flex;
  align-items: center;
  gap: $sp-3;
}

.m-search-field {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 $sp-3;
  border-radius: $radius-pill;
  background: $gray-2;

  .m-search-ico {
    color: $text-disabled;
    flex-shrink: 0;
  }
}

.m-search-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: $fs-md;
  font-family: inherit;
  color: $text-title;

  &::placeholder {
    color: $text-disabled;
  }
}

.m-search-cancel {
  flex-shrink: 0;
  border: none;
  background: transparent;
  padding: 4px 2px;
  font-size: $fs-md;
  color: $primary-5;
  font-family: inherit;
  cursor: pointer;
}

.m-search-list {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  margin-top: $sp-2;
}

.m-search-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: $sp-2;
  padding: 13px 2px;
  border: none;
  border-bottom: 1px solid $border-light;
  background: transparent;
  font-size: $fs-md;
  color: $text-title;
  font-family: inherit;
  text-align: left;
  cursor: pointer;

  &:active {
    background: $bg-hover;
  }

  .m-search-item-label {
    flex: 1;
    min-width: 0;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.m-search-tag {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: $radius-base;
  font-size: 10px;
  background: $color-warning-soft;
  color: $color-warning;

  &.ai {
    background: $gray-3;
    color: $text-secondary;
  }
}

.m-search-empty {
  margin: 0;
  padding: $sp-5 0;
  text-align: center;
  font-size: $fs-sm;
  color: $text-disabled;
}

/* 搜索层过渡：顶部轻落 + 淡入 */
.m-search-enter-active,
.m-search-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}

.m-search-enter-from,
.m-search-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* ---------- 抽屉 ---------- */
.m-drawer-body {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  padding: $sp-5 $sp-4 calc($sp-6 + env(safe-area-inset-bottom));
}

.m-drawer-head {
  display: flex;
  align-items: center;
  gap: $sp-3;
  padding-bottom: $sp-5;
  border-bottom: 1px solid $border-light;
  margin-bottom: $sp-4;
}

.m-drawer-avatar {
  flex-shrink: 0;
  background: $primary-2;
  color: $primary-6;
  font-weight: 600;
}

.m-drawer-user {
  flex: 1;
  min-width: 0;
}

.m-drawer-name {
  margin: 0 0 4px;
  font-size: $fs-2xl;
  font-weight: 600;
  color: $text-title;
}

.m-drawer-sub {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: $fs-sm;
  color: $text-secondary;
  text-decoration: none;
}

.m-drawer-login {
  margin-top: $sp-2;
  padding: 0 $sp-6;
  height: 38px;
}

.m-menu-group {
  margin-bottom: $sp-5;
}

.m-menu-title {
  margin: 0 0 $sp-2;
  font-size: $fs-sm;
  color: $text-secondary;
}

.m-menu-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $sp-2;
}

.m-menu-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 68px;
  padding: $sp-2 4px;
  border: none;
  border-radius: $radius-card;
  background: $gray-2;
  color: $text-body;
  font-size: $fs-sm;
  cursor: pointer;
  transition: background $transition-fast, transform $transition-fast;

  .m-menu-icon {
    color: $primary-5;
  }

  &:active {
    background: $primary-1;
    transform: scale(0.95);
  }
}

.m-logout-btn {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $sp-2;
  height: 46px;
  border: none;
  border-radius: $radius-pill;
  background: $color-danger-soft;
  color: $color-danger;
  font-size: $fs-md;
  cursor: pointer;

  &:active {
    opacity: 0.8;
  }
}
</style>

<style lang="scss">
/* el-drawer 挂载在 body 下，样式需非 scoped（参考 lv-tip-popper 先例） */
.m-drawer {
  .el-drawer__body {
    padding: 0;
  }
}
</style>
