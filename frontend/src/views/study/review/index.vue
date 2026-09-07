<template>
  <div class="page-container review-page" :class="{ 'is-immersive': immersive }">
    <!-- 页头（沉浸模式下隐藏） -->
    <div v-if="!immersive" class="page-header">
      <div>
        <h1 class="page-title">背单词复习</h1>
        <p class="page-desc">艾宾浩斯遗忘曲线安排复习 · 今日到期的单词会优先出现</p>
      </div>
    </div>

    <!-- 左右分栏 -->
    <div class="review-grid">
      <!-- 左列：侧边概览 -->
      <aside class="col-left">
        <!-- 今日进度卡 -->
        <div class="ws-card side-card">
          <div class="side-head">
            <span class="side-title">今日学习</span>
            <span class="side-tag">{{ todayWords }} / {{ dailyGoal }}</span>
          </div>
          <div class="daily-progress">
            <div class="dp-track">
              <div class="dp-fill" :style="{ width: dailyPercent + '%' }"></div>
            </div>
            <div class="dp-foot">
              <span>{{ dailyStatus }}</span>
              <span class="dp-pct">{{ dailyPercent }}%</span>
            </div>
          </div>
        </div>

        <!-- 学习统计卡 -->
        <div class="ws-card side-card">
          <div class="side-head">
            <span class="side-title">学习统计</span>
          </div>
          <div class="stat-list">
            <div class="stat-row">
              <span class="stat-row-label">累计掌握</span>
              <span class="stat-row-num">{{ totalWords }}</span>
            </div>
            <div class="stat-row">
              <span class="stat-row-label">今日待复习</span>
              <span class="stat-row-num">{{ dueCount }}</span>
            </div>
            <div class="stat-row">
              <span class="stat-row-label">本轮掌握率</span>
              <span class="stat-row-num">{{ roundPercent }}%</span>
            </div>
          </div>
        </div>

        <!-- 快捷入口卡 -->
        <div class="ws-card side-card">
          <div class="side-head">
            <span class="side-title">快捷入口</span>
          </div>
          <div class="quick-list">
            <!--
              巩固测验快捷入口：与"复习"是并列的活动，不应被本轮复习进度阻塞。
              原实现 `disabled="!finished"` 强制要求"本轮复习完成"才能进入测验，
              实际用户场景中常常已经完成今日目标（48/20）但本轮复习刚翻到第 1 张卡，
              此时按钮就按不下去，UX 阻塞。
              改为：今日目标达成（todayWords >= dailyGoal）即启用；否则禁用并提示。
            -->
            <button class="quick-row" @click="openTestDialog" :disabled="todayWords < dailyGoal">
              <span class="quick-icon"><AppIcon name="check-circle" :size="16" /></span>
              <span class="quick-main">
                <span class="quick-name">巩固测验</span>
                <span class="quick-desc">学完一轮后触发</span>
              </span>
              <span class="quick-arrow">›</span>
            </button>
            <button class="quick-row" @click="go('/word-book')">
              <span class="quick-icon"><AppIcon name="book-open" :size="16" /></span>
              <span class="quick-main">
                <span class="quick-name">生词本</span>
                <span class="quick-desc">管理你学过的词</span>
              </span>
              <span class="quick-arrow">›</span>
            </button>
            <button class="quick-row" @click="go('/ai-assistant')">
              <span class="quick-icon"><AppIcon name="sparkles" :size="16" /></span>
              <span class="quick-main">
                <span class="quick-name">词灵 AI</span>
                <span class="quick-desc">向 AI 提问巩固</span>
              </span>
              <span class="quick-arrow">›</span>
            </button>
          </div>
        </div>
      </aside>

      <!-- 右列：主学习区 -->
      <div class="col-right">
        <!-- 工具条：模式 / 等级 / 数量 -->
        <div class="toolbar">
          <div class="tb-group">
            <span class="tb-label">模式</span>
            <div class="tb-tabs">
              <button
                v-for="opt in modeOptions"
                :key="opt.value"
                class="tb-tab"
                :class="{ active: mode === opt.value }"
                @click="setMode(opt.value)"
              >{{ opt.label }}</button>
            </div>
          </div>
          <div v-if="mode === 'new'" class="tb-group">
            <span class="tb-sep"></span>
            <span class="tb-label">等级</span>
            <div class="tb-tabs">
              <el-tooltip
                v-for="opt in levelOptions"
                :key="opt.value"
                :content="opt.tip"
                :disabled="!opt.tip"
                :show-after="120"
                placement="bottom"
                effect="dark"
              >
                <button
                  class="tb-tab"
                  :class="{ active: level === opt.value }"
                  @click="setLevel(opt.value)"
                >{{ opt.label }}</button>
              </el-tooltip>
            </div>
          </div>
          <div class="tb-group">
            <span class="tb-sep"></span>
            <span class="tb-label">数量</span>
            <el-select v-model="batchSize" size="default" class="tb-select" @change="onBatchSizeChange">
              <el-option v-for="opt in batchSizeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </div>
          <div v-if="!immersive" class="tb-group tb-immersive">
            <button class="immersive-btn" @click="onEnterImmersive">
              <AppIcon name="maximize" :size="14" />
              <span>沉浸模式</span>
            </button>
          </div>
        </div>

        <!-- 卡片主体 -->
        <div class="ws-card review-card">
          <!-- 加载态 -->
          <div v-if="loading" class="state-skel">
            <el-skeleton animated style="width: 100%">
              <template #template>
                <el-skeleton-item variant="h1" style="width: 40%; margin: 24px auto" />
                <el-skeleton-item variant="text" style="width: 60%; margin: 12px auto" />
                <el-skeleton-item variant="button" style="width: 200px; margin: 24px auto" />
              </template>
            </el-skeleton>
          </div>

          <!-- 空状态 -->
          <div v-else-if="!cards.length" class="state-empty">
            <span class="se-icon"><AppIcon :name="mode === 'due' ? 'party-popper' : 'book-open'" :size="16" /></span>
            <span class="se-text">{{ mode === 'due' ? '今日没有待复习的单词，很棒！' : levelEmptyText }}</span>
            <button v-if="mode === 'due'" class="se-link" @click="switchToNew">去学新词 →</button>
            <button v-else class="se-link" @click="go('/word-book')">查看生词本 →</button>
          </div>

          <!-- 完成态 -->
          <div v-else-if="finished" class="state-finish">
            <div class="sf-row">
              <span class="sf-check"><AppIcon name="check" :size="14" /></span>
              <span class="sf-title">{{ mode === 'due' ? '今日复习完成' : '本轮学习完成' }}</span>
              <span class="sf-sub">
                <template v-if="mode === 'new' && currentRoundNo">第 {{ currentRoundNo }} 轮 · </template>
                共学习 {{ cards.length }} 个单词，认识 {{ familiarCount }} 个
              </span>
            </div>
            <div class="sf-actions">
              <button class="se-link" @click="openCurrentRoundTest">巩固测验 →</button>
              <button class="se-link" :class="{ 'is-loading': restarting }" :disabled="restarting" @click="restart">再来一轮 →</button>
              <button class="se-link" @click="go('/word-book')">查看生词本 →</button>
            </div>
          </div>

          <!-- 单词卡 -->
          <template v-else>
            <!-- 进度 -->
            <div class="card-progress">
              <div class="progress-meta">
                <span class="progress-count">{{ currentIndex + 1 }}<span class="progress-divider"> / </span>{{ cards.length }}</span>
                <span class="progress-tag">剩余 {{ cards.length - currentIndex - 1 }} 个</span>
              </div>
              <div class="progress-track">
                <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
              </div>
            </div>

            <!-- 新词提示（今日复习模式保留等高占位，避免切换时卡片高度跳动） -->
            <p class="review-hint">{{ mode === 'new' ? '不记得 / 模糊会加入生词本；认识会标记为已掌握并跳过' : '\u00A0' }}</p>

            <!-- 卡片 -->
            <transition :name="flipDir" mode="out-in">
              <div class="flash-card" :key="currentIndex" @click="reveal">
                <div class="fc-word" :class="{ 'fc-word-boost': currentCard.boosted }">{{ currentCard.word }}</div>
                <div v-if="currentCard.boosted" class="fc-boost-tag">曾认识 · 加深印象</div>
                <div class="fc-phonetic">{{ currentCard.phonetic || '—' }}</div>

                <div class="fc-detail">
                  <div class="fc-divider"></div>

                  <template v-if="revealed">
                    <div class="fc-meaning">{{ currentCard.meaning }}</div>
                    <div v-if="currentCard.example" class="fc-example">
                      <p class="fc-example-en">{{ currentCard.example }}</p>
                      <p v-if="currentCard.exampleCn" class="fc-example-cn">{{ currentCard.exampleCn }}</p>
                    </div>
                    <div v-else class="fc-example fc-example-empty">暂无例句</div>
                  </template>
                  <template v-else>
                    <div class="fc-placeholder">点击卡片或按任意键查看释义</div>
                  </template>
                </div>
              </div>
            </transition>

            <!-- 操作 -->
            <div class="card-actions">
              <template v-if="!revealed">
                <button class="act-btn act-btn-primary" @click="reveal">显示释义</button>
              </template>
              <template v-else>
                <button class="act-btn act-btn-danger" @click="answer('forget')">
                  <span class="act-label">不记得</span>
                  <span class="act-key">1</span>
                </button>
                <button class="act-btn act-btn-warning" @click="answer('vague')">
                  <span class="act-label">模糊</span>
                  <span class="act-key">2</span>
                </button>
                <button class="act-btn act-btn-success" @click="answer('know')">
                  <span class="act-label">认识</span>
                  <span class="act-key">3</span>
                </button>
              </template>
            </div>
            <transition name="hint-fade">
              <div v-if="hintVisible" class="card-hint">
                <AppIcon name="check-circle-2" :size="14" />
                <span>{{ hintText }}</span>
              </div>
            </transition>
          </template>
        </div>
      </div>
    </div>

    <!-- 沉浸模式：右上角悬浮退出按钮 -->
    <transition name="fade">
      <div v-if="immersive" class="immersive-exit" @click="exitImmersive">
        <AppIcon name="minimize" :size="15" />
        <span>退出沉浸</span>
        <span class="ie-key">ESC</span>
      </div>
    </transition>

    <!-- 巩固测验弹窗：先选学习轮次，再选测试模式 -->
    <el-dialog
      v-model="testDialogVisible"
      title="巩固测验"
      width="480px"
      :close-on-click-modal="false"
      align-center
    >
      <!-- 第 1 步：选择学习轮次 -->
      <div v-if="testStep === 'round'" v-loading="roundLoading">
        <p class="td-desc">选择要测验的学习轮次（按"学习新词"的那一轮）</p>
        <el-empty v-if="!roundLoading && !roundList.length" description="还没有学习轮次，先去学一轮新词吧" />
        <div v-else class="td-list">
          <div
            v-for="r in roundList"
            :key="r.roundId"
            class="td-row-wrap"
          >
            <button
              class="td-row"
              @click="pickRound(r)"
            >
              <span class="td-row-icon"><AppIcon name="layers" :size="16" /></span>
              <span class="td-row-main">
                <span class="td-row-name">第 {{ r.roundNo }} 轮 · {{ r.count }} 词 · 掌握 {{ r.masteredCount }}</span>
                <span class="td-row-desc">{{ formatTime(r.finishedAt) }}</span>
              </span>
              <span class="td-row-arrow">›</span>
            </button>
            <button
              class="td-row-del"
              title="删除这一轮"
              @click.stop="deleteRound(r)"
            >
              <AppIcon name="trash-2" :size="14" />
            </button>
          </div>
        </div>
      </div>

      <!-- 第 2 步：选择测试模式 -->
      <div v-else>
        <p class="td-desc">第 {{ selectedRound?.roundNo }} 轮 · 选择测验方式</p>
        <div class="td-list">
          <button
            v-for="m in testModes"
            :key="m.value"
            class="td-row"
            @click="startTest(m.value)"
          >
            <span class="td-row-icon"><AppIcon :name="m.icon" :size="16" /></span>
            <span class="td-row-main">
              <span class="td-row-name">{{ m.label }}</span>
              <span class="td-row-desc">{{ m.desc }}</span>
            </span>
            <span class="td-row-arrow">›</span>
          </button>
        </div>
        <div class="td-foot">
          <el-button text @click="testStep = 'round'">‹ 返回轮次</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { InfoFilled } from '@element-plus/icons-vue'
