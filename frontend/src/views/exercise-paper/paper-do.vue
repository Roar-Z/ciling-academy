<template>
  <div class="page-container paper-do-page">
    <!-- 加载态 -->
    <div v-if="loading" class="ws-card state-box">
      <el-skeleton animated :rows="6" style="width: 100%" />
    </div>

    <!-- 错误态 -->
    <div v-else-if="loadError" class="ws-card state-box">
      <div class="state-icon"><AppIcon name="file-warning" :size="32" /></div>
      <p class="state-text">试卷加载失败，可能已被删除</p>
      <el-button type="primary" class="mt12" @click="router.push('/paper-list')">返回试卷列表</el-button>
    </div>

    <template v-else>
      <!-- 卷首 -->
      <div class="ws-card paper-head">
        <div class="ph-main">
          <h1 class="paper-name">{{ paper?.paperName }}</h1>
          <p v-if="paper?.paperIntro" class="paper-intro">{{ paper?.paperIntro }}</p>
        </div>
        <div class="ph-stats">
          <div class="ph-stat">
            <span class="phs-value">{{ paper?.questionCount }}</span>
            <span class="phs-label">题目</span>
          </div>
          <div class="ph-stat">
            <span class="phs-value">{{ paper?.totalScore }}</span>
            <span class="phs-label">总分</span>
          </div>
          <div class="ph-stat">
            <span class="phs-value phs-highlight">{{ answeredCount }}</span>
            <span class="phs-label">已作答</span>
          </div>
        </div>
      </div>

      <!-- 进度条 -->
      <div class="answer-progress ws-card" v-if="!submitted">
        <div class="ap-track">
          <div class="ap-fill" :style="{ width: progressPercent + '%' }"></div>
        </div>
        <span class="ap-text">完成 {{ answeredCount }} / {{ paper?.questionCount }}</span>
      </div>

      <!-- 题目列表 -->
      <div class="questions">
        <div
          v-for="(q, idx) in questions"
          :key="q.id"
          class="ws-card question-item"
          :class="{ 'is-wrong': submitted && !isQuestionCorrect(q), 'is-right': submitted && isQuestionCorrect(q) }"
        >
          <!-- 题头 -->
          <div class="q-head">
            <span class="q-seq">{{ idx + 1 }}</span>
            <span class="q-type">{{ typeLabel(q.qType) }}</span>
            <span v-if="submitted" class="q-result" :class="{ ok: isQuestionCorrect(q), bad: !isQuestionCorrect(q) }">
              {{ isQuestionCorrect(q) ? '✓ 正确' : '✗ 错误' }}
            </span>
          </div>

          <!-- 题干 -->
          <p class="q-stem">{{ q.stem }}</p>

          <!-- 题型1&2&4：单选 -->
          <div v-if="['en2cn', 'cn2en', 'context_choice'].includes(q.qType)" class="q-opts">
            <div
              v-for="(opt, oi) in qOpts(q)"
              :key="oi"
              class="opt-item"
              :class="{
                'is-selected': userAnswers[q.id] === String.fromCharCode(65 + oi),
                'is-correct': submitted && String.fromCharCode(65 + oi) === correctAnswer(q),
                'is-wrong': submitted && userAnswers[q.id] === String.fromCharCode(65 + oi) && String.fromCharCode(65 + oi) !== correctAnswer(q)
              }"
              @click="!submitted && choose(q, String.fromCharCode(65 + oi))"
            >
              <span class="opt-key">{{ String.fromCharCode(65 + oi) }}</span>
              <span class="opt-text">{{ opt }}</span>
            </div>
          </div>

          <!-- 题型3：拼写填空 -->
          <div v-else-if="q.qType === 'spell_fill'" class="q-spell">
            <el-input
              v-model="userAnswers[q.id]"
              :disabled="submitted"
              placeholder="输入完整的单词拼写"
              size="large"
              class="spell-input"
            />
            <p v-if="submitted" class="spell-feedback">
              正确答案：<b>{{ correctAnswer(q) }}</b>
            </p>
          </div>

          <!-- 题型5：词义匹配 -->
          <div v-else-if="q.qType === 'match'" class="q-match">
            <el-alert
              v-if="!matchItems(q).length"
              type="warning"
              :closable="false"
              title="此题格式异常，无法作答，可回到AI对话页重新生成"
              class="match-alert"
            />
            <div v-for="(item, mi) in matchItems(q)" :key="mi" class="match-row">
              <span class="match-left">{{ item.word }}</span>
              <el-icon class="match-arrow"><Right /></el-icon>
              <el-select
                :model-value="matchAnswers[q.id] ? matchAnswers[q.id][mi] : undefined"
                :disabled="submitted"
                placeholder="选择对应释义"
                class="match-select"
                @change="(val) => setMatchAnswer(q, mi, val)"
              >
                <el-option v-for="(opt, oi) in qOpts(q)" :key="oi" :label="opt" :value="oi" />
              </el-select>
            </div>
          </div>

          <!-- 解析 -->
          <div v-if="submitted && q.analysis" class="q-analysis">
            <span class="qa-label">解析</span>
            <span class="qa-text">{{ q.analysis }}</span>
          </div>
        </div>
      </div>

      <!-- 操作栏 -->
      <div class="submit-bar">
        <el-button size="large" @click="router.push('/paper-list')">返回列表</el-button>
        <el-button
          v-if="!submitted"
          type="primary"
          size="large"
          :disabled="answeredCount < (paper?.questionCount || 0)"
          @click="handleSubmit"
        >
          交卷批改（{{ answeredCount }}/{{ paper?.questionCount }}）
        </el-button>
        <el-button v-else type="primary" size="large" @click="restart">重新作答</el-button>
      </div>
    </template>

    <!-- 成绩弹窗 -->
    <el-dialog v-model="resultVisible" title="本次成绩" width="520px" :close-on-click-modal="false">
      <template v-if="submitResult">
        <div class="score-box">
          <div class="sb-score">
            <span class="sbs-value">{{ submitResult.score }}</span>
            <span class="sbs-total">/ {{ submitResult.totalScore }}</span>
          </div>
          <div class="sb-meta">
            <p>答对 <b class="ok">{{ submitResult.correctCount }}</b> / {{ submitResult.totalCount }} 题</p>
            <p>正确率 <b class="hl">{{ submitResult.correctRate }}%</b></p>
          </div>
        </div>
        <p class="score-advice" v-if="submitResult.correctRate >= 80">太棒了！这套卷掌握得很好</p>
        <p class="score-advice" v-else-if="submitResult.correctRate >= 60">不错，再复习一下错题会更好</p>
        <p class="score-advice" v-else>错题已收录，建议先复习生词再练一遍</p>
      </template>
      <template #footer>
        <el-button type="primary" @click="resultVisible = false">查看解析</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Right } from '@element-plus/icons-vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { getPaper, submitPaper } from '@/api/exercise'

