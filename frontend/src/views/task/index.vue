<template>
  <div class="page-container task-page">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">任务中心</h1>
        <p class="page-desc">每天一点小目标，坚持 30 天解锁专属奖励</p>
      </div>
      <div class="td-badge">
        <AppIcon name="calendar" :size="15" />
        <span>{{ todayText }}</span>
      </div>
    </div>

    <div class="task-grid">
      <!-- ============ 左列 ============ -->
      <div class="col-main">
        <!-- 每日签到 + 连签奖励（紧凑合并卡，主流布局） -->
        <div class="ws-card checkin-card">
          <!-- 顶部：连签 + 签到按钮 -->
          <div class="ck-head">
            <div class="ck-head-left">
              <div class="ck-flame" :class="{ lit: panel.streak > 0 }">
                <AppIcon name="flame" :size="16" />
              </div>
              <div class="ck-head-text">
                <span class="ck-streak">连续 <b>{{ panel.streak }}</b> 天</span>
                <span class="ck-sub">{{ panel.checkedIn ? '今日已签到，明天再来' : '点击签到开启连签' }}</span>
              </div>
            </div>
            <button
              class="ck-btn"
              :class="{ done: panel.checkedIn }"
              :disabled="panel.checkedIn || checkingIn"
              @click="onCheckIn"
            >
              <AppIcon v-if="!panel.checkedIn" name="calendar-check" :size="13" />
              <span>{{ checkingIn ? '签到中…' : panel.checkedIn ? '已签到' : '立即签到' }}</span>
              <span v-if="!panel.checkedIn && !checkingIn" class="ck-btn-pts">+20 活跃度</span>
            </button>
          </div>

          <!-- 主体：左侧连签奖励侧栏 + 右侧月历 -->
          <div class="ck-body">
            <!-- 左侧：连签奖励 -->
            <div class="ms-sidebar">
              <div class="ms-side-title">
                <AppIcon name="trophy" :size="13" />
                <span>连签奖励</span>
              </div>
              <button
                v-for="m in panel.streakRewards"
                :key="m.streakDays"
                class="ms-side-item"
                :class="streakState(m)"
                :disabled="streakState(m) !== 'claimable' || claimingStreak === m.streakDays"
                @click="onClaimStreak(m.streakDays)"
              >
                <div class="ms-side-icon">
                  <AppIcon
                    :name="streakState(m) === 'claimed' ? 'check' : streakState(m) === 'claimable' ? 'gift' : 'lock'"
                    :size="14"
                  />
                </div>
                <div class="ms-side-info">
                  <div class="ms-side-days">连续 {{ m.streakDays }} 天</div>
                  <div class="ms-side-reward">
                    <span v-if="m.aiAward">+{{ m.aiAward }} AI</span>
                    <span v-if="m.coinAward">+{{ m.coinAward }} 金币</span>
                    <span v-if="!m.aiAward && !m.coinAward" class="ms-side-empty">荣誉勋章</span>
                  </div>
                </div>
                <div class="ms-side-action">
                  <AppIcon
                    v-if="streakState(m) === 'claimed'"
                    name="check"
                    :size="11"
                  />
                  <span>{{ streakBtnText(m) }}</span>
                </div>
              </button>
            </div>

            <!-- 右侧：月历 -->
            <div class="cal-wrap">
              <div class="cal-head">
                <button class="cal-nav" :disabled="!canGoPrev" @click="prevMonth">
                  <AppIcon name="chevron-left" :size="16" />
                </button>
                <div class="cal-title">{{ calendarMonth.year }} 年 {{ calendarMonth.month }} 月</div>
                <button class="cal-nav" :disabled="!canGoNext" @click="nextMonth">
                  <AppIcon name="chevron-right" :size="16" />
                </button>
              </div>
              <div class="cal-grid cal-weekrow">
                <div v-for="lbl in ['一','二','三','四','五','六','日']" :key="lbl" class="cal-hcell">{{ lbl }}</div>
              </div>
              <div class="cal-grid cal-body">
                <div class="cal-cell-wrap">
                  <div
                    v-for="(c, i) in monthData"
                    :key="i"
                    class="cal-cell"
                    :class="cellClass(c)"
                  >
                    <div v-if="c" class="cal-inner">
                                            <span class="cal-num">{{ c.day }}</span>
                                          </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 活跃度进度 + 宝箱 -->
        <div class="ws-card act-card">
          <div class="sec-head">
            <h3 class="sec-title">
              <AppIcon name="zap" :size="17" />
              今日活跃度
            </h3>
            <span class="act-points">
              <b>{{ panel.activityPoints }}</b> 分
              <el-tooltip
                placement="bottom-start"
                effect="light"
                :show-after="80"
              >
                <template #content>
                  <div class="act-tip">
                    <div class="act-tip-title">今日活跃度来源</div>
                    <div v-if="!hasActivePoints" class="act-tip-empty">今天还没有活跃度，完成任务即可累积</div>
                    <div
                      v-for="(p, k) in panel.activityBreakdown"
                      :key="k"
                      v-show="p > 0"
                      class="act-tip-row"
                    >
                      <span>{{ TASK_LABELS[k] }}</span>
                      <b class="act-tip-pts">+{{ p }}</b>
                    </div>
                    <div class="act-tip-sum">
                      <span>合计</span>
                      <b>{{ panel.activityPoints }} 分</b>
                    </div>
                  </div>
                </template>
                <AppIcon name="info" :size="12" class="act-info" />
              </el-tooltip>
            </span>
          </div>

          <div class="act-bar">
            <div class="act-fill" :style="{ width: fillPercent + '%' }"></div>
            <div
              v-for="m in panel.activityRewards"
              :key="m.threshold"
              class="act-node"
              :class="actState(m)"
              :style="{ left: nodePercent(m.threshold) + '%' }"
              @click="actState(m) === 'claimable' && onClaimActive(m.threshold)"
            >
              <div class="an-badge">
                <AppIcon v-if="actState(m) === 'claimed'" name="check" :size="13" />
                <AppIcon v-else-if="actState(m) === 'claimable'" name="gift" :size="15" />
                <AppIcon v-else name="lock" :size="13" />
              </div>
              <div class="an-label">{{ m.threshold }}</div>
            </div>
          </div>

          <div class="act-rewards">
            <div
              v-for="m in panel.activityRewards"
              :key="m.threshold"
              class="ar-item"
              :class="actState(m)"
            >
              <div class="ar-th">
                <span class="ar-score">{{ m.threshold }} 分</span>
                <span class="ar-status">
                  <AppIcon
                    v-if="actState(m) === 'claimed'"
                    name="circle-check"
                    :size="14"
                    color="#52C41A"
                  />
                  <AppIcon
                    v-else-if="actState(m) === 'claimable'"
                    name="gift"
                    :size="14"
                    color="#FA8C16"
                  />
                  <AppIcon v-else name="lock" :size="14" color="#C9CDD4" />
                  {{ actStateText(m) }}
                </span>
              </div>
              <div class="ar-rw">
                <span v-if="m.coinAward" class="rw rw-coin">
                  <AppIcon name="coins" :size="12" />+{{ m.coinAward }} 金币
                </span>
                <span v-if="m.aiAward" class="rw rw-ai">
                  <AppIcon name="sparkles" :size="12" />+{{ m.aiAward }} AI
                </span>
              </div>
              <button
                class="ar-btn"
                :class="actState(m)"
                :disabled="actState(m) !== 'claimable' || claimingActive === m.threshold"
                @click="onClaimActive(m.threshold)"
              >
                {{ actState(m) === 'claimed' ? '已领取' : actState(m) === 'claimable' ? '领取' : '未达成' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- ============ 右列：今日任务 ============ -->
      <div class="col-side">
        <div class="ws-card task-list">
          <div class="tl-head">
            <h3 class="tl-title">今日任务清单</h3>
            <span class="tl-progress">{{ doneCount }} / {{ panel.tasks.length }} 已完成</span>
          </div>
          <div class="tl-progressbar">
            <div class="tlp-fill" :style="{ width: progressPercent + '%' }"></div>
          </div>

          <div
            v-for="t in panel.tasks"
            :key="t.name"
            class="tl-row"
            :class="{ 'is-done': t.done }"
            @click="!t.done && go(t.path)"
          >
            <div class="tl-icon" :class="{ 'tli-done': t.done }">
              <AppIcon :name="t.done ? 'check' : t.icon" :size="19" />
            </div>
            <div class="tl-body">
              <div class="tl-name">
                {{ t.name }}
                <span class="tl-points" :class="{ 'tl-points-done': t.done }">
                  <AppIcon name="zap" :size="11" />+{{ t.point }}
                </span>
              </div>
              <div class="tl-desc">{{ t.desc }}</div>
            </div>
            <div class="tl-right">
              <el-tag v-if="t.done" type="success" size="small" effect="light">已完成</el-tag>
              <span v-else class="tl-go">
                去完成<AppIcon name="chevron-right" :size="14" />
              </span>
            </div>
          </div>
        </div>

        <div class="ws-card ds-card">
          <div class="ds-head">
            <AppIcon name="book-open" :size="16" />
            <span>句灵日选</span>
            <span class="ds-date">{{ sentence?.date || todayText.slice(0, 10) }}</span>
          </div>

          <!-- 加载/空态 -->
          <div v-if="!sentence" class="ds-empty">
            <AppIcon name="loader" :size="14" /> 正在加载今日佳句…
          </div>

          <template v-else>
            <div v-if="sentence.fallback" class="ds-fallback-tag">
              <AppIcon name="history" :size="11" /> 今日精选（来自历史佳句库）
            </div>

            <div class="ds-en">{{ sentence.enSentence }}</div>
            <div class="ds-cn">{{ sentence.cnTrans }}</div>

            <div v-if="sentence.keyCollocation" class="ds-key">
              <AppIcon name="key" :size="12" />
              <span>{{ sentence.keyCollocation }}</span>
            </div>

            <div v-if="sentence.tags" class="ds-tags">
              <span v-for="t in sentence.tags.split('｜')" :key="t" class="ds-tag">{{ t }}</span>
            </div>

            <div v-if="sentence.note" class="ds-note">
              <AppIcon name="info" :size="12" /> {{ sentence.note }}
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import { todayPanel, checkIn, claimStreakReward, claimActiveReward } from '@/api/task'
import { getTodaySentence } from '@/api/sentence'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const panel = ref({
  checkedIn: false,
  streak: 0,
  streakRewards: [],
  streakClaimed: [],
  activityPoints: 0,
  activityBreakdown: {},
  activityRewards: [],
  activeClaimed: [],
  tasks: [],
  today: '',
  checkedDates: []
})

const checkingIn = ref(false)
const claimingStreak = ref(0)
const claimingActive = ref(0)

const todayText = ref('')
const sentence = ref(null)

const doneCount = computed(() => panel.value.tasks.filter((t) => t.done).length)
const progressPercent = computed(() =>
  panel.value.tasks.length ? Math.round((doneCount.value / panel.value.tasks.length) * 100) : 0
)

// 月历：当前展示的年月 + 月内日期格子（带检查、未来、今天状态）
const calendarMonth = ref({ year: 0, month: 0 })
function pad2(n) { return String(n).padStart(2, '0') }
function fmtIso(y, m, d) { return `${y}-${pad2(m)}-${pad2(d)}` }

const monthData = computed(() => {
  const { year, month } = calendarMonth.value
  if (!year || !month) return []
  const firstDay = new Date(year, month - 1, 1)
  const lastDay = new Date(year, month, 0) // 上一个月 0 = 当月最后一天
  const daysInMonth = lastDay.getDate()
  // 让周一为第一列：JS getDay 0=日 1=一 … 6=六
  const firstWeekday = (firstDay.getDay() + 6) % 7
  const checked = new Set(panel.value.checkedDates || [])
  const todayStr = panel.value.today
  const cells = []
  // 前面空格子
  for (let i = 0; i < firstWeekday; i++) cells.push(null)
  // 当月每一天
  for (let d = 1; d <= daysInMonth; d++) {
    const iso = fmtIso(year, month, d)
    cells.push({
      day: d,
      iso,
      isToday: iso === todayStr,
      isPast: iso < todayStr,
      isFuture: iso > todayStr,
      checked: checked.has(iso)
    })
  }
  // 末尾空格子凑整 7 列
  while (cells.length % 7 !== 0) cells.push(null)
  return cells
})

const todayYM = computed(() => {
  if (!panel.value.today) return { year: 0, month: 0 }
  const [y, m] = panel.value.today.split('-').map(Number)
  return { year: y, month: m }
})
const earliestYM = computed(() => {
  // 只允许回看 3 个月（90 天数据范围之外无勾）
  if (!panel.value.today) return todayYM.value
  const [y, m, d] = panel.value.today.split('-').map(Number)
  const dt = new Date(y, m - 1, d)
  dt.setMonth(dt.getMonth() - 3)
  return { year: dt.getFullYear(), month: dt.getMonth() + 1 }
})
const canGoPrev = computed(() => {
  const a = calendarMonth.value
  const b = earliestYM.value
  return a.year > b.year || (a.year === b.year && a.month > b.month)
})
const canGoNext = computed(() => {
  const a = calendarMonth.value
  const b = todayYM.value
  return a.year < b.year || (a.year === b.year && a.month < b.month)
})

function prevMonth() {
  if (!canGoPrev.value) return
  const { year, month } = calendarMonth.value
  calendarMonth.value = month === 1
    ? { year: year - 1, month: 12 }
    : { year, month: month - 1 }
}
function nextMonth() {
  if (!canGoNext.value) return
  const { year, month } = calendarMonth.value
  calendarMonth.value = month === 12
    ? { year: year + 1, month: 1 }
    : { year, month: month + 1 }
}

function cellClass(c) {
  if (!c) return 'empty'
  return {
    checked: c.checked,
    today: c.isToday && !c.checked,
    past_missed: c.isPast && !c.checked,
    future: c.isFuture
  }
}

// 活跃度进度条：以 100 为满刻度
const ACT_MAX = 100
/** 活跃度任务 key → 中文名（用于右上 tooltip 展示来源） */
const TASK_LABELS = {
  review:  '清空今日复习',
  newWord: '新学 5 个生词',
  aiUse:   '向阅灵 AI 提问',
  game:    '挑战一周拼写游戏',
  paper:   '做一套练习试卷',
  checkin: '每日签到'
}
const fillPercent = computed(() =>
  Math.min(100, Math.round((panel.value.activityPoints / ACT_MAX) * 100))
)
const hasActivePoints = computed(() =>
  Object.values(panel.value.activityBreakdown || {}).some(v => v > 0)
)
const nodePercent = (threshold) => Math.min(100, (threshold / ACT_MAX) * 100)

/** 今日积分明细（任务驱动型）：每个任务满分固定，完成才计分 */
const bdRows = computed(() => {
  const bd = panel.value.activityBreakdown || {}
  return [
    { key: 'review',  label: '复习单词',   points: bd.review  || 0, max: 10, color: '#5CA9A5' },
    { key: 'newWord', label: '学 5 个新词', points: bd.newWord || 0, max: 15, color: '#9B7EF7' },
    { key: 'aiUse',   label: '词灵 AI',   points: bd.aiUse   || 0, max: 10, color: '#409EFF' },
    { key: 'game',    label: '单词游戏',   points: bd.game    || 0, max: 20, color: '#FA8C16' },
    { key: 'paper',   label: '练习试卷',   points: bd.paper   || 0, max: 25, color: '#52C41A' },
  ]
})

function streakState(m) {
  if (panel.value.streakClaimed.includes(m.streakDays)) return 'claimed'
  if (panel.value.streak >= m.streakDays) return 'claimable'
  return 'locked'
}
function streakBtnText(m) {
  const s = streakState(m)
  if (s === 'claimed') return '已领取'
  if (s === 'claimable') return '领取'
  return `${m.streakDays - panel.value.streak}天后`
}

function actState(m) {
  if (panel.value.activeClaimed.includes(m.threshold)) return 'claimed'
  if (panel.value.activityPoints >= m.threshold) return 'claimable'
  return 'locked'
}
function actStateText(m) {
  const s = actState(m)
  return s === 'claimed' ? '已领取' : s === 'claimable' ? '可领取' : '未达成'
}

async function loadPanel() {
  try {
    panel.value = await todayPanel()
    // 用服务端 today 初始化月历（避免浏览器时区把 9/2 拉到 9/1）
    if (panel.value.today) {
      const [y, m] = panel.value.today.split('-').map(Number)
      calendarMonth.value = { year: y, month: m }
    }
    await loadSentence()
  } catch (e) {
    /* 接口异常静默，保留默认空态 */
  }
}

async function onCheckIn() {
  if (checkingIn.value) return
  checkingIn.value = true
  try {
    const res = await checkIn()
    panel.value.checkedIn = true
    panel.value.streak = res.streak
    panel.value.streakClaimed = res.streakClaimed || []
    // 立即把今天加进日历已签到集合，避免等待页面刷新
    if (res.todayFresh && panel.value.today && !panel.value.checkedDates.includes(panel.value.today)) {
      panel.value.checkedDates = [...panel.value.checkedDates, panel.value.today]
    }
    if (res.todayFresh) {
      // 活跃度同步 +20，进度条与宝箱状态即时刷新，无需手动刷新页面
      panel.value.activityPoints = (panel.value.activityPoints || 0) + 20
      panel.value.activityBreakdown = {
        ...panel.value.activityBreakdown,
        checkin: (panel.value.activityBreakdown?.checkin || 0) + 20
      }
      const extra = res.aiBonus ? `，获得 ${res.aiBonus} 次永久 AI 额度` : ''
      ElMessage.success(`签到成功，+20 活跃度，连续 ${res.streak} 天 🔥${extra}`)
    } else {
      ElMessage.info('今日已签到')
    }
  } catch (e) {
    /* 错误提示由拦截器统一处理 */
  } finally {
    checkingIn.value = false
  }
}

async function onClaimStreak(streakDays) {
  if (claimingStreak.value) return
  claimingStreak.value = streakDays
  try {
    const res = await claimStreakReward(streakDays)
    panel.value.streakClaimed = res.streakClaimed || []
    const parts = []
    if (res.aiAward) parts.push(`+${res.aiAward} 次 AI 额度`)
    if (res.coinAward) parts.push(`+${res.coinAward} 金币`)
    ElMessage.success(`连续 ${streakDays} 天奖励：${parts.join('，') || '已领取'}`)
    if (res.aiAward) userStore.refreshQuota()
  } catch (e) {
    /* 拦截器处理 */
  } finally {
    claimingStreak.value = 0
  }
}

async function onClaimActive(threshold) {
  if (claimingActive.value) return
  claimingActive.value = threshold
  try {
    const res = await claimActiveReward(threshold)
    panel.value.activeClaimed = res.activeClaimed || []
    const parts = []
    if (res.coinAward) parts.push(`+${res.coinAward} 金币`)
    if (res.aiAward) parts.push(`+${res.aiAward} 次 AI 额度`)
    ElMessage.success(`活跃度 ${threshold} 奖励：${parts.join('，') || '已领取'}`)
    if (res.aiAward) userStore.refreshQuota()
  } catch (e) {
    /* 拦截器处理 */
  } finally {
    claimingActive.value = 0
  }
}

function go(path) {
  router.push(path)
}

async function loadSentence() {
  try {
    sentence.value = await getTodaySentence()
  } catch (e) {
    /* 静默失败，保留空态 */
  }
}

let dayCheckTimer = null

onMounted(() => {
  todayText.value = new Date().toLocaleDateString('zh-CN', {
    year: 'numeric', month: 'long', day: 'numeric', weekday: 'long'
  })
  loadPanel()
  // 跨天自动重拉：每分钟检测本地日期是否跨天，跨天则刷新任务面板与日期显示
  let lastDay = new Date().toDateString()
  dayCheckTimer = setInterval(() => {
    const now = new Date()
    const cur = now.toDateString()
    if (cur !== lastDay) {
      lastDay = cur
      todayText.value = now.toLocaleDateString('zh-CN', {
        year: 'numeric', month: 'long', day: 'numeric', weekday: 'long'
      })
      loadPanel()
    }
  }, 60 * 1000)
})

onUnmounted(() => {
  if (dayCheckTimer) {
    clearInterval(dayCheckTimer)
    dayCheckTimer = null
  }
})
</script>

<style lang="scss" scoped>
@use '@/assets/scss/variables.scss' as *;
@use '@/assets/scss/mixins.scss' as *;

.task-page {
  padding-top: $sp-6;
  min-height: 100%;
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

/* 内容保持 1200px 居中，不随浏览器变宽 */
.task-page > .page-header,
.task-page > .task-grid {
  max-width: $page-max-width;
  margin-left: auto;
  margin-right: auto;
}

.td-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: $bg-card;
  border: 1px solid $border-light;
  border-radius: $radius-pill;
  font-size: 13px;
  color: $text-caption;
}

/* 布局：左主右侧 */
.task-grid {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: $sp-4;
  align-items: start;
}
.col-main, .col-side {
  display: flex;
  flex-direction: column;
  gap: $sp-4;
}

/* ---------- 打卡紧凑卡（签到+日历条+连签奖励，主流布局） ---------- */
.checkin-card {
  padding: $sp-4 $sp-5 $sp-3;
}

/* 顶部：连签数 + 签到按钮 */
.ck-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
  padding-bottom: $sp-3;

  .ck-head-left { display: flex; align-items: center; gap: $sp-3; }

  .ck-flame {
    width: 32px;
    height: 32px;
    border-radius: 9px;
    @include flex-center;
    background: $gray-3;
    color: $gray-5;
    flex-shrink: 0;
    transition: all $transition-fast;
    &.lit {
      background: $color-warning-soft;
      color: $color-warning;
    }
  }

  .ck-head-text { display: flex; flex-direction: column; line-height: 1.3; }
  .ck-streak { font-size: $fs-lg; font-weight: 600; color: $text-title;
    b { color: $color-primary; font-size: $fs-3xl; margin: 0 2px; font-weight: 700; }
  }
  .ck-sub { font-size: $fs-xs; color: $text-caption; }

  .ck-btn {
    @include btn-primary;
    padding: 7px 18px;
    font-size: $fs-sm;
    border-radius: $radius-pill;
    flex-shrink: 0;
    &.done {
      background: $bg-soft;
      color: $text-caption;
      border-color: $border-base;
      cursor: default;
    }
    &:disabled { opacity: 0.85; cursor: default; }
  }
  .ck-btn-pts {
    display: inline-flex;
    align-items: center;
    margin-left: 6px;
    padding: 1px 6px;
    font-size: 11px;
    line-height: 1.2;
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.22);
    color: #fff;
    font-weight: 500;
    letter-spacing: 0.2px;
  }
}

