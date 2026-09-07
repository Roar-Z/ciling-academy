<template>
  <div class="page-container word-test-page">
    <!-- 顶部信息条 -->
    <div class="wt-header">
      <button class="wt-back" @click="onExit">
        <AppIcon name="chevron-left" :size="16" />
        退出
      </button>
      <div class="wt-progress">
        <div class="wp-track">
          <div class="wp-fill" :style="{ width: progressPercent + '%' }"></div>
        </div>
        <span class="wp-text">{{ currentIndex + 1 }} / {{ questions.length }}</span>
      </div>
      <div class="wt-timer" :class="{ warn: timeLeft <= 5, danger: timeLeft <= 2 }">
        <AppIcon name="clock" :size="14" />
        <span>{{ timeLeft }}s</span>
      </div>
    </div>

    <!-- 加载态 -->
    <div v-if="loading" class="wt-loading">
      <el-skeleton animated style="width: 100%; max-width: 640px">
        <template #template>
          <el-skeleton-item variant="h1" style="width: 60%; margin: 32px auto" />
          <el-skeleton-item variant="text" style="width: 80%; margin: 16px auto" />
          <el-skeleton-item variant="button" style="width: 240px; height: 48px; margin: 32px auto" />
        </template>
      </el-skeleton>
    </div>

    <!-- 题目主体 -->
    <div v-else-if="!finished && currentQ" class="wt-body">
      <!-- ============ spelling 模式 ============ -->
      <div v-if="mode === 'spelling'" class="mode-card">
        <div class="mode-tag">看中文拼写英文</div>
        <div class="prompt-meaning">{{ currentQ.meaning }}</div>
        <div v-if="currentQ.pos" class="prompt-pos">{{ currentQ.pos }}</div>

        <div class="answer-row">
          <input
            :ref="(el) => (spellingInputRef = el)"
            v-model="spellingInput"
            class="spell-input"
            type="text"
            :placeholder="'输入英文单词'"
            autocomplete="off"
            spellcheck="false"
            @keydown.enter="onSpellingSubmit"
          />
          <button class="submit-btn" :disabled="!spellingInput.trim()" @click="onSpellingSubmit">
            提交
          </button>
        </div>

        <div v-if="feedback" class="feedback" :class="feedback.correct ? 'ok' : 'bad'">
          <span v-if="feedback.correct">✓ 正确！</span>
          <span v-else>正确答案：<b>{{ currentQ.word }}</b></span>
        </div>
      </div>

      <!-- ============ matching 模式 ============ -->
      <div v-else-if="mode === 'matching'" class="mode-card">
        <div class="mode-tag">中英文连线 · 点左选中文，点右选英文</div>
        <div ref="matchGridRef" class="match-grid">
          <div class="match-col">
            <button
              v-for="cn in currentQ.choicesCn"
              :key="'cn-' + cn"
              :ref="(el) => registerCnRef(cn, el)"
              :data-cn="cn"
              class="match-btn"
              :class="matchCnClass(cn)"
              :disabled="matchedCn.includes(cn) || !!feedback"
              @click="onPickCn(cn)"
            >
              {{ cn }}
            </button>
          </div>
          <div class="match-col">
            <button
              v-for="en in currentQ.choicesEn"
              :key="'en-' + en"
              :ref="(el) => registerEnRef(en, el)"
              :data-en="en"
              class="match-btn match-btn-en"
              :class="matchEnClass(en)"
              :disabled="matchedEn.includes(en) || !!feedback"
              @click="onPickEn(en)"
            >
              {{ en }}
            </button>
          </div>
          <!-- 配对连线 SVG 覆盖层 -->
          <svg
            class="match-lines"
            :viewBox="`0 0 ${svgSize.w} ${svgSize.h}`"
            preserveAspectRatio="none"
          >
            <path
              v-for="(line, idx) in matchLines"
              :key="'line-' + idx"
              :d="`M ${line.x1} ${line.y1} C ${line.midX} ${line.y1}, ${line.midX} ${line.y2}, ${line.x2} ${line.y2}`"
              :class="['match-line', `match-line-${line.status}`]"
            />
          </svg>
        </div>
        <div class="match-progress">
          已配对 {{ matchedCn.length }} / {{ Math.min(currentQ.choicesEn.length, currentQ.choicesCn.length) }}
        </div>
        <div v-if="feedback" class="feedback" :class="feedback.correct ? 'ok' : 'bad'">
          <span v-if="feedback.correct">✓ 全部正确！</span>
          <span v-else>红色线为错配，看看正确连线吧</span>
        </div>
      </div>

      <!-- ============ sentence 模式 ============ -->
      <div v-else-if="mode === 'sentence'" class="mode-card">
        <div class="mode-tag">翻译句子</div>
        <div class="prompt-en">{{ currentQ.example || currentQ.word }}</div>
        <div v-if="currentQ.phonetic" class="prompt-phonetic">/{{ currentQ.phonetic }}/</div>

        <div class="answer-row">
          <textarea
            :ref="(el) => (sentenceInputRef = el)"
            v-model="sentenceInput"
            class="sentence-input"
            :placeholder="'输入中文翻译'"
            rows="2"
            @keydown.ctrl.enter="onSentenceSubmit"
          />
          <button class="submit-btn" :disabled="!sentenceInput.trim()" @click="onSentenceSubmit">
            提交
          </button>
        </div>

        <div v-if="feedback" class="feedback" :class="feedback.correct ? 'ok' : 'bad'">
          <span v-if="feedback.correct">✓ 包含核心词，正确！</span>
          <span v-else>参考答案：<b>{{ currentQ.exampleCn || currentQ.meaning }}</b></span>
        </div>
      </div>
    </div>

    <!-- 完成态：结果页 -->
    <div v-else-if="finished && result" class="wt-result">
      <div class="result-card ws-card">
        <!-- 上一轮对照：点过"再来一轮"后展示，让用户看到前后对比 -->
        <div v-if="lastResult" class="last-round">
          <span class="lr-tag">上一轮</span>
          <span class="lr-stats">
            共 {{ lastResult.total }} 题 · 答对 <b>{{ lastResult.correctCount }}</b> · 正确率
            <b :class="diffClass(lastResult.accuracy, result.accuracy)">
              {{ lastResult.accuracy }}%
              <AppIcon :name="result.accuracy >= lastResult.accuracy ? 'trending-up' : 'trending-down'" :size="13" />
            </b>
          </span>
        </div>

        <div class="result-icon" :class="result.accuracy >= 80 ? 'good' : 'mid'">
          <AppIcon :name="result.accuracy >= 80 ? 'party-popper' : 'check'" :size="34" />
        </div>
        <div class="result-round" v-if="currentRoundNo">
          <span class="round-badge">第 {{ currentRoundNo }} 轮</span>
        </div>
        <h2 class="result-title">{{ result.accuracy >= 80 ? '表现优秀！' : '继续努力' }}</h2>
        <p class="result-sub">
          共 {{ result.total }} 题，答对 {{ result.correctCount }} 题
          · 正确率 <b>{{ result.accuracy }}%</b>
        </p>

        <div class="result-actions">
          <el-button type="primary" @click="onAgain">再来一轮</el-button>
          <el-button @click="openRoundPicker">巩固测验</el-button>
          <el-button @click="go('/review')">回复习</el-button>
          <el-button @click="go('/word-book')">查看生词本</el-button>
        </div>

        <!-- 错题列表 -->
        <div v-if="result.wrongList && result.wrongList.length" class="wrong-block">
          <h3 class="wrong-title">
            <AppIcon name="alert-circle" :size="15" color="#FA8C16" />
            错题回顾（按艾宾浩斯，明天再复习）
          </h3>
          <div class="wrong-list">
            <div v-for="w in result.wrongList" :key="w.wordId" class="wrong-item">
              <div class="wi-head">
                <span class="wi-word">{{ w.word }}</span>
                <span v-if="w.phonetic" class="wi-phonetic">/{{ w.phonetic }}/</span>
              </div>
              <div class="wi-meaning">{{ w.meaning }}</div>
              <div v-if="w.userAnswer" class="wi-user">
                你的作答：<span class="user-answer-text">{{ w.userAnswer }}</span>
              </div>
              <div class="wi-next">下次复习：{{ w.nextReviewAt }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 巩固测验：先选学习轮次 → 再选测试模式 -->
    <el-dialog v-model="roundPickerVisible" title="巩固测验" width="540px" append-to-body>
      <div v-loading="roundLoading">
        <!-- 第 1 步：选学习轮次 -->
        <div v-if="roundStep === 'round'">
          <p class="hist-tip" v-if="!roundLoading && roundList.length">选择要测验的学习轮次（按"学习新词"的那一轮）</p>
          <el-empty v-if="!roundLoading && !roundList.length" description="还没有学习轮次，先去学一轮新词吧" />
          <div v-else class="hist-list">
            <div
              v-for="r in roundList"
              :key="r.roundId"
              class="hist-item"
              @click="pickRound(r)"
            >
              <span class="hist-round">第 {{ r.roundNo }} 轮</span>
              <span class="hist-meta">{{ r.count }} 词 · 掌握 {{ r.masteredCount }}</span>
              <span class="hist-time">{{ formatTime(r.finishedAt) }}</span>
              <span class="hist-arrow">›</span>
            </div>
          </div>
        </div>

        <!-- 第 2 步：选测试模式 -->
        <div v-else>
          <p class="hist-tip">第 {{ selectedRound?.roundNo }} 轮 · 选择测验方式</p>
          <div class="hist-list">
            <div
              v-for="m in testModes"
              :key="m.value"
              class="hist-item"
              @click="startRoundTest(m.value)"
            >
              <span class="hist-round"><AppIcon :name="m.icon" :size="14" /></span>
              <span class="hist-meta">{{ m.label }}</span>
              <span class="hist-time">{{ m.desc }}</span>
              <span class="hist-arrow">›</span>
            </div>
          </div>
          <div class="detail-actions">
            <el-button text @click="roundStep = 'round'">‹ 返回轮次</el-button>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import { startWordTest, submitWordTestAnswer, finishWordTest, getWordTestHistory } from '@/api/wordTest'
import { getLearnRoundHistory } from '@/api/learnRound'

const route = useRoute()
const router = useRouter()

const mode = route.query.mode || 'spelling'   // spelling | matching | sentence
const source = route.query.source || 'new'
const count = Number(route.query.count) || 10
const learnRoundId = route.query.learnRoundId ? Number(route.query.learnRoundId) : null

const loading = ref(true)
const batchId = ref(null)
const questions = ref([])
const currentIndex = ref(0)
const timeLimit = ref(30)
const timeLeft = ref(30)
const finished = ref(false)
const result = ref(null)
/**
 * 上一轮结果存档：onAgain 时把当前 result 暂存到这里，让用户在新一轮结果页能看到对比
 * - 仅前端 localStorage 之外的内存态，刷新即清空（数据持久化靠后端 word_test_batch）
 * - 主要用于"上一轮 vs 本轮"的视觉对照
 */
const lastResult = ref(null)

/** 当前轮次序号（"第 N 轮"，测验历史轮次），由历史接口回填 */
const currentRoundNo = ref(null)

// 巩固测验弹窗：先选学习轮次 → 再选测试模式
const roundPickerVisible = ref(false)
const roundStep = ref('round')        // round | mode
const roundLoading = ref(false)
const roundList = ref([])
const selectedRound = ref(null)
const testModes = [
  { value: 'spelling',  label: '看中文拼英文', desc: '给出中文，输入英文单词拼写', icon: 'keyboard' },
  { value: 'matching',  label: '中英文连线',   desc: '点击配对中文释义与英文单词',  icon: 'link-2' },
  { value: 'sentence',  label: '翻译句子',     desc: '翻译例句，巩固语境理解',      icon: 'languages' }
]

/** 模式中文名 */
function modeLabel(m) {
  return { spelling: '拼写', matching: '连线', sentence: '翻译' }[m] || m
}

/** 时间格式化：YYYY-MM-DD HH:mm */
function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  if (isNaN(d.getTime())) return ''
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

const spellingInput = ref('')
const sentenceInput = ref('')
let spellingInputRef = null
let sentenceInputRef = null
/**
 * 当前题作答反馈：{correct, costMs, userAnswer}
 * 命名 fix：之前误命名为 lastResult，与"上一轮结果存档"重名导致整个 SFC 编译失败
 */
const feedback = ref(null)
const costStart = ref(Date.now())
let timerId = null

// matching
const pickedCn = ref('')
const pickedEn = ref('')
const matchedCn = ref([])
const matchedEn = ref([])

// matching 连线渲染：每个按钮的 DOM ref + 计算出的线段坐标
const matchGridRef = ref(null)
const svgSize = ref({ w: 0, h: 0 })
const matchLines = ref([])
const cnRefs = new Map()
const enRefs = new Map()
let resizeObserver = null

function registerCnRef(key, el) {
  if (el) cnRefs.set(key, el); else cnRefs.delete(key)
}
function registerEnRef(key, el) {
  if (el) enRefs.set(key, el); else enRefs.delete(key)
}

function recalcLines() {
  if (!matchGridRef.value) {
    matchLines.value = []
    return
  }
  const gridRect = matchGridRef.value.getBoundingClientRect()
  svgSize.value = { w: gridRect.width, h: gridRect.height }
  const q = currentQ.value
  if (!q) return
  const lines = []
  for (let i = 0; i < matchedCn.value.length; i++) {
    const cn = matchedCn.value[i]
    const en = matchedEn.value[i]
    const cnEl = cnRefs.get(cn)
    const enEl = enRefs.get(en)
    if (!cnEl || !enEl) continue
    const cnRect = cnEl.getBoundingClientRect()
    const enRect = enEl.getBoundingClientRect()
    const x1 = cnRect.right - gridRect.left
    const y1 = cnRect.top + cnRect.height / 2 - gridRect.top
    const x2 = enRect.left - gridRect.left
    const y2 = enRect.top + enRect.height / 2 - gridRect.top
    let status = 'pending'
    if (feedback.value) {
      // 完成态：每条线单独判定（与正确答案 choicesCn/choicesEn 对比）
      const ok = cn === q.choicesCn[i] && en === q.choicesEn[i]
      status = ok ? 'correct' : 'wrong'
    }
    lines.push({
      x1, y1, x2, y2,
      midX: (x1 + x2) / 2,
      status
    })
  }
  matchLines.value = lines
}

// 进入新题时清空 refs
function clearMatchRefs() {
  cnRefs.clear()
  enRefs.clear()
}

// 监听 grid DOM 变化（题切换时按钮会重建），绑定 ResizeObserver 重新算线
watch(matchGridRef, (el) => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (el) {
    resizeObserver = new ResizeObserver(() => recalcLines())
    resizeObserver.observe(el)
  }
})

