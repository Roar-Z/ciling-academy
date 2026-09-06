<template>
  <div class="game-box">
    <div v-if="loading" class="state-box"><el-skeleton animated :rows="5" style="width: 100%" /></div>
    <div v-else-if="error" class="state-box">
      <div class="state-icon"><AppIcon name="circle-alert" :size="32" /></div>
      <p class="state-text">连线加载失败，请稍后再试</p>
      <el-button type="primary" style="margin-top: 12px" @click="restart">重试</el-button>
    </div>

    <template v-else>
      <div class="game-status">
        <span>得分：<b class="primary">{{ score }}</b></span>
        <span>已连接：<b class="primary">{{ matchedCount }}</b> / {{ leftItems.length }}</span>
        <span class="status-tip">先点左侧单词，再点右侧释义完成连线</span>
        <el-button v-if="!gameOver && !showAnswer" plain size="small" class="view-answer-btn" @click="revealAnswer">查看答案</el-button>
        <el-button v-else-if="!gameOver && showAnswer" type="primary" size="small" @click="restart">重新开始</el-button>
      </div>

      <div class="connect-board">
        <!-- 左列单词 -->
        <div class="connect-col">
          <div
            v-for="(item, i) in leftItems"
            :key="i"
            class="connect-item left-item"
            :class="{ selected: selectedLeft === i && !showAnswer, matched: item.matched, dimmed: showAnswer && !item.matched, [`pair-${item.colorIdx}`]: item.matched && item.colorIdx >= 0 }"
            @click="pickLeft(i)"
          >{{ item.word }}</div>
        </div>

        <!-- 右列释义 -->
        <div class="connect-col">
          <div
            v-for="(item, i) in rightItems"
            :key="i"
            class="connect-item right-item"
            :class="{ matched: item.matched, wrong: item.wrong, dimmed: showAnswer && !item.matched, [`pair-${item.colorIdx}`]: item.matched && item.colorIdx >= 0 }"
            @click="pickRight(i)"
          >{{ item.meaning }}</div>
        </div>
      </div>

      <!-- 查看答案：仅展示未连线的配对 -->
      <div v-if="showAnswer" class="answer-card">
        <div class="answer-head">
          <span class="answer-title">未连线答案</span>
          <span class="answer-hint">已扣分，重新开始才能继续</span>
        </div>
        <div class="answer-list">
          <div class="answer-row" v-for="p in unmatchedPairs" :key="p.id">
            <span class="a-word">{{ p.word }}</span>
            <span class="a-arrow">→</span>
            <span class="a-meaning">{{ p.meaning }}</span>
          </div>
        </div>
      </div>

      <!-- 结算 -->
      <div class="game-over" v-if="gameOver">
        <h3><AppIcon name="link" :size="20" /> 全部连上啦！</h3>
        <p>得分 {{ score }} · 用时{{ duration }}秒</p>
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
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useGame } from '@/composables/useGame'
import { useGameProgress } from '@/composables/useGameProgress'
import AppIcon from '@/components/common/AppIcon.vue'

const router = useRouter()
const { words, loading, error, load, finish, elapsedSec, reportReview } = useGame('game4', '词义连连看', 8)

const leftItems = ref([])
const rightItems = ref([])
const selectedLeft = ref(-1)
const matchedCount = ref(0)
const score = ref(0)
const gameOver = ref(false)
const reward = ref(null)
const duration = ref(0)
const colorSeq = ref(0) // 已连线的颜色序号（每对一种颜色，0..7 循环）
const showAnswer = ref(false) // 是否查看了答案（查看后锁定棋盘）

// 进度持久化：退出再进来续上（words 一并存档，恢复后答对/答错仍能正确上报锁定）
const progress = useGameProgress('game4', { words, leftItems, rightItems, selectedLeft, matchedCount, score, gameOver, reward, duration, colorSeq, showAnswer })

// 查看答案后，仅列出尚未连线的配对
const unmatchedPairs = computed(() =>
  showAnswer.value
    ? leftItems.value
        .filter((l) => !l.matched)
        .map((l) => {
          const r = rightItems.value.find((x) => x.id === l.id)
          return { id: l.id, word: l.word, meaning: r ? r.meaning : '' }
        })
    : []
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

function initBoard() {
  const pairs = words.value.map((w, i) => ({
    id: i,
    word: w.word,
    meaning: w.meaning.replace(/^[nvadjprep]\.\s*/, '')
  }))
  leftItems.value = pairs.map((p) => ({ id: p.id, word: p.word, matched: false, colorIdx: -1 }))
  // 右侧打乱
  const rights = pairs.map((p) => ({ id: p.id, meaning: p.meaning, matched: false, wrong: false, colorIdx: -1 }))
  shuffle(rights)
  rightItems.value = rights
  selectedLeft.value = -1
  matchedCount.value = 0
  score.value = 0
  gameOver.value = false
  reward.value = null
  colorSeq.value = 0
  showAnswer.value = false
}

function shuffle(arr) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[arr[i], arr[j]] = [arr[j], arr[i]]
  }
}

