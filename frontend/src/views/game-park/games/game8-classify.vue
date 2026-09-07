<template>
  <div class="game-box">
    <div v-if="loading" class="state-box"><el-skeleton animated :rows="5" style="width: 100%" /></div>
    <div v-else-if="error" class="state-box">
      <div class="state-icon"><AppIcon name="circle-alert" :size="32" /></div>
      <p class="state-text">分类卡片加载失败，请稍后再试</p>
      <el-button type="primary" style="margin-top: 12px" @click="restart">重试</el-button>
    </div>

    <template v-else>
      <div class="game-status">
        <span>得分：<b class="primary">{{ score }}</b></span>
        <span>已归类：<b class="primary">{{ classifiedCount }}</b> / {{ words.length }}</span>
        <span class="status-tip">拖拽单词到正确的词性分类中</span>
      </div>

      <!-- 待分类单词池 -->
      <div class="word-pool">
        <div
          v-for="w in pendingWords"
          :key="w.word"
          class="drag-word"
          draggable="true"
          @dragstart="onDragStart($event, w)"
          @touchstart.prevent="onTouchStart($event, w)"
          @touchmove.prevent="onTouchMove"
          @touchend="onTouchEnd"
          @touchcancel="onTouchCancel"
        >{{ w.word }}</div>
        <el-empty v-if="!pendingWords.length" description="全部归类完成！" :image-size="60" />
      </div>

      <!-- 分类桶 -->
      <div class="category-buckets">
        <div
          v-for="cat in categories"
          :key="cat.key"
          class="bucket"
          :class="{ over: dragOver === cat.key }"
          :data-cat="cat.key"
          @dragover.prevent="dragOver = cat.key"
          @dragleave="dragOver = null"
          @drop.prevent="onDrop(cat)"
        >
          <div class="bucket-head"><span class="cat-dot" :class="cat.key"></span>{{ cat.label }}</div>
          <div class="bucket-body">
            <span v-for="w in cat.items" :key="w.word" class="bucket-word" :class="{ wrong: w.wrong }">
              {{ w.word }}
            </span>
            <span v-if="!cat.items.length" class="bucket-empty">拖到这里</span>
          </div>
        </div>
      </div>

      <!-- 结算 -->
      <div class="game-over" v-if="gameOver">
        <h3><AppIcon name="shapes" :size="20" /> 分类大师通关！</h3>
        <p>得分 {{ score }} · 分类正确 {{ correctCount }}/{{ words.length }}</p>
        <p class="reward" v-if="reward && reward.coins > 0">获得金币 +{{ reward.coins }}</p>
        <div class="over-actions">
          <el-button type="primary" @click="restart">再来一局</el-button>
          <el-button @click="back">返回乐园</el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useGame } from '@/composables/useGame'
import { useGameProgress } from '@/composables/useGameProgress'
import AppIcon from '@/components/common/AppIcon.vue'

const router = useRouter()
const { words, loading, error, load, finish, reportReview } = useGame('game8', '分类大师', 12)

const pendingWords = ref([])
const categories = ref([])
const dragWord = ref(null)
const dragOver = ref(null)
const score = ref(0)
const correctCount = ref(0)
const gameOver = ref(false)
const reward = ref(null)
// 进度持久化：退出再进来续上（拖拽临时态 dragWord/dragOver 不持久化）
const progress = useGameProgress('game8', { pendingWords, categories, score, correctCount, gameOver, reward })

const classifiedCount = computed(() =>
  categories.value.reduce((sum, c) => sum + c.items.length, 0)
)

onMounted(async () => {
  const restored = progress.restore()
  if (!restored) {
    await load()
    if (!error.value) initBoard()
  } else {
    loading.value = false
  }
})

function classify(word) {
  const pos = (word.pos || word.meaning || '').toLowerCase()
  if (pos.startsWith('n')) return 'noun'
  if (pos.startsWith('v')) return 'verb'
  if (pos.startsWith('adj')) return 'adj'
  return 'other'
}

function initBoard() {
  categories.value = [
    { key: 'noun', label: '名词 n.', items: [] },
    { key: 'verb', label: '动词 v.', items: [] },
    { key: 'adj', label: '形容词 adj.', items: [] },
    { key: 'other', label: '其他', items: [] }
  ]
  pendingWords.value = words.value.map((w) => ({
    word: w.word,
    pos: w.pos || '',
    meaning: w.meaning || '',
    bookId: w.bookId || null,
    target: classify(w)
  }))
  score.value = 0
  correctCount.value = 0
  gameOver.value = false
  reward.value = null
}

function onDragStart(e, w) {
  dragWord.value = w
  e.dataTransfer.effectAllowed = 'move'
}

