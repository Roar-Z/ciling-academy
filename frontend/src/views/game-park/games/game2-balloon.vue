<template>
  <div class="game-box balloon-game">
    <div v-if="loading" class="state-box"><el-skeleton animated :rows="5" style="width: 100%" /></div>
    <div v-else-if="error" class="state-box">
      <div class="state-icon"><AppIcon name="circle-alert" :size="32" /></div>
      <p class="state-text">气球没气啦，单词加载失败</p>
      <el-button type="primary" style="margin-top: 12px" @click="restart">重试</el-button>
    </div>

    <template v-else>
      <!-- 顶部 HUD -->
      <div class="hud">
        <div class="hud-chip">得分 <b>{{ score }}</b></div>
        <div class="hud-chip lives-chip" :class="{ danger: lives <= 1 }">
          <span v-for="n in 3" :key="n" class="heart" :class="{ lost: n > lives }">
            <AppIcon name="star" :size="14" />
          </span>
        </div>
        <div class="hud-dots">
          <span
            v-for="(w, i) in words"
            :key="i"
            class="dot"
            :class="{ done: i < currentIndex, cur: i === currentIndex }"
          ></span>
        </div>
        <span class="src-tag" :class="'src-' + (currentWord?.source || 'core')">{{ sourceLabel(currentWord?.source) }}</span>
      </div>

      <!-- 提示条 -->
      <div class="prompt-bar">
        戳破释义为「<b>{{ currentPrompt }}</b>」的气球
      </div>

      <!-- 天空舞台 + 结算（grid 同格堆叠，结算完全覆盖气球区） -->
      <div class="sky-zone">
        <div class="balloon-sky">
          <div class="cloud c1"></div>
          <div class="cloud c2"></div>
          <div class="cloud c3"></div>
          <div
            v-for="(opt, i) in currentOptions"
            :key="currentIndex + '-' + i"
            class="balloon"
            :class="{ flying: flyingIndex === i, popped: poppedIndex === i }"
            :style="{ '--sway': 3 + (i % 3) + 's', '--delay': i * 0.35 + 's' }"
            @click="hit(opt, i)"
          >
            <div class="b-stage">
              <img class="b-img" :src="balloonSrc(i)" alt="" draggable="false" />
              <span class="b-word">{{ opt }}</span>
              <img v-if="poppedIndex === i" class="b-pop" :src="balloonPop" alt="" />
            </div>
            <div class="b-string"></div>
          </div>
        </div>

        <!-- 结算：覆盖气球区 -->
        <div class="game-over" v-if="gameOver" :class="{ win: lives > 0 }">
          <div class="over-card">
            <div class="over-badge" :class="lives > 0 ? 'ok' : 'fail'">
              <AppIcon :name="lives > 0 ? 'check' : 'x'" :size="40" color="#ffffff" />
            </div>
            <h3>{{ lives > 0 ? '全部戳破！' : '气球飞走了…' }}</h3>
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
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useGame } from '@/composables/useGame'
import { useGameProgress } from '@/composables/useGameProgress'
import AppIcon from '@/components/common/AppIcon.vue'
import balloonPink from '@/assets/images/game/balloon-pink.png'
import balloonBlue from '@/assets/images/game/balloon-blue.png'
import balloonYellow from '@/assets/images/game/balloon-yellow.png'
import balloonGreen from '@/assets/images/game/balloon-green.png'
import balloonPop from '@/assets/images/game/balloon-pop.png'

const router = useRouter()
const { words, loading, error, load, finish, reportReview } = useGame('game2', '气球打单词', 10)

const currentIndex = ref(0)
const score = ref(0)
const correctCount = ref(0)
const lives = ref(3)
const gameOver = ref(false)
const reward = ref(null)
const hitResult = ref(null)
const flyingIndex = ref(-1) // 答对：气球飘走
const poppedIndex = ref(-1) // 答错：气球爆炸

// 进度持久化：退出再进来续上（动画态不参与持久化）
const progress = useGameProgress('game2', { words, currentIndex, score, correctCount, lives, gameOver, reward })

/* ---------- 本地气球贴纸 ---------- */
const BALLOON_IMGS = [balloonPink, balloonBlue, balloonYellow, balloonGreen]
function balloonSrc(i) {
  return BALLOON_IMGS[i % BALLOON_IMGS.length]
}

const currentWord = computed(() => words.value[currentIndex.value])

/** 释义提示（去掉词性前缀） */
const currentPrompt = computed(() => {
  const m = currentWord.value?.meaning || ''
  return m.replace(/^[nvadjprep]\.\s*/, '').split('；')[0].split(',')[0]
})

