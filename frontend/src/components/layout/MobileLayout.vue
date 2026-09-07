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
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useImmersive } from '@/composables/useImmersive'
import NotificationBell from '@/components/common/NotificationBell.vue'
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
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))

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
  }
)

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