/* ---------- 移动端触摸拖拽（与 PC 端 HTML5 拖拽并存） ---------- */
let touchGhost = null
let scrollLocked = false
let savedScrollY = 0

/**
 * 拖拽期间彻底锁死页面滚动（双层保险）：
 * 1) document 级非被动 touchmove 守卫：显式 { passive: false } 添加监听，
 *    绕过部分手机内核（夸克/X5 等）对元素级监听的强制被动化——
 *    它们会无视元素上的 touchmove preventDefault 与 touch-action，但尊重显式非被动监听；
 * 2) body 定格 position:fixed + 负 top 补偿滚动位置：文档流脱离视口后物理上滚不动，
 *    兜底 iOS Safari（overflow:hidden 对其无效）与一切无视 preventDefault 的内核。
 */
function preventScrollGuard(e) {
  if (scrollLocked) e.preventDefault()
}

function lockPageScroll() {
  if (scrollLocked) return
  scrollLocked = true
  savedScrollY = window.scrollY
  document.documentElement.style.overflow = 'hidden'
  document.body.style.position = 'fixed'
  document.body.style.top = `${-savedScrollY}px`
  document.body.style.left = '0'
  document.body.style.right = '0'
  document.body.style.width = '100%'
  // 合成器级禁滚：touch-action 由浏览器输入管线处理（不走 JS 监听），
  // 即使内核强制被动化所有 touchmove，也不会再把触摸手势判定为视口平移
  document.documentElement.style.touchAction = 'none'
  document.body.style.touchAction = 'none'
  document.addEventListener('touchmove', preventScrollGuard, { passive: false })
}

function unlockPageScroll() {
  if (!scrollLocked) return
  scrollLocked = false
  document.removeEventListener('touchmove', preventScrollGuard)
  document.documentElement.style.overflow = ''
  document.documentElement.style.touchAction = ''
  document.body.style.position = ''
  document.body.style.top = ''
  document.body.style.left = ''
  document.body.style.right = ''
  document.body.style.width = ''
  document.body.style.touchAction = ''
  // 先还原 overflow 再恢复滚动位置，瞬间回到拖拽前视野
  window.scrollTo(0, savedScrollY)
}

function onTouchStart(e, w) {
  dragWord.value = w
  lockPageScroll()
  const t = e.touches[0]
  touchGhost = document.createElement('div')
  touchGhost.className = 'drag-word touch-ghost'
  touchGhost.textContent = w.word
  document.body.appendChild(touchGhost)
  moveGhost(t.clientX, t.clientY)
  updateTouchOver(t.clientX, t.clientY)
}

function onTouchMove(e) {
  if (!dragWord.value) return
  const t = e.touches[0]
  moveGhost(t.clientX, t.clientY)
  updateTouchOver(t.clientX, t.clientY)
}

function onTouchEnd() {
  const cat = categories.value.find((c) => c.key === dragOver.value)
  cleanupTouch()
  if (cat) onDrop(cat)
}

function onTouchCancel() {
  cleanupTouch()
}

function cleanupTouch() {
  unlockPageScroll()
  if (touchGhost) {
    touchGhost.remove()
    touchGhost = null
  }
  dragOver.value = null
}

function moveGhost(x, y) {
  if (!touchGhost) return
  const rect = touchGhost.getBoundingClientRect()
  touchGhost.style.left = `${x - rect.width / 2}px`
  touchGhost.style.top = `${y - rect.height - 14}px`
}

function updateTouchOver(x, y) {
  const el = document.elementFromPoint(x, y)
  const bucket = el && el.closest ? el.closest('.bucket') : null
  dragOver.value = bucket ? bucket.dataset.cat : null
}

function onDrop(cat) {
  dragOver.value = null
  const w = dragWord.value
  if (!w) return
  pendingWords.value = pendingWords.value.filter((x) => x.word !== w.word)
  const isCorrect = w.target === cat.key
  if (isCorrect) {
    score.value += 10
    correctCount.value++
    reportReview(w, 'know')
  } else {
    reportReview(w, 'forget')
  }
  cat.items.push({ word: w.word, wrong: !isCorrect })
  dragWord.value = null

  if (!pendingWords.value.length) {
    setTimeout(endGame, 600)
  }
}

async function endGame() {
  gameOver.value = true
  try {
    reward.value = await finish({ score: score.value, correctCount: correctCount.value, totalCount: words.value.length })
  } catch (e) {
    reward.value = null
  }
  progress.clear()
}

function restart() {
  progress.clear()
  load().then(() => !error.value && initBoard())
}

function back() {
  router.push('/game-park')
}