const currentOptions = computed(() => {
  if (!currentWord.value) return []
  const correct = currentWord.value.word
  const distractors = words.value
    .map((w) => w.word)
    .filter((w) => w !== correct)
  shuffle(distractors)
  const opts = [correct, ...distractors.slice(0, 3)]
  shuffle(opts)
  return opts
})

onMounted(async () => {
  await load() // 先拉题目兜底，再恢复存档（已结算的存档会被丢弃直接开新局）
  progress.restore()
})

function shuffle(arr) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[arr[i], arr[j]] = [arr[j], arr[i]]
  }
}

function hit(word, index) {
  if (gameOver.value || hitResult.value !== null || flyingIndex.value !== -1 || poppedIndex.value !== -1) return
  if (word === currentWord.value.word) {
    score.value += 10
    correctCount.value++
    reportReview(currentWord.value, 'know')
    hitResult.value = true
    flyingIndex.value = index
    setTimeout(() => {
      flyingIndex.value = -1
      next()
    }, 900)
  } else {
    lives.value--
    reportReview(currentWord.value, 'forget')
    hitResult.value = false
    poppedIndex.value = index
    const done = lives.value <= 0
    setTimeout(() => {
      poppedIndex.value = -1
      if (done) endGame()
      else next()
    }, 550)
  }
}

function next() {
  hitResult.value = null
  if (currentIndex.value + 1 >= words.value.length) {
    endGame()
  } else {
    currentIndex.value++
  }
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
  progress.clear()
}

