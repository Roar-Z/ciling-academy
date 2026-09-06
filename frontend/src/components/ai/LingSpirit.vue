<template>
  <div ref="rootRef" class="ling-spirit" :style="containerStyle" aria-hidden="true">
    <!-- 真实渲染：单张 <img> 不断切换 src，浏览器命中预加载缓存，无白屏 -->
    <img
      v-if="frames.length && currentUrl"
      :src="currentUrl"
      :width="size"
      :height="size"
      class="ling-img"
      :class="{ 'is-loaded': loaded }"
      draggable="false"
      @load="onLoad"
      @error="onFrameError"
    />
    <!-- 兜底：在帧未加载完成前的占位，避免页面抖动 -->
    <div v-else class="ling-fallback">{{ fallbackIcon }}</div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

/**
 * 词灵 2D 序列帧组件
 *
 * - mode 决定动画来源 (greet / hold / think / talk)
 * - 每个模式下，按 fps 速度循环播放完整序列帧，模型完整呈现
 * - 不依赖任何 3D 资源
 *
 * 兼容性：原 3D 组件曾使用 mode="idle" 表示待机态，这里自动映射到 hold
 */

const props = defineProps({
  /**
   * 词灵当前动作：
   *   greet  - 进入页面/新会话时的欢迎动画（一次性，可循环显示完整欢迎动作）
   *   hold   - 待机/陪伴动画（用户在页面且没有问题时的常态）
   *   think  - 用户点击发送后到 AI 返回前的思考动画
   *   talk   - AI 正在回答时的说话动画
   * 兼容旧值：'idle' 等同 hold
   */
  mode: { type: String, default: 'hold' },
  /** 渲染尺寸（正方形），自动按帧本身尺寸画布等比 */
  size: { type: Number, default: 160 },
  /** 透明背景 */
  transparent: { type: Boolean, default: true }
})

/* ============ 帧配置 ============ */
// 内置默认配置（兜底用），实际优先从 /frames/manifest.json 加载
const DEFAULT_CONFIG = {
  greet:    { count: 96, prefix: '/frames/greet/video_',    pad: 3, fps: 24, looping: true },
  holding:  { count: 96, prefix: '/frames/holding/video_', pad: 3, fps: 24, looping: true },
  // thinking：原速 24fps 单向循环，到末尾直接 cross-fade 回起点，不在尾帧定格（避免"卡顿感"）
  thinking: { count: 60, prefix: '/frames/thinking/video_', pad: 3, fps: 24, looping: true },
  talking:  { count: 57, prefix: '/frames/talking/video_',  pad: 3, fps: 24, looping: true }
}

// 'idle' → 'holding' 的兼容映射
const ALIAS = { idle: 'holding', hold: 'holding' }

function normMode(m) {
  return ALIAS[m] || m
}

/* ============ 状态 ============ */
const rootRef = ref(null)
const frames = ref([])        // 当前激活模式的帧 URL 列表
const currentIndex = ref(0)
const currentUrl = ref('')
const loaded = ref(false)
const fallbackIcon = ref('✨')

let manifestConfig = null
let preloadCache = null       // { [mode]: { urls: string[], imgs: HTMLImageElement[] } }
let fpsTimer = null           // 随当前模式刷新
let pausedByVisibility = false

const containerStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  background: props.transparent ? 'transparent' : '#fff'
}))

/* ============ 数据加载 ============ */
async function loadManifest() {
  if (manifestConfig) return manifestConfig
  try {
    const res = await fetch('/frames/manifest.json', { cache: 'no-cache' })
    if (res.ok) {
      const json = await res.json()
      manifestConfig = json
      return manifestConfig
    }
  } catch (e) { /* ignore */ }
  manifestConfig = DEFAULT_CONFIG
  return manifestConfig
}

function buildFrames(modeKey) {
  const cfg = (manifestConfig || DEFAULT_CONFIG)[modeKey] || DEFAULT_CONFIG.holding
  const urls = []
  for (let i = 0; i < cfg.count; i++) {
    const idx = String(i).padStart(cfg.pad, '0')
    urls.push(`${cfg.prefix}${idx}.webp`)
  }
  return urls
}

/* ============ 预加载 ============ */
function preloadMode(modeKey, urls) {
  if (!preloadCache) preloadCache = {}
  if (preloadCache[modeKey]) return preloadCache[modeKey]
  const imgs = urls.map((u) => {
    const img = new Image()
    img.decoding = 'async'
    img.loading = 'eager'
    img.src = u
    return img
  })
  preloadCache[modeKey] = { urls, imgs }
  return preloadCache[modeKey]
}

/* ============ 播放控制 ============ */
/**
 * 各模式跳过"站立"前缀帧的起始索引
 * 原因：thinking/000、holding/000、talking/000 是同一张"站立持卡"图，
 *       直接从 0 播放会让用户感觉"没在思考/说话"。
 *       thinking/015 起明显"皱眉思考"，talking/005 起明显"开口说话"。
 *       hold / greet 保持从 0 开始（站立就是常态）。
 */
const MODE_START_INDEX = {
  thinking: 15,
  talking: 5
}

