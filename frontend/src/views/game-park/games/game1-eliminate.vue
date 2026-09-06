<template>
  <div class="game-box">
    <!-- 加载/错误态 -->
    <div v-if="loading" class="state-box"><el-skeleton animated :rows="5" style="width: 100%" /></div>
    <div v-else-if="error" class="state-box">
      <div class="state-icon"><AppIcon name="circle-alert" :size="32" /></div>
      <p class="state-text">单词加载失败，请稍后再试</p>
      <el-button type="primary" style="margin-top: 12px" @click="restart">重试</el-button>
    </div>

    <template v-else>
      <!-- 顶部状态 -->
      <div class="game-status">
        <span class="stat-chip">得分 <b>{{ score }}</b></span>
        <span class="stat-chip">已消除 <b>{{ matchedPairs }}<i>/{{ pairs.length }}</i></b></span>
        <span class="status-tip">点击两张卡片，配对单词与释义</span>
      </div>

      <!-- 卡牌网格 -->
      <div class="card-grid">
        <div
          v-for="card in cards"
          :key="card.key"
          class="mem-card"
          :class="{
            flipped: card.flipped,
            matched: card.matched
          }"
          @click="flip(card)"
        >
          <div class="mem-inner">
            <div class="mem-front">
              <span class="mf-ring"></span>
              <AppIcon name="sparkles" :size="20" />
            </div>
            <div class="mem-back" :class="{ word: card.isWord }">
              <span class="mb-tag">{{ card.isWord ? 'EN' : '中' }}</span>
              <span class="mb-text">{{ card.text }}</span>
              <span v-if="card.matched" class="mb-check"><AppIcon name="check" :size="11" color="#ffffff" /></span>
            </div>
          </div>
        </div>
      </div>

      <!-- 完成结算 -->
      <div class="game-over" v-if="gameOver">
        <div class="over-card">
          <div class="over-badge"><AppIcon name="medal" :size="34" color="#ffffff" /></div>
          <h3>通关啦！</h3>
          <p class="over-score">得分 <b>{{ score }}</b> · 用时 <b>{{ duration }}</b> 秒</p>
          <p class="reward" v-if="reward && reward.coins > 0">获得金币 +{{ reward.coins }}</p>
          <div class="over-actions">
            <el-button type="primary" round @click="restart">再来一局</el-button>
            <el-button round @click="back">返回乐园</el-button>
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

const router = useRouter()
const { words, loading, error, load, finish, elapsedSec, reportReview } = useGame('game1', '单词消消乐', 8)

const cards = ref([])
const flipped = ref([])
const matchedPairs = ref(0)
const score = ref(0)
const gameOver = ref(false)
const reward = ref(null)
const duration = ref(0)

// 进度持久化：退出再进来续上（words 一并存档，恢复后答对/答错仍能正确上报锁定）
const progress = useGameProgress('game1', { words, cards, flipped, matchedPairs, score, gameOver, reward, duration })

const pairs = computed(() => {
  const list = []
  words.value.forEach((w) => {
    list.push({ word: w.word, meaning: w.meaning.replace(/^[nvadjprep].\s*/, '') })
  })
  return list
})

onMounted(async () => {
  const restored = progress.restore()
  if (!restored) {
    await load()
    if (!error.value) initCards()
  } else {
    loading.value = false
  }
})

function initCards() {
  const list = []
  pairs.value.forEach((p, i) => {
    list.push({ key: `w${i}`, pairId: i, text: p.word, isWord: true, flipped: false, matched: false })
    list.push({ key: `m${i}`, pairId: i, text: p.meaning, isWord: false, flipped: false, matched: false })
  })
  shuffle(list)
  cards.value = list
  flipped.value = []
  matchedPairs.value = 0
  score.value = 0
  gameOver.value = false
  reward.value = null
}

function shuffle(arr) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[arr[i], arr[j]] = [arr[j], arr[i]]
  }
}

function flip(card) {
  if (gameOver.value || card.flipped || card.matched) return
  if (flipped.value.length >= 2) return

  card.flipped = true
  flipped.value.push(card)

  if (flipped.value.length === 2) {
    const [a, b] = flipped.value
    setTimeout(() => {
      if (a.pairId === b.pairId && a.isWord !== b.isWord) {
        a.matched = true
        b.matched = true
        matchedPairs.value++
        score.value += 10
        reportReview(words.value[a.pairId], 'know')
        if (matchedPairs.value === pairs.value.length) {
          endGame()
        }
      } else {
        a.flipped = false
        b.flipped = false
        reportReview(words.value[a.pairId], 'forget')
      }
      flipped.value = []
    }, 420)
  }
}

async function endGame() {
  duration.value = elapsedSec()
  gameOver.value = true
  try {
    reward.value = await finish({ score: score.value, correctCount: pairs.value.length, totalCount: pairs.value.length })
  } catch (e) {
    reward.value = null
  }
  progress.clear()
}

function restart() {
  progress.clear()
  load().then(() => !error.value && initCards())
}

function back() {
  router.push('/game-park')
}
</script>

<style lang="scss" scoped>
/* ---------- 顶部状态 ---------- */
.game-status {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 18px;
  font-size: 13px;
  color: $text-regular;

  .stat-chip {
    display: inline-flex;
    align-items: baseline;
    gap: 6px;
    padding: 6px 14px;
    background: #fff;
    border: 1px solid #f1e9dd;
    border-radius: $radius-pill;
    box-shadow: $shadow-xs;

    b {
      color: $color-primary;
      font-size: 15px;
      font-weight: 700;
    }

    i {
      font-style: normal;
      font-size: 12px;
      color: $text-caption;
    }
  }

  .status-tip {
    margin-left: auto;
    font-size: 12px;
    color: $text-caption;
  }
}