const route = useRoute()
const router = useRouter()

const paper = ref(null)
const questions = ref([])
const loading = ref(true)
const loadError = ref(false)
const submitted = ref(false)
const submitResult = ref(null)
const resultVisible = ref(false)

const userAnswers = ref({})
const matchAnswers = ref({})

const TYPE_LABELS = {
  en2cn: '英译汉单选',
  cn2en: '汉译英单选',
  spell_fill: '单词拼写填空',
  context_choice: '语境选词填空',
  match: '词义匹配题'
}

const STOP_WORDS = ['the', 'a', 'an', 'and', 'or', 'to', 'of', 'with', 'for', 'match', 'matching', 'each', 'word', 'words', 'their', 'meanings', 'meaning', 'left', 'right', 'correct', 'following', 'below', 'list', 'lists', 'please', 'select']

onMounted(loadPaper)

const progressPercent = computed(() => {
  const total = paper.value?.questionCount || 0
  if (!total) return 0
  return Math.round((answeredCount.value / total) * 100)
})

function typeLabel(t) {
  return TYPE_LABELS[t] || t || ''
}

function qOpts(q) {
  try {
    return JSON.parse(q.opts || '[]')
  } catch (e) {
    return []
  }
}

function correctAnswer(q) {
  // 提交后后端返回的 detail 在 result 里；题目本身不带答案。
  // 这里用 submitResult.details 反查
  const d = submitResult.value?.details?.find((x) => x.questionId === q.id)
  return d ? d.correctAnswer : ''
}

function isQuestionCorrect(q) {
  const d = submitResult.value?.details?.find((x) => x.questionId === q.id)
  return d ? d.correct : false
}

function choose(q, letter) {
  userAnswers.value[q.id] = letter
}

