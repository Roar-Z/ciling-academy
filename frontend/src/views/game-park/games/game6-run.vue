<template>
  <div class="game-box run-game">
    <div v-if="loading" class="state-box"><el-skeleton animated :rows="5" style="width: 100%" /></div>
    <div v-else-if="error" class="state-box">
      <div class="state-icon"><AppIcon name="circle-alert" :size="32" /></div>
      <p class="state-text">跑道加载失败，请稍后再试</p>
      <el-button type="primary" style="margin-top: 12px" @click="restart">重试</el-button>
    </div>

    <template v-else>
      <div class="game-status">
        <span class="status-chip score-chip">🏅 得分 <b>{{ score }}</b></span>
        <span class="status-chip">👟 进度 <b>{{ correctCount }}</b> / {{ words.length }}</span>
        <span class="status-chip time-chip" :class="{ urgent: remaining <= 20 && !gameOver }">⏱ <b>{{ timeText }}</b></span>
        <span class="src-tag" :class="'src-' + (currentWord?.source || 'core')">{{ sourceLabel(currentWord?.source) }}</span>
      </div>

      <!-- 卡通跑道场景（Kenney CC0 贴图） -->
      <div class="track" :class="{ shaking: feedback === 'wrong', sprinting: feedback === 'correct' }">
        <!-- 天空层 -->
        <div class="sky">
          <div class="sun"></div>
          <div class="parallax clouds"></div>
          <div class="parallax hills"></div>
        </div>

        <!-- 草地跑道 -->
        <div class="grass">
          <!-- 终点旗 -->
          <div class="finish-pole">
            <img class="flag-img" :src="flagSprite" alt="终点" />
            <span class="finish-word">终点</span>
          </div>

          <!-- 沿途金币 -->
          <img v-for="c in 3" :key="c" class="track-coin" :style="{ left: 18 + c * 17 + '%' }" :src="coinSprite" alt="" />

          <!-- 答题金币弹出 -->
          <img v-if="showCoin" class="coin-pop" :style="{ left: runnerLeft + '%' }" :src="coinSprite" alt="" />

          <!-- 跑步小人 -->
          <div class="runner-wrap" :style="{ left: runnerLeft + '%' }">
            <span v-if="feedback === 'correct'" class="score-pop">+10</span>
            <div class="runner" :class="{ hurt: feedback === 'wrong' }"></div>
            <div class="dust d1"></div>
            <div class="dust d2"></div>
            <div class="dust d3"></div>
          </div>
        </div>
      </div>

      <!-- 题目 -->
      <div class="run-question">
        <p class="q-tip">答对一步冲过终点！这个单词是什么？</p>
        <p class="q-meaning">{{ currentMeaning }}</p>
        <div class="run-opts">
          <button
            v-for="(opt, i) in options"
            :key="i"
            class="run-opt"
            :class="{
              picked: answered && opt === lastPick,
              right: answered && opt === currentWord,
              wrong: answered && opt === lastPick && opt !== currentWord,
            }"
            :disabled="answered"
            @click="pick(opt)"
          >{{ opt }}</button>
        </div>
      </div>

      <!-- 结算 -->
      <div class="game-over" v-if="gameOver">
        <div class="confetti">
          <i v-for="n in 14" :key="n" :class="'cf cf' + n"></i>
        </div>
        <img class="trophy" :src="starSprite" alt="冠军" />
        <h3>{{ timeUp ? '时间到！' : '到达终点！' }}</h3>
        <p>{{ timeUp ? '没跑完，下次手速再快一点～' : '答对一步冲过终点！' }}</p>
        <p>得分 {{ score }} · 答对 {{ correctCount }}/{{ words.length }}</p>
        <p class="reward" v-if="reward && reward.coins > 0">🪙 金币 +{{ reward.coins }}</p>
        <div class="over-actions">
          <el-button type="primary" @click="restart">再来一局</el-button>
          <el-button @click="back">返回乐园</el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useGame } from '@/composables/useGame'
import { useGameProgress } from '@/composables/useGameProgress'
import AppIcon from '@/components/common/AppIcon.vue'
import flagSprite from '@/assets/images/game/run-flag.png'
import coinSprite from '@/assets/images/game/run-coin.png'
import starSprite from '@/assets/images/game/run-star.png'

const router = useRouter()
const { words, loading, error, load, finish, reportReview } = useGame('game6', '单词快跑', 10)

