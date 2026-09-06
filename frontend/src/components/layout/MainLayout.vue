<template>
  <div class="main-layout" :class="{ 'is-chrome-hidden': hideChrome }">
    <!-- ============ 顶部导航（60px 白底 + 底部分隔线） ============ -->
    <header v-if="!hideChrome" class="ws-header" :class="{ 'is-scrolled': scrolled }">
      <div class="ws-header-inner">
        <!-- Logo -->
        <router-link to="/" class="logo">
          <img src="/logo.png" alt="词灵学园" class="logo-mark" />
          <span class="logo-text">词灵学园</span>
        </router-link>

        <!-- 中间导航：同一布局根据登录状态渲染两套 -->
        <nav class="ws-nav">
          <!-- 未登录导航 -->
          <template v-if="!userStore.isLogin">
            <router-link to="/" class="nav-item" :class="{ 'is-active': route.path === '/' }">首页</router-link>
            <span class="nav-item nav-ai" @click="handleGuestAi">词灵AI</span>
            <router-link to="/intro" class="nav-item" :class="{ 'is-active': route.path === '/intro' }">介绍</router-link>
          </template>

          <!-- 已登录导航 -->
          <template v-else>
            <router-link to="/task" class="nav-item" :class="{ 'is-active': route.path === '/task' }">今日任务</router-link>

            <el-dropdown trigger="click" class="nav-dropdown" @command="go">
              <span
                class="nav-item dropdown-trigger"
                :class="{ 'is-active': ['/review', '/word-book', '/paper-list', '/sentence'].includes(route.path) }"
              >
                学习中心
                <el-icon class="drop-icon"><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="/review">背单词复习</el-dropdown-item>
                  <el-dropdown-item command="/word-book">生词本</el-dropdown-item>
                  <el-dropdown-item command="/sentence">句灵集</el-dropdown-item>
                  <el-dropdown-item command="/paper-list">练习试卷</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>

            <router-link to="/ai-assistant" class="nav-item nav-ai" :class="{ 'is-active': route.path === '/ai-assistant' }">
              词灵AI
            </router-link>
            <router-link to="/game-park" class="nav-item" :class="{ 'is-active': route.path === '/game-park' }">趣味乐园</router-link>
            <router-link to="/tools" class="nav-item" :class="{ 'is-active': ['/tools', '/ai-note', '/long-sentence', '/translate'].includes(route.path) }">词灵工具</router-link>
          </template>
        </nav>

        <!-- 右侧区域 -->
        <div class="nav-right">
          <!-- 未登录：注册 + 登录 -->
          <template v-if="!userStore.isLogin">
            <el-button class="btn-ghost" @click="go('/login?tab=register')">注册</el-button>
            <el-button type="primary" class="btn-solid" @click="go('/login')">登录</el-button>
          </template>

          <!-- 已登录：搜索 + 额度 + 铃铛 + 头像 -->
          <template v-else>
            <div class="search-box">
              <el-autocomplete
                v-model="searchWord"
                placeholder="搜索功能 / 查单词"
                size="default"
                :prefix-icon="Search"
                clearable
                :trigger-on-focus="true"
                :highlight-first-item="true"
                :fetch-suggestions="fetchSuggestions"
                popper-class="search-popper"
                @select="handleSelect"
              >
                <template #default="{ item }">
                  <div class="search-option" :class="{ hot: item.hot }">
                    <span class="search-option-label">{{ item.label }}</span>
                    <span v-if="item.hot" class="search-option-tag">热门</span>
                    <span v-else-if="item.ai" class="search-option-tag ai">AI</span>
                  </div>
                </template>
              </el-autocomplete>
            </div>

            <AiQuotaDropdown />

            <el-tooltip content="系统通知" placement="bottom">
              <NotificationBell />
            </el-tooltip>

            <el-dropdown trigger="click" @command="handleAvatarCommand">
              <div class="avatar-wrap">
                <el-avatar :size="32" :src="userStore.userInfo?.avatar || ''" class="user-avatar">
                  {{ avatarText }}
                </el-avatar>
                <span class="nickname">{{ userStore.userInfo?.nickname || '同学' }}</span>
                <el-icon class="drop-icon"><ArrowDown /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item :icon="MagicStick" command="/ai-assistant">词灵AI</el-dropdown-item>
                  <el-dropdown-item :icon="Notebook" command="/ai-note">AI笔记</el-dropdown-item>
                  <el-dropdown-item :icon="User" command="/profile">个人中心</el-dropdown-item>
                  <el-dropdown-item :icon="Shop" command="/shop">金币商城</el-dropdown-item>
                  <el-dropdown-item :icon="DataLine" command="/report">学习报告</el-dropdown-item>
                  <el-dropdown-item :icon="Setting" command="/settings">设置</el-dropdown-item>
                  <el-dropdown-item divided :icon="SwitchButton" command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </div>
      </div>
    </header>

    <!-- ============ 内容区 ============ -->
    <main class="main-content">
      <router-view />
    </main>

    <!-- ============ 页脚 ============ -->
    <!-- 首页自带完整 footer，隐藏全局简化页脚避免重复 -->
    <footer v-if="!hideChrome && !isHome" class="ws-footer">
      <div class="footer-inner">
        <div class="footer-brand">
          <img src="/logo.webp" alt="词灵学园" class="footer-logo" />
          <span>词灵学园</span>
        </div>
        <p class="footer-slogan">AI增强型英语单词学习平台 · 坚持比天赋更重要</p>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, Bell, ArrowDown, MagicStick, Notebook, Shop, DataLine, Setting, SwitchButton, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useImmersive } from '@/composables/useImmersive'
