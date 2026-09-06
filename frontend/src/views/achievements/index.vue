<template>
  <div class="page-container ach-page">
    <!-- ============ 页头 ============ -->
    <div class="page-header">
      <div class="page-header-main">
        <h1 class="page-title">成就殿堂</h1>
        <p class="page-desc">每一份坚持都值得被铭记，继续加油</p>
      </div>
      <button class="ach-back" @click="router.push('/profile')">
        <AppIcon name="chevron-left" :size="14" />
        <span>返回个人中心</span>
      </button>
    </div>

    <!-- ============ 成就总览（白卡数据条，克制主流） ============ -->
    <div class="sum-card" v-if="ach">
      <div class="sum-icon">
        <AppIcon name="trophy" :size="26" />
      </div>
      <div class="sum-body">
        <div class="sum-row">
          <div class="sum-item">
            已解锁 <b>{{ ach.unlockedCount }}</b><i>/{{ ach.totalCount }}</i>
          </div>
          <span class="sum-sep"></span>
          <div class="sum-item">
            成就点 <b>{{ ach.unlockedPoints }}</b><i>/{{ ach.totalPoints }}</i>
          </div>
          <span class="sum-sep"></span>
          <div class="sum-item">
            完成度 <b>{{ percent }}</b><i>%</i>
          </div>
        </div>
        <div class="sum-bar">
          <div class="sum-bar-fill" :style="{ width: percent + '%' }"></div>
        </div>
      </div>
    </div>

    <!-- ============ 分类 Tab（下划线式） ============ -->
    <div class="ach-tabs" v-if="ach">
      <button
        v-for="t in tabs"
        :key="t.key"
        class="ach-tab"
        :class="{ active: activeTab === t.key }"
        @click="activeTab = t.key"
      >
        <span>{{ t.label }}</span>
        <em>{{ t.key === 'all' ? ach.totalCount : countByCategory(t.key) }}</em>
      </button>
    </div>

    <!-- ============ 成就卡片网格 ============ -->
    <div class="ach-grid" v-if="ach">
      <div
        v-for="a in filteredList"
        :key="a.code"
        class="ach-card"
        :class="{ unlocked: a.unlocked }"
      >
        <!-- 已解锁右上角小勾角标 -->
        <span class="ach-check" v-if="a.unlocked">
          <AppIcon name="check" :size="11" />
        </span>

        <div class="ach-card-icon">
          <AppIcon :name="a.unlocked ? a.icon : 'lock'" :size="20" />
        </div>
        <div class="ach-card-body">
          <div class="ach-card-name">
            <span class="name-text">{{ a.name }}</span>
            <span class="ach-card-points">{{ a.points }}分</span>
          </div>
          <div class="ach-card-desc">{{ a.desc }}</div>

          <!-- 已解锁：达成日期 -->
          <div class="ach-card-meta" v-if="a.unlocked">
            {{ formatDate(a.unlockedAt) }} 达成
          </div>

          <!-- 未解锁：进度条 + 还差多少 -->
          <div class="ach-card-progress" v-else>
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: progressPercent(a) + '%' }"></div>
            </div>
            <div class="progress-text">
              <span>{{ a.progress }} / {{ a.target }}</span>
              <span class="remain">还差 {{ a.target - a.progress }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 空态兜底 -->
    <div class="ach-empty" v-else>
      <span>成就加载中…</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import { getAchievements } from '@/api/user'

const router = useRouter()
const ach = ref(null)
const activeTab = ref('all')

/**
 * 后端成就图标名白名单。
 * 图标数据在 Java 端定义，collect-icons.js 扫不到，
 * 必须在此维护字面量数组并重跑 node scripts/collect-icons.js
 */
// eslint-disable-next-line no-unused-vars
const ACH_ICONS = [
  'footprints', 'book-open', 'target', 'swords', 'layers', 'gem',
  'circle-check', 'crown', 'flame', 'activity', 'mountain', 'calendar-check',
  'alarm-clock', 'trending-up', 'sparkles', 'rocket', 'bot', 'bookmark',
  'library', 'gamepad-2', 'trophy', 'medal'
]

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'vocab', label: '词汇积累' },
  { key: 'streak', label: '坚持打卡' },
  { key: 'explore', label: '多元探索' }
]

const filteredList = computed(() => {
  const list = ach.value?.list || []
  return activeTab.value === 'all' ? list : list.filter((a) => a.category === activeTab.value)
})

const percent = computed(() => {
  if (!ach.value?.totalCount) return 0
  return Math.round((ach.value.unlockedCount / ach.value.totalCount) * 100)
})

function countByCategory(cat) {
  return (ach.value?.list || []).filter((a) => a.category === cat).length
}

function progressPercent(a) {
  if (!a.target) return 0
  return Math.min(100, Math.round((a.progress / a.target) * 100))
}

/** 达成时间格式化：2026-09-06 */
function formatDate(raw) {
  if (!raw) return ''
  const d = new Date(raw)
  if (isNaN(d.getTime())) return String(raw).slice(0, 10)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(async () => {
  try {
    ach.value = await getAchievements()
  } catch (e) {
    /* 错误由全局拦截器提示 */
  }
})
</script>

<style lang="scss" scoped>
.ach-page {
  max-width: 1000px;
  margin: 0 auto;
}

/* ============ 页头返回链接（轻文字链，不做大按钮） ============ */
.ach-back {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 6px 10px;
  font-size: $fs-sm;
  color: $text-caption;
  background: transparent;
  border: none;
  border-radius: $radius-sm;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    color: $color-primary;
    background: $primary-1;
  }
}

