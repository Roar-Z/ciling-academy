<template>
  <el-popover
    ref="popoverRef"
    :width="240"
    placement="bottom-end"
    :show-arrow="false"
    trigger="click"
    popper-class="ai-quota-popover"
    v-model:visible="visible"
    @show="onShow"
    @hide="onHide"
  >
    <!-- ============================================
         触发器（导航栏 chip 按钮）
         ============================================ -->
    <template #reference>
      <button
        class="quota-trigger"
        :class="{ 'is-empty': totalRemain <= 0, 'is-open': visible }"
        type="button"
        aria-label="AI 额度详情"
      >
        <span class="quota-trigger-dot" />
        <span class="quota-trigger-text">AI {{ totalRemain }}</span>
        <span class="quota-trigger-arrow" :class="{ 'is-open': visible }">
          <AppIcon name="chevron-down" :size="10" />
        </span>
      </button>
    </template>

    <!-- ============================================
         弹层卡片（紧凑极简版）
         ============================================ -->
    <div class="aqd-card" role="dialog" aria-label="AI 额度详情">
      <h3 class="aqd-title">AI 额度</h3>

      <!-- ============ 每日免费额度 ============ -->
      <div class="aqd-row" :class="{ 'is-zero': dailyRemain === 0 }">
        <div class="aqd-row-head">
          <span class="aqd-label">每日免费额度</span>
          <span class="aqd-value">
            <span class="aqd-num">{{ dailyRemain }}</span>
            <span class="aqd-sep">/</span>
            <span class="aqd-total">{{ dailyQuota }}</span>
          </span>
        </div>
        <div class="aqd-bar">
          <div class="aqd-bar-fill" :style="{ width: dailyUsedPercent + '%' }" />
        </div>
      </div>

      <!-- ============ 永久奖励额度 ============ -->
      <div class="aqd-row">
        <div class="aqd-row-head">
          <span class="aqd-label">永久奖励额度</span>
          <span class="aqd-value">
            <span class="aqd-num">{{ bonusRemain }}</span>
            <span class="aqd-unit">次</span>
          </span>
        </div>
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const popoverRef = ref(null)
const visible = ref(false)
let refreshInterval = null

const detail = computed(() => userStore.aiQuotaDetail)
const dailyQuota = computed(() => detail.value.dailyQuota || 0)
const dailyRemain = computed(() => detail.value.dailyRemain || 0)
const dailyUsed = computed(() => detail.value.dailyUsed || 0)
const dailyUsedPercent = computed(() => {
  if (!dailyQuota.value) return 0
  return Math.min(100, Math.round((dailyUsed.value / dailyQuota.value) * 100))
})
const bonusRemain = computed(() => detail.value.bonusRemain || 0)
const totalRemain = computed(() => detail.value.totalRemain || 0)

/** 弹层打开：立即拉一次 + 启动 10s 自动刷新 */
async function onShow() {
  stopRefresh()
  await doRefresh()
  refreshInterval = setInterval(doRefresh, 10000)
}

/** 弹层关闭：清除定时器 */
function onHide() {
  stopRefresh()
}

function stopRefresh() {
  if (refreshInterval) {
    clearInterval(refreshInterval)
    refreshInterval = null
  }
}

async function doRefresh() {
  try {
    await userStore.refreshQuota()
  } catch (e) {
    /* 静默失败 */
  }
}

onBeforeUnmount(stopRefresh)
</script>

<style lang="scss" scoped>
/* ============================================
   触发器（chip 按钮，带 ▼ 箭头表示可点击）
   ============================================ */