const currentIndex = ref(0)
const score = ref(0)
const correctCount = ref(0)
const gameOver = ref(false)
const reward = ref(null)
const answered = ref(false)
const lastPick = ref('')
const feedback = ref('') // 'correct' | 'wrong' | ''
const showCoin = ref(false)
// 限时规则：单局 2 分钟内必须冲线，超时直接结算
const TIME_LIMIT = 120
const remaining = ref(TIME_LIMIT)
const timeUp = ref(false)
let feedbackTimer = null
let coinTimer = null
let tickTimer = null
// 进度持久化：退出再进来续上（选项锁定态 answered/lastPick 不持久化）
const progress = useGameProgress('game6', { words, currentIndex, score, correctCount, gameOver, reward, remaining, timeUp })

const timeText = computed(() => {
  const s = Math.max(0, remaining.value)
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
})

const runnerLeft = computed(() => (correctCount.value / words.value.length) * 78 + 4)

const currentWord = computed(() => words.value[currentIndex.value]?.word || '')
const currentMeaning = computed(() => {
  const m = words.value[currentIndex.value]?.meaning || ''
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
  await load() // 先拉题目兜底，再恢复存档（存档含 words 时以存档为准）
  progress.restore()
  if (!gameOver.value) startTimer()
})
onUnmounted(() => {
  clearTimeout(feedbackTimer)
  clearInterval(tickTimer)
})

