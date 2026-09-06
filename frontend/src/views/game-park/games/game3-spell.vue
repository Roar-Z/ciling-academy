<template>
  <div class="game-box">
    <div v-if="loading" class="state-box"><el-skeleton animated :rows="5" style="width: 100%" /></div>
    <div v-else-if="error" class="state-box">
      <div class="state-icon"><AppIcon name="circle-alert" :size="32" /></div>
      <p class="state-text">字母加载失败，请稍后再试</p>
      <el-button type="primary" style="margin-top: 12px" @click="restart">重试</el-button>
    </div>

    <template v-else>
      <div class="game-status">
        <span>得分：<b class="primary">{{ score }}</b></span>
        <span>进度：<b class="primary">{{ currentIndex + 1 }}</b> / {{ words.length }}</span>
        <span class="src-tag" :class="'src-' + (currentWord?.source || 'core')">
          {{ sourceLabel(currentWord?.source) }}
        </span>
      </div>

      <!-- 释义 + 音标 + 词性 -->
      <div class="spell-prompt">
        <p class="prompt-meaning">{{ currentMeaning }}</p>
        <p v-if="currentPhonetic" class="prompt-phonetic">{{ currentPhonetic }}</p>
        <p v-if="currentPos" class="prompt-pos">{{ currentPos }}</p>
      </div>

      <!-- 答案槽位：键盘敲入的字符填到这里，灰度表示已揭示的英文 -->
      <div class="slots" :class="{ 'is-hinted': hinted }">
        <div
          v-for="(ch, i) in displayChars"
          :key="i"
          class="slot"
          :class="{ filled: filled[i] !== '', hinted: hinted && filled[i] === '', shake: shakeSlot === i }"
        >{{ ch }}</div>
      </div>

      <!-- 提示工具栏 -->
      <div class="spell-toolbar">
        <el-button :disabled="hinted || submitting" @click="useHint">
          <AppIcon name="lightbulb" :size="14" /> 提示（-3 分）
        </el-button>
        <el-button :disabled="filledCount === 0 || submitting" @click="clearFilled">
          <AppIcon name="x" :size="14" /> 清除
        </el-button>
        <el-button :disabled="submitting" @click="onSkip">
          <AppIcon name="skip-forward" :size="14" /> 跳过（算不会）
        </el-button>
      </div>

      <!-- 字母池（辅助）：键盘敲也支持，点击也支持 -->
      <div class="letter-pool">
        <el-button
          v-for="(letter, i) in letters"
          :key="i"
          size="large"
          class="letter-btn"
          :disabled="used[i] || filled.length >= wordLen || submitting"
          @click="pickLetter(i)"
        >{{ letter }}</el-button>
      </div>

      <!-- 提交按钮 -->
      <div class="spell-actions">
        <el-button
          type="primary"
          size="large"
          :disabled="filledCount !== wordLen || submitting"
          :loading="submitting"
          @click="onSubmit"
        >提交拼写</el-button>
      </div>

      <!-- 反馈条 -->
      <transition name="fade">
        <div v-if="feedback" :class="['feedback', 'fb-' + feedback.type]">
          <AppIcon :name="feedback.icon" :size="18" />
          <span>{{ feedback.text }}</span>
        </div>
      </transition>

      <!-- 结算 -->
      <div class="game-over" v-if="gameOver">
        <h3><AppIcon name="puzzle" :size="20" /> 游戏结束</h3>
        <p>得分 {{ score }} · 拼对 {{ correctCount }}/{{ words.length }}</p>
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
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useGame } from '@/composables/useGame'
import { spellGameWords, spellReview, reportGameResult } from '@/api/wordBook'
import AppIcon from '@/components/common/AppIcon.vue'

const router = useRouter()
const { finish } = useGame('game3', '字母拼拼乐', 10)

const currentIndex = ref(0)
const score = ref(0)
const correctCount = ref(0)
const gameOver = ref(false)
const reward = ref(null)

const loading = ref(true)
const error = ref(false)
const words = ref([])

