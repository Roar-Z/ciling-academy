<template>
  <el-popover
    ref="popoverRef"
    :width="380"
    placement="bottom-end"
    :hide-after="0"
    :show-arrow="false"
    trigger="click"
    popper-class="notif-popper"
    @show="onShow"
  >
    <template #reference>
      <button class="bell-btn" :class="{ unread: unreadCount > 0 }">
        <AppIcon name="bell" :size="18" />
        <span v-if="unreadCount > 0" class="bell-dot">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
      </button>
    </template>

    <!-- 头部 -->
    <div class="nt-head">
      <div class="nt-title">
        <AppIcon name="bell" :size="14" />
        <span>系统通知</span>
        <span v-if="unreadCount > 0" class="nt-unread">{{ unreadCount }} 条未读</span>
      </div>
      <button
        class="nt-allread"
        :disabled="unreadCount === 0"
        @click="onMarkAllRead"
      >全部已读</button>
    </div>

    <!-- 列表 -->
    <div class="nt-list" v-if="list.length">
      <div
        v-for="item in list"
        :key="item.id"
        class="nt-item"
        :class="{ unread: item.isRead === 0 }"
        @click="onItemClick(item)"
      >
        <div class="nt-icon" :class="`type-${item.type}`">
          <AppIcon :name="iconOf(item.type)" :size="15" />
        </div>
        <div class="nt-body">
          <div class="nt-row1">
            <span class="nt-name">{{ item.title }}</span>
            <span class="nt-time">{{ formatTime(item.createdAt) }}</span>
          </div>
          <div class="nt-content">{{ item.content }}</div>
        </div>
        <span v-if="item.isRead === 0" class="nt-new-dot"></span>
      </div>
    </div>

    <!-- 空态 -->
    <div v-else class="nt-empty">
      <AppIcon name="inbox" :size="36" />
      <div class="nt-empty-text">暂无通知</div>
    </div>

    <!-- 底部 -->
    <div class="nt-foot">
      <span>最多保留 30 条，超出会自动清理</span>
      <button class="nt-foot-btn" @click="refresh">刷新</button>
    </div>
  </el-popover>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import AppIcon from './AppIcon.vue'
import { listNotifications, markRead as apiMarkRead, markAllRead as apiMarkAllRead, unreadCount as apiUnreadCount, touchDailyGift } from '@/api/notification'

const list = ref([])
const unreadCount = ref(0)
const popoverRef = ref()
let pollTimer = null

// 类型 → 图标映射（Lucide 图标名）
const ICON_MAP = {
  ai_daily: 'sparkles',
  ai_quota_exhausted: 'zap-off',
  review_reminder: 'book-open',
  register_bonus: 'gift',
  reward_check_in: 'flame',
  reward_streak: 'trophy',
  reward_active: 'gift',
  gen: 'check-circle',
  system: 'info'
}
function iconOf(type) { return ICON_MAP[type] || 'bell' }

// 相对时间：刚刚 / N 分钟前 / N 小时前 / YYYY-MM-DD
function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const diff = Math.floor((Date.now() - d.getTime()) / 1000)
  if (diff < 60) return '刚刚'
  if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前'
  if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前'
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function refresh() {
  try {
    list.value = await listNotifications()
  } catch (e) { /* 接口异常静默 */ }
}
async function refreshUnread() {
  try {
    const res = await apiUnreadCount()
    unreadCount.value = res?.count ?? 0
  } catch (e) { /* 接口异常静默 */ }
}

async function onShow() {
  await refresh()
  await refreshUnread()
}

async function onItemClick(item) {
  if (item.isRead === 0) {
    item.isRead = 1
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    try {
      await apiMarkRead(item.id)
    } catch (e) { /* 标记失败无妨 */ }
  }
}

async function onMarkAllRead() {
  if (unreadCount.value === 0) return
  // 乐观更新
  list.value.forEach(i => { i.isRead = 1 })
  unreadCount.value = 0
  try {
    await apiMarkAllRead()
  } catch (e) { /* 接口异常静默 */ }
}

