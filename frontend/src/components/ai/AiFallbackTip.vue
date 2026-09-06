<template>
  <div class="ai-fallback-tip" v-if="show">
    <el-icon class="tip-icon"><InfoFilled /></el-icon>
    <div class="tip-body">
      <p class="tip-title">词灵AI暂时不可用</p>
      <p class="tip-desc">请稍后再试，其他学习功能不受影响，可继续使用背单词、试卷与游戏</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'

const props = defineProps({
  /** 是否显示 */
  show: { type: Boolean, default: true },
  /** 自定义文案 */
  message: { type: String, default: '词灵AI暂时不可用，请稍后再试，其他学习功能不受影响。' }
})

const showTip = computed(() => props.show)
</script>

<style lang="scss" scoped>
.ai-fallback-tip {
  display: flex;
  align-items: flex-start;
  gap: $sp-2;
  padding: $sp-3 $sp-4;
  border-radius: $radius-base;
  background: $primary-1;
  border: 1px solid $primary-2;

  .tip-icon {
    color: $color-primary;
    font-size: 16px;
    margin-top: 2px;
    flex-shrink: 0;
  }

  .tip-title {
    font-size: $fs-md;
    font-weight: 600;
    color: $text-title;
  }

  .tip-desc {
    font-size: $fs-sm;
    color: $text-caption;
    margin-top: 1px;
  }
}
</style>