/* 主体：左侧连签奖励 + 右侧月历 */
.ck-body {
  display: flex;
  gap: $sp-3;
  padding-top: $sp-3;
  border-top: 1px dashed $border-light;
}

/* ---------- 连签奖励 侧栏 ---------- */
.ms-sidebar {
  flex-shrink: 0;
  width: 230px;
  display: flex;
  flex-direction: column;
  gap: 8px;

  .ms-side-title {
    display: flex; align-items: center; gap: 5px;
    font-size: $fs-sm; font-weight: 600; color: $text-caption;
    padding-bottom: 2px;
    :deep(.app-icon) { color: $color-primary; }
  }
  .ms-side-item {
    @include btn-base;
    flex-direction: row;
    align-items: center;
    gap: $sp-2;
    padding: 8px 9px;
    border-radius: $radius-base;
    background: $bg-soft;
    border: 1px solid $border-light;
    color: $text-caption;
    cursor: default;
    text-align: left;
    width: 100%;

    .ms-side-icon {
      width: 26px; height: 26px;
      border-radius: 7px;
      @include flex-center;
      background: $bg-card;
      color: $gray-5;
      flex-shrink: 0;
    }
    .ms-side-info { flex: 1; min-width: 0; line-height: 1.25; }
    .ms-side-days { font-size: $fs-xs; color: $text-caption; font-weight: 600; }
    .ms-side-reward {
      font-size: 11px; color: $text-caption; margin-top: 1px;
      span + span { margin-left: 4px; }
    }
    .ms-side-empty { color: $text-disabled; }

    // 右侧状态徽章
    .ms-side-action {
      flex-shrink: 0;
      display: inline-flex;
      align-items: center;
      gap: 2px;
      padding: 3px 7px;
      border-radius: $radius-pill;
      font-size: 11px;
      font-weight: 600;
      line-height: 1;
      background: $bg-card;
      color: $text-disabled;
      border: 1px solid $border-light;
      :deep(.app-icon) { color: inherit; }
    }

    &.claimable {
      background: $primary-1;
      border-color: $primary-3;
      cursor: pointer;
      animation: pulse 2s infinite;
      .ms-side-icon { background: $color-primary; color: #fff; }
      .ms-side-days { color: $color-primary; }
      .ms-side-action {
        background: $color-primary;
        color: #fff;
        border-color: $color-primary;
      }
    }
    &.claimed {
      background: $color-success-soft;
      border-color: rgba(82, 196, 26, 0.28);
      opacity: 0.78;
      .ms-side-icon { background: $color-success; color: #fff; }
      .ms-side-days { color: $color-success; }
      .ms-side-action {
        background: $color-success-soft;
        color: $color-success;
        border-color: rgba(82, 196, 26, 0.32);
      }
    }
    &.locked {
      background: $gray-3;
      .ms-side-days { color: $text-disabled; }
      .ms-side-reward { color: $text-disabled; }
    }
  }
}

/* ---------- 月历 ---------- */
.cal-wrap { flex: 1; min-width: 0; }

.cal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-2;
  margin-bottom: 8px;

  .cal-nav {
    @include btn-base;
    width: 28px; height: 28px;
    border-radius: 8px;
    background: $bg-soft;
    border: 1px solid $border-light;
    color: $text-caption;
    @include flex-center;
    padding: 0;
    &:hover:not(:disabled) { color: $color-primary; border-color: $primary-3; background: $primary-1; }
    &:disabled { opacity: 0.4; cursor: not-allowed; }
  }
  .cal-title {
    font-size: $fs-lg;
    font-weight: 600;
    color: $text-title;
  }
}

.cal-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px; }

