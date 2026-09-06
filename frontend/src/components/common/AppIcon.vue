<template>
  <Icon :icon="iconName" :width="size" :height="size" :style="colorStyle" class="app-icon" />
</template>

<script setup>
/**
 * 全站统一图标组件（基于 Iconify + Lucide 开源图标集）
 *
 * Lucide 是 ISC 许可的免费开源线性图标库，风格现代精致，
 * 全站用它替代 emoji，保证视觉专业统一。
 *
 * 用法：<AppIcon name="book-open" :size="20" color="#3A8C89" />
 * 图标名查询：https://lucide.dev/icons/
 */
import { computed } from 'vue'
import { Icon } from '@iconify/vue'

const props = defineProps({
  /** Iconify 图标名：
   *  - 带冒号写完整集合，如 "mdi:treasure-chest" / "noto:treasure-chest"（任意 iconify 集）
   *  - 不带冒号默认按 Lucide 处理，如 "gift" 等价 "lucide:gift" */
  name: { type: String, required: true },
  /** 尺寸（px） */
  size: { type: [Number, String], default: 18 },
  /** 颜色（单色图标生效；彩色贴图如 noto/fluent-emoji 自带颜色，外部 color 不会覆盖） */
  color: { type: String, default: 'currentColor' }
})
const iconName = computed(() => props.name.includes(':') ? props.name : `lucide:${props.name}`)
/* 默认 currentColor 时不输出内联样式，否则内联优先级会盖掉外部 CSS 的 color */
const colorStyle = computed(() => (props.color && props.color !== 'currentColor' ? { color: props.color } : undefined))
</script>

<style scoped>
.app-icon {
  display: inline-block;
  vertical-align: middle;
  flex-shrink: 0;
}
</style>