import { dueReview, reviewWord, addWord, markMastered } from '@/api/wordBook'
import { dictRandom, recordStudy, saveReviewBatchSize, getInfo } from '@/api/user'
import { finishLearnRound, getLearnRoundHistory, deleteLearnRound } from '@/api/learnRound'
import { useUserStore } from '@/store/user'
import { useImmersive } from '@/composables/useImmersive'
import AppIcon from '@/components/common/AppIcon.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 沉浸模式
const { immersive, enter: enterImmersive, exit: exitImmersive } = useImmersive()

async function onEnterImmersive() {
  const fullscreenOk = await enterImmersive()
  // 成功全屏时交给浏览器原生「按 Esc 退出全屏」横幅提示；
  // 全屏被拒绝（仅页面内沉浸）时才弹我们自己的提示，告知退出方式
  if (!fullscreenOk) {
    ElMessage.info({
      message: '已进入沉浸模式：按 ESC 或点击右上角「退出沉浸」即可退出',
      duration: 3000,
      placement: 'bottom'
    })
  }
}

// 支持 ?mode=new 入口（首页"开始单词学习"直达新词学习）
const mode = ref(route.query.mode === 'new' ? 'new' : 'due')
const modeOptions = [
  { label: '今日复习', value: 'due' },
  { label: '新词学习', value: 'new' }
]