.cal-weekrow { margin-bottom: 4px; }
.cal-hcell {
  height: 18px;
  @include flex-center;
  font-size: 11px;
  color: $text-disabled;
  font-weight: 500;
  letter-spacing: 0.06em;
}

.cal-cell-wrap {
  display: contents;
}

.cal-cell {
  height: 32px;
  @include flex-center;
  position: relative;
  background: transparent;

  .cal-inner {
    width: 30px;
    height: 30px;
    @include flex-center;
    border-radius: 50%;
    position: relative;
    transition: background $transition-fast;
  }
  &:hover:not(.empty):not(.today) .cal-inner {
    background: rgba(64, 158, 255, 0.08);
  }
  .cal-num {
    font-weight: 500;
    font-size: 12px;
    line-height: 1;
    color: $text-body;
    letter-spacing: 0.01em;
  }

  &.empty { background: transparent; pointer-events: none; }
  &.empty .cal-inner { display: none; }

  // 今日——深品牌色实心圆
  &.today .cal-inner {
    background: $color-primary;
  }
  &.today .cal-num {
    color: #fff;
    font-weight: 600;
  }

  // 已签（非今日）——浅品牌色填充圆
  &.checked:not(.today) .cal-inner {
    background: rgba(58, 140, 137, 0.12);
  }
  &.checked:not(.today) .cal-num {
    color: $color-primary;
    font-weight: 600;
  }

  &.past_missed .cal-num { color: $text-disabled; }
  &.future .cal-num { color: $text-disabled; opacity: 0.55; }
}

