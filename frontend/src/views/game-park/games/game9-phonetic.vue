<template>
  <div class="game-box">
    <div v-if="loading" class="state-box"><el-skeleton animated :rows="5" style="width: 100%" /></div>
    <div v-else-if="error" class="state-box">
      <div class="state-icon"><AppIcon name="circle-alert" :size="32" /></div>
      <p class="state-text">音标加载失败，请稍后再试</p>
      <el-button type="primary" style="margin-top: 12px" @click="restart">重试</el-button>
    </div>

    <template v-else>
      <div class="game-status">
        <span>得分：<b class="primary">{{ score }}</b></span>
        <span>进度：<b class="primary">{{ currentIndex + 1 }}</b> / {{ words.length }}</span>
        <span class="status-tip">看音标拼写单词（纯文字，无音频）</span>
        <span class="src-tag" :class="'src-' + (currentWord?.source || 'core')">{{ sourceLabel(currentWord?.source) }}</span>
      </div>

      <!-- 音标展示 -->
      <div class="phonetic-show">
        <div class="phonetic-big">{{ currentPhonetic || '（词典暂无音标）' }}</div>
        <p class="phonetic-hint">根据音标拼出对应的英文单词</p>
      </div>

      <!-- 输入 -->
      <div class="phonetic-input">
        <el-input
          v-model="answer"
          size="large"
          placeholder="输入单词"
          style="max-width: 320px"
          @keyup.enter="check"
        />
        <el-button type="primary" size="large" @click="check">确认</el-button>
      </div>

      <div class="feedback" v-if="feedback !== null">
        <span :class="feedback ? 'fb-ok' : 'fb-bad'">
          {{ feedback ? '✓ 拼对了！' : `✗ 正确答案是 ${currentWord}` }}
        </span>
      </div>

      <!-- 结算 -->
      <div class="game-over" v-if="gameOver">
        <h3><AppIcon name="case-sensitive" :size="20" /> 音标拼词完成！</h3>
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
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useGame } from '@/composables/useGame'
import { useGameProgress } from '@/composables/useGameProgress'
import AppIcon from '@/components/common/AppIcon.vue'

const router = useRouter()
const { words, loading, error, load, finish, reportReview } = useGame('game9', '音标拼词', 10)

const currentIndex = ref(0)
const score = ref(0)
const correctCount = ref(0)
const gameOver = ref(false)
const reward = ref(null)
const answer = ref('')
const feedback = ref(null)
// 进度持久化：退出再进来续上（反馈态 feedback 不持久化，已输入内容 answer 保留）
const progress = useGameProgress('game9', { words, currentIndex, score, correctCount, gameOver, reward, answer })

const currentWord = computed(() => words.value[currentIndex.value]?.word || '')
const currentPhonetic = computed(() => words.value[currentIndex.value]?.phonetic || '')

onMounted(async () => {
  await load() // 先拉题目兜底，再恢复存档（存档含 words 时以存档为准）
  progress.restore()
})

function check() {
  if (gameOver.value || feedback.value !== null) return
  const ok = answer.value.trim().toLowerCase() === currentWord.value.toLowerCase()
  feedback.value = ok
  if (ok) {
    score.value += 10
    correctCount.value++
    reportReview(words.value[currentIndex.value], 'know')
  } else {
    reportReview(words.value[currentIndex.value], 'forget')
  }
  setTimeout(() => {
    feedback.value = null
    answer.value = ''
    if (currentIndex.value + 1 >= words.value.length) {
      endGame()
    } else {
      currentIndex.value++
    }
  }, 900)
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
  answer.value = ''
  feedback.value = null
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

  .src-tag {
    margin-left: 12px;
    padding: 2px 10px;
    border-radius: 12px;
    font-size: 12px;
    font-weight: 500;

    &.src-due  { background: #FFF4E5; color: #D97706; }
    &.src-book { background: #EAF1FF; color: #2563EB; }
    &.src-core { background: #F0F9FF; color: #0EA5E9; }
  }
}

.phonetic-show {
  text-align: center;
  padding: 30px 20px;
  background: $bg-hover;
  border-radius: $radius-card;
  margin-bottom: 20px;

  .phonetic-big {
    font-size: 32px;
    color: $text-primary;
    letter-spacing: 1px;
  }

  .phonetic-hint {
    margin-top: 8px;
    font-size: 13px;
    color: $text-secondary;
  }
}

.phonetic-input {
  display: flex;
  justify-content: center;
  gap: 10px;
}

.feedback {
  text-align: center;
  margin-top: 14px;
  font-size: 15px;
  font-weight: 600;

  .fb-ok {
    color: $color-success;
  }

  .fb-bad {
    color: $color-danger;
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