// 四六级/中高考筛选（仅新词学习模式生效）
const levelOptions = [
  { label: '四级', value: 'cet4' },
  { label: '六级', value: 'cet6' },
  { label: '高考', value: 'gaokao' },
  { label: '中考', value: 'zhongkao' },
  { label: '混合', value: 'mixed', tip: '四六级词汇混合' }
]
const level = ref('cet4')

const batchSizeOptions = [
  { label: '10个', value: 10 },
  { label: '20个', value: 20 },
  { label: '30个', value: 30 },
  { label: '50个', value: 50 }
]
const batchSize = ref(10)

function initBatchSize() {
  const server = userStore.userInfo?.reviewBatchSize
  if (server != null && server > 0) {
    batchSize.value = server
  } else {
    const local = Number(localStorage.getItem('ws_review_batch'))
    if (local > 0) batchSize.value = local
  }
}

function initLevel() {
  const local = localStorage.getItem('ws_review_level')
  if (local && ['cet4', 'cet6', 'gaokao', 'zhongkao', 'mixed'].includes(local)) {
    level.value = local
  }
}

const loading = ref(false)
const cards = ref([])
const currentIndex = ref(0)
const revealed = ref(false)
const familiarCount = ref(0)
const finished = ref(false)
const flipDir = ref('flip-left')
const dueCount = ref(0)
/** 本轮学习在 learn_round 里的第几轮（完成态展示用） */
const currentRoundNo = ref(null)
/** 每卡是否标记掌握，用于持久化学习轮次 */
const masteredFlags = ref([])

const currentCard = computed(() => cards.value[currentIndex.value])

const levelEmptyText = computed(() => {
  switch (level.value) {
    case 'cet4': return '四级词库已学完，继续保持！'
    case 'cet6': return '六级词库已学完，继续保持！'
    case 'gaokao': return '高考词库已学完，继续保持！'
    case 'zhongkao': return '中考词库已学完，继续保持！'
    case 'mixed': return '四六级词库已学完，继续保持！'
    default:     return '没有更多新词啦'
  }
})

const progressPercent = computed(() => {
  if (!cards.value.length) return 0
  return Math.round(((currentIndex.value + 1) / cards.value.length) * 100)
})

// 侧栏数据（来自 UserVo）
const todayWords = computed(() => userStore.userInfo?.todayWords ?? 0)
const totalWords = computed(() => userStore.userInfo?.masteredWords ?? userStore.userInfo?.totalWords ?? 0)
const dailyGoal  = computed(() => userStore.userInfo?.dailyGoal ?? 20)

const dailyPercent = computed(() => {
  if (!dailyGoal.value) return 0
  return Math.min(100, Math.round((todayWords.value / dailyGoal.value) * 100))
})
const dailyStatus = computed(() => {
  if (todayWords.value >= dailyGoal.value) return '今日目标已达成'
  return `还差 ${Math.max(0, dailyGoal.value - todayWords.value)} 个`
})
const roundPercent = computed(() => {
  const done = currentIndex.value + (finished.value ? 1 : 0)
  if (done <= 0) return 0
  return Math.round((familiarCount.value / done) * 100)
})

onMounted(async () => {
  initBatchSize()
  initLevel()
  // 拉一次最新 userInfo：拿到新字段 masteredWords（累计掌握·去重）
  try { userStore.userInfo = await getInfo() } catch (e) {}
  await loadDueCount()
  // 优先恢复上次的学习进度（F5 刷新 / 误触导航不会丢）
  const restored = restoreState()
  if (!restored) {
    await loadCards()
  }
  window.addEventListener('keydown', onKeyDown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeyDown)
})

// 键盘快捷键：未揭示时任意键=查看释义；揭示后 1=不记得 2=模糊 3=认识
function onKeyDown(e) {
  // 沉浸模式下 ESC 用于退出沉浸，不触发翻卡
  if (immersive.value && e.key === 'Escape') return
  // 输入框聚焦时不拦截，避免破坏搜索/输入
  const tag = (e.target?.tagName || '').toUpperCase()
  if (tag === 'INPUT' || tag === 'TEXTAREA' || e.target?.isContentEditable) return
  // 带修饰键不拦截（保留浏览器/系统快捷键）
  if (e.ctrlKey || e.metaKey || e.altKey) return
  // 已完成或无卡片时不响应
  if (finished.value || !cards.value.length) return

  // 未揭示：任意键 = 显示释义（preventDefault 防止 Space/F2 等触发页面动作）
  if (!revealed.value) {
    e.preventDefault()
    reveal()
    return
  }

  // 已揭示：1=不记得 2=模糊 3=认识
  const k = e.key
  if (k === '1') {
    e.preventDefault()
    answer('forget')
  } else if (k === '2') {
    e.preventDefault()
    answer('vague')
  } else if (k === '3') {
    e.preventDefault()
    answer('know')
  }
}

function go(path) {
  router.push(path)
}

async function loadDueCount() {
  try {
    const list = await dueReview(100)
    dueCount.value = list.length
  } catch (e) {
    dueCount.value = 0
  }
}

async function loadCards() {
  loading.value = true
  finished.value = false
  familiarCount.value = 0
  currentIndex.value = 0
  revealed.value = false
  currentRoundNo.value = null
  masteredFlags.value = []
  // 切场景时清掉旧进度，避免恢复出"旧卡 + 新卡"混在一起的脏状态
  clearState()
  // 记录上一轮的词集合，新词学习时保证视觉上完全不重叠（即使后端返回少量重叠）
  const prevWords = new Set(cards.value.map(c => c.word))
  try {
    if (mode.value === 'due') {
      cards.value = await dueReview(batchSize.value)
    } else {
      // 新词学习：排除已学过的词，且每次真随机 —— "再来一轮换新词"
      let fresh = await dictRandom(batchSize.value, level.value, true)
      // 前端兜底：去重本会话内已加载过的词，最多 3 次
      for (let i = 0; i < 3 && prevWords.size > 0; i++) {
        const overlap = fresh.filter(c => prevWords.has(c.word))
        if (overlap.length === 0) break
        const more = await dictRandom(batchSize.value + overlap.length, level.value, true)
        const seen = new Set(prevWords)
        const merged = []
        for (const c of [...more, ...fresh]) {
          if (!seen.has(c.word)) {
            merged.push(c)
            seen.add(c.word)
            if (merged.length >= batchSize.value) break
          }
        }
        fresh = merged
        if (fresh.length >= batchSize.value) break
      }
      cards.value = fresh
    }
    persistState()
  } catch (e) {
    cards.value = []
    ElMessage.error('加载单词失败，请稍后再试')
  } finally {
    loading.value = false
  }
}

