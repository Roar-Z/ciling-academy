<template>
  <Teleport to="body">
    <Transition name="aqd-fade">
      <div v-if="modelValue" class="aqd-mask" @click.self="close">
        <div class="aqd-modal" role="dialog" aria-modal="true" aria-labelledby="aqd-title">
          <!-- 关闭按钮（右上角，国内大厂标准位置） -->
          <button class="aqd-close" aria-label="关闭" @click="close">
            <AppIcon name="x" :size="16" />
          </button>

          <!-- 头部：标题 + 副标题 -->
          <div class="aqd-header">
            <div class="aqd-icon">
              <AppIcon name="sparkles" :size="18" />
            </div>
            <h3 id="aqd-title" class="aqd-title">AI 额度</h3>
            <p class="aqd-sub">实时更新 · 智能分配</p>
          </div>

          <!-- 主体：两张额度卡片 -->
          <div class="aqd-body">
            <!-- ============ 每日免费额度 ============ -->
            <div class="quota-card qc-daily" :class="{ 'is-zero': dailyRemain === 0 }">
              <div class="qc-head">
                <span class="qc-label">
                  <AppIcon name="zap" :size="13" class="qc-label-icon" />
                  每日免费额度
                </span>
                <span v-if="dailyRemain === 0" class="qc-tag qc-tag-warn">已用完</span>
              </div>

              <div class="qc-num">
                <span class="qc-current">{{ dailyRemain }}</span>
                <span class="qc-sep">/</span>
                <span class="qc-total">{{ dailyQuota }}</span>
              </div>

              <div class="qc-bar">
                <div class="qc-fill" :style="{ width: dailyUsedPercent + '%' }"></div>
              </div>

              <div class="qc-foot">
                <span>已用 <b>{{ dailyUsed }}</b> 次</span>
                <span>每日 00:00 自动重置</span>
              </div>
            </div>

            <!-- ============ 永久奖励额度 ============ -->
            <div class="quota-card qc-bonus" :class="{ 'is-zero': bonusRemain === 0 }">
              <div class="qc-head">
                <span class="qc-label">
                  <AppIcon name="gift" :size="13" class="qc-label-icon" />
                  永久奖励额度
                </span>
              </div>

              <div class="qc-num qc-num-single">
                <span class="qc-current">{{ bonusRemain }}</span>
                <span class="qc-unit">次</span>
              </div>

              <div class="qc-foot qc-foot-single">
                永不过期 · 可叠加累积
              </div>
            </div>

            <!-- ============ 实时刷新状态 ============ -->
            <div class="aqd-meta">
              <span class="meta-dot" :class="{ 'is-pulsing': refreshing }"></span>
              <span class="meta-text">实时更新中 · 最近一次 {{ lastUpdateText }}</span>
            </div>
          </div>

          <!-- 底部：按钮 -->
          <div class="aqd-footer">
            <button class="btn-secondary" @click="close">关闭</button>
            <button class="btn-primary" @click="goShop">购买更多</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import { useUserStore } from '@/store/user'

const props = defineProps({
  /** v-model:visible */
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'close'])

const router = useRouter()
const userStore = useUserStore()

const refreshing = ref(false)
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

/** 实时显示"刚刚 / X 秒前 / X 分钟前" */
const lastUpdateText = computed(() => {
  const t = userStore.aiQuotaLastUpdate
  if (!t) return '刚刚'
  const diff = Math.floor((Date.now() - t) / 1000)
  if (diff < 10) return '刚刚'
  if (diff < 60) return `${diff} 秒前`
  const min = Math.floor(diff / 60)
  if (min < 60) return `${min} 分钟前`
  const h = Math.floor(min / 60)
  return `${h} 小时前`
})

function close() {
  emit('update:modelValue', false)
  emit('close')
}

function goShop() {
  close()
  router.push('/shop')
}

/** 弹窗打开：立即拉一次 + 启动 10s 自动刷新 */
async function startRefresh() {
  if (refreshInterval) return
  await doRefresh()
  refreshInterval = setInterval(doRefresh, 10000)
}

/** 弹窗关闭：清除定时器 */
function stopRefresh() {
  if (refreshInterval) {
    clearInterval(refreshInterval)
    refreshInterval = null
  }
}

async function doRefresh() {
  refreshing.value = true
  try {
    await userStore.refreshQuota()
  } finally {
    refreshing.value = false
  }
}

watch(
  () => props.modelValue,
  (val) => {
    if (val) startRefresh()
    else stopRefresh()
  }
)

onBeforeUnmount(stopRefresh)
</script>

<style lang="scss" scoped>
/* ============================================
   弹窗遮罩 + 容器（国内大厂风格：居中、轻阴影、不浮夸）
   ============================================ */
.aqd-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $sp-4;
}

.aqd-modal {
  position: relative;
  width: 420px;
  max-width: 100%;
  background: $bg-card;
  border-radius: $radius-large;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.2);
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 48px);
  overflow: hidden;
}

/* 关闭按钮（右上角 32×32 方形） */
.aqd-close {
  position: absolute;
  top: $sp-3;
  right: $sp-3;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: $text-caption;
  border-radius: $radius-base;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 160ms ease-out;
  z-index: 2;

  &:hover {
    background: $gray-3;
    color: $text-title;
  }
}

/* ============================================
   头部
   ============================================ */
.aqd-header {
  padding: $sp-6 $sp-6 $sp-4;
  text-align: center;
  border-bottom: 1px solid $border-light;
}

