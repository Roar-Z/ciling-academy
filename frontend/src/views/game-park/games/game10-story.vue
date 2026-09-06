<template>
  <div class="game-box">
    <div v-if="loading" class="state-box"><el-skeleton animated :rows="5" style="width: 100%" /></div>
    <div v-else-if="error" class="state-box">
      <div class="state-icon"><AppIcon name="circle-alert" :size="32" /></div>
      <p class="state-text">故事加载失败，请稍后再试</p>
      <el-button type="primary" style="margin-top: 12px" @click="restart">重试</el-button>
    </div>

    <template v-else>
      <div class="game-status">
        <span>得分：<b class="primary">{{ score }}</b></span>
        <span>句子数：<b class="primary">{{ story.length }}</b> / {{ words.length }}</span>
        <span class="src-tag" :class="'src-' + (currentWord?.source || 'core')">{{ sourceLabel(currentWord?.source) }}</span>
      </div>

      <!-- 当前单词 -->
      <div class="story-word">
        <p class="sw-label">用下面的单词写一句英文，接龙成一篇小故事</p>
        <div class="sw-word">
          <b>{{ currentWord }}</b>
          <span class="sw-meaning">{{ currentMeaning }}</span>
        </div>
      </div>

      <!-- 写作区 -->
      <div class="story-input">
        <el-input
          v-model="sentence"
          type="textarea"
          :rows="3"
          placeholder="Write a sentence here..."
        />
        <div class="story-actions">
          <el-button type="primary" :disabled="!sentence.trim()" @click="submitSentence">提交句子 +10分</el-button>
          <span class="story-feedback" :class="{ ok: feedback === 'ok', bad: feedback !== null && feedback !== 'ok' }" v-if="feedback !== null">
            {{ feedback === 'ok' ? '✓ 很好，继续！' : '✗ ' + feedback }}
          </span>
        </div>
      </div>

      <!-- 故事 -->
      <div class="story-panel" v-if="story.length">
        <h4><AppIcon name="book-open" :size="16" /> 我的小故事</h4>
        <p class="story-text">{{ storyText }}</p>
      </div>

      <!-- 结算 -->
      <div class="game-over" v-if="gameOver">
        <h3><AppIcon name="book-open" :size="20" /> 故事完成！</h3>
        <p>得分 {{ score }} · 一共写了 {{ story.length }} 个句子</p>
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
const { words, loading, error, load, finish, reportReview } = useGame('game10', '单词故事接龙', 8)

const currentIndex = ref(0)
const score = ref(0)
const story = ref([])
const gameOver = ref(false)
const reward = ref(null)
const sentence = ref('')
const feedback = ref(null)
// 进度持久化：退出再进来续上（words 一并存档，否则恢复后 currentIndex 会指向错位的词表）
const progress = useGameProgress('game10', { words, currentIndex, score, story, gameOver, reward, sentence })

const currentWord = computed(() => words.value[currentIndex.value]?.word || '')
const currentMeaning = computed(() => {
  const m = words.value[currentIndex.value]?.meaning || ''
  return m.replace(/^[nvadjprep]\.\s*/, '')
})

const storyText = computed(() => story.value.map((s) => s.text).join(' '))

onMounted(async () => {
  await load() // 先拉题目兜底，再恢复存档（存档含 words 时以存档为准）
  progress.restore()
})

/** 前端检测指定单词是否出现在句子中 */
function containsWord(sentenceText, word) {
  return new RegExp(`(^|[^a-zA-Z])${word.toLowerCase()}($|[^a-zA-Z])`, 'i').test(sentenceText.trim())
}

