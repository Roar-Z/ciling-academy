<template>
  <div class="ai-quota-tip" v-if="show">
    <el-icon class="tip-icon"><WarningFilled /></el-icon>
    <div class="tip-body">
      <p class="tip-title">今日词灵AI额度已用完</p>
      <p class="tip-desc">明日 00:00 自动恢复，背单词、试卷、游戏等学习功能不受影响</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'

const props = defineProps({
  /** 今日剩余额度 */
  remaining: { type: Number, default: 0 },
  /** 是否总是显示（用于预览） */
  forceShow: { type: Boolean, default: false }
})

const show = computed(() => props.forceShow || props.remaining <= 0)
</script>

<style lang="scss" scoped>
.ai-quota-tip {
  display: flex;
  align-items: flex-start;
  gap: $sp-2;
  padding: $sp-3 $sp-4;
  border-radius: $radius-base;
  background: $color-warning-soft;
  border: 1px solid #ffe0b2;

  .tip-icon {
    color: $color-warning;
    font-size: 16px;
    margin-top: 2px;
    flex-shrink: 0;
  }

  .tip-title {
    font-size: $fs-md;
    font-weight: 600;
    color: #874d00;
  }

  .tip-desc {
    font-size: $fs-sm;
    color: #b26a00;
    margin-top: 1px;
  }
}
</style>