/* ============ 成就总览：浅绿渐变数据条（顶部颜色区分） ============ */
.sum-card {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 22px 26px;
  margin-top: 16px;
  background: linear-gradient(135deg, $primary-1 0%, #fff 70%);
  border: 1px solid $primary-3;
  border-radius: $radius-card;

  .sum-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 54px;
    height: 54px;
    flex-shrink: 0;
    color: #fff;
    background: linear-gradient(135deg, $color-primary 0%, $primary-6 100%);
    border-radius: 16px;
    box-shadow: 0 4px 10px rgba(58, 140, 137, 0.28);
  }

  .sum-body {
    flex: 1;
    min-width: 0;
  }

  .sum-row {
    display: flex;
    align-items: baseline;
    gap: 18px;
    font-size: $fs-sm;
    color: $text-caption;

    .sum-item {
      b {
        margin: 0 2px;
        font-size: 22px;
        font-weight: 800;
        color: $primary-6;
        font-variant-numeric: tabular-nums;
      }

      i {
        font-style: normal;
        color: $text-disabled;
        font-variant-numeric: tabular-nums;
      }
    }

    .sum-sep {
      width: 1px;
      height: 26px;
      background: $primary-3;
    }
  }

  .sum-bar {
    height: 6px;
    margin-top: 14px;
    background: $primary-2;
    border-radius: $radius-pill;
    overflow: hidden;

    .sum-bar-fill {
      height: 100%;
      background: linear-gradient(90deg, $primary-5, $primary-6);
      border-radius: $radius-pill;
      transition: width 0.6s cubic-bezier(0.22, 1, 0.36, 1);
    }
  }
}

/* ============ 分类 Tab：下划线式 ============ */
.ach-tabs {
  display: flex;
  gap: 26px;
  margin: 24px 0 16px;
  border-bottom: 1px solid $border-light;
}

.ach-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 10px 2px;
  font-size: $fs-md;
  color: $text-secondary;
  background: transparent;
  border: none;
  cursor: pointer;
  transition: color 0.2s ease;

  em {
    font-style: normal;
    font-size: $fs-xs;
    color: $text-disabled;
    font-variant-numeric: tabular-nums;
  }

  &::after {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    bottom: -1px;
    height: 2px;
    background: transparent;
    border-radius: 2px;
    transition: background 0.2s ease;
  }

  &:hover {
    color: $color-primary;
  }

  &.active {
    color: $color-primary;
    font-weight: 700;

    em {
      color: $color-primary;
    }

    &::after {
      background: $color-primary;
    }
  }
}

/* ============ 成就卡片网格 ============ */
.ach-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.ach-card {
  position: relative;
  display: flex;
  gap: 13px;
  padding: 18px 16px;
  background: $bg-card;
  border: 1px solid $border-color;
  border-radius: $radius-card;
  transition:
    transform 0.25s ease,
    box-shadow 0.25s ease,
    border-color 0.25s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: $shadow-md;
    border-color: $primary-3;

    .ach-card-icon {
      border-color: $primary-3;
    }
  }

  &.unlocked {
    border-color: $primary-3;
    background: linear-gradient(180deg, $primary-1 0%, $bg-card 55%);

    .ach-card-icon {
      color: $color-primary;
      background: #fff;
      border-color: $primary-3;
    }

    .name-text {
      color: $primary-6;
    }
  }
}

.ach-check {
  position: absolute;
  top: 10px;
  right: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  color: #fff;
  background: $color-primary;
  border-radius: 50%;
}

.ach-card-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  color: $text-disabled;
  background: $gray-2;
  border: 1px solid $border-light;
  border-radius: 50%;
  transition: all 0.25s ease;
}

.ach-card-body {
  flex: 1;
  min-width: 0;
}

.ach-card-name {
  display: flex;
  align-items: center;
  gap: 7px;

  .name-text {
    font-size: $fs-md;
    font-weight: 700;
    color: $text-primary;
  }
}

.ach-card-points {
  flex-shrink: 0;
  font-size: $fs-xs;
  font-weight: 600;
  color: $text-caption;
  font-variant-numeric: tabular-nums;

  .unlocked & {
    color: $color-primary;
  }
}

.ach-card-desc {
  margin-top: 3px;
  font-size: $fs-xs;
  color: $text-caption;
}

.ach-card-meta {
  margin-top: 10px;
  font-size: $fs-xs;
  color: $color-primary;
}

.ach-card-progress {
  margin-top: 10px;
}

.progress-bar {
  height: 4px;
  background: $gray-3;
  border-radius: $radius-pill;
  overflow: hidden;

  .progress-fill {
    height: 100%;
    background: $color-primary;
    border-radius: $radius-pill;
    transition: width 0.5s ease;
  }
}

.progress-text {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  font-size: $fs-xs;
  color: $text-caption;
  font-variant-numeric: tabular-nums;

  .remain {
    color: $text-disabled;
  }
}

/* ============ 空态 ============ */
.ach-empty {
  padding: 80px 0;
  color: $text-caption;
  font-size: $fs-sm;
  text-align: center;
}

/* 响应式 */
@media (max-width: 900px) {
  .ach-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 600px) {
  .ach-grid {
    grid-template-columns: 1fr;
  }

  .sum-card {
    flex-direction: column;
    align-items: flex-start;
    gap: 14px;
  }

  .ach-tabs {
    overflow-x: auto;
    gap: 18px;
  }
}
</style>