/** 启动倒计时（幂等：先清旧定时器） */
function startTimer() {
  clearInterval(tickTimer)
  if (remaining.value <= 0) {
    timeUp.value = true
    endGame()
    return
  }
  tickTimer = setInterval(() => {
    if (gameOver.value) {
      clearInterval(tickTimer)
      return
    }
    remaining.value--
    if (remaining.value <= 0) {
      clearInterval(tickTimer)
      timeUp.value = true
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

function pick(opt) {
  if (answered.value || gameOver.value) return
  lastPick.value = opt
  answered.value = true
  const correct = opt === currentWord.value
  feedback.value = correct ? 'correct' : 'wrong'
  if (correct) {
    score.value += 10
    correctCount.value++
    reportReview(words.value[currentIndex.value], 'know')
    showCoin.value = false
    void document.body.offsetWidth // 重置动画
    showCoin.value = true
    clearTimeout(coinTimer)
    coinTimer = setTimeout(() => (showCoin.value = false), 700)
  } else {
    reportReview(words.value[currentIndex.value], 'forget')
  }
  clearTimeout(feedbackTimer)
  feedbackTimer = setTimeout(() => {
    feedback.value = ''
    answered.value = false
    if (correctCount.value >= words.value.length) {
      endGame()
    } else if (currentIndex.value + 1 >= words.value.length && correct) {
      endGame()
    } else {
      currentIndex.value = (currentIndex.value + 1) % words.value.length
    }
  }, correct ? 700 : 1100)
}

async function endGame() {
  if (gameOver.value) return
  gameOver.value = true
  clearInterval(tickTimer)
  try {
    reward.value = await finish({ score: score.value, correctCount: correctCount.value, totalCount: words.value.length })
  } catch (e) {
    reward.value = null
  }
  progress.clear()
}

function restart() {
  progress.clear()
  currentIndex.value = 0
  score.value = 0
  correctCount.value = 0
  gameOver.value = false
  reward.value = null
  feedback.value = ''
  answered.value = false
  remaining.value = TIME_LIMIT
  timeUp.value = false
  load()
  startTimer()
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
.run-game {
  position: relative;
}

/* ---------- 顶部状态 ---------- */
.game-status {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  font-size: 14px;

  .status-chip {
    padding: 4px 14px;
    border-radius: 16px;
    background: #f4f7f6;
    color: $text-regular;

    b {
      color: $color-primary;
      font-size: 16px;
    }
  }
  .score-chip b {
    color: $color-warning;
  }

  /* 倒计时：最后 20 秒变红并轻微跳动提示 */
  .time-chip {
    font-variant-numeric: tabular-nums;

    b {
      color: $text-regular;
    }
    &.urgent {
      background: #fdecec;

      b {
        color: $color-danger;
        animation: tick-hurry 1s ease-in-out infinite;
      }
    }
  }

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

/* ---------- 卡通跑道场景 ---------- */
.track {
  position: relative;
  height: 150px;
  border-radius: $radius-card;
  margin-bottom: 20px;
  overflow: hidden;
  box-shadow: inset 0 -3px 0 rgba(0, 0, 0, 0.05);

  /* 天空 */
  .sky {
    position: absolute;
    inset: 0 0 52px 0;
    background: linear-gradient(180deg, #bfe3ff 0%, #e6f6ff 100%);
    overflow: hidden;
  }
  .sun {
    position: absolute;
    top: 12px;
    right: 34px;
    width: 34px;
    height: 34px;
    border-radius: 50%;
    background: radial-gradient(circle, #ffe9a8 40%, #ffd666 100%);
    box-shadow: 0 0 22px rgba(255, 214, 102, 0.8);
    animation: sun-pulse 3s ease-in-out infinite;
    z-index: 1;
  }
  /* 远景视差：云 + 山丘贴图横向平铺滚动 */
  .parallax {
    position: absolute;
    left: 0;
    right: 0;
    background-repeat: repeat-x;
    background-position: bottom left;
  }
  .clouds {
    top: 0;
    height: 100%;
    background-size: auto 74%;
    background-image: url('@/assets/images/game/run-clouds.png');
    animation: pan-x 26s linear infinite;
  }
  .hills {
    bottom: 0;
    height: 62%;
    background-size: auto 100%;
    background-image: url('@/assets/images/game/run-hills.png');
    animation: pan-x 14s linear infinite;
  }

  /* 草地跑道：Kenney 草皮贴图滚动 */
  .grass {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 58px;
    background-color: #5cb85c;
    background-image: url('@/assets/images/game/run-ground.png');
    background-repeat: repeat-x;
    background-position: left bottom;
    background-size: auto 100%;
    animation: pan-x 1.6s linear infinite;
  }

  /* 终点旗 */
  .finish-pole {
    position: absolute;
    right: 16px;
    bottom: 46px;
    width: 52px;
    text-align: center;
    z-index: 2;

    .flag-img {
      width: 52px;
      height: 52px;
      image-rendering: pixelated;
      transform-origin: bottom left;
      animation: flag-wave 1.1s ease-in-out infinite;
    }
    .finish-word {
      display: block;
      margin-top: -2px;
      font-size: 11px;
      color: #fff;
      background: rgba(0, 0, 0, 0.35);
      padding: 1px 6px;
      border-radius: 8px;
      white-space: nowrap;
    }
  }

  /* 沿途金币：轻微浮动 */
  .track-coin {
    position: absolute;
    bottom: 30px;
    width: 20px;
    height: 20px;
    image-rendering: pixelated;
    animation: coin-float 1.4s ease-in-out infinite;
    z-index: 1;
  }
  /* 答对时从头顶弹起的金币 */
  .coin-pop {
    position: absolute;
    bottom: 70px;
    width: 26px;
    height: 26px;
    image-rendering: pixelated;
    margin-left: -13px;
    animation: coin-jump 0.7s ease-out forwards;
    z-index: 3;
  }

  /* 跑步小人 */
  .runner-wrap {
    position: absolute;
    bottom: 24px;
    transition: left 0.55s cubic-bezier(0.22, 1, 0.36, 1);
    z-index: 2;
  }
  .runner {
    width: 56px;
    height: 56px;
    image-rendering: pixelated;
    background: url('@/assets/images/game/run-walk-a.png') center / contain no-repeat;
    filter: drop-shadow(0 3px 2px rgba(0, 0, 0, 0.18));
    animation: walk-cycle 0.32s steps(1) infinite;
    transform-origin: center bottom;

    &.hurt {
      animation: none;
      background-image: url('@/assets/images/game/run-hit.png');
      filter: grayscale(0.4) drop-shadow(0 3px 2px rgba(0, 0, 0, 0.18));
    }
  }
  /* 冲刺：答对时播放跳跃贴图 */
  &.sprinting .runner {
    background-image: url('@/assets/images/game/run-jump.png');
    animation: jump-pop 0.5s ease;
  }

  /* 脚下扬尘 */
  .dust {
    position: absolute;
    bottom: -2px;
    left: -6px;
    width: 9px;
    height: 9px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.75);
    opacity: 0;
    animation: puff 0.7s ease-out infinite;
  }
  .d2 { animation-delay: 0.22s; left: -12px; width: 7px; height: 7px; }
  .d3 { animation-delay: 0.45s; left: -2px; width: 6px; height: 6px; }

  /* 答对飘分 */
  .score-pop {
    position: absolute;
    top: -26px;
    left: 50%;
    transform: translateX(-50%);
    font-size: 16px;
    font-weight: 800;
    color: #fff;
    background: $color-success;
    padding: 2px 10px;
    border-radius: 12px;
    animation: pop-up 0.7s ease-out forwards;
    white-space: nowrap;
  }

  /* 答错：跑道抖动 */
  &.shaking {
    animation: shake 0.4s ease;
  }
}

/* ---------- 题目区 ---------- */
.run-question {
  text-align: center;

  .q-tip {
    font-size: 12px;
    color: $text-caption;
    margin-bottom: 6px;
  }
  .q-meaning {
    font-size: 21px;
    font-weight: 700;
    color: $text-primary;
    margin-bottom: 18px;
  }

  .run-opts {
    display: flex;
    justify-content: center;
    gap: 14px;
    flex-wrap: wrap;
  }
}

/* 游戏风立体按钮：按下有回弹 */
.run-opt {
  min-width: 132px;
  padding: 12px 18px;
  font-size: 16px;
  font-weight: 700;
  color: #374151;
  background: #fff;
  border: 2px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 4px 0 #d7dde3;
  cursor: pointer;
  transition: all 0.15s ease;
  font-family: inherit;

  &:hover:not(:disabled) {
    transform: translateY(-2px);
    border-color: $primary-3;
    box-shadow: 0 6px 0 #d7dde3;
  }
  &:active:not(:disabled) {
    transform: translateY(3px);
    box-shadow: 0 1px 0 #d7dde3;
  }
  &:disabled {
    cursor: default;
  }

  &.right {
    background: #eafaf1;
    border-color: $color-success;
    color: #15803d;
    box-shadow: 0 4px 0 rgba(34, 154, 89, 0.35);
    animation: bounce-in 0.4s ease;
  }
  &.wrong {
    background: #fdecec;
    border-color: $color-danger;
    color: #b91c1c;
    box-shadow: 0 4px 0 rgba(200, 60, 60, 0.3);
    animation: shake 0.4s ease;
  }
}

/* ---------- 结算 ---------- */
.game-over {
  position: relative;
  text-align: center;
  padding: 34px 0 26px;
  overflow: hidden;

  .trophy {
    font-size: 56px;
    line-height: 1;
    animation: bounce-in 0.6s cubic-bezier(0.34, 1.56, 0.64, 1);
  }
  h3 {
    font-size: 22px;
    margin-top: 8px;
    color: $text-primary;
  }
  p {
    margin-top: 6px;
    color: $text-regular;
  }
  .reward {
    color: $color-warning;
    font-weight: 700;
    font-size: 16px;
    animation: bounce-in 0.5s ease 0.3s backwards;
  }
  .over-actions {
    margin-top: 18px;
    display: flex;
    justify-content: center;
    gap: 10px;
  }
}

/* 彩带粒子 */
.confetti {
  position: absolute;
  inset: 0;
  pointer-events: none;

  .cf {
    position: absolute;
    top: -12px;
    width: 8px;
    height: 12px;
    border-radius: 2px;
    opacity: 0;
    animation: fall 2.4s linear infinite;
  }
  $cf-colors: #f97316, #22c55e, #3b82f6, #eab308, #ec4899, #14b8a6;
  @for $n from 1 through 14 {
    .cf#{$n} {
      left: random(96) * 1%;
      background: nth($cf-colors, ($n % 6) + 1);
      animation-delay: random(200) / 100 * 1s;
      transform: rotate(random(360) * 1deg);
    }
  }
}

/* ---------- 动画关键帧 ---------- */
@keyframes pan-x {
  from { background-position-x: 0; }
  to { background-position-x: -256px; }
}
@keyframes sun-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.12); }
}
@keyframes walk-cycle {
  0%, 100% { background-image: url('@/assets/images/game/run-walk-a.png'); }
  50% { background-image: url('@/assets/images/game/run-walk-b.png'); }
}
@keyframes jump-pop {
  0% { transform: translateY(0); }
  40% { transform: translateY(-14px); }
  100% { transform: translateY(0); }
}
@keyframes flag-wave {
  0%, 100% { transform: skewY(0deg) scaleX(1); }
  50% { transform: skewY(-6deg) scaleX(0.94); }
}
@keyframes coin-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-5px); }
}
@keyframes coin-jump {
  0% { opacity: 0; transform: translateY(10px) scale(0.5); }
  35% { opacity: 1; transform: translateY(-18px) scale(1.15); }
  100% { opacity: 0; transform: translateY(-38px) scale(1); }
}
@keyframes puff {
  0% { opacity: 0.9; transform: translate(0, 0) scale(0.5); }
  100% { opacity: 0; transform: translate(-16px, -8px) scale(1.3); }
}
@keyframes pop-up {
  0% { opacity: 0; transform: translate(-50%, 6px) scale(0.6); }
  30% { opacity: 1; transform: translate(-50%, -6px) scale(1.1); }
  100% { opacity: 0; transform: translate(-50%, -26px) scale(1); }
}
@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-6px); }
  50% { transform: translateX(6px); }
  75% { transform: translateX(-4px); }
}
@keyframes bounce-in {
  0% { opacity: 0; transform: scale(0.4); }
  60% { transform: scale(1.15); }
  100% { opacity: 1; transform: scale(1); }
}
@keyframes tick-hurry {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.18); }
}
@keyframes fall {
  0% { opacity: 1; transform: translateY(0) rotate(0deg); }
  100% { opacity: 0; transform: translateY(150px) rotate(540deg); }
}

/* 尊重系统「减弱动态效果」偏好 */
@media (prefers-reduced-motion: reduce) {
  .track *,
  .run-opt,
  .game-over * {
    animation: none !important;
    transition: none !important;
  }
}
</style>