const letters = ref([])      // 打乱的字母池
const used = ref([])         // 每个字母是否已用
const filled = ref([])       // 已填入的字符（按顺序）
const hinted = ref(false)    // 是否用提示（灰度显示英文）
const submitting = ref(false)
const shakeSlot = ref(-1)    // 错答时抖动动画的下标
const feedback = ref(null)   // {type, icon, text}

let feedbackTimer = null

/** 进度持久化：sessionStorage 存当前局，退出再进来能续上 */
const STORAGE_KEY = 'game3_state'
function saveState() {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
      words: words.value,
      currentIndex: currentIndex.value,
      score: score.value,
      correctCount: correctCount.value,
      letters: letters.value,
      used: used.value,
      filled: filled.value,
      hinted: hinted.value
    }))
  } catch (e) { /* 忽略写入异常（隐私模式 / 容量满） */ }
}
function clearState() {
  try { sessionStorage.removeItem(STORAGE_KEY) } catch (e) {}
}
function restoreState() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return false
    const s = JSON.parse(raw)
    if (!s || !Array.isArray(s.words) || !s.words.length) return false
    words.value = s.words
    currentIndex.value = Number.isFinite(s.currentIndex) ? s.currentIndex : 0
    score.value = Number.isFinite(s.score) ? s.score : 0
    correctCount.value = Number.isFinite(s.correctCount) ? s.correctCount : 0
    letters.value = Array.isArray(s.letters) ? s.letters : []
    used.value = Array.isArray(s.used) ? s.used : []
    filled.value = Array.isArray(s.filled) ? s.filled : []
    hinted.value = !!s.hinted
    return true
  } catch (e) {
    return false
  }
}
// 任意进度变化都自动存档（deep 监听数组/对象内部变化）
watch([words, currentIndex, score, correctCount, letters, used, filled, hinted], saveState, { deep: true })

const currentWord = computed(() => words.value[currentIndex.value] || null)
const currentMeaning = computed(() => {
  const m = currentWord.value?.meaning || ''
  return m.replace(/^[nvadjprep]+\.\s*/, '')
})
const currentPhonetic = computed(() => currentWord.value?.phonetic || '')
const currentPos = computed(() => currentWord.value?.pos || '')
const wordLen = computed(() => (currentWord.value?.word || '').length)
// 已填入的字母数量（filled 是定长数组，'' 表示空位，需用"已填数量"判断）
const filledCount = computed(() => filled.value.filter((c) => c !== '').length)
const displayChars = computed(() => {
  const w = currentWord.value?.word || ''
  const word = w.split('')
  // 提示态：先铺完整答案作为底色，已填入的字母会覆盖对应位置（filled 优先）
  const arr = hinted.value ? word.slice() : new Array(word.length).fill('')
  filled.value.forEach((c, i) => { if (c) arr[i] = c })
  return arr
})

function sourceLabel(s) {
  if (s === 'due') return '艾宾浩斯复习'
  if (s === 'book') return '生词本'
  if (s === 'core') return '高频词'
  return ''
}

onMounted(async () => {
  // 用 document capture 阶段监听 keydown，确保按钮抢走焦点后键盘仍能响应
  document.addEventListener('keydown', onKeyDown, true)
  // 优先恢复上次进度；没有存档才重新拉题
  const restored = restoreState()
  if (!restored) {
    await load()
    if (!error.value) initRound()
  } else {
    loading.value = false
  }
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeyDown, true)
  if (feedbackTimer) clearTimeout(feedbackTimer)
})