/* ---------- 区块通用头 ---------- */
.sec-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $sp-4;
}
.sec-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: $fs-lg;
  font-weight: 600;
  color: $text-title;
  :deep(.app-icon) { color: $color-primary; }
}
.sec-hint, .act-points { font-size: $fs-sm; color: $text-caption; }
.act-points b { color: $color-primary; font-size: $fs-xl; }
.act-points .act-info {
  margin-left: 4px;
  color: $gray-5;
  cursor: help;
  transition: color $transition-fast;
}
.act-points .act-info:hover { color: $color-primary; }
.act-tip {
  font-size: $fs-sm;
  min-width: 170px;
  .act-tip-title { font-weight: 600; color: $text-title; margin-bottom: 6px; padding-bottom: 4px; border-bottom: 1px dashed $border-base; }
  .act-tip-empty { color: $text-caption; padding: 2px 0 6px; }
  .act-tip-row { display: flex; justify-content: space-between; gap: 16px; padding: 2px 0; color: $text-secondary; }
  .act-tip-pts { color: $color-primary; font-weight: 600; }
  .act-tip-sum { display: flex; justify-content: space-between; gap: 16px; margin-top: 6px; padding-top: 4px; border-top: 1px dashed $border-base; color: $text-title; font-weight: 600; }
}