function onModeChange() {
  loadCards()
}

function onLevelChange() {
  localStorage.setItem('ws_review_level', level.value)
  loadCards()
}

function onBatchSizeChange() {
  localStorage.setItem('ws_review_batch', String(batchSize.value))
  if (userStore.isLogin) {
    saveReviewBatchSize(batchSize.value).catch(() => {})
  }
  loadCards()
}

function setMode(v) {
  if (mode.value === v) return
  mode.value = v
  onModeChange()
}
function setLevel(v) {
  if (level.value === v) return
  level.value = v
  onLevelChange()
}
function setBatchSize(v) {
  if (batchSize.value === v) return
  batchSize.value = v
  onBatchSizeChange()
}

function switchToNew() {
  mode.value = 'new'
  loadCards()
}

function reveal() {
  if (revealed.value) return
  revealed.value = true
  // 学习动作
  recordStudy().catch(() => {})
}

/**
 * 答题入口锁：避免键盘长按 / 快速连点导致同一张卡被多次计入，
 * 进而触发 next() 并发执行、saveCurrentRound() 重复写 learn_round 记录。
 */
const answering = ref(false)

async function answer(level) {
  // level: 'know' 认识 / 'vague' 模糊 / 'forget' 不记得
  if (answering.value || finished.value || !cards.value.length) return
  answering.value = true
  try {
    await doAnswer(level)
  } finally {
    answering.value = false
  }
}

async function doAnswer(level) {
  const card = currentCard.value
  const familiar = level === 'know'
  if (familiar) familiarCount.value++
  masteredFlags.value[currentIndex.value] = familiar

  try {
    // 优先：当前卡片来自"艾宾浩斯到期复习词"（bookId 不为空），走 reviewWord 走 due 流程
    if (card.bookId) {
      await reviewWord(card.bookId, familiar)
      showHint(familiar ? '已记录 · 复习进度已更新' : '已记录 · 下次会再复习')
    } else if (mode.value === 'due') {
      // 复习模式：记录熟悉度，更新艾宾浩斯计划
      await reviewWord(card.id, familiar)
      showHint(familiar ? '已记录 · 复习进度已更新' : '已记录 · 下次会再复习')
    } else {
      // 新词模式：
      // - 认识：不加生词本，写入 mastered_words 集合 → 累计掌握 +1
      // - 不记得 / 模糊：加入生词本（source 区分 → 后端按艾宾浩斯早期复习曲线安排）
      if (familiar) {
        await markMastered(card.word)
        showHint(`已标记「${card.word}」为认识`)
      } else {
        const source = level === 'forget' ? 'new_forget' : 'new_vague'
        await addWord({ word: card.word, meaning: card.meaning || '', phonetic: card.phonetic || '', source })
        showHint(level === 'forget'
          ? `「${card.word}」不记得 · 5 分钟后复习`
          : `「${card.word}」模糊 · 30 分钟后复习`)
      }
    }
  } catch (e) {
    ElMessage.error(e.message || '操作失败，请稍后再试')
    return
  }

  await next()
  // 实时刷新侧栏（累计掌握 / 今日学习数）
  try { userStore.userInfo = await getInfo() } catch (e) {}
}

async function next() {
  flipDir.value = 'flip-left'
  if (currentIndex.value + 1 >= cards.value.length) {
    // 最后一卡：先 await 持久化本轮 → 再切到完成页
    // 否则用户立刻点"再来一轮"时 learn_round_item 还没写入，
    // randomUnlearned 排除不到本轮词，会再次抽到相同词
    await saveCurrentRound()
    finished.value = true
  } else {
    currentIndex.value++
    revealed.value = false
  }
  persistState()
}

// 卡片内联轻提示（不弹全局消息，避免打断心流）
const hintVisible = ref(false)
const hintText = ref('')
let hintTimer = null
function showHint(text) {
  hintText.value = text
  hintVisible.value = true
  if (hintTimer) clearTimeout(hintTimer)
  hintTimer = setTimeout(() => {
    hintVisible.value = false
  }, 1200)
}

/** 完成一轮学习后，持久化本轮（含每个词 + 是否掌握），用于"巩固测验"选轮次 */
// ---------- 学习进度持久化（F5 / 路由跳转回 review 时恢复） ----------
const STATE_KEY = 'ws_review_state'
const STATE_TTL_MS = 24 * 3600 * 1000 // 超过 24h 视为过期，丢弃

function persistState() {
  try {
    if (!cards.value.length) return
    localStorage.setItem(STATE_KEY, JSON.stringify({
      mode: mode.value,
      level: level.value,
      batchSize: batchSize.value,
      cards: cards.value,
      idx: currentIndex.value,
      flags: masteredFlags.value,
      familiarCount: familiarCount.value,
      finished: finished.value,
      roundNo: currentRoundNo.value,
      ts: Date.now()
    }))
  } catch (e) {
    /* localStorage 可能满了（cards 数组大），忽略 */
  }
}

function restoreState() {
  try {
    const raw = localStorage.getItem(STATE_KEY)
    if (!raw) return false
    const s = JSON.parse(raw)
    if (!s.ts || Date.now() - s.ts > STATE_TTL_MS) {
      localStorage.removeItem(STATE_KEY)
      return false
    }
    if (!Array.isArray(s.cards) || !s.cards.length) return false
    mode.value = s.mode
    level.value = s.level
    batchSize.value = s.batchSize
    cards.value = s.cards
    currentIndex.value = Math.min(s.idx || 0, s.cards.length - 1)
    masteredFlags.value = Array.isArray(s.flags) ? s.flags : []
    familiarCount.value = s.familiarCount || 0
    finished.value = !!s.finished
    currentRoundNo.value = s.roundNo || null
    revealed.value = false
    return true
  } catch (e) {
    return false
  }
}

function clearState() {
  try { localStorage.removeItem(STATE_KEY) } catch (e) {}
}

async function saveCurrentRound() {
  if (mode.value !== 'new' || !cards.value.length) return
  try {
    const words = cards.value.map((c, i) => ({
      word: c.word,
      wordId: c.id,
      phonetic: c.phonetic,
      meaning: c.meaning,
      isMastered: masteredFlags.value[i] ? 1 : 0
    }))
    const r = await finishLearnRound({
      source: mode.value,
      // 词库独立去重：新词轮次记录所选词库档位（due 复习轮不记档位）
      ...(mode.value === 'new' ? { level: level.value } : {}),
      count: cards.value.length,
      masteredCount: familiarCount.value,
      words
    })
    currentRoundNo.value = r.roundNo
  } catch (e) {
    /* 不影响完成页展示 */
  }
}

