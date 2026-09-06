<template>
  <el-config-provider :locale="zhCn">
    <router-view v-slot="{ Component }">
      <transition name="fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </el-config-provider>
</template>

<script setup>
// 词灵学园根组件：承载路由出口，切换时淡入淡出（消除刷新/跳转闪屏）
// el-config-provider 提供全局中文语言包（替代原 app.use(ElementPlus) 全量注册）
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<style>
/* 路由过渡动画（全局，需非 scoped） */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