function applyMode(rawMode) {
  const m = normMode(rawMode)
  const cfg = (manifestConfig || DEFAULT_CONFIG)[m] || DEFAULT_CONFIG.holding
  const urls = buildFrames(m)
  frames.value = urls
  preloadMode(m, urls)

  // 起始帧：跳过站立前缀（闭包内捕获，供 fpsTimer 循环时使用）
  const total = urls.length
  const startIndex = Math.min(MODE_START_INDEX[m] || 0, Math.max(0, total - 1))
  currentIndex.value = startIndex
  currentUrl.value = urls[startIndex] || ''
  loaded.value = false

  // 重置播放 FPS
  if (fpsTimer) {
    clearInterval(fpsTimer)
    fpsTimer = null
  }
  if (fadeTimer) {
    clearTimeout(fadeTimer)
    fadeTimer = null
  }
  const intervalMs = Math.max(33, Math.round(1000 / (cfg.fps || 24)))
  const holdAtEndMs = Math.max(0, Number(cfg.holdAtEndMs) || 0)
  const bidirectional = cfg.bidirectional === true
  let holdRemaining = 0  // 当前轮末"停留尾帧"的剩余毫秒
  let direction = 1      // 1=正向（startIndex→末尾），-1=反向；仅 bidirectional=true 时启用
  fpsTimer = setInterval(() => {
    if (pausedByVisibility) return
    const len = frames.value.length
    if (!len) return
    // 轮末停留倒计时：归零时单向模式回到起点（并淡入），双向模式保持尾帧继续反向
    if (holdRemaining > 0) {
      holdRemaining -= intervalMs
      if (holdRemaining <= 0) {
        holdRemaining = 0
        if (!bidirectional) {
          currentIndex.value = startIndex
          currentUrl.value = frames.value[startIndex] || ''
          triggerLoopFade()
        }
        // 双向模式：保持当前尾帧，下一 tick 自然反向播放
      }
      return
    }
    let next = currentIndex.value + direction
    if (cfg.looping !== false && next >= len) {
      if (bidirectional) {
        // 双向：到末尾先停留（若有），再反向
        if (holdAtEndMs > 0) {
          holdRemaining = holdAtEndMs
          const lastIdx = len - 1
          currentIndex.value = lastIdx
          currentUrl.value = frames.value[lastIdx] || ''
        }
        direction = -1
        next = currentIndex.value - 1
      } else {
        // 单向：到末尾 → 定格尾帧（holdAtEndMs>0）或直接回起点（并淡入）
        const lastIdx = len - 1
        currentIndex.value = lastIdx
        currentUrl.value = frames.value[lastIdx] || ''
        if (holdAtEndMs <= 0) {
          currentIndex.value = startIndex
          currentUrl.value = frames.value[startIndex] || ''
          triggerLoopFade()
        } else {
          holdRemaining = holdAtEndMs
        }
        return
      }
    } else if (cfg.looping !== false && bidirectional && next < startIndex) {
      if (holdAtEndMs > 0) {
        holdRemaining = holdAtEndMs
        currentIndex.value = startIndex
        currentUrl.value = frames.value[startIndex] || ''
      }
      direction = 1
      next = currentIndex.value + 1
    }
    if (next >= 0 && next < len) {
      currentIndex.value = next
      currentUrl.value = frames.value[next]
    }
  }, intervalMs)
}

/**
 * 循环点平滑过渡：切回起点那一帧时，先短暂淡出再淡入，形成柔和的"眨眼过渡"，
 * 把硬切跳回第 15 帧变成柔和过渡，避免循环跳跃感 + 尾帧定格卡顿。
 * 仅影响循环边界，不影响动作速度。
 */
let fadeTimer = null
function triggerLoopFade() {
  loaded.value = false
  if (fadeTimer) clearTimeout(fadeTimer)
  // 100ms 淡出窗口（配合 CSS 180ms transition 的 ease-out，前段几乎完全淡完再淡入）
  fadeTimer = setTimeout(() => { loaded.value = true; fadeTimer = null }, 100)
}

/* ============ 帧加载回调 ============ */
function onLoad() { loaded.value = true }
function onFrameError() {
  // 静默失败：保留兜底
  // eslint-disable-next-line no-console
  console.warn('[LingSpirit] 帧加载失败:', currentUrl.value)
}

/* ============ 生命周期 ============ */
onMounted(async () => {
  await loadManifest()
  applyMode(props.mode)
  document.addEventListener('visibilitychange', onVisibility)
})

onBeforeUnmount(() => {
  if (fpsTimer) clearInterval(fpsTimer)
  fpsTimer = null
  if (fadeTimer) clearTimeout(fadeTimer)
  fadeTimer = null
  document.removeEventListener('visibilitychange', onVisibility)
})

function onVisibility() {
  pausedByVisibility = document.hidden
}

/* ============ 响应模式切换 ============ */
watch(
  () => props.mode,
  (v) => applyMode(v)
)
</script>

<style lang="scss" scoped>
.ling-spirit {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  pointer-events: none;
  user-select: none;
  -webkit-user-drag: none;

  .ling-img {
    display: block;
    width: 100% !important;
    height: 100% !important;
    object-fit: contain;        /* 完整渲染，不裁切 */
    object-position: center;
    image-rendering: -webkit-optimize-contrast;
    opacity: 0;
    transition: opacity 180ms ease-out;

    &.is-loaded {
      opacity: 1;
    }
  }
}

.ling-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  color: #999;
  background: linear-gradient(180deg, #f7faf9 0%, #eef6f3 100%);
  border-radius: 12px;
}
</style>