// 兜底：拖拽中途退出页面时解锁滚动
onBeforeUnmount(() => {
  unlockPageScroll()
})
</script>

<style lang="scss" scoped>
.game-status {
  display: flex;
  gap: 20px;
  align-items: center;
  margin-bottom: 12px;
  font-size: 14px;

  .primary {
    color: $color-primary;
  }

  .status-tip {
    margin-left: auto;
    font-size: 12px;
    color: $text-secondary;
  }
}

.word-pool {
  display: flex;
  flex-wrap: wrap;
  gap: $sp-3;
  min-height: 68px;
  padding: $sp-4;
  background: $bg-soft;
  border: 1px solid $border-base;
  border-radius: $radius-card;
  margin-bottom: $sp-4;
  align-items: center;
  justify-content: center;
}

.drag-word {
  padding: 8px 16px;
  background: $gray-1;
  color: $text-title;
  border: 1px solid $border-base;
  border-radius: $radius-sm;
  font-size: $fs-md;
  font-weight: 500;
  cursor: grab;
  user-select: none;
  /* 移动端手指按住单词拖拽时，禁止页面跟着滚动 */
  touch-action: none;
  -webkit-user-drag: element;
  box-shadow: $shadow-xs;
  transition: border-color $transition-fast, color $transition-fast, box-shadow $transition-fast, transform $transition-fast;

  &:hover {
    border-color: $color-primary;
    color: $color-primary;
    box-shadow: $shadow-hover;
    transform: translateY(-1px);
  }

  &:active {
    cursor: grabbing;
  }
}

.category-buckets {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $sp-3;
}

.bucket {
  min-height: 168px;
  background: $gray-1;
  border: 1px solid $border-base;
  border-radius: $radius-card;
  box-shadow: $shadow-sm;
  transition: border-color $transition-fast, background $transition-fast, box-shadow $transition-fast;

  .cat-dot {
    display: inline-block;
    width: 8px;
    height: 8px;
    border-radius: 2px;
    margin-right: 8px;
    vertical-align: middle;
  }

  &.noun .cat-dot { background: $color-violet; }
  &.verb .cat-dot { background: $color-mint; }
  &.adj .cat-dot { background: $color-sun; }
  &.other .cat-dot { background: $color-info; }

  &.over {
    border-color: $color-primary;
    border-style: dashed;
    background: $primary-1;
    box-shadow: $shadow-card;
  }

  .bucket-head {
    padding: 12px 14px;
    text-align: left;
    font-weight: 600;
    color: $text-title;
    border-bottom: 1px solid $border-light;
    font-size: $fs-md;
  }

  .bucket-body {
    padding: $sp-3;
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    align-content: flex-start;
    min-height: 116px;
  }

  .bucket-word {
    padding: 4px 10px;
    background: $color-success-soft;
    color: $color-success;
    border: 1px solid #D9EFD0;
    border-radius: $radius-sm;
    font-size: $fs-sm;
    font-weight: 500;

    &.wrong {
      background: $color-danger-soft;
      color: $color-danger;
      border-color: #FBC4C4;
    }
  }

  .bucket-empty {
    color: $text-disabled;
    font-size: $fs-sm;
    margin: auto;
  }
}

.game-over {
  text-align: center;
  padding: 30px 0;

  h3 {
    font-size: 20px;
  }

  p {
    margin-top: 6px;
    color: $text-regular;
  }

  .reward {
    color: $color-warning;
    font-weight: 600;
  }

  .over-actions {
    margin-top: 16px;
    display: flex;
    justify-content: center;
    gap: 10px;
  }
}

/* 移动端：4 列分类桶在手机上太挤，改 2×2，桶变小一点方便拖 */
@media (max-width: 768px) {
  .game-status {
    flex-wrap: wrap;
    gap: 10px;
    font-size: 12px;

    .status-tip {
      width: 100%;
      margin-left: 0;
    }
  }

  .category-buckets {
    grid-template-columns: repeat(2, 1fr);
    gap: $sp-2;
  }

  .bucket {
    min-height: 120px;

    .bucket-head {
      padding: 9px 12px;
      font-size: $fs-sm;
    }

    .bucket-body {
      min-height: 70px;
      padding: $sp-2;
    }

    .bucket-word {
      padding: 3px 8px;
      font-size: 12px;
    }
  }
}
</style>

<style lang="scss">
/* 触摸拖拽幽灵卡片：挂在 body 下，scoped 样式作用不到，这里用全局块 */
.touch-ghost {
  position: fixed;
  z-index: 3000;
  pointer-events: none;
  margin: 0;
  background: #fff;
  color: $color-primary;
  border-color: $color-primary;
  box-shadow: $shadow-hover;
}
</style>