import NotificationBell from '@/components/common/NotificationBell.vue'
import AiQuotaDropdown from '@/components/ai/AiQuotaDropdown.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 沉浸模式：学习页开启后隐藏顶部导航与底部页脚
const { immersive } = useImmersive()

// 某些页面（登录/注册）隐藏顶部导航与底部页脚
const hideChrome = computed(() => Boolean(route.meta?.hideChrome) || immersive.value)

// 首页有自己的完整页脚，不显示全局简化页脚
const isHome = computed(() => route.path === '/')

const searchWord = ref('')

// 滚动后导航栏加深阴影（主流站点吸顶导航通用做法）
const scrolled = ref(false)
function onScroll() {
  scrolled.value = (window.scrollY || document.documentElement.scrollTop || 0) > 4
}

const features = [
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

const avatarText = computed(() => {
  const name = userStore.userInfo?.nickname || userStore.userInfo?.username || 'W'
  return name.charAt(0).toUpperCase()
})

onMounted(async () => {
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
  // 已登录：拉取用户信息 + 启动额度定时刷新
  if (userStore.isLogin) {
    try {
      await userStore.fetchInfo()
    } catch (e) {
      /* 已由拦截器处理 */
    }
    userStore.startQuotaTimer()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  userStore.clearQuotaTimer()
})

function go(path) {
  router.push(path)
}

function handleAvatarCommand(cmd) {
  if (cmd === 'logout') {
    handleLogout()
  } else {
    go(cmd)
  }
}

/** 游客点击词灵AI：跳登录并提示 */
function handleGuestAi() {
  ElMessage.info('登录后即可使用词灵AI，其他学习功能不受影响')
  router.push({ path: '/login', query: { redirect: '/ai-assistant' } })
}

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

function fetchSuggestions(query, cb) {
  const q = query.trim()
  if (!q) {
    cb(features.filter((f) => f.hot))
    return
  }
  const matched = features.filter(
    (f) =>
      fuzzyMatch(f.label, q) ||
      fuzzyMatch(f.value, q) ||
      fuzzyMatch(f.path, q) ||
      (f.keywords && fuzzyMatch(f.keywords, q))
  )
  if (!features.some((f) => f.label === '查单词')) {
    // noop
  }
  matched.unshift({ label: `查单词：${q}`, value: `dict:${q}`, path: '/tools', query: { word: q }, hot: false })
  cb(matched)
}

function handleSelect(item) {
  searchWord.value = ''
  if (item.query) {
    router.push({ path: item.path, query: item.query })
  } else {
    router.push(item.path)
  }
}

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
  await userStore.logout()
  ElMessage.success('已退出登录，期待下次见面～')
  router.push('/')
}
</script>

<style lang="scss" scoped>
.main-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ---------- 头部 ---------- */
.ws-header {
  position: sticky;
  top: 0;
  z-index: 100;
  height: $nav-height;
  background: $bg-card;
  border-bottom: 1px solid $border-light;
  box-shadow: none;
  transition: box-shadow 0.25s ease;

  /* 滚动后：浮现柔和投影，与内容自然分层 */
  &.is-scrolled {
    box-shadow: 0 4px 16px rgba(15, 23, 42, 0.08), 0 1px 3px rgba(15, 23, 42, 0.05);
  }
}

.ws-header-inner {
  max-width: $page-max-width;
  margin: 0 auto;
  height: 100%;
  padding: 0 $safe-padding;
  display: flex;
  align-items: center;
  gap: $sp-8;
}

.logo {
  display: flex;
  align-items: center;
  gap: $sp-2;
  flex-shrink: 0;

  .logo-mark {
    width: 30px;
    height: 30px;
    border-radius: 9px;
    object-fit: contain;
    flex-shrink: 0;
  }

  .logo-text {
    font-family: -apple-system, 'PingFang SC', 'Microsoft YaHei UI', 'Microsoft YaHei', sans-serif;
    font-size: 17px;
    font-weight: 600;
    color: $color-primary;
  }
}

.ws-nav {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 1;
}

.nav-item {
  display: inline-flex;
  align-items: center;
  padding: 7px 13px;
  border-radius: $radius-base;
  font-size: 14px;
  font-weight: 500;
  color: $text-body;
  cursor: pointer;
  white-space: nowrap;
  transition: color $transition-fast;

  &:hover {
    color: $color-primary;
  }

  &.is-active {
    color: $color-primary;
  }
}

.dropdown-trigger {
  .drop-icon {
    margin-left: 3px;
    font-size: 12px;
    transition: transform $transition-fast;
  }

  &:hover .drop-icon {
    transform: translateY(1px);
  }
}

.nav-right {
  display: flex;
  align-items: center;
  gap: $sp-3;
  flex-shrink: 0;
}

.btn-ghost {
  color: $text-body;
  border-color: $border-base;

  &:hover {
    color: $color-primary;
    border-color: $primary-3;
    background: $primary-1;
  }
}

.btn-solid {
  box-shadow: 0 1px 2px rgba(58, 140, 137, 0.2);
}

.search-box {
  width: 200px;

  :deep(.el-input__wrapper) {
    background: $gray-3;
    box-shadow: none;
    border-radius: $radius-base;

    &:hover,
    &.is-focus {
      background: #fff;
      box-shadow: 0 0 0 1px $primary-3 inset;
    }
  }
}

.search-popper {
  min-width: 220px !important;

  .search-option {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 6px 0;

    .search-option-label {
      font-size: 14px;
      color: $text-body;
    }

    /* 参考国内主流下拉（百度/知乎）：小圆角方标签，而非大胶囊 */
    .search-option-tag {
      font-size: 10px;
      line-height: 16px;
      height: 16px;
      padding: 0 4px;
      border-radius: 3px;
      background: #fff1f0;
      color: #f5222d;
      font-weight: 500;

      &.ai {
        background: #f5f5f5;
        color: #8c8c8c;
      }
    }

    &.hot .search-option-label {
      color: $text-title;
      font-weight: 600;
    }
  }
}

.quota-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  border-radius: $radius-pill;
  background: $primary-1;
  border: 1px solid $primary-2;
  font-size: 12px;
  white-space: nowrap;

  .quota-dot {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: $color-primary;
  }

  .quota-text {
    color: $color-primary;
    font-weight: 600;
  }

  &.is-empty {
    background: $color-warning-soft;
    border-color: #ffe0b2;

    .quota-dot {
      background: $color-warning;
    }

    .quota-text {
      color: #b26a00;
    }
  }
}