/**
 * "再来一轮"入口锁：避免用户快速连点两次按钮，
 * 在 loading.value 更新前两次触发 loadCards()，导致同一会话开两个 batch。
 */
const restarting = ref(false)
async function restart() {
  if (restarting.value) return
  restarting.value = true
  try {
    await loadCards()
  } finally {
    restarting.value = false
  }
}

// 巩固测验弹窗：先选学习轮次 → 再选测试模式
const testDialogVisible = ref(false)
const testStep = ref('round')      // round | mode
const roundLoading = ref(false)
const roundList = ref([])
const selectedRound = ref(null)
const testModes = [
  { value: 'spelling',  label: '看中文拼英文', desc: '给出中文，输入英文单词拼写', icon: 'keyboard' },
  { value: 'matching',  label: '中英文连线',   desc: '点击配对中文释义与英文单词',  icon: 'link-2' },
  { value: 'sentence',  label: '翻译句子',     desc: '翻译例句，巩固语境理解',      icon: 'languages' }
]
function openTestDialog() {
  testDialogVisible.value = true
  testStep.value = 'round'
  selectedRound.value = null
  roundLoading.value = true
  getLearnRoundHistory().then(list => { roundList.value = list || [] })
    .catch(() => { roundList.value = [] })
    .finally(() => { roundLoading.value = false })
}