function pickLeft(i) {
  if (showAnswer.value || leftItems.value[i].matched) return
  selectedLeft.value = i
}

function pickRight(i) {
  if (showAnswer.value || rightItems.value[i].matched || selectedLeft.value < 0) return
  const left = leftItems.value[selectedLeft.value]
  const right = rightItems.value[i]
  if (left.id === right.id) {
    const idx = colorSeq.value % 8
    left.matched = true
    right.matched = true
    left.colorIdx = idx
    right.colorIdx = idx
    colorSeq.value++
    matchedCount.value++
    score.value += 10
    reportReview(words.value[left.id], 'know')
    selectedLeft.value = -1
    if (matchedCount.value === leftItems.value.length) {
      endGame()
    }
  } else {
    right.wrong = true
    score.value = Math.max(0, score.value - 2)
    reportReview(words.value[left.id], 'forget')
    setTimeout(() => {
      right.wrong = false
    }, 400)
    selectedLeft.value = -1
  }
}

async function endGame() {
  duration.value = elapsedSec()
  gameOver.value = true
  try {
    reward.value = await finish({ score: score.value, correctCount: matchedCount.value, totalCount: leftItems.value.length })
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

// 查看答案：展示未连线的配对，按未连线数量扣分（每条 -5，最低 0），并锁定棋盘
function revealAnswer() {
  showAnswer.value = true
  selectedLeft.value = -1
  const remain = leftItems.value.filter((l) => !l.matched).length
  score.value = Math.max(0, score.value - remain * 5)
}
</script>

<style lang="scss" scoped>
.game-status {
  display: flex;
  gap: 20px;
  align-items: center;
  margin-bottom: 14px;
  font-size: 14px;

  .primary {
    color: $color-primary;
  }

  .status-tip {
    margin-left: auto;
    font-size: 12px;
    color: $text-secondary;
  }

  .view-answer-btn {
    font-weight: 500;
    color: $text-regular;
  }
}

.connect-board {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 40px;
  padding: 10px 0;
}

.connect-col {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.connect-item {
  height: 44px;
  padding: 0 18px;
  border-radius: $radius-base;
  border: 1px solid $border-color;
  font-size: 14px;
  cursor: pointer;
  transition: all $transition-normal;
  text-align: center;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;

  &.left-item {
    background: #fff;
    color: $color-primary;
    font-size: 17px;
    font-weight: 300;
    line-height: 1;
    letter-spacing: 0.5px;

    &.selected {
      border-color: $color-primary;
      box-shadow: 0 0 0 2px rgba(58, 140, 137, 0.2);
    }
  }

  &.right-item {
    background: #fff;

    &:hover {
      border-color: $color-primary;
    }

    &.wrong {
      border-color: $color-danger;
      background: #FFF0F0;
    }
  }

  // 已连线：经典八色（红橙黄绿青蓝紫粉），按用户要求使用最基础、最易区分的色相
  &.pair-0 { color: #DC2626; border-color: #FCA5A5; cursor: default; } // 红
  &.pair-1 { color: #F97316; border-color: #FDBA74; cursor: default; } // 橙
  &.pair-2 { color: #CA8A04; border-color: #FDE047; cursor: default; } // 黄（深金，可读）
  &.pair-3 { color: #16A34A; border-color: #86EFAC; cursor: default; } // 绿
  &.pair-4 { color: #0891B2; border-color: #67E8F9; cursor: default; } // 青
  &.pair-5 { color: #2563EB; border-color: #93C5FD; cursor: default; } // 蓝
  &.pair-6 { color: #9333EA; border-color: #D8B4FE; cursor: default; } // 紫
  &.pair-7 { color: #DB2777; border-color: #F9A8D4; cursor: default; } // 粉

  // 查看答案后，未连线的卡淡化为锁定态
  &.dimmed {
    opacity: 0.4;
    cursor: default;
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

// 查看答案后的答案卡：白底、细边、留白克制
.answer-card {
  margin-top: 18px;
  padding: 16px 20px;
  border: 1px solid $border-color;
  border-radius: $radius-base;
  background: #fff;
  box-shadow: 0 2px 12px rgba(17, 24, 39, 0.05);

  .answer-head {
    display: flex;
    align-items: baseline;
    gap: 12px;
    margin-bottom: 12px;
  }

  .answer-title {
    font-size: 13px;
    font-weight: 600;
    letter-spacing: 2px;
    color: $text-regular;
  }

  .answer-hint {
    font-size: 12px;
    color: $text-secondary;
  }

  .answer-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .answer-row {
    display: flex;
    align-items: center;
    gap: 14px;
    font-size: 14px;

    .a-word {
      min-width: 96px;
      font-weight: 600;
      color: $color-primary;
    }

    .a-arrow {
      color: $text-secondary;
    }

    .a-meaning {
      color: $text-regular;
    }
  }
}
</style>