/* 奖励小标签 */
.rw {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 8px;
  border-radius: $radius-pill;
  font-size: $fs-xs;
  font-weight: 600;
  &.rw-ai { background: $gray-3; color: $text-caption; }
  &.rw-coin { background: $color-warning-soft; color: #B26A00; }
  &.rw-none { background: $gray-3; color: $text-caption; }
}

/* ---------- 活跃度进度条 ---------- */
.act-bar {
  position: relative;
  height: 8px;
  background: $gray-3;
  border-radius: $radius-pill;
  margin: $sp-10 $sp-6 $sp-12;

  .act-fill {
    height: 100%;
    border-radius: $radius-pill;
    background: linear-gradient(90deg, #6BB5B0 0%, #3A8C89 100%);
    transition: width $transition-slow;
    max-width: 100%;
  }

  .act-node {
    position: absolute;
    top: 50%;
    transform: translate(-50%, -50%);
    text-align: center;
    cursor: default;

    .an-badge {
      @include flex-center;
      width: 30px;
      height: 30px;
      margin: 0 auto;
      border-radius: 50%;
      background: #fff;
      border: 2px solid $gray-4;
      color: $text-disabled;
      line-height: 0;
      transition: all $transition-fast;
    }
    .an-label {
      font-size: $fs-xs;
      color: $text-caption;
      margin-top: 7px;
      font-weight: 600;
      font-variant-numeric: tabular-nums;
    }

    /* 可领取：暖橙描边 + 轻呼吸，点击领取 */
    &.claimable {
      cursor: pointer;
      .an-badge {
        border-color: #F5A623;
        color: #F5A623;
        box-shadow: 0 0 0 4px rgba(245, 166, 35, 0.14);
        animation: nodeBreathe 2s ease-in-out infinite;
      }
      .an-label { color: #C97A0E; }
    }
    /* 已领取：主题绿实心 + 白勾 */
    &.claimed {
      .an-badge {
        background: $color-primary;
        border-color: $color-primary;
        color: #fff;
      }
      .an-label { color: $color-primary; }
    }
    /* 未达成：灰锁 */
    &.locked .an-label { color: $text-disabled; }
  }
}

/* 活跃度奖励明细：四张"奖励券" */
.act-rewards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $sp-3;
}
.ar-item {
  position: relative;
  @include inset-block($sp-4 $sp-4, $radius-base);
  display: flex;
  flex-direction: column;
  gap: 7px;
  text-align: center;
  align-items: center;
  background: #FBFAF7;
  border: 1px solid $border-light;
  transition: all $transition-fast;

  /* 顶部细色条:区分档位状态 */
  &::before {
    content: '';
    position: absolute;
    top: -1px; left: 14px; right: 14px;
    height: 3px;
    border-radius: 0 0 4px 4px;
    background: $gray-4;
  }
  &.claimed::before { background: $primary-3; }
  &.claimable::before { background: #F5A623; }

  &.claimable {
    border-color: rgba(245, 166, 35, 0.45);
    background: #FFFDF6;
  }
  &.claimed { opacity: 0.72; }

  .ar-th { display: flex; flex-direction: column; gap: 2px; align-items: center; }
  .ar-score { font-size: $fs-md; font-weight: 700; color: $text-title; letter-spacing: 0.5px; }
  .ar-status {
    display: inline-flex; align-items: center; gap: 3px;
    font-size: $fs-xs; color: $text-caption;
  }
  .ar-rw { display: flex; flex-direction: column; gap: 4px; }

  .ar-btn {
    @include btn-base;
    width: 100%;
    padding: 6px 0;
    font-size: $fs-sm;
    border-radius: $radius-pill;
    margin-top: 2px;
    background: $bg-card;
    color: $text-caption;
    border: 1px solid $border-base;
    cursor: default;
    font-weight: 600;
    transition: all $transition-fast;
    &.claimable {
      background: #F5A623;
      color: #fff;
      border-color: transparent;
      box-shadow: 0 2px 8px rgba(245, 166, 35, 0.35);
      cursor: pointer;
      &:hover { background: #E6951A; box-shadow: 0 3px 10px rgba(245, 166, 35, 0.45); transform: translateY(-1px); }
      &:active { transform: translateY(0); }
    }
    &.claimed, &.locked { background: transparent; color: $text-caption; border-color: $border-base; cursor: default; }
    &.locked { color: $text-disabled; }
  }
}

/* ---------- 今日任务 ---------- */
.task-list { padding: $sp-5 $sp-6; }
.tl-head { @include flex-between; margin-bottom: $sp-3; }
.tl-title { font-size: $fs-lg; font-weight: 600; color: $text-title; }
.tl-progress { font-size: $fs-sm; color: $text-caption; }
.tl-progressbar {
  height: 6px;
  background: $gray-3;
  border-radius: $radius-pill;
  overflow: hidden;
  margin-bottom: $sp-3;
  .tlp-fill {
    height: 100%;
    background: $gradient-primary;
    border-radius: $radius-pill;
    transition: width $transition-slow;
  }
}

.tl-row {
  display: flex;
  align-items: center;
  gap: $sp-3;
  padding: $sp-3 0;
  border-bottom: 1px solid $border-light;
  cursor: pointer;
  transition: background $transition-fast;
  &:hover:not(.is-done) { background: $bg-soft; margin: 0 -#{$sp-3}; padding-left: $sp-3; padding-right: $sp-3; border-radius: $radius-base; }
  &:last-child { border-bottom: none; }
  &.is-done { opacity: 0.6; cursor: default; }

  .tl-icon {
    width: 38px; height: 38px;
    border-radius: 10px;
    background: $primary-1;
    color: $color-primary;
    @include flex-center;
    flex-shrink: 0;
    &.tli-done { background: $color-success-soft; color: $color-success; }
  }
  .tl-body { flex: 1; min-width: 0; }
  .tl-name {
    font-size: $fs-md; font-weight: 600; color: $text-title;
    display: flex; align-items: center; gap: 7px;
  }
  .tl-points {
    display: inline-flex; align-items: center; gap: 2px;
    padding: 2px 7px;
    border-radius: $radius-pill;
    font-size: 11px;
    font-weight: 600;
    line-height: 1;
    background: $primary-1;
    color: $color-primary;
    :deep(.app-icon) { color: inherit; }
    &.tl-points-done {
      background: $color-success-soft;
      color: $color-success;
    }
  }
  .tl-desc { margin-top: 2px; font-size: $fs-sm; color: $text-caption; }
  .tl-right { flex-shrink: 0; }
  .tl-go {
    display: inline-flex; align-items: center;
    font-size: $fs-sm; color: $color-primary; font-weight: 600;
  }
}

/* ---------- 句灵日选卡（替换原今日积分明细） ---------- */
.ds-card {
  padding: $sp-5 $sp-6;
  background: linear-gradient(135deg, #F7F8FF 0%, #FFFFFF 60%);
  border: 1px solid rgba(155, 126, 247, 0.18);
}
.ds-head {
  display: flex; align-items: center; gap: 7px;
  font-size: $fs-md; font-weight: 600; color: $text-title;
  margin-bottom: $sp-4;
  :deep(.app-icon) { color: #9B7EF7; }
  .ds-date {
    margin-left: auto;
    font-size: 12px;
    font-weight: 600;
    color: $color-primary;
    background: rgba(155, 126, 247, 0.1);
    padding: 3px 10px;
    border-radius: $radius-pill;
    font-variant-numeric: tabular-nums;
  }
}
.ds-empty {
  display: flex; align-items: center; gap: 6px;
  padding: 22px 0;
  font-size: 13px;
  color: $text-caption;
  justify-content: center;
}
.ds-fallback-tag {
  display: inline-flex; align-items: center; gap: 4px;
  margin-bottom: 10px;
  font-size: 11px;
  color: #C77E00;
  background: rgba(250, 140, 22, 0.08);
  padding: 2px 8px;
  border-radius: $radius-pill;
}
.ds-en {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.55;
  color: $text-title;
  letter-spacing: 0.1px;
  margin-bottom: 8px;
  font-style: italic;
}
.ds-cn {
  font-size: 13px;
  color: $text-body;
  line-height: 1.7;
  margin-bottom: 12px;
}
.ds-key {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 10px;
  background: rgba(58, 140, 137, 0.08);
  border-radius: 8px;
  font-size: 12px;
  color: $text-title;
  font-weight: 500;
  margin-bottom: 8px;
  :deep(.app-icon) { color: #3A8C89; flex-shrink: 0; }
}
.ds-tags {
  display: flex; flex-wrap: wrap; gap: 6px;
  margin-bottom: 8px;
}
.ds-tag {
  font-size: 11px;
  color: $color-primary;
  background: $primary-1;
  padding: 2px 8px;
  border-radius: $radius-pill;
  font-weight: 500;
}
.ds-note {
  display: flex; align-items: flex-start; gap: 6px;
  font-size: 12px;
  color: $text-caption;
  line-height: 1.65;
  padding: 8px 10px;
  background: rgba(0,0,0,0.02);
  border-radius: 8px;
  margin-bottom: 4px;
  :deep(.app-icon) { color: $text-caption; flex-shrink: 0; margin-top: 1px; }
}
.ds-footer {
  margin-top: $sp-3;
  padding-top: $sp-3;
  border-top: 1px dashed $border-light;
  font-size: 11px;
  color: $text-caption;
  text-align: center;
  b { color: $color-primary; font-weight: 700; }
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(58, 140, 137, 0.18); }
  50% { box-shadow: 0 0 0 5px rgba(58, 140, 137, 0); }
}
@keyframes nodeBreathe {
  0%, 100% { box-shadow: 0 0 0 4px rgba(245, 166, 35, 0.14); }
  50%      { box-shadow: 0 0 0 7px rgba(245, 166, 35, 0.05); }
}

@media (max-width: 980px) {
  .task-grid { grid-template-columns: 1fr; }
  .act-rewards { grid-template-columns: repeat(2, 1fr); }
  .ms-sidebar { width: 130px; }
}
@media (max-width: 640px) {
  .act-rewards { grid-template-columns: 1fr; }
  .ck-body { flex-direction: column; }
  .ms-sidebar { width: 100%; flex-direction: row; flex-wrap: wrap; }
  .ms-side-title { width: 100%; }
  .ms-side-item { flex: 1 1 calc(50% - 4px); }
}
</style>