async function deleteRound(r) {
  try {
    await ElMessageBox.confirm(
      `确定删除第 ${r.roundNo} 轮（${r.count} 词）？该轮的词将不再作为"新词"候选。`,
      '删除学习轮次',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch { return }
  try {
    await deleteLearnRound(r.roundId)
    // 本地立即更新（后端序号会重排）
    roundList.value = roundList.value.filter(x => x.roundId !== r.roundId)
    // 序号重排：最新一条 roundNo = 当前总数
    const total = roundList.value.length
    roundList.value.forEach((x, i) => { x.roundNo = total - i })
    ElMessage.success('已删除')
  } catch (e) {
    ElMessage.error('删除失败：' + (e?.message || '请稍后再试'))
  }
}

/**
 * 完成页"巩固测验"直达当前轮：跳过选轮次 → 直接进入选模式
 * 侧栏快捷入口仍走 openTestDialog（无当前轮概念，需手动选）
 */
async function openCurrentRoundTest() {
  let list = roundList.value
  if (!list || !list.length) {
    try { list = await getLearnRoundHistory() } catch (e) { list = [] }
  } else {
    roundList.value = list
  }
  const cur = list.find(r => r.roundNo === currentRoundNo.value)
  if (!cur) {
    // 理论不应发生（刚完成时一定有轮），降级到选轮次
    openTestDialog()
    return
  }
  selectedRound.value = cur
  testStep.value = 'mode'
  testDialogVisible.value = true
}
function pickRound(r) {
  selectedRound.value = r
  testStep.value = 'mode'
}
function startTest(testMode) {
  if (!selectedRound.value) return
  testDialogVisible.value = false
  router.push({
    path: '/word-test',
    query: { mode: testMode, learnRoundId: selectedRound.value.roundId, count: selectedRound.value.count }
  })
}
/** 时间格式化 YYYY-MM-DD HH:mm */
function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  if (isNaN(d.getTime())) return ''
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
</script>

<style lang="scss" scoped>
.review-page {
  padding-top: $sp-6;
  /* 至少占满一屏，保证固定背景铺满视口、底部无留白 */
  min-height: 100vh;
  /* 页面容器占满浏览器宽度，用于铺满固定背景图 */
  max-width: none;
  /* 半透明遮罩 + 背景图：滚动时图片固定在浏览器视口不动 */
  background-image: linear-gradient(rgba(255, 252, 248, 0.45), rgba(255, 252, 248, 0.45)),
    url('@/assets/images/review-bg.png');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  background-attachment: fixed;
}

/* 内容保持居中，左右留白对称 */
.review-page > .page-header,
.review-page > .review-grid {
  max-width: 1120px;
  margin-left: auto;
  margin-right: auto;
}

/* ---------- 沉浸模式布局 ---------- */
.review-page.is-immersive {
  /* 占满整个视口：背景铺满无留白，内容垂直居中 */
  position: relative;
  min-height: 100vh;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  justify-content: center;

  /* 沉浸模式聚焦学习：隐藏左侧栏，卡片独占横向空间 */
  .col-left { display: none; }
  .review-grid {
    width: 100%;
    max-width: none;
    grid-template-columns: 1fr;
  }

  /* 沉浸模式只保留卡片本体：模式/等级/数量是开始学习前的设置，
     学习中途不需要，退出沉浸即可调整（与主流背词 App 全屏态一致） */
  .toolbar { display: none; }

  /* 卡片锁定 16:9 横版比例：宽度同时受视口宽/高约束，
     保证任何屏幕（含手机横屏）都完整可见且比例恒定，
     新词学习 / 今日复习 / 空态 / 完成态大小完全一致，长单词不会使其变形 */
  .review-card {
    position: relative;
    width: min(1400px, 94vw, calc((100vh - 64px) * 16 / 9));
    height: auto;
    min-height: 0;
    aspect-ratio: 16 / 9;
    overflow: hidden;
    margin: 0 auto;
    /* 竖直弹性布局：单词 + 音标始终锁定卡片视觉中心 */
    display: flex;
    flex-direction: column;
    justify-content: center;
    /* 内边距跟随视口高度缩放：手机横屏自动收紧，不挤压正文 */
    padding: clamp(48px, 12vh, 104px) clamp(20px, 4vw, 56px);
    border-radius: $radius-card;
  }

  /* 进度、提示、操作按钮脱离文档流贴边排布，不占用卡片中部空间；
     间距/字号用 vh 联动缩放，矮视口（手机横屏）自动收紧不重叠 */
  .card-progress {
    position: absolute;
    top: clamp(12px, 2.6vh, 24px);
    left: clamp(16px, 4vw, 40px);
    right: clamp(16px, 4vw, 40px);
    margin-bottom: 0;
    .progress-meta .progress-count { font-size: clamp(14px, 2.4vh, 20px); }
    .progress-tag { font-size: clamp(11px, 1.9vh, 13px); }
  }
  .review-hint {
    position: absolute;
    top: clamp(38px, 8vh, 66px);
    left: $sp-4;
    right: $sp-4;
    margin-bottom: 0;
    font-size: clamp(11px, 2.2vh, 17px);
  }
  .card-actions {
    position: absolute;
    bottom: clamp(18px, 4vh, 36px);
    left: 0;
    right: 0;
    margin-top: 0;
    /* 沉浸模式按钮保持居中天然宽度，不被 ≤960px 媒体查询的 flex:1 拉满整行 */
    .act-btn { flex: 0 0 auto; }
  }
  .card-hint {
    position: absolute;
    bottom: 4px;
    left: 0;
    right: 0;
    margin-top: 0;
  }

  /* 内容随视口高度等比缩放：单词 + 音标留在流内居中，
     释义区整体绝对定位到卡片下部，「点击显示释义」时单词位置不跳动 */
  .flash-card {
    min-height: 0;
    overflow: hidden;
    position: relative;
    padding: 0;

    .fc-word {
      font-size: clamp(34px, min(6vw, 12vh), 92px);
      line-height: 1.08;
      word-break: break-word;
      max-width: 100%;

      /* 加深印象重现的词：暖橙区分默认主题色 */
      &.fc-word-boost {
        color: #d97806;
      }
    }
    /* 曾认识角标 */
    .fc-boost-tag {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      margin-top: clamp(4px, 1vh, 10px);
      padding: 3px 12px;
      font-size: 12px;
      font-weight: 500;
      color: #b45309;
      background: #fdf3e3;
      border: 1px solid #f3ddba;
      border-radius: 999px;
    }
    .fc-phonetic {
      font-size: clamp(15px, min(1.9vw, 3.4vh), 26px);
      margin-top: $sp-2;
    }

    .fc-detail {
      position: absolute;
      bottom: clamp(8px, 1.6vh, 16px);
      left: clamp(20px, 4vw, 56px);
      right: clamp(20px, 4vw, 56px);
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
    }
    .fc-divider {
      width: 36px;
      margin: 0 0 clamp(8px, 1.6vh, 14px);
    }
    /* 释义、例句刻意收小：沉浸模式视觉重心留给单词 */
    .fc-meaning {
      font-size: clamp(14px, min(1.5vw, 2.8vh), 22px);
      font-weight: 400;
      color: $text-body;
    }
    .fc-example {
      margin-top: clamp(6px, 1.2vh, 10px);
      max-width: 760px;
      .fc-example-en { font-size: clamp(12px, min(1.1vw, 2vh), 15px); line-height: 1.6; color: $text-secondary; }
      .fc-example-cn { font-size: clamp(11px, min(1vw, 1.8vh), 14px); color: $text-caption; }
      &.fc-example-empty { font-size: clamp(11px, min(1vw, 1.8vh), 14px); }
    }
    .fc-placeholder {
      font-size: clamp(12px, min(1.2vw, 2.2vh), 16px);
      color: $text-caption;
    }
  }
  .state-empty { font-size: clamp(15px, 2.6vh, 20px); }

  /* 操作按钮跟随视口高度缩放：手机横屏不与单词区相互挤压 */
  .act-btn {
    height: clamp(42px, 8.5vh, 56px);
    min-width: clamp(120px, 14vw, 190px);
    font-size: clamp(15px, 2.4vh, 18px);
    padding: 0 clamp(16px, 2vw, 28px);
  }

  /* 矮视口（手机横屏）：底部提示 toast 让位给操作按钮 */
  @media (max-height: 560px) {
    .card-hint { display: none; }
  }
}

/* ---------- 左右分栏 ---------- */
.review-grid {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: $sp-2;
  align-items: stretch;
}

/* ---------- 左列侧栏 ---------- */
.col-left {
  display: flex;
  flex-direction: column;
  gap: $sp-4;
  position: sticky;
  top: $sp-5;
}
/* 右列：工具条 + 卡片作为整体，占满网格列宽（宽度由 .review-grid 统一控制，保证左右留白对称） */
.col-right {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.side-card {
  padding: $sp-4 $sp-5;
}
.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $sp-3;
}
.side-title {
  font-size: $fs-base;
  font-weight: 600;
  color: $color-primary;
  letter-spacing: 0.3px;
}
.side-tag {
  font-size: $fs-xs;
  font-weight: 500;
  color: $text-caption;
  padding: 2px 8px;
  background: $bg-soft;
  border: 1px solid $border-light;
  border-radius: $radius-pill;
  letter-spacing: 0.3px;
}

/* 今日进度卡 */
.daily-progress {
  .dp-track {
    height: 6px;
    background: $border-light;
    border-radius: $radius-pill;
    overflow: hidden;
  }
  .dp-fill {
    height: 100%;
    background: $color-primary;
    border-radius: $radius-pill;
    transition: width $transition-normal;
  }
  .dp-foot {
    display: flex;
    justify-content: space-between;
    margin-top: $sp-2;
    font-size: $fs-sm;
    color: $text-caption;
  }
  .dp-pct {
    font-weight: 600;
    color: $color-primary;
  }
}

/* 学习统计卡 */
.stat-list {
  display: flex;
  flex-direction: column;
}
.stat-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $sp-2 0;
  border-bottom: 1px dashed $border-light;
  &:last-child { border-bottom: none; }
  .stat-row-label {
    font-size: $fs-base;
    color: $text-caption;
  }
  .stat-row-num {
    font-size: $fs-md;
    font-weight: 600;
    color: $text-title;
    letter-spacing: 0.2px;
  }
}

/* 快捷入口卡 */
.quick-list {
  display: flex;
  flex-direction: column;
  margin: -4px 0;
}
.quick-row {
  display: flex;
  align-items: center;
  gap: $sp-3;
  padding: $sp-3 0;
  background: none;
  border: none;
  cursor: pointer;
  text-align: left;
  border-bottom: 1px dashed $border-light;
  transition: opacity $transition-fast;

  &:last-child { border-bottom: none; }
  &:hover:not(:disabled) { opacity: 0.7; }
  &:disabled {
    cursor: not-allowed;
    opacity: 0.45;
  }
}
.quick-icon {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-sm;
  background: $bg-soft;
  color: $text-body;
}
.quick-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.quick-name {
  font-size: $fs-md;
  font-weight: 500;
  color: $text-title;
  line-height: 1.4;
}
.quick-desc {
  font-size: $fs-sm;
  color: $text-caption;
  line-height: 1.4;
}
.quick-arrow {
  font-size: 16px;
  color: $text-disabled;
  line-height: 1;
}
.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: $sp-4;
  margin-bottom: 0;
  padding: $sp-3 $sp-5;
  background: $bg-card;
  border: 1px solid $border-light;
  /* 与下方单词卡无缝拼接：仅保留上方圆角 */
  border-radius: $radius-card $radius-card 0 0;
  border-bottom: none;
}
.tb-group {
  display: flex;
  align-items: center;
  gap: $sp-3;
}
.tb-label {
  font-size: $fs-base;
  color: $text-caption;
  letter-spacing: 0.5px;
}
.tb-sep {
  width: 1px;
  height: 14px;
  background: $border-base;
}
.tb-tabs {
  display: flex;
  align-items: center;
  gap: 2px;
}
.tb-tab {
  position: relative;
  padding: 6px 0;
  margin: 0 $sp-3;
  background: none;
  border: none;
  cursor: pointer;
  font-size: $fs-md;
  font-weight: 400;
  color: $text-caption;
  transition: color $transition-fast;

  &:first-child { margin-left: 0; }
  &:hover { color: $text-body; }
  &.active {
    color: $color-primary;
    font-weight: 600;
  }
  &.active::after {
    content: '';
    position: absolute;
    left: 50%;
    bottom: -1px;
    transform: translateX(-50%);
    width: 18px;
    height: 2px;
    background: $color-primary;
    border-radius: 1px;
  }
}
.tb-select {
  width: 110px;
  :deep(.el-select__wrapper) {
    background: $bg-soft;
    box-shadow: 0 0 0 1px $border-light inset;
  }
}