function restart() {
  progress.clear()
  currentIndex.value = 0
  score.value = 0
  correctCount.value = 0
  lives.value = 3
  gameOver.value = false
  reward.value = null
  hitResult.value = null
  load()
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
.balloon-game {
  user-select: none;
}

/* ---------- HUD ---------- */
.hud {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;

  .hud-chip {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 14px;
    background: #fff;
    border: 1px solid #f1e9dd;
    border-radius: 999px;
    font-size: 13px;
    color: $text-regular;
    box-shadow: 0 2px 8px rgba(120, 90, 60, 0.05);

    b {
      color: $color-primary;
      font-size: 15px;
    }
  }

  .lives-chip {
    .heart {
      display: inline-flex;
      color: $color-warning;
      transition: all 0.3s;

      &.lost {
        color: #ddd;
        transform: scale(0.8);
      }
    }
  }

  .hud-dots {
    display: flex;
    align-items: center;
    gap: 5px;
    margin-left: auto;

    .dot {
      width: 7px;
      height: 7px;
      border-radius: 999px;
      background: #e8e0d5;
      transition: all 0.3s;

      &.done { background: $color-primary; }
      &.cur {
        width: 18px;
        background: $color-warning;
      }
    }
  }

  .src-tag {
    padding: 3px 10px;
    border-radius: 6px;
    font-size: 11px;
    font-weight: 500;

    &.src-due  { background: #FFF4E5; color: #D97706; }
    &.src-book { background: #EAF1FF; color: #2563EB; }
    &.src-core { background: #F0F9FF; color: #0EA5E9; }
  }
}

/* ---------- 提示条 ---------- */
.prompt-bar {
  text-align: center;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #f1e9dd;
  border-radius: $radius-base;
  font-size: 15px;
  color: $text-regular;
  margin-bottom: 14px;
  box-shadow: 0 2px 8px rgba(120, 90, 60, 0.04);

  b { color: $text-primary; }
}

/* ---------- 天空舞台 ---------- */
/* grid 同格堆叠：结算层与天空完全同宽同高 */
.sky-zone {
  display: grid;

  .balloon-sky,
  .game-over {
    grid-area: 1 / 1;
  }
}

.balloon-sky {
  position: relative;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  align-items: center;
  justify-items: center;
  min-height: 320px;
  background: linear-gradient(180deg, #cfe8ff 0%, #e8f5ff 55%, #f7fbff 100%);
  border-radius: $radius-card;
  padding: 34px 20px 20px;
  overflow: hidden;
}

/* 飘云 */
.cloud {
  position: absolute;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 999px;
  filter: blur(1px);
  animation: drift linear infinite;

  &::before,
  &::after {
    content: '';
    position: absolute;
    background: inherit;
    border-radius: 50%;
  }
  &::before { width: 46%; height: 150%; left: 16%; top: -70%; }
  &::after { width: 36%; height: 120%; right: 14%; top: -50%; }
}
.c1 { width: 90px; height: 30px; top: 14%; animation-duration: 26s; }
.c2 { width: 64px; height: 22px; top: 34%; animation-duration: 34s; animation-delay: -12s; opacity: 0.75; }
.c3 { width: 110px; height: 34px; top: 62%; animation-duration: 42s; animation-delay: -24s; opacity: 0.5; }

@keyframes drift {
  0% { left: -14%; }
  100% { left: 110%; }
}

/* ---------- 气球 ---------- */
.balloon {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  animation: bob 3.4s ease-in-out infinite;
  animation-duration: var(--sway);
  animation-delay: var(--delay);
  transition: filter 0.2s;

  &:hover .b-img {
    filter: brightness(1.06) drop-shadow(0 10px 18px rgba(90, 130, 170, 0.35));
    transform: scale(1.06);
  }

  &.flying {
    animation: fly-away 0.9s cubic-bezier(0.4, 0, 0.6, 1) forwards;
    pointer-events: none;
  }

  &.popped {
    pointer-events: none;
  }

  .b-stage {
    position: relative;
    width: 120px;
    height: 120px;
  }

  .b-img {
    width: 100%;
    height: 100%;
    object-fit: contain;
    transition: transform 0.2s, filter 0.2s;
  }

  /* 单词压在气球中央 */
  .b-word {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0 14px;
    text-align: center;
    font-size: 14px;
    font-weight: 700;
    color: #fff;
    text-shadow: 0 1px 3px rgba(0, 0, 0, 0.28);
    pointer-events: none;
    word-break: break-all;
  }

  /* 爆炸贴纸 */
  .b-pop {
    position: absolute;
    left: 50%;
    top: 50%;
    width: 150px;
    height: 150px;
    object-fit: contain;
    transform: translate(-50%, -50%) scale(0);
    animation: pop-burst 0.55s cubic-bezier(0.2, 0.9, 0.3, 1.2) forwards;
    pointer-events: none;
  }

  .b-string {
    width: 2px;
    height: 34px;
    margin-top: -4px;
    background: linear-gradient(180deg, rgba(120, 140, 160, 0.55), rgba(120, 140, 160, 0.15));
    border-radius: 2px;
    transform-origin: top;
    animation: string-sway 3.4s ease-in-out infinite;
    animation-duration: var(--sway);
    animation-delay: var(--delay);
  }

  &.popped .b-img {
    animation: balloon-crush 0.22s ease-in forwards;
  }
  &.popped .b-string {
    animation: none;
    opacity: 0;
    transition: opacity 0.2s;
  }
  &.flying .b-string {
    animation: none;
  }
}

@keyframes bob {
  0%, 100% { transform: translateY(0) rotate(-1.5deg); }
  50% { transform: translateY(-14px) rotate(1.5deg); }
}

@keyframes string-sway {
  0%, 100% { transform: rotate(4deg); }
  50% { transform: rotate(-4deg); }
}

/* 答对：飘走 */
@keyframes fly-away {
  0% {
    transform: translateY(0) rotate(0deg) scale(1);
    opacity: 1;
  }
  35% {
    transform: translateY(-30px) rotate(6deg) scale(1.04);
  }
  100% {
    transform: translateY(-420px) rotate(-8deg) scale(0.85);
    opacity: 0;
  }
}

/* 答错：先鼓一下再缩没 */
@keyframes balloon-crush {
  0% { transform: scale(1); }
  45% { transform: scale(1.22); }
  100% { transform: scale(0); opacity: 0; }
}

@keyframes pop-burst {
  0% { transform: translate(-50%, -50%) scale(0); opacity: 0; }
  30% { transform: translate(-50%, -50%) scale(1.15); opacity: 1; }
  70% { transform: translate(-50%, -50%) scale(1); opacity: 1; }
  100% { transform: translate(-50%, -50%) scale(1.3); opacity: 0; }
}

/* ---------- 结算：覆盖天空区 ---------- */
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
    text-align: center;
  }

  .over-badge {
    width: 72px;
    height: 72px;
    margin: 0 auto 12px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;

    &.ok {
      background: linear-gradient(145deg, #4cc38a, #2fa36b);
      box-shadow: 0 8px 20px rgba(47, 163, 107, 0.35);
    }
    &.fail {
      background: linear-gradient(145deg, #f0a35e, #e07b39);
      box-shadow: 0 8px 20px rgba(224, 123, 57, 0.35);
    }
  }

  h3 {
    font-size: 20px;
    color: $text-primary;
  }

  .over-score {
    margin-top: 6px;
    color: $text-regular;
    font-size: 14px;

    b { color: $text-primary; }
  }

  .reward {
    margin-top: 4px;
    color: $color-warning;
    font-weight: 600;
    font-size: 14px;
  }

  .over-actions {
    margin-top: 18px;
    display: flex;
    justify-content: center;
    gap: 10px;
  }
}

@keyframes card-in {
  0% { transform: scale(0.86) translateY(14px); opacity: 0; }
  100% { transform: scale(1) translateY(0); opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .balloon,
  .b-string,
  .cloud,
  .b-pop,
  .game-over,
  .balloon.popped .b-img {
    animation: none !important;
  }
  .balloon.flying {
    opacity: 0;
    transition: opacity 0.2s;
  }
}
</style>