.aqd-icon {
  width: 40px;
  height: 40px;
  margin: 0 auto $sp-3;
  border-radius: $radius-base;
  background: $primary-1;
  color: $color-primary;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.aqd-title {
  margin: 0 0 4px;
  font-size: 17px;
  font-weight: 600;
  color: $text-title;
  letter-spacing: -0.01em;
  line-height: 1.3;
}

.aqd-sub {
  margin: 0;
  font-size: $fs-base;
  color: $text-caption;
  letter-spacing: 0.01em;
}

/* ============================================
   主体：两张额度卡片
   ============================================ */
.aqd-body {
  padding: $sp-5 $sp-6;
  overflow-y: auto;
  @include thin-scrollbar;
}

.quota-card {
  background: $bg-soft;
  border: 1px solid $border-light;
  border-radius: $radius-base;
  padding: $sp-4 $sp-5;
  margin-bottom: $sp-3;
  transition: all 200ms ease-out;

  &:last-child {
    margin-bottom: 0;
  }

  &.is-zero {
    background: $color-warning-soft;
    border-color: #fde0a8;
  }
}

/* 卡片头部 */
.qc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $sp-3;
}

.qc-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: $fs-base;
  font-weight: 500;
  color: $text-body;
  letter-spacing: 0.01em;

  .qc-label-icon {
    color: $color-primary;
  }
}

.qc-bonus .qc-label-icon {
  color: $color-warning;
}

.qc-tag {
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 6px;
  font-size: 11px;
  font-weight: 500;
  border-radius: 3px;

  &.qc-tag-warn {
    background: $color-warning;
    color: #fff;
  }
}

/* 大数字（剩余 / 总额） */
.qc-num {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: $sp-3;
  font-variant-numeric: tabular-nums;
}

.qc-current {
  font-size: 32px;
  font-weight: 700;
  color: $text-title;
  letter-spacing: -0.02em;
  line-height: 1.05;
}

.qc-num .qc-current.is-zero {
  color: $color-warning;
}

.qc-sep {
  font-size: 18px;
  font-weight: 400;
  color: $text-disabled;
  margin: 0 2px;
}

.qc-total {
  font-size: 18px;
  font-weight: 500;
  color: $text-caption;
}

/* 永久额度单数字 */
.qc-num-single {
  margin-bottom: $sp-2;

  .qc-current {
    color: $color-warning;
  }

  .qc-unit {
    font-size: 13px;
    color: $text-caption;
    font-weight: 400;
    margin-left: 4px;
  }
}

/* 进度条 */
.qc-bar {
  height: 4px;
  background: $gray-3;
  border-radius: 2px;
  overflow: hidden;
  margin-bottom: $sp-3;

  .quota-card.is-zero & {
    background: rgba($color-warning, 0.15);
  }
}

.qc-fill {
  height: 100%;
  background: $color-primary;
  border-radius: 2px;
  transition: width 420ms ease-out;

  .quota-card.is-zero & {
    background: $color-warning;
  }
}

/* 卡片底部元信息 */
.qc-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: $fs-sm;
  color: $text-caption;
  font-variant-numeric: tabular-nums;

  b {
    font-weight: 600;
    color: $text-body;
    margin: 0 2px;
  }
}

.qc-foot-single {
  justify-content: flex-start;
  font-size: $fs-sm;
  color: $text-caption;
}

/* ============================================
   实时刷新状态
   ============================================ */
.aqd-meta {
  margin-top: $sp-4;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: $fs-sm;
  color: $text-caption;
}

.meta-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: $color-success;
  flex-shrink: 0;
  position: relative;

  &.is-pulsing::before {
    content: '';
    position: absolute;
    inset: -3px;
    border-radius: 50%;
    background: $color-success;
    opacity: 0.3;
    animation: meta-pulse 1.2s ease-out infinite;
  }
}

@keyframes meta-pulse {
  0% { transform: scale(0.6); opacity: 0.6; }
  100% { transform: scale(1.8); opacity: 0; }
}

.meta-text {
  letter-spacing: 0.01em;
}

/* ============================================
   底部按钮
   ============================================ */
.aqd-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: $sp-2;
  padding: $sp-3 $sp-6 $sp-5;
  border-top: 1px solid $border-light;
  background: $bg-card;
}

.btn-primary,
.btn-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 0 $sp-4;
  font-size: 13px;
  font-weight: 500;
  border-radius: $radius-base;
  cursor: pointer;
  transition: all 160ms ease-out;
  border: 1px solid transparent;
  white-space: nowrap;
}

.btn-primary {
  background: $color-primary;
  color: #fff;
  border-color: $color-primary;

  &:hover {
    background: $primary-6;
    border-color: $primary-6;
  }
  &:active {
    background: $primary-7;
    border-color: $primary-7;
  }
}

.btn-secondary {
  background: #fff;
  color: $text-title;
  border-color: $border-base;

  &:hover {
    border-color: $color-primary;
    color: $color-primary;
    background: $primary-1;
  }
}

/* ============================================
   进场动画
   ============================================ */
.aqd-fade-enter-active,
.aqd-fade-leave-active {
  transition: opacity 220ms ease-out;

  .aqd-modal {
    transition: transform 220ms cubic-bezier(0.16, 1, 0.3, 1);
  }
}

.aqd-fade-enter-from,
.aqd-fade-leave-to {
  opacity: 0;

  .aqd-modal {
    transform: scale(0.96) translateY(8px);
  }
}

/* ============================================
   移动端适配
   ============================================ */
@media (max-width: 480px) {
  .aqd-mask {
    padding: $sp-3;
    align-items: flex-end;
  }

  .aqd-modal {
    width: 100%;
    border-radius: $radius-large $radius-large 0 0;
    max-height: 85vh;
  }

  .aqd-header {
    padding: $sp-5 $sp-4 $sp-3;
  }

  .aqd-body {
    padding: $sp-4;
  }

  .aqd-footer {
    padding: $sp-3 $sp-4 $sp-4;
  }
}
</style>