/* ---------- 沉浸模式 ---------- */
.tb-immersive {
  margin-left: auto;
}
.immersive-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: 1px solid $primary-2;
  border-radius: $radius-pill;
  background: $primary-1;
  color: $color-primary;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: all $transition-fast;

  &:hover {
    background: $color-primary;
    border-color: $color-primary;
    color: #fff;
    box-shadow: 0 2px 8px rgba(58, 140, 137, 0.25);
  }
}

/* 右上角悬浮退出按钮 */
.immersive-exit {
  position: fixed;
  top: 16px;
  right: 20px;
  z-index: 2000;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: $radius-pill;
  background: rgba(30, 30, 30, 0.55);
  backdrop-filter: blur(6px);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  user-select: none;
  transition: background $transition-fast;

  &:hover {
    background: rgba(30, 30, 30, 0.75);
  }

  .ie-key {
    padding: 1px 6px;
    border-radius: 5px;
    border: 1px solid rgba(255, 255, 255, 0.35);
    font-size: 11px;
    line-height: 1.5;
  }
}

/* ---------- 卡片主体 ---------- */
.review-card {
  /* 固定高度：空态/完成态/加载态切换时卡片大小不变 */
  min-height: 470px;
  display: flex;
  flex-direction: column;
  padding: $sp-3 $sp-8 $sp-6;
  /* 与上方工具条无缝拼接：仅保留下方圆角 */
  border-radius: 0 0 $radius-card $radius-card;
}

/* 进度 */
.card-progress {
  margin-bottom: $sp-5;

  .progress-meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $sp-2;

    .progress-count {
      font-size: $fs-base;
      font-weight: 600;
      color: $text-title;
      letter-spacing: 0.3px;
    }
    .progress-divider {
      color: $text-disabled;
      margin: 0 1px;
      font-weight: 400;
    }

    .progress-tag {
      font-size: $fs-xs;
      color: $text-caption;
      letter-spacing: 0.4px;
      font-weight: 500;
    }
  }

  .progress-track {
    height: 2px;
    background: $border-light;
    border-radius: $radius-pill;
    overflow: hidden;

    .progress-fill {
      height: 100%;
      background: $color-primary;
      border-radius: $radius-pill;
      transition: width $transition-normal;
    }
  }
}

/* 新词提示（极简文字行） */
.review-hint {
  margin: 0 0 $sp-5;
  padding: 0;
  font-size: $fs-base;
  color: $text-caption;
  text-align: center;
  background: none;
  border: none;
}

/* 单词卡（极简：纯白底、细边、大字） */
.flash-card {
  flex: 1;
  min-height: 320px;
  border: 1px solid $border-light;
  border-radius: $radius-card;
  background: $bg-card;
  padding: $sp-6 $sp-6;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  transition: border-color $transition-fast;

  &:hover {
    border-color: $gray-4;
  }

  .fc-word {
    font-size: 76px;
    font-weight: 700;
    color: $color-primary;
    letter-spacing: -0.02em;
    line-height: 1.15;
    word-break: break-word;
  }

  .fc-phonetic {
    margin-top: 0;
    font-size: $fs-md;
    color: $text-caption;
    font-family: Georgia, 'Times New Roman', serif;
    letter-spacing: 0.2px;
  }

  .fc-detail {
    /* 包裹层保持居中对齐，分隔线才能落在音标正下方 */
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  .fc-divider {
    width: 28px;
    height: 1px;
    background: $gray-5;
    /* 紧贴音标下方，与释义保持间距 */
    margin: $sp-1 0 $sp-4;
  }

  .fc-meaning {
    font-size: $fs-2xl;
    font-weight: 500;
    color: $text-body;
    letter-spacing: 0.2px;
  }

  .fc-example {
    margin-top: $sp-4;
    max-width: 520px;

    .fc-example-en {
      font-size: $fs-md;
      color: $text-body;
      font-style: italic;
      line-height: 1.7;
    }

    .fc-example-cn {
      margin-top: 4px;
      font-size: $fs-base;
      color: $text-caption;
    }

    &.fc-example-empty {
      font-size: $fs-base;
      color: $text-disabled;
    }
  }

  .fc-placeholder {
    font-size: $fs-md;
    color: $text-disabled;
    letter-spacing: 0.5px;
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }
  .kbd-chip {
    display: inline-block;
    padding: 2px 8px;
    border: 1px solid $border-base;
    border-radius: 4px;
    background: $bg-soft;
    color: $text-secondary;
    font-size: $fs-xs;
    font-weight: 500;
    font-family: ui-monospace, 'SF Mono', Menlo, Monaco, Consolas, monospace;
    line-height: 1.3;
  }
}

/* 操作按钮（等宽三按钮，无悬浮动效） */
.card-actions {
  display: flex;
  justify-content: center;
  gap: $sp-3;
  margin-top: $sp-6;
}

/* 卡片内联轻提示（替代全局 ElMessage，避免打断心流） */
.card-hint {
  margin-top: $sp-4;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: $radius-pill;
  background: $primary-1;
  color: $color-primary;
  font-size: $fs-sm;
  font-weight: 500;
  letter-spacing: 0.01em;
}

.hint-fade-enter-active,
.hint-fade-leave-active {
  transition: opacity $transition-fast, transform $transition-fast;
}

.hint-fade-enter-from,
.hint-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.act-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: $sp-3;
  min-width: 160px;
  height: 48px;
  padding: 0 $sp-5;
  border: 1px solid $border-base;
  border-radius: $radius-base;
  background: $bg-card;
  cursor: pointer;
  font-size: $fs-md;
  font-weight: 500;
  color: $text-body;
  transition: color $transition-fast, border-color $transition-fast, background $transition-fast;

  .act-key {
    font-size: 11px;
    color: $text-secondary;
    border: 1px solid $border-base;
    border-radius: 4px;
    padding: 1px 6px;
    line-height: 1.3;
    font-weight: 500;
    background: $bg-soft;
    font-family: ui-monospace, 'SF Mono', Menlo, Monaco, Consolas, monospace;
    min-width: 16px;
    text-align: center;
  }

  &.act-btn-primary {
    min-width: 140px;
    height: 36px;
    background: $bg-card;
    color: $color-primary;
    border-color: $color-primary;

    &:hover {
      background: $primary-1;
      border-color: $color-primary-deep;
      color: $color-primary-deep;
    }
  }

  &.act-btn-danger:hover {
    border-color: $color-danger;
    color: $color-danger;
    background: $bg-card;

    .act-key {
      border-color: $color-danger;
      color: $color-danger;
    }
  }

  &.act-btn-warning:hover {
    border-color: $color-warning;
    color: $color-warning;
    background: $bg-card;

    .act-key {
      border-color: $color-warning;
      color: $color-warning;
    }
  }

  &.act-btn-success:hover {
    border-color: $color-success;
    color: $color-success;
    background: $bg-card;

    .act-key {
      border-color: $color-success;
      color: $color-success;
    }
  }
}

