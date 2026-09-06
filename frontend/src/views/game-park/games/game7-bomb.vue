<template>
  <div class="game-box bomb-game">
    <div v-if="loading" class="state-box"><el-skeleton animated :rows="5" style="width: 100%" /></div>
    <div v-else-if="error" class="state-box">
      <div class="state-icon"><AppIcon name="circle-alert" :size="32" /></div>
      <p class="state-text">炸弹加载失败，请稍后再试</p>
      <el-button type="primary" style="margin-top: 12px" @click="restart">重试</el-button>
    </div>

    <template v-else>
      <!-- 顶部 HUD -->
      <div class="hud">
        <div class="hud-chip">得分 <b>{{ score }}</b></div>
        <div class="hud-chip time-chip" :class="{ danger: timeLeft <= 5 }">
          <AppIcon name="timer" :size="14" />
          剩余 <b>{{ timeLeft }}</b> 秒
          <transition name="chip">
            <span v-if="flashText" :key="flashKey" class="time-flash" :class="flashType">{{ flashText }}</span>
          </transition>
        </div>
        <div class="hud-dots">
          <span
            v-for="(w, i) in words"
            :key="i"
            class="dot"
            :class="{ done: i < currentIndex, cur: i === currentIndex }"
          ></span>
        </div>
        <span class="src-tag" :class="'src-' + (currentWordObj?.source || 'core')">{{ sourceLabel(currentWordObj?.source) }}</span>
      </div>

      <!-- 炸弹舞台：倒计时圆环 + 贴纸 -->
      <div class="bomb-stage" :class="{ danger: timeLeft <= 5 && !gameOver, boom: gameOver && bombExploded }">
        <div class="ring-wrap">
          <div class="ring" :style="{ '--pct': timePercent, '--ring': ringColor }"></div>
          <div class="ring-glow"></div>
          <img v-if="!(gameOver && bombExploded)" class="bomb-sprite" :src="bombIdle" alt="炸弹" />
          <img v-else class="boom-sprite" :src="boomImg" alt="爆炸" />
        </div>
        <p class="bomb-tip">答对 <em>+5s</em> 续命 · 答错 <em class="minus">-3s</em> · 时间归零即爆炸</p>
      </div>

      <!-- 题目 + 结算（结算覆盖题目区） -->
      <div class="quiz-zone">
        <div class="q-card">
          <p class="q-label">选出对应的单词</p>
          <p class="q-meaning">{{ currentMeaning }}</p>
          <div class="q-opts">
            <button
              v-for="(opt, i) in options"
              :key="opt"
              class="q-opt"
              :class="{
                right: picked && opt === currentWord,
                wrong: picked && picked.opt === opt && !picked.correct
              }"
              :disabled="!!picked || gameOver"
              @click="pick(opt)"
            >
              <span class="letter">{{ 'ABCD'[i] }}</span>{{ opt }}
            </button>
          </div>
        </div>

        <!-- 结算：覆盖在选项卡之上 -->
        <div class="game-over" v-if="gameOver" :class="{ win: !bombExploded }">
          <div class="over-card">
            <div class="over-sticker-wrap">
              <img v-if="bombExploded" class="over-sticker" :src="boomImg" alt="爆炸" />
              <div v-else class="win-badge">
                <AppIcon name="check" :size="44" color="#ffffff" />
              </div>
              <template v-if="!bombExploded">
                <span v-for="n in 8" :key="n" class="confetti" :class="'c' + n"></span>
              </template>
            </div>
            <h3 v-if="bombExploded">炸弹爆炸了！</h3>
            <h3 v-else>危机解除！</h3>
            <p class="over-score">得分 <b>{{ score }}</b> · 答对 <b>{{ correctCount }}</b> / {{ words.length }}</p>
            <p class="reward" v-if="reward && reward.coins > 0">获得金币 +{{ reward.coins }}</p>
            <div class="over-actions">
              <el-button type="primary" round @click="restart">再来一局</el-button>
              <el-button round @click="back">返回乐园</el-button>
            </div>
          </div>
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
import bombIdle from '@/assets/images/game/bomb-idle.png'
import boomImg from '@/assets/images/game/bomb-boom.png'