/* ---------- 卡牌网格 ---------- */
.card-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.mem-card {
  perspective: 700px;
  height: 104px;
  cursor: pointer;

  .mem-inner {
    position: relative;
    width: 100%;
    height: 100%;
    transition: transform 0.45s cubic-bezier(0.4, 0.2, 0.2, 1);
    transform-style: preserve-3d;
  }

  &.flipped .mem-inner,
  &.matched .mem-inner {
    transform: rotateY(180deg);
  }

  /* 背面（未翻开）：暖米色牌背 + 同心圆纹 + 星点 */
  .mem-front,
  .mem-back {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: $radius-card;
    backface-visibility: hidden;
    overflow: hidden;
  }

  .mem-front {
    flex-direction: column;
    gap: 2px;
    background:
      radial-gradient(circle at 85% 12%, rgba(58, 140, 137, 0.08) 0 14px, transparent 15px),
      radial-gradient(circle at 12% 88%, rgba(58, 140, 137, 0.06) 0 10px, transparent 11px),
      linear-gradient(160deg, #fdfaf5 0%, #f6efe4 100%);
    border: 1px solid #ece2d2;
    color: #b99a6b;
    box-shadow: $shadow-sm;
    transition: transform 0.2s ease-out, box-shadow 0.2s ease-out;

    .mf-ring {
      position: absolute;
      inset: 7px;
      border: 1px dashed rgba(185, 154, 107, 0.4);
      border-radius: 9px;
    }
  }

  &:not(.flipped):not(.matched):hover .mem-front {
    transform: translateY(-3px);
    box-shadow: 0 8px 18px rgba(150, 120, 80, 0.16);
    color: #a3814f;
  }

  &:active:not(.flipped):not(.matched) .mem-front {
    transform: translateY(-1px) scale(0.99);
  }

  /* 正面：英语卡 / 中文卡 两种牌面 */
  .mem-back {
    flex-direction: column;
    transform: rotateY(180deg);
    padding: 10px 12px;
    text-align: center;
    word-break: break-word;

    .mb-tag {
      position: absolute;
      top: 8px;
      left: 8px;
      font-size: 10px;
      font-weight: 700;
      line-height: 1;
      letter-spacing: 0.5px;
      padding: 3px 6px;
      border-radius: $radius-sm;
    }

    .mb-text {
      position: relative;
      max-height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
    }
  }

  /* 英语牌：白纸底 + 墨色衬线单词 + 实色小标签 */
  .mem-back.word {
    background: #fffdf8;
    border: 1px solid #e4dccd;
    box-shadow: $shadow-xs;
    color: #2b3a37;
    font-family: Georgia, 'Times New Roman', 'Songti SC', serif;
    font-size: 17px;
    font-weight: 700;

    .mb-tag {
      background: #3a8c89;
      color: #fff;
    }
  }

  /* 中文牌：暖纸底 + 深墨字 */
  .mem-back:not(.word) {
    background: #f7f1e5;
    border: 1px solid #e4dccd;
    box-shadow: $shadow-xs;
    color: #4a4034;
    font-size: 13px;
    line-height: 1.55;

    .mb-tag {
      background: #b98a4f;
      color: #fff;
    }
  }

  /* 配对成功：淡出 + 角标 */
  &.matched {
    cursor: default;

    .mem-back {
      box-shadow: none;
    }

    .mem-back.word {
      background: #eef4f2;
      border-color: #dbe6e2;
      color: #8fa39d;

      .mb-tag { background: #b9cfcc; }
    }

    .mem-back:not(.word) {
      background: #f2eee4;
      border-color: #e2dbcb;
      color: #a49a88;

      .mb-tag { background: #d8c6a8; }
    }

    .mb-check {
      position: absolute;
      right: 7px;
      bottom: 7px;
      width: 18px;
      height: 18px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 50%;
      background: $color-success;
      box-shadow: 0 2px 5px rgba(82, 196, 26, 0.35);
      animation: check-in 0.3s cubic-bezier(0.2, 0.9, 0.3, 1.4);
    }
  }
}

@keyframes check-in {
  0% { transform: scale(0); }
  100% { transform: scale(1); }
}

/* ---------- 结算 ---------- */
.game-over {
  display: flex;
  justify-content: center;
  padding: 34px 0 20px;

  .over-card {
    text-align: center;
    padding: 30px 48px 28px;
    background: #fff;
    border: 1px solid #f1e9dd;
    border-radius: $radius-large;
    box-shadow: $shadow-md;
  }

  .over-badge {
    width: 64px;
    height: 64px;
    margin: 0 auto 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: linear-gradient(150deg, #f7b733 0%, #f58529 100%);
    box-shadow: 0 6px 16px rgba(245, 133, 41, 0.35);
  }

  h3 {
    font-size: 20px;
    font-weight: 700;
    color: $text-title;
  }

  .over-score {
    margin-top: 8px;
    font-size: 14px;
    color: $text-regular;

    b { color: $color-primary; }
  }

  .reward {
    margin-top: 6px;
    color: $color-warning;
    font-weight: 600;
    font-size: 13px;
  }

  .over-actions {
    margin-top: 18px;
    display: flex;
    justify-content: center;
    gap: 10px;
  }
}

@media (max-width: 700px) {
  .card-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: 10px;
  }

  .game-status .status-tip {
    display: none;
  }
}
</style>