/* ---------- 紧凑状态：空 / 完成 ---------- */
.state-skel {
  flex: 1;
  display: flex;
  align-items: center;
}

.state-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $sp-3;
  padding: $sp-10 0;
  font-size: $fs-base;
  color: $text-caption;
}
.se-icon {
  display: inline-flex;
  align-items: center;
  color: $text-disabled;
}
.se-text {
  color: $text-body;
}
.se-link {
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  font-size: $fs-base;
  font-weight: 500;
  color: $color-primary;
  transition: opacity $transition-fast;
  &:hover { opacity: 0.75; }
  &:disabled,
  &.is-loading {
    cursor: not-allowed;
    opacity: 0.55;
  }
}

.state-finish {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: $sp-5;
  padding: $sp-10 0;
  text-align: center;
}
.sf-row {
  display: inline-flex;
  align-items: center;
  gap: $sp-3;
}
.sf-check {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: $text-title;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  @include flex-center;
}
.sf-title {
  font-size: $fs-xl;
  font-weight: 600;
  color: $text-title;
}
.sf-sub {
  font-size: $fs-base;
  color: $text-caption;
}
.sf-actions {
  display: flex;
  gap: $sp-6;
}

/* ---------- 巩固测验弹窗（列表式） ---------- */
.td-desc {
  margin: 0 0 $sp-4;
  font-size: $fs-base;
  color: $text-caption;
  text-align: center;
  line-height: 1.6;
}
.td-list {
  display: flex;
  flex-direction: column;
  border-top: 1px solid $border-light;
}
.td-row {
  display: flex;
  align-items: center;
  gap: $sp-3;
  padding: $sp-4 $sp-2;
  background: none;
  border: none;
  border-bottom: 1px solid $border-light;
  cursor: pointer;
  text-align: left;
  transition: background $transition-fast;
  flex: 1;
  min-width: 0;

  &:last-child { border-bottom: none; }
  &:hover { background: $bg-soft; }
}
.td-row-wrap {
  display: flex;
  align-items: stretch;
  border-bottom: 1px solid $border-light;
  position: relative;

  &:last-child { border-bottom: none; }
  &:hover .td-row-del { opacity: 1; }
}
.td-row-del {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 $sp-4;
  background: none;
  border: none;
  color: $text-disabled;
  cursor: pointer;
  opacity: 0;
  transition: opacity $transition-fast, color $transition-fast;

  &:hover { color: $color-danger; }
}
.td-row-icon {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: $text-caption;
}
.td-row-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.td-row-name {
  font-size: $fs-md;
  font-weight: 500;
  color: $text-title;
}
.td-row-desc {
  font-size: $fs-sm;
  color: $text-caption;
  line-height: 1.5;
}
.td-row-arrow {
  font-size: 18px;
  color: $text-disabled;
  line-height: 1;
}
.td-foot {
  margin-top: $sp-4;
  text-align: center;
}

/* 翻转动画 */
.flip-left-enter-active,
.flip-left-leave-active {
  transition: all 0.28s ease-out;
}
.flip-left-enter-from {
  opacity: 0;
  transform: translateX(24px);
}
.flip-left-leave-to {
  opacity: 0;
  transform: translateX(-24px);
}

@media (max-width: 960px) {
  .review-grid {
    grid-template-columns: 1fr;
  }
  .col-left {
    position: static;
    flex-direction: row;
    flex-wrap: wrap;
    .side-card { flex: 1 1 220px; }
  }

  .flash-card {
    padding: $sp-6 $sp-4;
    .fc-word { font-size: 40px; }
  }

  .act-btn {
    min-width: 0;
    flex: 1;
    padding: 0 $sp-3;
  }
}

/* ========== 移动端窄屏适配（<= 768px） ========== */
@media (max-width: 768px) {
  /* 页面容器 padding 收紧为 12px */
  .review-page {
    padding-left: $sp-3;
    padding-right: $sp-3;
  }

  /* 双栏改单列；侧栏卡片纵向自然排列 */
  .review-grid {
    grid-template-columns: 1fr;
  }
  .col-left {
    position: static;
    flex-direction: column;
    .side-card { flex: none; }
  }

  /* 非沉浸模式：单词卡 + 操作按钮的窄屏布局
     （沉浸模式有独立的绝对定位卡片布局，保持原样不干预） */
  .review-page:not(.is-immersive) {
    /* 为底部操作按钮预留 iOS 安全区，避免被手势条遮挡 */
    padding-bottom: calc(#{$sp-3} + env(safe-area-inset-bottom, 0px));

    .review-card {
      padding: $sp-3 $sp-3 $sp-4;
    }

    /* 单词主字号自适应，长单词断行防溢出 */
    .flash-card {
      .fc-word {
        font-size: clamp(28px, 8vw, 40px);
        word-break: break-word;
        max-width: 100%;
      }
      .fc-example { max-width: 100%; }
    }

    /* 操作按钮：全宽纵向排列 */
    .card-actions {
      flex-direction: column;
      align-items: stretch;
      gap: $sp-2;
    }
    .act-btn {
      width: 100%;
      min-width: 0;
    }
    .act-btn.act-btn-primary { min-height: 44px; }
  }

  /* 完成态按钮行防溢出 */
  .sf-actions {
    flex-wrap: wrap;
    justify-content: center;
    row-gap: $sp-2;
  }
}
</style>
