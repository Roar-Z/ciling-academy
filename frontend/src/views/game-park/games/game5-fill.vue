<template>
  <div class="game-box">
    <div v-if="loading" class="state-box"><el-skeleton animated :rows="5" style="width: 100%" /></div>
    <div v-else-if="error" class="state-box">
      <div class="state-icon"><AppIcon name="circle-alert" :size="32" /></div>
      <p class="state-text">题目加载失败，请稍后再试</p>
      <el-button type="primary" style="margin-top: 12px" @click="restart">重试</el-button>
    </div>

    <template v-else>
      <div class="game-status">
        <span>得分：<b class="primary">{{ score }}</b></span>
        <span>进度：<b class="primary">{{ currentIndex + 1 }}</b> / {{ words.length }}</span>
        <span class="src-tag" :class="'src-' + (currentWord?.source || 'core')">{{ sourceLabel(currentWord?.source) }}</span>
      </div>

      <!-- 题干（填空） -->
      <div class="fill-prompt">
        <p v-if="currentExample">
          请从选项中选出正确的单词补全句子
        </p>
        <div class="fill-sentence">
          <span v-for="(part, i) in sentenceParts" :key="i">
            <template v-if="part.type === 'blank'">
              <span class="blank">&nbsp;</span>
            </template>
            <template v-else>{{ part.text }}</template>
          </span>
        </div>
        <p class="fill-hint" v-if="!currentExample">根据中文释义选择对应的英文单词</p>
              <p class="fill-meaning" v-if="showHint">{{ currentMeaning }}</p>
              <el-button v-if="!showHint" link type="primary" class="hint-btn" @click="showMeaning">查看提示（-2 分）</el-button>
            </div>

      <!-- 选项 -->
      <div class="fill-opts">
        <el-button
          v-for="(opt, i) in options"
          :key="i"
          size="large"
          class="fill-opt"
          :class="{ correct: answered && opt === currentWord, wrong: answered && opt === lastPick && opt !== currentWord }"
          @click="pick(opt)"
        >{{ opt }}</el-button>
      </div>

      <!-- 结算 -->
      <div class="game-over" v-if="gameOver">
        <h3><AppIcon name="pen-line" :size="20" /> 闯关完成！</h3>
        <p>得分 {{ score }} · 答对 {{ correctCount }}/{{ words.length }}</p>
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
const { words, loading, error, load, finish, reportReview } = useGame('game5', '单词填空闯关', 10)

const currentIndex = ref(0)
const score = ref(0)
const correctCount = ref(0)
const gameOver = ref(false)
const reward = ref(null)
const answered = ref(false)
const lastPick = ref('')
const showHint = ref(false) // 是否已查看中文提示（默认隐藏）
// 进度持久化：退出再进来续上（选项锁定态 answered/lastPick 不持久化）
const progress = useGameProgress('game5', { words, currentIndex, score, correctCount, gameOver, reward })

const currentWord = computed(() => words.value[currentIndex.value]?.word || '')
const currentExample = computed(() => {
  const ex = words.value[currentIndex.value]?.example || ''
  return ex && !ex.toLowerCase().includes('undefined') ? ex : ''
})
const currentMeaning = computed(() => {
  const m = words.value[currentIndex.value]?.meaning || ''
  return m.replace(/^[nvadjprep]\.\s*/, '')
})

/** 将例句中的目标词替换为空格：允许可选复数 s，避免 form/forms 类变形漏匹配 */
const sentenceParts = computed(() => {
  const ex = currentExample.value
  if (!ex) return []
  const word = currentWord.value
  if (!word) return []
  const escaped = word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const regex = new RegExp(`(\\b${escaped}s?\\b)`, 'gi')
  const parts = []
  let last = 0
  let m
  while ((m = regex.exec(ex)) !== null) {
    if (m.index > last) parts.push({ type: 'text', text: ex.slice(last, m.index) })
    parts.push({ type: 'blank', text: '' })
    last = m.index + m[0].length
  }
  if (last < ex.length) parts.push({ type: 'text', text: ex.slice(last) })
  return parts.length ? parts : []
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
  await load() // 必须先拉题目，否则恢复进度后 words 为空、UI 空白
  if (!error.value) progress.restore()
})

function shuffle(arr) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[arr[i], arr[j]] = [arr[j], arr[i]]
  }
}

function pick(opt) {
  if (answered.value) return
  lastPick.value = opt
  answered.value = true
  if (opt === currentWord.value) {
    score.value += 10
    correctCount.value++
    reportReview(words.value[currentIndex.value], 'know')
  } else {
    reportReview(words.value[currentIndex.value], 'forget')
  }
  setTimeout(() => {
    answered.value = false
    showHint.value = false // 下一题重置提示
    if (currentIndex.value + 1 >= words.value.length) {
      endGame()
    } else {
      currentIndex.value++
    }
  }, 700)
}

// 查看中文提示：扣 2 分并显示
function showMeaning() {
  showHint.value = true
  score.value = Math.max(0, score.value - 2)
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
  currentIndex.value = 0
  score.value = 0
  correctCount.value = 0
  gameOver.value = false
  reward.value = null
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
.game-status {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
  font-size: 14px;

  .primary {
    color: $color-primary;
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

.fill-prompt {
  text-align: center;
  padding: 18px;
  background: $bg-hover;
  border-radius: $radius-card;
  margin-bottom: 20px;

  p {
    font-size: 13px;
    color: $text-secondary;
  }

  .fill-sentence {
    font-size: 19px;
    color: $text-primary;
    line-height: 1.7;
    margin-top: 8px;

    .blank {
      display: inline-block;
      min-width: 90px;
      vertical-align: bottom;
      color: $color-primary;
      font-weight: 700;
      border-bottom: 2px solid $color-primary;
      padding: 0 4px;
      text-align: center;
    }
  }

  .fill-hint {
    margin-top: 8px;
  }

  .fill-meaning {
    margin-top: 6px;
    font-size: 16px;
    font-weight: 600;
    color: $color-primary;
  }

  .hint-btn {
    margin-top: 4px;
    font-size: 13px;
  }
}

.fill-opts {
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;

  .fill-opt {
    min-width: 130px;
    font-size: 16px;
    font-weight: 600;

    &.correct {
      background: $color-success;
      border-color: $color-success;
      color: #fff;
    }

    &.wrong {
      background: $color-danger;
      border-color: $color-danger;
      color: #fff;
    }
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
</style>