async function load() {
  loading.value = true
  error.value = false
  try {
    const list = await spellGameWords(10, null, 'game3')
    if (!list || !list.length) { error.value = true; return }
    words.value = list
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
}

function initRound() {
  const word = currentWord.value.word
  const chars = word.toLowerCase().split('')
  // Fisher–Yates 洗牌
  for (let i = chars.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[chars[i], chars[j]] = [chars[j], chars[i]]
  }
  letters.value = chars
  used.value = new Array(chars.length).fill(false)
  filled.value = new Array(chars.length).fill('')
  hinted.value = false
  feedback.value = null
}

function pickLetter(i) {
  if (used.value[i]) return
  // 已填字母数达到词长则不能再填（filled 是定长数组，用 '' 占位，需用"已填数量"判断）
  if (filled.value.filter((c) => c !== '').length >= wordLen.value) return
  used.value[i] = true
  const slot = filled.value.findIndex((c) => c === '')
  if (slot >= 0) filled.value[slot] = letters.value[i]
}

function clearFilled() {
  used.value = used.value.map(() => false)
  filled.value = filled.value.map(() => '')
}

function useHint() {
  if (hinted.value) return
  hinted.value = true
  score.value = Math.max(0, score.value - 3)
  showFeedback('hint', 'lightbulb', `已揭示：${currentWord.value.word}`)
}

function onSkip() {
  if (submitting.value) return
  submitWith('forget')
}

async function onSubmit() {
  if (submitting.value) return
  if (filledCount.value !== wordLen.value) return
  const answer = filled.value.join('').toLowerCase()
  const target = currentWord.value.word.toLowerCase()
  if (answer === target) {
    score.value += 10
    await submitWith('know')
  } else {
    // 字符序列不对：判定 forget
    await submitWith('forget')
  }
}

/**
 * 提交并按艾宾浩斯更新复习计划
 * - know：拼对，走 EB_DAYS（1d/2d/4d/7d/15d...）
 * - vague：占位（当前逻辑直接命中 know / forget 两态；后续扩展）
 * - forget：5 分钟后再次出现
 */
async function submitWith(result) {
  submitting.value = true
  try {
    // 生词本词（bookId 不为空）调 spell-review 更新艾宾浩斯；所有词上报游戏结果做跨游戏去重
    if (currentWord.value?.bookId) {
      await spellReview(currentWord.value.bookId, result).catch(() => {})
    }
    if (currentWord.value?.word) {
      reportGameResult([currentWord.value.word], result, 'game3').catch(() => {})
    }
    // 反馈动画
    if (result === 'know') {
      correctCount.value++
      showFeedback('ok', 'check', '拼对了！')
    } else if (result === 'vague') {
      showFeedback('warn', 'alert-circle', '已记录 · 30 分钟后复习')
    } else {
      // 错误抖动
      shakeSlot.value = filled.value.findIndex(c => c !== '')
      setTimeout(() => (shakeSlot.value = -1), 400)
      showFeedback('err', 'x', `正确拼写：${currentWord.value.word} · 5 分钟后复习`)
    }
    await new Promise(r => setTimeout(r, 700))
    goNext()
  } finally {
    submitting.value = false
  }
}

function goNext() {
  if (currentIndex.value + 1 >= words.value.length) {
    endGame()
  } else {
    currentIndex.value++
    initRound()
  }
}

function showFeedback(type, icon, text) {
  feedback.value = { type, icon, text }
  if (feedbackTimer) clearTimeout(feedbackTimer)
  feedbackTimer = setTimeout(() => (feedback.value = null), 1200)
}

async function endGame() {
  gameOver.value = true
  try {
    reward.value = await finish({
      score: score.value,
      correctCount: correctCount.value,
      totalCount: words.value.length
    })
  } catch (e) {
    reward.value = null
  }
  // 通关结算完成后清存档，避免下次进来读旧数据
  clearState()
}

async function restart() {
  // "再来一局"清旧存档，重新拉题
  clearState()
  currentIndex.value = 0
  score.value = 0
  correctCount.value = 0
  gameOver.value = false
  reward.value = null
  await load()
  if (!error.value) initRound()
}

function back() {
  router.push('/game-park')
}

// 键盘敲入：a-z 直接填到第一个空位；Backspace 清掉最后一个；Enter 提交
// 用 document capture 阶段监听（onMounted 中第三个参数 true）—— 这样无论焦点在 el-button
// 还是 box 上，键盘事件都能被捕获，焦点丢失问题彻底解决。
function onKeyDown(e) {
  if (gameOver.value || submitting.value) return
  // 若焦点在原生输入框（搜索框等），不拦截，避免影响其他组件
  const t = e.target
  if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable)) return
  if (e.ctrlKey || e.metaKey || e.altKey) return

  console.log('[spell] keydown:', e.key) // TODO: 调试用，确认监听器触发后删除
  const key = e.key
  if (key === 'Enter') {
    e.preventDefault()
    onSubmit()
    return
  }
  if (key === 'Backspace') {
    e.preventDefault()
    // 从后往前找第一个已填的格子，对应释放字母池
    const lastIdx = (() => {
      for (let i = filled.value.length - 1; i >= 0; i--) if (filled.value[i]) return i
      return -1
    })()
    if (lastIdx < 0) return
    const ch = filled.value[lastIdx]
    filled.value[lastIdx] = ''
    // 释放字母池里对应位置（按字符匹配第一个未用的）
    for (let i = 0; i < letters.value.length; i++) {
      if (used.value[i] && letters.value[i] === ch) {
        used.value[i] = false
        break
      }
    }
    return
  }
  if (/^[a-zA-Z]$/.test(key)) {
    e.preventDefault()
    const lower = key.toLowerCase()
    const idx = letters.value.findIndex((c, i) => !used.value[i] && c === lower)
    if (idx >= 0) pickLetter(idx)
  }
}
</script>

