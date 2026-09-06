<template>
  <div class="ai-loading">
    <!-- 骨架屏 -->
    <div class="skeleton-wrap">
      <el-skeleton animated>
        <template #template>
          <el-skeleton-item variant="h3" style="width: 38%; margin-bottom: 14px" />
          <el-skeleton-item variant="text" style="width: 94%; margin-bottom: 8px" />
          <el-skeleton-item variant="text" style="width: 86%; margin-bottom: 8px" />
          <el-skeleton-item variant="text" style="width: 90%" />
        </template>
      </el-skeleton>
    </div>

    <!-- 打字机 -->
    <div class="typewriter" v-if="showTypewriter">
      <span class="typing-text">{{ displayed }}</span>
      <span class="typing-cursor"></span>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  /** 打字机文案，为空则只显示骨架屏 */
  text: { type: String, default: '词灵AI正在思考中…' },
  /** 打字速度（毫秒/字） */
  speed: { type: Number, default: 90 },
  /** 是否展示打字机 */
  showTypewriter: { type: Boolean, default: true }
})

const displayed = ref('')
let timer = null

watch(
  () => props.text,
  (val) => startTyping(val),
  { immediate: true }
)

function startTyping(text) {
  clearInterval(timer)
  displayed.value = ''
  if (!text) return
  let index = 0
  timer = setInterval(() => {
    displayed.value = text.slice(0, ++index)
    if (index >= text.length) {
      clearInterval(timer)
    }
  }, props.speed)
}

onMounted(() => startTyping(props.text))
onBeforeUnmount(() => clearInterval(timer))
</script>

<style lang="scss" scoped>
.ai-loading {
  padding: 2px 0;
}

.skeleton-wrap {
  padding: 4px 0;
}

.typewriter {
  margin-top: $sp-3;
  display: flex;
  align-items: center;
  gap: 4px;
  color: $text-caption;
  font-size: $fs-base;
  line-height: 1.6;

  .typing-cursor {
    display: inline-block;
    width: 2px;
    height: 14px;
    background: $color-primary;
    animation: blink 1s step-end infinite;
  }
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}
</style>