/** 解析 match 左列单词 */
function parseMatchWords(stem) {
  const words = String(stem || '').match(/[A-Za-z][A-Za-z'-]*/g) || []
  return words.filter((w) => !STOP_WORDS.includes(w.toLowerCase()))
}

/** 组装匹配项：左列单词 + 对应右列选项 */
function matchItems(q) {
  const left = parseMatchWords(q.stem)
  const opts = qOpts(q)
  if (!left.length || left.length !== opts.length) return []
  return left.map((word, i) => ({ word, rightIndex: i }))
}

function setMatchAnswer(q, mi, val) {
  if (!matchAnswers.value[q.id]) {
    matchAnswers.value[q.id] = new Array(qOpts(q).length).fill(undefined)
  }
  matchAnswers.value[q.id][mi] = val
}

const answeredCount = computed(() => {
  let count = 0
  for (const q of questions.value) {
    if (q.qType === 'match') {
      const arr = matchAnswers.value[q.id] || []
      if (arr.length && arr.every((v) => v !== undefined)) count++
    } else {
      if (userAnswers.value[q.id] !== undefined && userAnswers.value[q.id] !== '') count++
    }
  }
  return count
})

async function loadPaper() {
  loading.value = true
  loadError.value = false
  try {
    const data = await getPaper(route.params.id)
    paper.value = data.paper
    questions.value = data.questions || []
    // 初始化答案
    questions.value.forEach((q) => {
      if (q.qType === 'match') {
        matchAnswers.value[q.id] = new Array(qOpts(q).length).fill(undefined)
      } else {
        userAnswers.value[q.id] = ''
      }
    })
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  const answers = questions.value.map((q) => {
    let answer = ''
    if (q.qType === 'match') {
      const arr = matchAnswers.value[q.id] || []
      answer = JSON.stringify(arr.map((v) => (v === undefined ? null : v)))
    } else {
      answer = String(userAnswers.value[q.id] ?? '')
    }
    return { questionId: q.id, answer }
  })

  try {
    const result = await submitPaper({ paperId: paper.value.id, answers })
    submitResult.value = result
    submitted.value = true
    resultVisible.value = true
    // 回填解析
    result.details.forEach((d) => {
      const q = questions.value.find((x) => x.id === d.questionId)
      if (q) q.analysis = d.analysis
    })
  } catch (e) {
    ElMessage.error(e.message || '交卷失败，请重试')
  }
}

function restart() {
  submitted.value = false
  submitResult.value = null
  resultVisible.value = false
  userAnswers.value = {}
  matchAnswers.value = {}
  loadPaper()
}
</script>

<style lang="scss" scoped>
.paper-do-page {
  padding-top: $sp-6;
}

.mt12 {
  margin-top: $sp-3;
}

/* ---------- 卷首 ---------- */
.paper-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $sp-6;
  padding: $sp-5 $sp-6;

  .paper-name {
    font-size: $fs-4xl;
    font-weight: 700;
    color: $text-title;
    letter-spacing: -0.01em;
  }

  .paper-intro {
    margin-top: $sp-1;
    font-size: $fs-base;
    color: $text-caption;
    max-width: 640px;
  }

  .ph-stats {
    display: flex;
    gap: $sp-6;
    flex-shrink: 0;
  }

  .ph-stat {
    text-align: center;

    .phs-value {
      display: block;
      font-size: $fs-3xl;
      font-weight: 700;
      color: $text-title;
      line-height: 1.2;
    }

    .phs-highlight {
      color: $color-primary;
    }

    .phs-label {
      font-size: $fs-sm;
      color: $text-caption;
    }
  }
}

/* ---------- 进度 ---------- */
.answer-progress {
  display: flex;
  align-items: center;
  gap: $sp-3;
  padding: $sp-3 $sp-5;
  margin-top: $sp-3;

  .ap-track {
    flex: 1;
    height: 5px;
    background: $gray-3;
    border-radius: $radius-pill;
    overflow: hidden;

    .ap-fill {
      height: 100%;
      background: $color-primary;
      border-radius: $radius-pill;
      transition: width $transition-normal;
    }
  }

  .ap-text {
    font-size: $fs-base;
    color: $text-caption;
    white-space: nowrap;
  }
}

/* ---------- 题目 ---------- */
.questions {
  display: flex;
  flex-direction: column;
  gap: $sp-3;
  margin-top: $sp-3;
}

.question-item {
  padding: $sp-5 $sp-6;
  border-left: 3px solid transparent;
  transition: border-color $transition-fast;

  &.is-wrong {
    border-left-color: $color-danger;
  }

  &.is-right {
    border-left-color: $color-success;
  }

  .q-head {
    display: flex;
    align-items: center;
    gap: $sp-2;
    margin-bottom: $sp-3;

    .q-seq {
      width: 24px;
      height: 24px;
      border-radius: $radius-sm;
      background: $primary-1;
      color: $color-primary;
      font-size: $fs-sm;
      font-weight: 700;
      @include flex-center;
    }

    .q-type {
      font-size: $fs-sm;
      color: $text-caption;
      background: $gray-3;
      padding: 2px $sp-2;
      border-radius: $radius-sm;
    }

    .q-result {
      margin-left: auto;
      font-size: $fs-base;
      font-weight: 600;

      &.ok {
        color: $color-success;
      }

      &.bad {
        color: $color-danger;
      }
    }
  }

  .q-stem {
    font-size: $fs-xl;
    color: $text-title;
    font-weight: 500;
    line-height: 1.7;
    margin-bottom: $sp-4;
  }
}

/* 选项 */
.q-opts {
  display: flex;
  flex-direction: column;
  gap: $sp-2;
}

.opt-item {
  display: flex;
  align-items: flex-start;
  gap: $sp-3;
  padding: $sp-3 $sp-4;
  border: 1px solid $border-base;
  border-radius: $radius-base;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    border-color: $primary-3;
    background: $bg-soft;
  }

  &.is-selected {
    border-color: $color-primary;
    background: $primary-1;

    .opt-key {
      background: $color-primary;
      color: #fff;
      border-color: $color-primary;
    }
  }

  &.is-correct {
    border-color: $color-success;
    background: $color-success-soft;

    .opt-key {
      background: $color-success;
      color: #fff;
      border-color: $color-success;
    }
  }

  &.is-wrong {
    border-color: $color-danger;
    background: $color-danger-soft;

    .opt-key {
      background: $color-danger;
      color: #fff;
      border-color: $color-danger;
    }
  }

  .opt-key {
    width: 22px;
    height: 22px;
    border-radius: 50%;
    border: 1px solid $border-base;
    @include flex-center;
    font-size: $fs-sm;
    font-weight: 600;
    color: $text-caption;
    flex-shrink: 0;
    transition: all $transition-fast;
  }

  .opt-text {
    font-size: $fs-md;
    color: $text-body;
    line-height: 1.6;
  }
}

/* 拼写 */
.q-spell {
  .spell-input {
    max-width: 320px;
  }

  .spell-feedback {
    margin-top: $sp-2;
    font-size: $fs-base;
    color: $text-body;

    b {
      color: $color-success;
      font-weight: 600;
    }
  }
}

/* 匹配 */
.q-match {
  display: flex;
  flex-direction: column;
  gap: $sp-2;

  .match-alert {
    margin-bottom: $sp-2;
  }

  .match-row {
    display: flex;
    align-items: center;
    gap: $sp-3;

    .match-left {
      width: 150px;
      font-size: $fs-md;
      font-weight: 600;
      color: $text-title;
      flex-shrink: 0;
    }

    .match-arrow {
      color: $text-disabled;
      font-size: 13px;
      flex-shrink: 0;
    }

    .match-select {
      width: 240px;
    }
  }
}

/* 解析 */
.q-analysis {
  margin-top: $sp-4;
  padding: $sp-3 $sp-4;
  background: $bg-soft;
  border: 1px solid $border-light;
  border-radius: $radius-base;
  font-size: $fs-base;
  display: flex;
  gap: $sp-2;

  .qa-label {
    color: $color-primary;
    font-weight: 600;
    flex-shrink: 0;
  }

  .qa-text {
    color: $text-caption;
  }
}

/* ---------- 操作栏 ---------- */
.submit-bar {
  display: flex;
  justify-content: center;
  gap: $sp-3;
  margin-top: $sp-5;
  padding-bottom: $sp-4;
}

/* ---------- 成绩弹窗 ---------- */
.score-box {
  display: flex;
  align-items: center;
  gap: $sp-6;
  padding: $sp-5;
  background: $bg-soft;
  border: 1px solid $border-light;
  border-radius: $radius-card;

  .sb-score {
    display: flex;
    align-items: baseline;
    gap: $sp-1;

    .sbs-value {
      font-size: 48px;
      font-weight: 700;
      color: $color-primary;
      line-height: 1;
    }

    .sbs-total {
      font-size: $fs-md;
      color: $text-caption;
    }
  }

  .sb-meta {
    p {
      font-size: $fs-md;
      color: $text-body;
      margin-bottom: $sp-1;
    }

    .ok {
      color: $color-success;
    }

    .hl {
      color: $color-primary;
      font-size: $fs-lg;
    }
  }
}

.score-advice {
  margin-top: $sp-4;
  font-size: $fs-md;
  color: $text-body;
  text-align: center;
}

@media (max-width: 800px) {
  .paper-head {
    flex-direction: column;
    gap: $sp-4;
  }

  .question-item {
    padding: $sp-4;
  }

  .q-match .match-row {
    flex-wrap: wrap;

    .match-left {
      width: 100%;
    }
  }
}
</style>
