<template>
  <component :is="isMobile ? MobileLayout : MainLayout" />
</template>

<script setup>
/**
 * 布局桥：路由表中唯一的布局壳占位。
 * 按屏宽（768 断点）在 PC 布局与移动端布局之间自动切换，
 * 子页面 view 组件两套布局完全复用，PC 端样式零改动。
 * 跨断点时子 router-view 整棵重建（App.vue 无 keep-alive，为默认行为）。
 */
import { useDevice } from '@/composables/useDevice'
import MainLayout from './MainLayout.vue'
import MobileLayout from './MobileLayout.vue'

const { isMobile } = useDevice()
</script>