// 常见谓语动词（原形），主流背词 App 的启发式判定：词表 + 屈折后缀
const BASE_VERBS = new Set([
  'am', 'is', 'are', 'was', 'were', 'be', 'been', 'being',
  'have', 'has', 'had', 'do', 'does', 'did',
  'can', 'could', 'will', 'would', 'shall', 'should', 'may', 'might', 'must', 'need', 'dare',
  'go', 'goes', 'went', 'come', 'comes', 'came', 'see', 'saw', 'look', 'want', 'wish',
  'like', 'love', 'hate', 'know', 'think', 'say', 'said', 'tell', 'ask', 'answer',
  'get', 'got', 'make', 'made', 'take', 'took', 'give', 'gave', 'find', 'found',
  'keep', 'let', 'put', 'mean', 'meet', 'play', 'run', 'read', 'write', 'work',
  'study', 'learn', 'teach', 'help', 'try', 'use', 'call', 'feel', 'felt', 'become',
  'leave', 'bring', 'begin', 'start', 'finish', 'enjoy', 'remember', 'forget',
  'understand', 'speak', 'talk', 'hear', 'watch', 'buy', 'sell', 'pay', 'sit', 'stand',
  'walk', 'eat', 'drink', 'sleep', 'live', 'die', 'grow', 'change', 'open', 'close',
  'send', 'show', 'hear', 'hold', 'turn', 'follow', 'stop', 'believe', 'hold',
])

/** 是否包含谓语动词：情态/系动词、常用词表，或 -ing / -ed 屈折形式 */
function hasVerb(tokens) {
  return tokens.some((t) => {
    const w = t.toLowerCase()
    if (BASE_VERBS.has(w)) return true
    if (/^[a-z]{3,}ing$/.test(w)) return true
    if (/^[a-z]{3,}ed$/.test(w)) return true
    return false
  })
}

/**
 * 句子合法性校验（参考主流背词 App 的启发式规则）：
 * 1. 以 . ! ? 结尾  2. 首字母大写  3. 至少 3 个单词  4. 含谓语动词
 * 返回 null 表示通过，否则返回提示语
 */
function validateSentence(text) {
  if (!/[.!?。！？]$/.test(text)) return '句子要以 . ! ? 结尾哦'
  const tokens = text.replace(/[.!?。！？]+$/, '').split(/[^A-Za-z']+/).filter(Boolean)
  if (tokens.length < 3) return '句子太短了，至少写 3 个单词'
  if (!/^[A-Z]/.test(tokens[0])) return '句首字母要大写哦'
  if (!hasVerb(tokens)) return '句子缺少动词，写一个完整的句子吧'
  return null
}

function submitSentence() {
  if (gameOver.value || feedback.value !== null) return
  const text = sentence.value.trim()
  if (!text) return
  const err = validateSentence(text)
  if (err) {
    feedback.value = err
    setTimeout(() => { feedback.value = null }, 1600)
    return
  }
  if (containsWord(text, currentWord.value)) {
    feedback.value = 'ok'
    score.value += 10
    story.value.push({ text, word: currentWord.value })
    reportReview(words.value[currentIndex.value], 'know')
    setTimeout(() => {
      feedback.value = null
      sentence.value = ''
      if (currentIndex.value + 1 >= words.value.length) {
        endGame()
      } else {
        currentIndex.value++
      }
    }, 700)
  } else {
    feedback.value = '句子中没用到指定单词，再试试'
    reportReview(words.value[currentIndex.value], 'forget')
    setTimeout(() => {
      feedback.value = null
    }, 1600)
  }
}

async function endGame() {
  gameOver.value = true
  try {
    reward.value = await finish({ score: score.value, correctCount: story.value.length, totalCount: words.value.length })
  } catch (e) {
    reward.value = null
  }
  progress.clear()
}

function restart() {
  progress.clear()
  currentIndex.value = 0
  score.value = 0
  story.value = []
  gameOver.value = false
  reward.value = null
  sentence.value = ''
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

.story-word {
  text-align: center;
  padding: 16px;
  background: #EAF1FF;
  border-radius: $radius-card;
  margin-bottom: 16px;

  .sw-label {
    font-size: 13px;
    color: $text-secondary;
  }

  .sw-word {
    margin-top: 8px;

    b {
      font-size: 26px;
      color: $color-primary;
    }

    .sw-meaning {
      margin-left: 12px;
      font-size: 14px;
      color: $text-regular;
    }
  }
}

.story-input {
  .story-actions {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 10px;
  }

  .story-feedback {
    font-size: 13px;
    font-weight: 600;

    &.ok {
      color: $color-success;
    }

    &.bad {
      color: $color-danger;
    }
  }
}

.story-panel {
  margin-top: 16px;
  padding: 14px 16px;
  background: $bg-hover;
  border-radius: $radius-card;

  h4 {
    font-size: 14px;
    margin-bottom: 6px;
  }

  .story-text {
    font-size: 14px;
    color: $text-primary;
    line-height: 1.8;
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