const router = useRouter()
const { words, loading, error, load, finish, reportReview } = useGame('game7', '单词炸弹危机', 10)

const currentIndex = ref(0)
const score = ref(0)
const correctCount = ref(0)
const gameOver = ref(false)
const bombExploded = ref(false)
const reward = ref(null)
const timeLeft = ref(15)
const MAX_TIME = 15
let timer = null
// 答题反馈锁：选中后短暂展示对错高亮再进入下一题
const picked = ref(null)
let lockTimer = null
// +5s / -3s 浮动提示
const flashText = ref('')
const flashType = ref('up')
let flashKey = 0
let flashTimer = null
// 进度持久化：退出再进来续上（计时态 timeLeft 一并恢复，倒计时重新启动）
const progress = useGameProgress('game7', { words, currentIndex, score, correctCount, gameOver, bombExploded, reward, timeLeft })

const timePercent = computed(() => Math.max(0, Math.min(100, (timeLeft.value / MAX_TIME) * 100)))
const ringColor = computed(() => {
  if (timeLeft.value <= 5) return '#ef4444'
  if (timeLeft.value <= 8) return '#f59e0b'
  return '#22c55e'
})

const currentWordObj = computed(() => words.value[currentIndex.value])
const currentWord = computed(() => currentWordObj.value?.word || '')
const currentMeaning = computed(() => {
  const m = currentWordObj.value?.meaning || ''
  return m.replace(/^[nvadjprep]\.\s*/, '')
})

const options = computed(() => {
  if (!currentWord.value) return []
  const correct = currentWord.value
  const distractors = words.value.map((w) => w.word).filter((w) => w !== correct)
  shuffle(distractors)
  const opts = [correct, ...distractors.slice(0, 3)]
  shuffle(opts)
  return opts
})

onMounted(async () => {
  await load() // 先拉题目兜底，再恢复存档（存档含 words 时以存档为准；已结算的存档会被丢弃直接开新局）
  progress.restore()
  if (!gameOver.value) startTimer()
})

onBeforeUnmount(() => {
  clearInterval(timer)
  clearTimeout(lockTimer)
  clearTimeout(flashTimer)
})

function startTimer() {
  clearInterval(timer)
  timer = setInterval(() => {
    timeLeft.value--
    if (timeLeft.value <= 0) {
      clearInterval(timer)
      bombExploded.value = true
      endGame()
    }
  }, 1000)
}

function shuffle(arr) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[arr[i], arr[j]] = [arr[j], arr[i]]
  }
}

function showFlash(text, type) {
  flashText.value = text
  flashType.value = type
  flashKey++
  clearTimeout(flashTimer)
  flashTimer = setTimeout(() => { flashText.value = '' }, 900)
}

function pick(opt) {
  if (gameOver.value || picked.value) return
  const correct = opt === currentWord.value
  picked.value = { opt, correct }
  if (correct) {
    score.value += 10
    correctCount.value++
    reportReview(words.value[currentIndex.value], 'know')
    timeLeft.value = Math.min(MAX_TIME, timeLeft.value + 5) // 答对续命
    showFlash('+5s', 'up')
  } else {
    reportReview(words.value[currentIndex.value], 'forget')
    timeLeft.value -= 3 // 答错扣时
    showFlash('-3s', 'down')
    if (timeLeft.value <= 0) {
      clearInterval(timer)
      bombExploded.value = true
      picked.value = null
      endGame()
      return
    }
  }
  // 展示 650ms 对错高亮后进入下一题
  clearTimeout(lockTimer)
  lockTimer = setTimeout(() => {
    picked.value = null
    next()
  }, 650)
}