.quota-trigger {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 8px 5px 10px;
  border-radius: $radius-pill;
  background: $primary-1;
  border: 1px solid $primary-2;
  font-size: 12px;
  font-family: inherit;
  font-weight: 400;
  white-space: nowrap;
  cursor: pointer;
  transition: all 160ms ease-out;
  color: $text-title;

  &:hover {
    background: $primary-2;
    border-color: $primary-3;
  }

  &:active,
  &.is-open {
    background: $primary-2;
    border-color: $primary-3;
  }

  .quota-trigger-dot {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: $color-primary;
    flex-shrink: 0;
  }

  .quota-trigger-text {
    color: $color-primary;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  .quota-trigger-arrow {
    display: inline-flex;
    color: $color-primary;
    opacity: 0.65;
    transition: transform 220ms cubic-bezier(0.16, 1, 0.3, 1);
    margin-left: 1px;

    &.is-open {
      transform: rotate(180deg);
      opacity: 1;
    }
  }

  &.is-empty {
    background: $color-warning-soft;
    border-color: #ffe0b2;

    &:hover,
    &.is-open {
      background: #fff0d4;
      border-color: #fadc8c;
    }

    .quota-trigger-dot {
      background: $color-warning;
    }
    .quota-trigger-text {
      color: #b26a00;
    }
    .quota-trigger-arrow {
      color: #b26a00;
    }
  }
}

/* ============================================
   弹层卡片（紧凑极简）
   ============================================ */
.aqd-card {
  padding: 12px 14px;
}

/* 标题（一行，无关闭按钮） */
.aqd-title {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 500;
  color: $text-title;
  letter-spacing: 0.01em;
}

/* 行：第二行起顶部加分隔线 */
.aqd-row {
  & + .aqd-row {
    margin-top: 8px;
    padding-top: 8px;
    border-top: 1px solid $border-light;
  }
}

.aqd-row-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.aqd-label {
  font-size: 12px;
  color: $text-body;
}

.aqd-value {
  display: inline-flex;
  align-items: baseline;
  gap: 2px;
  font-variant-numeric: tabular-nums;
}

.aqd-num {
  font-size: 15px;
  font-weight: 600;
  color: $color-primary;
  letter-spacing: -0.01em;
  line-height: 1.1;
}

.aqd-num.is-zero {
  color: $color-warning;
}

.aqd-sep {
  font-size: 12px;
  color: $text-disabled;
  margin: 0 1px;
}

.aqd-total {
  font-size: 12px;
  color: $text-caption;
}

.aqd-unit {
  font-size: 11px;
  color: $text-caption;
  font-weight: 400;
  margin-left: 2px;
}

/* 进度条（细 3px） */
.aqd-bar {
  height: 3px;
  background: $gray-3;
  border-radius: 2px;
  overflow: hidden;
  margin-top: 6px;
}

.aqd-row.is-zero .aqd-bar {
  background: rgba($color-warning, 0.18);
}

.aqd-bar-fill {
  height: 100%;
  background: $color-primary;
  border-radius: 2px;
  transition: width 420ms cubic-bezier(0.16, 1, 0.3, 1);
}

.aqd-row.is-zero .aqd-bar-fill {
  background: $color-warning;
}
</style>

<!--
  全局样式：覆盖 el-popover 默认 zoom-in-top 动画，
  替换为丝滑 spring 曲线 + 微下拉位移。
  transform-origin 在右上角，与触发器位置对应。
-->
<style lang="scss">
.ai-quota-popover {
  padding: 0 !important;
  border: 1px solid $border-light !important;
  border-radius: 10px !important;
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.10) !important;
  background: $bg-card !important;
  transform-origin: top right !important;
  will-change: opacity, transform;
}

.ai-quota-popover.el-zoom-in-top-enter-active {
  animation: aqd-pop-in 220ms cubic-bezier(0.16, 1, 0.3, 1) both !important;
}
.ai-quota-popover.el-zoom-in-top-leave-active {
  animation: aqd-pop-out 160ms cubic-bezier(0.4, 0, 1, 1) both !important;
}

@keyframes aqd-pop-in {
  from {
    opacity: 0;
    transform: translateY(-6px) scale(0.96);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes aqd-pop-out {
  from {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
  to {
    opacity: 0;
    transform: translateY(-4px) scale(0.98);
  }
}
</style>