<style lang="scss" scoped>
.game-status {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  font-size: 14px;

  .primary { color: $color-primary; }

  .src-tag {
    margin-left: auto;
    padding: 2px 10px;
    border-radius: 12px;
    font-size: 12px;
    font-weight: 500;

    &.src-due  { background: #FFF4E5; color: #D97706; }
    &.src-book { background: #EAF1FF; color: #2563EB; }
    &.src-core { background: #F0F9FF; color: #0EA5E9; }
  }
}

.spell-prompt {
  text-align: center;
  padding: 14px;
  background: #EAF1FF;
  border-radius: $radius-base;
  margin-bottom: 18px;

  .prompt-meaning {
    font-size: 20px;
    font-weight: 600;
    color: $text-primary;
    margin: 0;
  }
  .prompt-phonetic {
    margin-top: 6px;
    color: $color-primary;
    font-size: 14px;
  }
  .prompt-pos {
    margin-top: 4px;
    color: $text-secondary;
    font-size: 12px;
  }
}

.slots {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-bottom: 18px;
  flex-wrap: wrap;
  transition: filter $transition-normal;

  &.is-hinted { filter: saturate(0.4); }

  .slot {
    width: 38px;
    height: 46px;
    border: 2px dashed $border-color;
    border-radius: $radius-base;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
    font-weight: 700;
    color: $text-primary;
    background: $bg-card;
    transition: all $transition-fast;

    &.filled {
      border-style: solid;
      border-color: $color-primary;
      background: #EAF1FF;
    }
    &.hinted {
      border-style: solid;
      border-color: $text-secondary;
      background: $bg-page;
      color: $text-secondary;
      opacity: 0.65;
    }
    &.shake {
      animation: shake 0.4s;
      border-color: $color-danger;
      background: #FEE2E2;
    }
  }
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-4px); }
  40% { transform: translateX(4px); }
  60% { transform: translateX(-3px); }
  80% { transform: translateX(2px); }
}

.spell-toolbar {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-bottom: 14px;
  flex-wrap: wrap;

  .el-button {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
}

.letter-pool {
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;

  .letter-btn {
    font-size: 18px;
    font-weight: 700;
    min-width: 44px;
  }
}

.spell-actions {
  text-align: center;
  margin-bottom: 12px;
}

.feedback {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: $radius-base;
  margin: 0 auto;
  max-width: 480px;
  font-size: 14px;
  font-weight: 500;

  &.fb-ok   { background: #DCFCE7; color: #15803D; }
  &.fb-warn { background: #FEF3C7; color: #B45309; }
  &.fb-err  { background: #FEE2E2; color: #B91C1C; }
  &.fb-hint { background: #E0E7FF; color: #4338CA; }
}

.fade-enter-active, .fade-leave-active { transition: opacity $transition-fast; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

.game-over {
  text-align: center;
  padding: 30px 0;

  h3 { font-size: 20px; }
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
</style>