function next() {
  if (gameOver.value) return
  if (currentIndex.value + 1 >= words.value.length) {
    clearInterval(timer)
    bombExploded.value = false
    endGame()
  } else {
    currentIndex.value++
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
  clearInterval(timer)
  clearTimeout(lockTimer)
  clearTimeout(flashTimer)
  picked.value = null
  flashText.value = ''
  currentIndex.value = 0
  score.value = 0
  correctCount.value = 0
  gameOver.value = false
  bombExploded.value = false
  reward.value = null
  timeLeft.value = MAX_TIME
  load().then(() => startTimer())
}

function back() {
  router.push('/game-park')
}

function sourceLabel(s) {
  if (s === 'due') return '艾宾浩斯复习'
  if (s === 'book') return '生词本'
  if (s === 'core') return '高频词'
  return ''
}
</script>

<style lang="scss" scoped>
$brown-ink: #3d3733;

.bomb-game {
  max-width: 720px;
  margin: 0 auto;
}

/* ---------- HUD ---------- */
.hud {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;

  .hud-chip {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 6px 14px;
    background: #fff;
    border: 1px solid #efe9e1;
    border-radius: 999px;
    font-size: 13px;
    color: #8a8177;
    box-shadow: 0 2px 6px rgba(120, 90, 60, 0.05);

    b {
      font-size: 15px;
      color: $brown-ink;
    }

    &.time-chip {
      position: relative;

      &.danger b {
        color: #ef4444;
      }
    }
  }

  .time-flash {
    position: absolute;
    right: 2px;
    top: -4px;
    font-size: 13px;
    font-weight: 800;
    pointer-events: none;

    &.up { color: #16a34a; }
    &.down { color: #ef4444; }
    animation: float-up 0.9s ease forwards;
  }

  .hud-dots {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-left: auto;

    .dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #e8e2d9;
      transition: all 0.25s ease;

      &.done { background: $color-primary; }
      &.cur {
        width: 18px;
        border-radius: 999px;
        background: $color-warning;
      }
    }
  }

  .src-tag {
    padding: 2px 10px;
    border-radius: 12px;
    font-size: 12px;
    font-weight: 500;

    &.src-due  { background: #FFF4E5; color: #D97706; }
    &.src-book { background: #EAF1FF; color: #2563EB; }
    &.src-core { background: #F0F9FF; color: #0EA5E9; }
  }
}

@keyframes float-up {
  0% { opacity: 0; transform: translateY(6px); }
  25% { opacity: 1; }
  100% { opacity: 0; transform: translateY(-16px); }
}

/* ---------- 炸弹舞台 ---------- */
.bomb-stage {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 22px 16px 14px;
  background: linear-gradient(180deg, #fffdf9 0%, #fdf6ec 100%);
  border: 1px solid #f1e9dd;
  border-radius: $radius-card;
  margin-bottom: 14px;
  overflow: hidden;
  transition: background 0.4s ease;

  &.danger {
    background: linear-gradient(180deg, #fff5f4 0%, #ffe9e5 100%);
    border-color: #f8d3cd;
  }

  &.boom .ring,
  &.boom .ring-glow {
    animation: fade-out 0.4s ease forwards;
  }
}

.ring-wrap {
  position: relative;
  width: 168px;
  height: 168px;
}

.ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: conic-gradient(var(--ring) calc(var(--pct) * 3.6deg), rgba(120, 90, 60, 0.08) 0);
  -webkit-mask: radial-gradient(closest-side, transparent 77%, #000 78%);
  mask: radial-gradient(closest-side, transparent 77%, #000 78%);
  transition: background 0.5s linear;
}

.ring-glow {
  position: absolute;
  inset: 14px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 170, 60, 0.18), transparent 70%);
  opacity: 0;
  transition: opacity 0.3s;
}

.bomb-stage.danger .ring-glow {
  opacity: 1;
  background: radial-gradient(circle, rgba(239, 68, 68, 0.28), transparent 70%);
  animation: pulse 0.8s ease-in-out infinite;
}

.bomb-sprite {
  position: absolute;
  inset: 26px;
  width: 116px;
  height: 116px;
  object-fit: contain;
  animation: bomb-bob 2.4s ease-in-out infinite;
  filter: drop-shadow(0 8px 10px rgba(80, 50, 20, 0.18));
  transition: filter 0.3s ease;
}

/* 危险态：同一张贴图打红色光晕，避免引入第二张贴纸 */
.bomb-stage.danger .bomb-sprite {
  filter: drop-shadow(0 0 14px rgba(239, 68, 68, 0.85)) drop-shadow(0 8px 10px rgba(80, 50, 20, 0.18)) hue-rotate(-24deg) saturate(1.6);
  animation: bomb-bob 0.5s ease-in-out infinite, bomb-tremble 0.16s linear infinite;
}

.boom-sprite {
  position: absolute;
  inset: 8px;
  width: 152px;
  height: 152px;
  object-fit: contain;
  animation: boom-in 0.6s cubic-bezier(0.22, 1.2, 0.36, 1) forwards;
}

@keyframes boom-in {
  0% { transform: scale(0.3); opacity: 0; }
  45% { transform: scale(1.25); opacity: 1; }
  100% { transform: scale(1); opacity: 1; }
}

@keyframes bomb-bob {
  0%, 100% { transform: translateY(0) rotate(-2deg); }
  50% { transform: translateY(-7px) rotate(2deg); }
}

@keyframes bomb-tremble {
  0%, 100% { margin-left: 0; }
  25% { margin-left: -2px; }
  75% { margin-left: 2px; }
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 0.7; }
  50% { transform: scale(1.12); opacity: 1; }
}

@keyframes fade-out {
  0% { opacity: 1; }
  100% { opacity: 0; }
}

.bomb-tip {
  margin-top: 12px;
  font-size: 12px;
  color: #a29888;

  em {
    font-style: normal;
    font-weight: 700;
    color: #16a34a;

    &.minus { color: #ef4444; }
  }
}

/* ---------- 题卡 ---------- */
.q-card {
  background: #fff;
  border: 1px solid #f1e9dd;
  border-radius: $radius-card;
  padding: 20px 18px 22px;
  text-align: center;
  box-shadow: 0 6px 18px rgba(120, 90, 60, 0.06);

  .q-label {
    font-size: 12px;
    color: #b3a897;
    letter-spacing: 2px;
    margin-bottom: 6px;
  }

  .q-meaning {
    font-size: 22px;
    font-weight: 700;
    color: $brown-ink;
    margin-bottom: 18px;
    line-height: 1.4;
  }
}

.q-opts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  max-width: 540px;
  margin: 0 auto;
}

.q-opt {
  position: relative;
  padding: 14px 12px 14px 46px;
  background: #fff;
  border: 2px solid #ece5da;
  border-radius: 14px;
  font-size: 16px;
  font-weight: 700;
  color: $brown-ink;
  text-align: left;
  cursor: pointer;
  box-shadow: 0 3px 0 #e5ddcf;
  transition: transform 0.14s ease, box-shadow 0.14s ease, border-color 0.14s ease, background 0.14s ease;
  font-family: inherit;

  .letter {
    position: absolute;
    left: 12px;
    top: 50%;
    transform: translateY(-50%);
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: #f5f0e8;
    color: #9a8f7f;
    font-size: 12px;
    font-weight: 800;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.14s ease;
  }

  &:hover:not(:disabled) {
    transform: translateY(-2px);
    border-color: $color-primary;
    box-shadow: 0 5px 0 #d8cfc0;

    .letter { background: $color-primary; color: #fff; }
  }

  &:active:not(:disabled) {
    transform: translateY(1px);
    box-shadow: 0 1px 0 #e5ddcf;
  }

  &:disabled { cursor: default; }

  &.right {
    border-color: #34c06b;
    background: #eefaf1;
    box-shadow: 0 3px 0 #b9e3c8;
    animation: opt-pop 0.4s ease;

    .letter { background: #22a55a; color: #fff; }
  }

  &.wrong {
    border-color: #f0655a;
    background: #fdefee;
    box-shadow: 0 3px 0 #f3c4bf;
    animation: opt-shake 0.4s ease;

    .letter { background: #e5484d; color: #fff; }
  }
}

@keyframes opt-pop {
  0% { transform: scale(1); }
  40% { transform: scale(1.05); }
  100% { transform: scale(1); }
}

@keyframes opt-shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-5px); }
  50% { transform: translateX(5px); }
  75% { transform: translateX(-3px); }
}

/* 答对/答错时整卡闪一下边框色 */
.q-card:has(.q-opt.right) { border-color: #b9e3c8; }
.q-card:has(.q-opt.wrong) { border-color: #f3c4bf; }

/* ---------- 题目区 + 结算覆盖 ---------- */
/* grid 同格堆叠：结算层与题卡完全同宽同高，实现全覆盖 */
.quiz-zone {
  display: grid;

  .q-card,
  .game-over {
    grid-area: 1 / 1;
  }
}

.game-over {
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 16px;
  background: #fff;
  border: 1px solid #f1e9dd;
  border-radius: $radius-card;
  box-shadow: 0 6px 18px rgba(120, 90, 60, 0.06);
  animation: card-in 0.4s cubic-bezier(0.34, 1.4, 0.64, 1);

  .over-card {
    position: relative;
    text-align: center;
    overflow: hidden;
  }

  .over-sticker-wrap {
    position: relative;
    display: inline-block;
  }

  .over-sticker {
    width: 96px;
    height: 96px;
    object-fit: contain;
    animation: sticker-in 0.5s cubic-bezier(0.34, 1.56, 0.64, 1);
    filter: drop-shadow(0 6px 10px rgba(80, 50, 20, 0.18));
  }

  .win-badge {
    width: 96px;
    height: 96px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(145deg, #4ade80, #16a34a);
    box-shadow: 0 8px 18px rgba(22, 163, 74, 0.35), inset 0 -4px 0 rgba(0, 0, 0, 0.12);
    animation: sticker-in 0.5s cubic-bezier(0.34, 1.56, 0.64, 1);
  }

  h3 {
    font-size: 21px;
    color: $brown-ink;
    margin-top: 8px;
  }

  .over-score {
    margin-top: 6px;
    color: #8a8177;
    font-size: 14px;

    b { color: $brown-ink; }
  }

  .reward {
    margin-top: 4px;
    color: $color-warning;
    font-weight: 700;
  }

  .over-actions {
    margin-top: 18px;
    display: flex;
    justify-content: center;
    gap: 10px;
  }
}

@keyframes card-in {
  0% { opacity: 0; transform: translateY(16px) scale(0.96); }
  100% { opacity: 1; transform: translateY(0) scale(1); }
}

@keyframes sticker-in {
  0% { transform: scale(0.2) rotate(-12deg); opacity: 0; }
  100% { transform: scale(1) rotate(0); opacity: 1; }
}

/* 拆弹成功的彩带 */
.confetti {
  position: absolute;
  top: 8px;
  width: 8px;
  height: 12px;
  border-radius: 3px;
  opacity: 0;
  animation: confetti-fall 1.6s ease-in infinite;
}

$cf-colors: #f59e0b, #ef4444, #22c55e, #3b82f6, #eab308, #f97316, #14b8a6, #e11d48;

@for $n from 1 through 8 {
  .c#{$n} {
    left: #{5 + $n * 11%};
    background: nth($cf-colors, $n);
    animation-delay: #{$n * 0.12}s;
  }
}

@keyframes confetti-fall {
  0% { opacity: 1; transform: translateY(-10px) rotate(0deg); }
  100% { opacity: 0; transform: translateY(120px) rotate(320deg); }
}

/* 过渡：+5s/-3s chip */
.chip-leave-active { transition: opacity 0.2s; }
.chip-leave-to { opacity: 0; }

/* 弱动效模式：关闭所有装饰动画 */
@media (prefers-reduced-motion: reduce) {
  .bomb-sprite,
  .boom-sprite,
  .ring-glow,
  .time-flash,
  .q-opt.right,
  .q-opt.wrong,
  .over-card,
  .over-sticker,
  .win-badge,
  .confetti {
    animation: none !important;
  }
  .time-flash { opacity: 1; }
}
</style>