// 完成态出现时刷新线颜色
watch(feedback, () => nextTick(recalcLines))

const currentQ = computed(() => questions.value[currentIndex.value])
const progressPercent = computed(() => {
  if (!questions.value.length) return 0
  return Math.round((currentIndex.value / questions.value.length) * 100)
})

onMounted(async () => {
  try {
    const data = await startWordTest({ mode: mode, source: source, count: count, learnRoundId })
    batchId.value = data.batchId
    questions.value = data.questions || []
    timeLimit.value = data.timeLimitSec || 30
    if (!questions.value.length) {
      ElMessage.warning('暂无可测验的单词，请先加入生词本')
      router.replace('/review')
      return
    }
    startTimer()
    focusInput()
  } catch (e) {
    ElMessage.error(e.message || '测验初始化失败')
    router.replace('/review')
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  stopTimer()
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
})

function startTimer() {
  stopTimer()
  timeLeft.value = timeLimit.value
  costStart.value = Date.now()
  timerId = setInterval(() => {
    timeLeft.value--
    if (timeLeft.value <= 0) {
      stopTimer()
      // 超时 = 答错（按当前模式默认规则）
      handleTimeout()
    }
  }, 1000)
}

function stopTimer() {
  if (timerId) {
    clearInterval(timerId)
    timerId = null
  }
}

function resetLocalInput() {
  spellingInput.value = ''
  sentenceInput.value = ''
  pickedCn.value = ''
  pickedEn.value = ''
  matchedCn.value = []
  matchedEn.value = []
  matchLines.value = []
  clearMatchRefs()
  feedback.value = null
}

// 判分与提交 ————————————————————————————————————
function judge(costMs) {
  const q = currentQ.value
  if (!q) return { correct: false, userAnswer: '' }
  if (mode === 'spelling') {
    const ans = spellingInput.value.trim().toLowerCase()
    const correct = ans === q.word.toLowerCase()
    return { correct, userAnswer: spellingInput.value.trim() }
  }
  if (mode === 'sentence') {
    const ans = sentenceInput.value.trim()
    const ref = (q.exampleCn || q.meaning || '').trim()
    // 关键词命中率：参考答案核心词至少 1 个出现在用户作答中（≥4 字长词）
    const keywords = ref
      .replace(/[，。！？、；：,.!?;:()"\s]/g, ' ')
      .split(' ')
      .filter((w) => w.length >= 2)
    const hit = keywords.length === 0 ? ans.length >= 2 : keywords.some((k) => ans.includes(k))
    return { correct: hit, userAnswer: ans }
  }
  // matching：matched 全对才算对（pairs 与 word 一一对应）
  const allRight = matchedCn.value.length === q.choicesEn.length &&
        matchedCn.value.every((cn, i) => cn === q.choicesCn[i])
  return { correct: allRight, userAnswer: matchedCn.value.join('|') }
}

async function submitCurrent() {
  if (feedback.value) return
  stopTimer()
  const costMs = Date.now() - costStart.value
  const j = judge(costMs)
  feedback.value = { ...j, costMs }
  try {
    await submitWordTestAnswer({
      batchId: batchId.value,
      wordId: currentQ.value.wordId,
      correct: j.correct ? 1 : 0,
      costMs: costMs,
      userAnswer: j.userAnswer
    })
  } catch (e) {
    /* 拦截器已提示，仍继续到下一题 */
  }
  // 短暂展示反馈，再进入下一题
  setTimeout(() => {
    next()
  }, j.correct ? 600 : 1400)
}

function next() {
  if (currentIndex.value + 1 >= questions.value.length) {
    onFinish()
    return
  }
  currentIndex.value++
  resetLocalInput()
  startTimer()
  focusInput()
}

function handleTimeout() {
  if (feedback.value) return
  // matching 超时：全部按错处理
  if (mode === 'matching') {
    matchedCn.value = [...(currentQ.value.choicesCn || [])]
    matchedEn.value = [...(currentQ.value.choicesEn || [])]
  }
  submitCurrent()
}

async function onFinish() {
  finished.value = true
  stopTimer()
  try {
    const r = await finishWordTest(batchId.value)
    result.value = r
    // 回填上「第几轮」：本轮完成后，从历史接口取最新一条
    try {
      const hist = await getWordTestHistory()
      if (hist && hist.length) currentRoundNo.value = hist[0].roundNo
    } catch (e) { /* 不影响结果页 */ }
  } catch (e) {
    /* 已提示 */
  }
}

// 巩固测验：选学习轮次 → 选测试模式 ————————————————————
async function openRoundPicker() {
  roundPickerVisible.value = true
  roundStep.value = 'round'
  selectedRound.value = null
  roundLoading.value = true
  try {
    roundList.value = await getLearnRoundHistory()
  } catch (e) {
    roundList.value = []
  } finally {
    roundLoading.value = false
  }
}

function pickRound(r) {
  selectedRound.value = r
  roundStep.value = 'mode'
}

/** 重测某一学习轮次：复用该轮词表开启新一轮测验 */
async function startRoundTest(testMode) {
  if (!selectedRound.value) return
  roundPickerVisible.value = false
  loading.value = true
  try {
    const data = await startWordTest({
      mode: testMode,
      learnRoundId: selectedRound.value.roundId,
      count: selectedRound.value.count
    })
    batchId.value = data.batchId
    questions.value = data.questions || []
    timeLimit.value = data.timeLimitSec || 30
    finished.value = false
    result.value = null
    currentIndex.value = 0
    currentRoundNo.value = null
    resetLocalInput()
    startTimer()
    focusInput()
  } catch (e) {
    ElMessage.error('开启巩固测验失败，请重试')
  } finally {
    loading.value = false
  }
}

// 模式入口 ————————————————————————————————————
function onSpellingSubmit() {
  if (!spellingInput.value.trim()) return
  submitCurrent()
}
function onSentenceSubmit() {
  if (!sentenceInput.value.trim()) return
  submitCurrent()
}
function onPickCn(cn) {
  if (matchedCn.value.includes(cn)) return
  pickedCn.value = cn
  tryMatch()
}
function onPickEn(en) {
  if (matchedEn.value.includes(en)) return
  pickedEn.value = en
  tryMatch()
}
function tryMatch() {
  if (!pickedCn.value || !pickedEn.value) return
  matchedCn.value.push(pickedCn.value)
  matchedEn.value.push(pickedEn.value)
  pickedCn.value = ''
  pickedEn.value = ''
  // 全部配对完自动判分
  const q = currentQ.value
  const target = Math.min((q.choicesEn || []).length, (q.choicesCn || []).length)
  if (matchedCn.value.length >= target) {
    submitCurrent()
  } else {
    nextTick(recalcLines)
  }
}
function matchCnClass(cn) {
  if (matchedCn.value.includes(cn)) return 'is-matched'
  if (pickedCn.value === cn) return 'is-picked'
  return ''
}
function matchEnClass(en) {
  if (matchedEn.value.includes(en)) return 'is-matched'
  if (pickedEn.value === en) return 'is-picked'
  return ''
}

function focusInput() {
  nextTick(() => {
    if (mode === 'spelling' && spellingInputRef) spellingInputRef.focus?.()
    if (mode === 'sentence' && sentenceInputRef) sentenceInputRef.focus?.()
  })
}

/**
 * 计算本轮 vs 上一轮的差异色（trending-up 用绿色、trending-down 用橙色）
 */
function diffClass(prev, curr) {
  if (curr > prev) return 'lr-up'
  if (curr < prev) return 'lr-down'
  return 'lr-flat'
}

/**
 * 保存上一轮 + 开启新一轮
 *
 * 1. 如果 onFinish 阶段 finish 接口异常（result 仍为空），
 *    这里再补一次 finish——后端 finish 幂等，安全可重入。
 * 2. 把当前 result 存档到 lastResult，让新一轮结果页能看到"上一轮 vs 本轮"。
 * 3. reset 状态 → startWordTest 开启新 batch。
 */
async function onAgain() {
  // 1. 兜底补调：确保上一轮被 finish
  if (batchId.value && !result.value) {
    try {
      result.value = await finishWordTest(batchId.value)
    } catch (e) {
      /* 已提示：即便失败，下方逻辑仍可继续开新一轮（数据由后端保证） */
    }
  }

  // 2. 存档上一轮结果（用于下一轮结果页对照）
  if (result.value) {
    lastResult.value = { ...result.value, _archivedAt: Date.now() }
  }

  // 3. 重置本地状态
  finished.value = false
  result.value = null
  currentIndex.value = 0
  resetLocalInput()

  // 4. 开启新一轮
  loading.value = true
  startWordTest({ mode: mode, source: source, count: count }).then((data) => {
    batchId.value = data.batchId
    questions.value = data.questions || []
    timeLimit.value = data.timeLimitSec || 30
    loading.value = false
    startTimer()
    focusInput()
  }).catch(() => {
    ElMessage.error('开启新一轮失败，请重试')
  })
}

async function onExit() {
  try {
    await ElMessageBox.confirm('退出后当前进度会丢失，确定退出吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '继续答题',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  // 退出时尽量 finish（不算统计）
  if (!finished.value && batchId.value) {
    finishWordTest(batchId.value).catch(() => {})
  }
  router.replace('/review')
}

function go(path) {
  router.push(path)
}
</script>

<style lang="scss" scoped>
@use '@/assets/scss/variables.scss' as *;
@use '@/assets/scss/mixins.scss' as *;

.word-test-page {
  padding-top: $sp-4;
  max-width: 760px;
  margin: 0 auto;
  min-height: 70vh;
}

/* ============ 顶部信息条 ============ */
.wt-header {
  display: flex;
  align-items: center;
  gap: $sp-4;
  padding: $sp-3 $sp-4;
  background: $bg-card;
  border: 1px solid $border-light;
  border-radius: $radius-card;
  margin-bottom: $sp-4;
}

.wt-back {
  @include btn-base;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: $radius-pill;
  background: $bg-soft;
  border: 1px solid $border-light;
  color: $text-caption;
  font-size: $fs-sm;
  &:hover { color: $color-primary; border-color: $primary-3; }
}

.wt-progress {
  flex: 1;
  display: flex;
  align-items: center;
  gap: $sp-3;
  .wp-track {
    flex: 1;
    height: 6px;
    background: $gray-3;
    border-radius: $radius-pill;
    overflow: hidden;
  }
  .wp-fill {
    height: 100%;
    background: $color-primary;
    transition: width $transition-normal;
  }
  .wp-text {
    font-size: $fs-sm;
    font-weight: 600;
    color: $text-title;
    flex-shrink: 0;
    min-width: 56px;
    text-align: right;
  }
}

.wt-timer {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 12px;
  background: $primary-1;
  color: $color-primary;
  border-radius: $radius-pill;
  font-size: $fs-sm;
  font-weight: 600;
  flex-shrink: 0;
  transition: all $transition-fast;
  &.warn { background: $color-warning-soft; color: #B26A00; }
  &.danger { background: $color-danger-soft; color: $color-danger; animation: shake 0.4s infinite; }
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-2px); }
  75% { transform: translateX(2px); }
}

/* ============ 题目主体 ============ */
.wt-body {
  display: flex;
  justify-content: center;
}
.mode-card {
  width: 100%;
  background: $bg-card;
  border: 1px solid $border-light;
  border-radius: $radius-card;
  padding: $sp-8 $sp-6;
  text-align: center;
}
.mode-tag {
  display: inline-block;
  font-size: $fs-xs;
  color: $color-primary;
  background: $primary-1;
  padding: 4px 12px;
  border-radius: $radius-pill;
  margin-bottom: $sp-5;
}

/* spelling */
.prompt-meaning {
  font-size: 32px;
  font-weight: 700;
  color: $text-title;
  letter-spacing: 0.02em;
  line-height: 1.3;
}
.prompt-pos {
  margin-top: 6px;
  font-size: $fs-sm;
  color: $text-caption;
}
.answer-row {
  margin-top: $sp-6;
  display: flex;
  gap: $sp-2;
  align-items: stretch;
  justify-content: center;
}
.spell-input, .sentence-input {
  font-size: 18px;
  padding: 12px 16px;
  border: 1.5px solid $border-base;
  border-radius: $radius-base;
  background: $bg-card;
  color: $text-title;
  outline: none;
  font-family: inherit;
  transition: border-color $transition-fast;
  &:focus { border-color: $color-primary; }
}
.spell-input {
  width: 320px;
  text-align: center;
  letter-spacing: 0.06em;
}
.sentence-input {
  width: 420px;
  resize: vertical;
  min-height: 60px;
  line-height: 1.6;
}
.submit-btn {
  padding: 0 24px;
  background: $color-primary;
  color: #fff;
  border: none;
  border-radius: $radius-base;
  font-size: $fs-md;
  font-weight: 600;
  cursor: pointer;
  transition: background $transition-fast;
  &:hover:not(:disabled) { background: $primary-6; }
  &:disabled { background: $gray-4; cursor: not-allowed; }
}

.feedback {
  margin-top: $sp-5;
  font-size: $fs-md;
  padding: 10px 18px;
  border-radius: $radius-base;
  display: inline-block;
  &.ok {
    background: $color-success-soft;
    color: $color-success;
    font-weight: 600;
  }
  &.bad {
    background: $color-warning-soft;
    color: #B26A00;
    b { color: $color-primary; }
  }
}

/* matching */
.match-grid {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $sp-4;
  margin-top: $sp-4;
}
.match-lines {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
  overflow: visible;
}
.match-line {
  fill: none;
  stroke-width: 2;
  stroke-linecap: round;
  transition: stroke 0.25s ease;
  &.match-line-pending {
    stroke: $color-primary;
    stroke-width: 2;
    opacity: 0.85;
  }
  &.match-line-correct {
    stroke: $color-success;
    stroke-width: 2.5;
  }
  &.match-line-wrong {
    stroke: $color-danger;
    stroke-width: 2.5;
  }
}
.match-col { display: flex; flex-direction: column; gap: $sp-2; }
.match-btn {
  padding: 12px 14px;
  border: 1.5px solid $border-base;
  border-radius: $radius-base;
  background: $bg-card;
  color: $text-body;
  font-size: $fs-base;
  font-weight: 500;
  cursor: pointer;
  text-align: left;
  transition: all $transition-fast;
  &:hover:not(:disabled) { border-color: $primary-3; color: $color-primary; }
  &.is-picked {
    border-color: $color-primary;
    background: $primary-1;
    color: $color-primary;
    font-weight: 600;
  }
  &.is-matched {
    opacity: 0.45;
    background: $bg-soft;
    cursor: default;
  }
  &.match-btn-en { font-family: 'Inter', sans-serif; letter-spacing: 0.02em; }
}
.match-progress {
  margin-top: $sp-4;
  font-size: $fs-sm;
  color: $text-caption;
}

/* sentence */
.prompt-en {
  font-size: 22px;
  font-weight: 600;
  color: $text-title;
  line-height: 1.6;
  font-style: italic;
  font-family: 'Georgia', serif;
}
.prompt-phonetic {
  margin-top: 6px;
  font-size: $fs-sm;
  color: $text-caption;
}

/* ============ 完成态：结果页 ============ */
.wt-result { display: flex; justify-content: center; }

/* 上一轮对照条（点过"再来一轮"后展示） */
.last-round {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  background: $bg-soft;
  border: 1px dashed $border-light;
  border-radius: $radius-pill;
  margin-bottom: $sp-4;
  font-size: $fs-sm;
  color: $text-caption;

  .lr-tag {
    font-weight: 600;
    color: $text-body;
  }
  b { color: $text-title; font-weight: 600; }
  .lr-up { color: $color-success; }
  .lr-down { color: $color-warning; }
  .lr-flat { color: $text-caption; }
}
.result-card {
  width: 100%;
  padding: $sp-8 $sp-6;
  text-align: center;
}
.result-icon {
  width: 64px; height: 64px;
  border-radius: 50%;
  margin: 0 auto $sp-3;
  @include flex-center;
  &.good { background: $color-success-soft; color: $color-success; }
  &.mid { background: $primary-1; color: $color-primary; }
}
.result-title { font-size: $fs-2xl; font-weight: 700; color: $text-title; }
.result-sub { margin-top: $sp-2; font-size: $fs-md; color: $text-caption;
  b { color: $color-primary; font-weight: 700; }
}
.result-actions { margin-top: $sp-5; display: flex; gap: $sp-2; justify-content: center; flex-wrap: wrap; }
.result-round { margin-top: $sp-2; }
.round-badge {
  display: inline-block;
  padding: 4px 14px;
  background: $primary-1;
  color: $color-primary;
  border-radius: $radius-pill;
  font-size: $fs-sm;
  font-weight: 600;
}

/* ============ 巩固测验：历史轮次 ============ */
.hist-tip { font-size: $fs-sm; color: $text-caption; margin-bottom: $sp-3; line-height: 1.6; }
.hist-list { display: flex; flex-direction: column; gap: $sp-2; }
.hist-item {
  display: flex;
  align-items: center;
  gap: $sp-3;
  padding: 12px 14px;
  border: 1px solid $border-light;
  border-radius: $radius-card;
  cursor: pointer;
  transition: all .15s ease;
  &:hover { border-color: $color-primary; background: $primary-1; }
  .hist-round {
    font-weight: 700;
    color: $color-primary;
    min-width: 64px;
  }
  .hist-meta { flex: 1; color: $text-body; font-size: $fs-sm; }
  .hist-acc { font-weight: 700;
    &.ok { color: $color-success; }
    &.low { color: $color-warning; }
  }
  .hist-time { color: $text-disabled; font-size: $fs-xs; }
  .hist-arrow { color: $text-caption; font-size: 18px; }
}
.detail-head {
  display: flex; align-items: center; gap: $sp-3;
  margin-bottom: $sp-4;
  .hist-meta { color: $text-body; font-size: $fs-sm; }
}
.detail-list { display: flex; flex-direction: column; gap: $sp-2; max-height: 50vh; overflow-y: auto; }
.detail-item {
  display: flex; gap: $sp-3;
  padding: 10px 12px;
  border: 1px solid $border-light;
  border-radius: $radius-card;
  .di-mark { font-weight: 700; }
  &.is-right .di-mark { color: $color-success; }
  &.is-wrong .di-mark { color: $color-danger; }
  .di-word { font-weight: 600; color: $text-title; }
  .di-phonetic { margin-left: 8px; font-size: $fs-sm; color: $text-caption; font-weight: 400; }
  .di-meaning { font-size: $fs-sm; color: $text-body; margin-top: 2px; }
  .di-answer { font-size: $fs-xs; color: $text-caption; margin-top: 4px; }
  .ok-text { color: $color-success; }
  .bad-text { color: $color-danger; text-decoration: line-through; }
}
.detail-actions { margin-top: $sp-4; display: flex; gap: $sp-2; justify-content: flex-end; }

.wrong-block {
  margin-top: $sp-6;
  text-align: left;
}
.wrong-title {
  display: flex; align-items: center; gap: 6px;
  font-size: $fs-md; font-weight: 600;
  color: $text-title;
  margin-bottom: $sp-3;
  padding-bottom: $sp-2;
  border-bottom: 1px dashed $border-light;
}
.wrong-list { display: flex; flex-direction: column; gap: 10px; }
.wrong-item {
  padding: $sp-3 $sp-4;
  border: 1px solid $border-light;
  border-radius: $radius-base;
  background: $bg-soft;
  .wi-head { display: flex; align-items: baseline; gap: $sp-2; }
  .wi-word { font-size: $fs-lg; font-weight: 700; color: $color-primary; }
  .wi-phonetic { font-size: $fs-sm; color: $text-caption; }
  .wi-meaning { margin-top: 4px; font-size: $fs-base; color: $text-body; }
  .wi-user { margin-top: 6px; font-size: $fs-sm; color: $text-caption;
    .user-answer-text { color: $color-danger; text-decoration: line-through; }
  }
  .wi-next { margin-top: 4px; font-size: 11px; color: $text-disabled; }
}

.wt-loading {
  display: flex; justify-content: center; padding: $sp-10 0;
}

@media (max-width: 700px) {
  .match-grid { grid-template-columns: 1fr; }
  .spell-input, .sentence-input { width: 100%; }
  .answer-row { flex-direction: column; }
  .submit-btn { padding: 12px 24px; }
}

/* ============ 移动端窄屏适配（≤768px） ============ */
@media (max-width: 768px) {
  /* 页面容器 padding 收紧为 12px */
  .word-test-page { padding: $sp-3 $sp-3 $sp-10; }

  /* 顶部信息条收紧 */
  .wt-header { gap: $sp-3; padding: $sp-2 $sp-3; }

  /* 题目卡片 padding 收紧 */
  .mode-card { padding: $sp-6 $sp-3; }

  /* 题目文字字号适当提升，保证 375px 下可读性 */
  .mode-tag { font-size: $fs-sm; }
  .match-btn { font-size: $fs-lg; }

  /* 输入框撑满且 ≥16px（避免 iOS 聚焦缩放），不溢出容器 */
  .spell-input,
  .sentence-input {
    width: 100%;
    max-width: 100%;
    font-size: 16px;
  }

  /* matching：两列布局保持，但按钮触控区最小 48px，长词安全换行 */
  .match-grid { gap: $sp-3; }
  .match-btn {
    display: flex;
    align-items: center;
    min-height: 48px;
    word-break: break-word;
  }

  /* 结果页收紧；4 个操作按钮排成 2×2 网格 */
  .result-card { padding: $sp-6 $sp-3; }
  .result-actions {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: $sp-2;
    .el-button {
      width: 100%;
      min-height: 44px;
      margin-left: 0;
    }
  }

  /* 长单词/长句换行兜底 */
  .prompt-meaning,
  .prompt-en,
  .feedback,
  .wi-word,
  .wi-meaning,
  .wi-user { word-break: break-word; }
  .wrong-item { padding: $sp-3; }
}
</style>