.icon-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: $radius-base;
  color: $text-caption;
  cursor: pointer;
  @include flex-center;
  transition: all $transition-fast;

  &:hover {
    background: $gray-3;
    color: $text-title;
  }
}

.avatar-wrap {
  display: flex;
  align-items: center;
  gap: $sp-2;
  cursor: pointer;
  padding: 4px 8px 4px 4px;
  border-radius: $radius-pill;
  transition: background $transition-fast;

  &:hover {
    background: $gray-3;
  }

  .user-avatar {
    background: $color-primary;
    color: #fff;
    font-weight: 600;
    font-size: 14px;
    flex-shrink: 0;
  }

  .nickname {
    max-width: 72px;
    @include ellipsis;
    font-size: 13px;
    font-weight: 500;
    color: $text-title;
  }

  .drop-icon {
    font-size: 12px;
    color: $text-caption;
  }
}

/* ---------- 内容区 ---------- */
.main-content {
  flex: 1;
  width: 100%;
}

/* ---------- 页脚 ---------- */
.ws-footer {
  /* 半透明白遮罩 + 同款固定插画：能透出背景，又保持一条柔和的白色底栏 */
  background-image: linear-gradient(rgba(255, 255, 255, 0.92), rgba(255, 255, 255, 0.92)),
    url('@/assets/images/review-bg.png');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  background-attachment: fixed;
  padding: $sp-6 $safe-padding;

  .footer-inner {
    max-width: $page-max-width;
    margin: 0 auto;
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: $sp-2;
  }

  .footer-brand {
    display: flex;
    align-items: center;
    gap: $sp-2;
    font-size: 14px;
    font-weight: 600;
    color: $text-title;

    .footer-logo {
      width: 22px;
      height: 22px;
      border-radius: 6px;
      object-fit: contain;
      flex-shrink: 0;
    }
  }

  .footer-slogan {
    font-size: 12px;
    color: $text-disabled;
  }
}

@media (max-width: 900px) {
  .ws-header-inner {
    gap: $sp-4;
  }

  .search-box {
    display: none;
  }

  .nickname {
    display: none;
  }
}
</style>