onMounted(async () => {
  await refreshUnread()
  // App 进入时尝试推送每日 AI 赠送通知（每日首次有效）
  try { await touchDailyGift() } catch (e) { /* 静默 */ }
  // 60s 轮询未读数
  pollTimer = setInterval(refreshUnread, 60_000)
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style lang="scss" scoped>
.bell-btn {
  position: relative;
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
  &.unread {
    color: $color-primary;
  }
}
.bell-dot {
  position: absolute;
  top: 2px;
  right: 2px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: $color-danger;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  line-height: 16px;
  text-align: center;
  box-shadow: 0 0 0 2px #fff;
}

/* ---------- 弹层内容 ---------- */
.nt-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  border-bottom: 1px solid $border-light;
}
.nt-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: $text-title;
  :deep(.app-icon) { color: $color-primary; }
  .nt-unread {
    font-size: 12px;
    font-weight: 500;
    color: $color-danger;
    margin-left: 4px;
  }
}
.nt-allread {
  border: none;
  background: transparent;
  font-size: 12px;
  color: $text-caption;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  &:hover:not(:disabled) { color: $color-primary; background: $primary-1; }
  &:disabled { opacity: 0.4; cursor: not-allowed; }
}

.nt-list {
  max-height: 380px;
  overflow-y: auto;
  padding: 6px 0;
  margin: 6px -12px;
  padding-left: 12px;
  padding-right: 12px;
}
.nt-item {
  position: relative;
  display: flex;
  gap: 10px;
  padding: 10px 8px;
  border-radius: $radius-base;
  cursor: pointer;
  transition: background $transition-fast;
  margin-bottom: 4px;
  &:hover { background: $gray-3; }
  &.unread .nt-name { color: $text-title; font-weight: 700; }
  &.unread .nt-content { color: $text-body; }
}
.nt-icon {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  @include flex-center;
  &.type-ai_daily      { background: $primary-1;   color: $color-primary; }
  &.type-ai_quota_exhausted { background: #fff1f0; color: $color-danger; }
  &.type-review_reminder { background: #fff7e6;   color: #fa8c16; }
  &.type-register_bonus{ background: $primary-1;   color: $color-primary; }
  &.type-reward_check_in { background: #fff4e6;   color: #fa8c16; }
  &.type-reward_streak   { background: #fff7e6;   color: #fa8c16; }
  &.type-reward_active   { background: $primary-1; color: $color-primary; }
  &.type-gen             { background: $color-success-soft; color: $color-success; }
  &.type-system          { background: $primary-1; color: $color-primary; }
}
.nt-body { flex: 1; min-width: 0; line-height: 1.4; }
.nt-row1 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.nt-name {
  font-size: 13px;
  color: $text-caption;
  font-weight: 500;
  flex: 1;
  @include ellipsis;
}
.nt-time {
  font-size: 11px;
  color: $text-disabled;
  flex-shrink: 0;
}
.nt-content {
  font-size: 12px;
  color: $text-caption;
  margin-top: 2px;
  word-break: break-all;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.nt-new-dot {
  position: absolute;
  top: 14px;
  right: 6px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: $color-danger;
}

.nt-empty {
  padding: 40px 0;
  @include flex-center;
  flex-direction: column;
  gap: 8px;
  color: $text-disabled;
  :deep(.app-icon) { color: $gray-5; }
  .nt-empty-text { font-size: 13px; }
}

.nt-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
  border-top: 1px solid $border-light;
  font-size: 11px;
  color: $text-disabled;
}
.nt-foot-btn {
  border: none;
  background: transparent;
  font-size: 12px;
  color: $color-primary;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  &:hover { background: $primary-1; }
}
</style>

<style lang="scss">
/* popover 容器在 body，不受 scoped 影响，独立声明 */
.notif-popper {
  padding: 14px !important;
  border-radius: 12px !important;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.12) !important;
  border: 1px solid $border-light !important;

  .el-popper__arrow { display: none; }
}
</style>