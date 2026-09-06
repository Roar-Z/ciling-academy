<template>
  <div class="page-container report-page">
    <!-- ============ 页头 ============ -->
    <div class="page-header">
      <div class="page-header-main">
        <h1 class="page-title">学习报告</h1>
        <p class="page-desc">学习数据总览，记录你的每一步进步</p>
      </div>
      <button class="btn-secondary" :disabled="loading" @click="refresh">
        <AppIcon name="refresh-cw" :size="14" :class="{ 'is-spin': loading }" />
        <span>{{ loading ? '刷新中…' : '刷新数据' }}</span>
      </button>
    </div>

    <!-- 首次进入用 skeleton 占位，不会"闪黑"；后续 refresh 走按钮 loading 态，不再遮挡内容 -->
    <el-skeleton v-if="firstLoading" :rows="0" animated class="report-skeleton">
      <template #template>
        <!-- KPI 区骨架：1.5fr + 1fr + 1fr + 1fr 网格 -->
        <div class="kpi-grid">
          <el-skeleton-item variant="rect" class="sk-tile sk-hero" />
          <el-skeleton-item variant="rect" class="sk-tile sk-card" />
          <el-skeleton-item variant="rect" class="sk-tile sk-card" />
          <el-skeleton-item variant="rect" class="sk-tile sk-card" />
        </div>
        <!-- 复习 banner 骨架 -->
        <el-skeleton-item variant="rect" class="sk-tile sk-banner" />
        <!-- 主体区骨架（游戏战绩 + 最近记录 两块） -->
        <el-skeleton-item variant="rect" class="sk-tile sk-block" />
      </template>
    </el-skeleton>

    <div v-else class="report-content">
      <!-- ============ 顶部 KPI 区 ============ -->
      <section class="kpi-section">
        <!-- Hero 主指标 + 三个辅指标，4 列网格 -->
        <div class="kpi-grid">
          <!-- Hero：今日学习单词（深色实色背景，单色，不用渐变） -->
          <div class="kpi-hero">
            <div class="kpi-hero-head">
              <span class="kpi-hero-label">今日学习单词</span>
              <span class="kpi-hero-badge">
                <AppIcon name="flame" :size="12" />
                连续 {{ userStore.userInfo?.studyDays ?? 0 }} 天
              </span>
            </div>
            <div class="kpi-hero-num">
              <span class="num">{{ userStore.userInfo?.todayWords ?? 0 }}</span>
              <span class="unit">词</span>
            </div>
            <div class="kpi-hero-divider"></div>
            <div class="kpi-hero-foot">
              <span class="hf-item">累计 <b>{{ userStore.userInfo?.totalWords ?? 0 }}</b> 词</span>
              <span class="hf-sep">·</span>
              <span class="hf-item">金币余额 <b>{{ coinBalance }}</b></span>
            </div>
          </div>

          <!-- 辅指标 1：连续学习天数 -->
          <div class="kpi-card">
            <div class="kpi-card-head">
              <div class="kpi-card-icon"><AppIcon name="calendar-check" :size="18" /></div>
              <div class="kpi-card-label">连续学习天数</div>
            </div>
            <div class="kpi-card-value">
              <span class="cv-num">{{ userStore.userInfo?.studyDays ?? 0 }}</span>
              <span class="cv-unit">天</span>
            </div>
          </div>

          <!-- 辅指标 2：累计 AI 调用 -->
          <div class="kpi-card">
            <div class="kpi-card-head">
              <div class="kpi-card-icon"><AppIcon name="sparkles" :size="18" /></div>
              <div class="kpi-card-label">累计 AI 调用</div>
            </div>
            <div class="kpi-card-value">
              <span class="cv-num">{{ userStore.userInfo?.aiUsedTotal ?? 0 }}</span>
              <span class="cv-unit">次</span>
            </div>
          </div>

          <!-- 辅指标 3：待复习单词 -->
          <div class="kpi-card">
            <div class="kpi-card-head">
              <div class="kpi-card-icon"><AppIcon name="bell" :size="18" /></div>
              <div class="kpi-card-label">待复习单词</div>
            </div>
            <div class="kpi-card-value">
              <span class="cv-num">{{ dueCount }}</span>
              <span class="cv-unit">个</span>
            </div>
          </div>
        </div>
      </section>

      <!-- ============ 复习 Banner ============ -->
      <div class="review-banner" :class="{ urgent: dueCount > 0 }">
        <div class="rb-icon">
          <AppIcon name="bookmark" :size="20" />
        </div>
        <div class="rb-body">
          <div class="rb-title">{{ dueCount > 0 ? '今日有单词到了复习时间' : '今日复习已清空' }}</div>
          <div class="rb-desc">
            <template v-if="dueCount > 0">
              有 <b>{{ dueCount }}</b> 个单词需要复习，趁热打铁效果更好
            </template>
            <template v-else>
              今天没有待复习单词，去学点新词充实自己吧
            </template>
          </div>
        </div>
        <button
          class="rb-btn"
          :class="dueCount > 0 ? 'btn-primary' : 'btn-secondary'"
          @click="go('/review')"
        >
          {{ dueCount > 0 ? '立即复习' : '去学新词' }}
          <AppIcon name="arrow-right" :size="14" style="margin-left:2px" />
        </button>
      </div>

      <!-- ============ 主体区 ============ -->
      <section class="report-main">
        <!-- 游戏战绩：2x5 卡片网格 -->
        <div class="game-report">
          <div class="block-head">
            <h3 class="block-title">小游戏战绩</h3>
            <span class="block-sub">各游戏历史最高分</span>
          </div>
          <div class="game-grid">
            <div
              v-for="g in gameStatsList"
              :key="g.id"
              class="game-tile"
              :class="{ empty: g.bestScore === 0 }"
              @click="goGame(g)"
            >
              <div class="gt-icon">
                <AppIcon :name="g.icon" :size="18" />
              </div>
              <div class="gt-body">
                <div class="gt-name">{{ g.name }}</div>
                <div class="gt-score">
                  <span class="gt-num">{{ g.bestScore }}</span>
                  <span class="gt-unit">最高分</span>
                </div>
                <div class="gt-bar">
                  <div class="gt-fill" :style="{ width: barWidth(g.bestScore) }"></div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 最近游戏记录 -->
        <div v-if="recentRecords.length" class="recent-report">
          <div class="block-head">
            <h3 class="block-title">最近游戏记录</h3>
            <span class="block-sub">最近 {{ recentRecords.length }} 条</span>
          </div>
          <div class="rr-list">
            <div class="rr-head">
              <span>游戏</span>
              <span>得分</span>
              <span>正确率</span>
              <span>金币</span>
              <span>时间</span>
            </div>
            <div v-for="(r, i) in recentRecords" :key="i" class="rr-row">
              <span class="rr-game">{{ r.gameName }}</span>
              <span class="rr-num">{{ r.score }}</span>
              <span class="rr-rate">
                <span class="rr-rate-bar"><span :style="{ width: r.correctRate + '%' }"></span></span>
                <span class="rr-rate-num">{{ r.correctRate }}%</span>
              </span>
              <span class="rr-coin">+{{ r.coins }}</span>
              <span class="rr-time">{{ r.createdAt }}</span>
            </div>
          </div>
        </div>

        <!-- 占位：暂无记录 -->
        <div v-else class="recent-report recent-empty">
          <div class="block-head">
            <h3 class="block-title">最近游戏记录</h3>
            <span class="block-sub">还没有记录</span>
          </div>
          <div class="empty-tip">
            <div class="empty-icon"><AppIcon name="gamepad-2" :size="28" /></div>
            <p class="empty-text">去趣味乐园玩一局，记录就会出现在这里</p>
            <button class="btn-secondary" @click="go('/game-park')">前往趣味乐园</button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import { dueReview } from '@/api/wordBook'
import { myCoin } from '@/api/shop'
import { gameStats, myGameRecords } from '@/api/game'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
/** 首次进入用骨架屏占位，避免 v-loading 闪屏。后续 refresh 走按钮 loading 态 */
const firstLoading = ref(true)
const coinBalance = ref(0)
const dueCount = ref(0)
const recentRecords = ref([])
const gameStatsList = ref([])

const GAMES = [
  { id: 'game1', name: '单词消消乐', icon: 'puzzle' },
  { id: 'game2', name: '气球打单词', icon: 'circle-dot' },
  { id: 'game3', name: '字母拼拼乐', icon: 'square-stack' },
  { id: 'game4', name: '词义连连看', icon: 'link-2' },
  { id: 'game5', name: '单词填空闯关', icon: 'square-pen' },
  { id: 'game6', name: '单词快跑', icon: 'zap' },
  { id: 'game7', name: '单词炸弹危机', icon: 'bomb' },
  { id: 'game8', name: '分类大师', icon: 'layers' },
  { id: 'game9', name: '音标拼词', icon: 'music-2' },
  { id: 'game10', name: '单词故事接龙', icon: 'book-text' }
]

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  try {
    await userStore.fetchInfo()
    const coin = await myCoin()
    coinBalance.value = coin.coinBalance || 0
    const due = await dueReview(100)
    dueCount.value = due.length

    const stats = await Promise.all(
      GAMES.map(async (g) => {
        try {
          const s = await gameStats({ gameId: g.id })
          return { ...g, bestScore: s.bestScore || 0, playCount: s.playCount || 0 }
        } catch (e) {
          return { ...g, bestScore: 0, playCount: 0 }
        }
      })
    )
    gameStatsList.value = stats

    const records = await myGameRecords({ page: 1, size: 30 })
    recentRecords.value = (records.records || []).map((r) => ({
      ...r,
      // ISO 时间（2026-09-04T00:23:43）→ 空格分隔
      createdAt: r.createdAt ? String(r.createdAt).replace('T', ' ').slice(0, 19) : ''
    }))
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
    firstLoading.value = false
  }
}

function refresh() {
  loadAll()
}

function barWidth(best) {
  const max = Math.max(...gameStatsList.value.map((g) => g.bestScore), 100)
  return Math.max(0, Math.round((best / max) * 100)) + '%'
}

function go(path) {
  router.push(path)
}

function goGame(g) {
  router.push({ path: '/game-park', query: { game: g.id } })
}
</script>

<style lang="scss" scoped>
.report-page {
  padding-top: $sp-2;
}

/* ============================================
   骨架屏（首次加载，避免 v-loading 闪黑）
   ============================================ */
.report-skeleton {
  /* 骨架内网格布局，复用 kpi-grid 视觉 */
  .kpi-grid {
    display: grid;
    grid-template-columns: 1.5fr 1fr 1fr 1fr;
    gap: $sp-4;
    margin-bottom: $sp-4;
  }
  .sk-tile {
    border-radius: $radius-card;
  }
  .sk-hero {
    height: 132px;
  }
  .sk-card {
    height: 132px;
  }
  .sk-banner {
    height: 72px;
    margin: $sp-4 0;
  }
  .sk-block {
    height: 220px;
    margin-top: $sp-4;
  }
}

/* ============================================
   刷新按钮 spinner 动画
   ============================================ */
.is-spin {
  animation: btn-spin 0.9s linear infinite;
}

@keyframes btn-spin {
  to { transform: rotate(360deg); }
}

/* ============================================
   通用按钮（国内大厂风格：克制、圆角 6px、字重 500）
   ============================================ */
.btn-primary,
.btn-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 32px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0;
  border-radius: 6px;
  cursor: pointer;
  transition: all 160ms ease-out;
  border: 1px solid transparent;
  white-space: nowrap;
}

.btn-primary {
  background: $color-primary;
  color: #fff;
  border-color: $color-primary;
  &:hover { background: $primary-6; border-color: $primary-6; }
  &:active { background: $primary-7; border-color: $primary-7; }
}

.btn-secondary {
  background: #fff;
  color: $text-title;
  border-color: $border-base;
  &:hover {
    border-color: $color-primary;
    color: $color-primary;
    background: $primary-1;
  }
  &:active {
    background: $primary-2;
  }
}

/* ============================================
   页头
   ============================================ */
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $sp-4;
  margin-bottom: $sp-5;
  flex-wrap: wrap;
}

.page-title {
  font-size: 22px;
  font-weight: 600;
  color: $text-title;
  letter-spacing: -0.01em;
  line-height: 1.3;
  margin: 0 0 6px;
}

.page-desc {
  font-size: $fs-base;
  color: $text-caption;
  margin: 0;
  line-height: 1.5;
}

/* ============================================
   区块标题
   ============================================ */
.block-head {
  display: flex;
  align-items: baseline;
  gap: $sp-3;
  margin-bottom: $sp-4;
}

.block-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-title;
  letter-spacing: -0.005em;
  margin: 0;
}

.block-sub {
  font-size: $fs-base;
  color: $text-caption;
}

/* ============================================
   顶部 KPI 区
   ============================================ */
.kpi-section {
  margin-bottom: $sp-4;
}

.kpi-grid {
  display: grid;
  grid-template-columns: 1.5fr 1fr 1fr 1fr;
  gap: $sp-4;
}

/* --- Hero 主指标：深色实色背景（不用渐变）--- */
.kpi-hero {
  background: $primary-7;       /* 单色深绿，不用 linear-gradient */
  color: #fff;
  border-radius: $radius-card;
  padding: $sp-5 $sp-6;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 132px;
  position: relative;
  overflow: hidden;
}

.kpi-hero-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
}

.kpi-hero-label {
  font-size: $fs-base;
  color: rgba(255, 255, 255, 0.72);
  letter-spacing: 0.01em;
}

.kpi-hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 22px;
  padding: 0 10px;
  font-size: $fs-sm;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.92);
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 4px;             /* 圆角矩形，不用胶囊 */
}

.kpi-hero-num {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-variant-numeric: tabular-nums;
}

.kpi-hero-num .num {
  font-size: 40px;                /* 之前 48px 略大 */
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.02em;
}

.kpi-hero-num .unit {
  font-size: 14px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.6);
}

/* 极轻分隔线（白色 1px + 低透明度） */
.kpi-hero-divider {
  height: 1px;
  background: rgba(255, 255, 255, 0.1);
  margin: $sp-3 0;
}

.kpi-hero-foot {
  display: flex;
  align-items: center;
  gap: $sp-3;
  font-size: $fs-base;
  color: rgba(255, 255, 255, 0.7);
  font-variant-numeric: tabular-nums;

  b {
    font-weight: 600;
    color: #fff;
  }
}

.hf-sep {
  color: rgba(255, 255, 255, 0.32);
}

/* --- 辅指标：白底 + 单色图标（不用彩色 emoji 图标）--- */
.kpi-card {
  background: $bg-card;
  border: 1px solid $border-light;
  border-radius: $radius-card;
  padding: $sp-4 $sp-5;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 132px;
  transition: all 160ms ease-out;
}

.kpi-card-head {
  display: flex;
  align-items: center;
  gap: $sp-2;
}

.kpi-card-icon {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: $primary-1;        /* 统一主色浅底，不用橙/紫/蓝 */
  color: $color-primary;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.kpi-card-label {
  font-size: $fs-base;
  color: $text-caption;
  font-weight: 400;
}

.kpi-card-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-variant-numeric: tabular-nums;

  .cv-num {
    font-size: 28px;               /* 比 Hero 数字小，明确层级 */
    font-weight: 600;
    color: $text-title;
    letter-spacing: -0.01em;
    line-height: 1.1;
  }

  .cv-unit {
    font-size: $fs-base;
    color: $text-caption;
  }
}

/* ============================================
   复习 Banner
   ============================================ */
.review-banner {
  display: flex;
  align-items: center;
  gap: $sp-4;
  padding: $sp-4 $sp-5;
  background: $bg-card;
  border: 1px solid $border-light;
  border-radius: $radius-card;
  margin-bottom: $sp-4;
  transition: all 200ms ease-out;
}

.review-banner.urgent {
  background: $color-warning-soft;   /* 淡橙底，不用黄色渐变 */
  border-color: #FADC8C;
}

.rb-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: $color-primary;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.review-banner:not(.urgent) .rb-icon {
  background: $gray-3;
  color: $text-caption;
}

.rb-body {
  flex: 1;
  min-width: 0;
}

.rb-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-title;
  line-height: 1.4;
}

.rb-desc {
  margin-top: 3px;
  font-size: $fs-base;
  color: $text-caption;
  line-height: 1.5;

  b {
    color: $color-warning;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    margin: 0 2px;
  }
}

.review-banner:not(.urgent) .rb-desc b {
  color: $text-caption;
}

.rb-btn {
  flex-shrink: 0;
}

/* ============================================
   主体区
   ============================================ */
.report-main {
  display: flex;
  flex-direction: column;
  gap: $sp-4;
}

/* --- 游戏战绩 --- */
.game-report {
  background: $bg-card;
  border: 1px solid $border-light;
  border-radius: $radius-card;
  padding: $sp-5 $sp-6;
}

.game-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: $sp-3;
}

.game-tile {
  display: flex;
  align-items: center;
  gap: $sp-3;
  padding: $sp-3 $sp-4;
  background: $bg-card;
  border: 1px solid $border-light;
  border-radius: $radius-base;
  cursor: pointer;
  transition: all 160ms ease-out;

  &:hover {
    border-color: $color-primary;   /* hover 边框变主色（不浮起） */
    background: $primary-1;

    .gt-icon {
      background: $color-primary;
      color: #fff;
    }
  }

  &.empty {
    background: $bg-soft;
    border-style: dashed;
    cursor: default;

    &:hover {
      border-color: $border-light;
      background: $bg-soft;
      .gt-icon { background: $gray-3; color: $text-disabled; }
    }

    .gt-icon { background: $gray-3; color: $text-disabled; }
    .gt-num { color: $text-disabled; }
    .gt-bar { display: none; }
  }
}

.gt-icon {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: $primary-1;
  color: $color-primary;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 160ms ease-out;
}

.gt-body {
  flex: 1;
  min-width: 0;
}

.gt-name {
  font-size: 13px;
  font-weight: 500;
  color: $text-title;
  line-height: 1.3;
  @include ellipsis;
}

.gt-score {
  margin-top: 2px;
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-variant-numeric: tabular-nums;
}

.gt-num {
  font-size: 14px;
  font-weight: 600;
  color: $text-title;
}

.gt-unit {
  font-size: $fs-sm;
  color: $text-caption;
}

.gt-bar {
  margin-top: 6px;
  height: 3px;
  background: $bg-page;
  border-radius: 2px;
  overflow: hidden;
}

.gt-fill {
  height: 100%;
  background: $color-primary;       /* 纯色填充，不用渐变 */
  border-radius: 2px;
  transition: width 420ms ease-out;
}

/* --- 最近游戏记录 --- */
.recent-report {
  background: $bg-card;
  border: 1px solid $border-light;
  border-radius: $radius-card;
  padding: $sp-5 $sp-6;
}

.rr-list {
  border: 1px solid $border-light;
  border-radius: $radius-base;
  overflow: hidden;
}

.rr-head,
.rr-row {
  display: grid;
  /* 按内容分配：游戏名较宽；得分/金币内容极短收窄；正确率条和时间最宽 */
  grid-template-columns: 1.4fr 0.5fr 1.5fr 0.5fr 1.5fr;
  align-items: center;
  padding: 12px $sp-4;
  font-size: $fs-base;
}

.rr-head {
  background: $bg-soft;
  color: $text-caption;
  font-weight: 500;
  border-bottom: 1px solid $border-light;
}

.rr-row {
  border-bottom: 1px solid $border-light;
  color: $text-body;
  transition: background 160ms ease-out;

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: $bg-soft;
  }
}

.rr-game {
  font-weight: 500;
  color: $text-title;
  @include ellipsis;
}

.rr-num {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: $text-title;
}

.rr-rate {
  display: flex;
  align-items: center;
  gap: 8px;
  font-variant-numeric: tabular-nums;
}

.rr-rate-bar {
  width: 120px;
  flex: none;
  height: 4px;
  background: $bg-page;
  border-radius: 2px;
  overflow: hidden;

  span {
    display: block;
    height: 100%;
    background: $color-primary;
    border-radius: 2px;
    transition: width 240ms ease-out;
  }
}

.rr-rate-num {
  font-weight: 500;
  color: $text-body;
  font-size: $fs-sm;
  min-width: 36px;
  text-align: right;
}

.rr-coin {
  font-variant-numeric: tabular-nums;
  color: $color-warning;
  font-weight: 600;
}

.rr-time {
  color: $text-caption;
  font-size: $fs-sm;
  @include ellipsis;
}

/* --- 空状态 --- */
.recent-empty {
  display: flex;
  flex-direction: column;
}

.empty-tip {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $sp-3;
  padding: $sp-12 0;
  color: $text-caption;
}

.empty-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: $gray-3;
  color: $gray-5;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.empty-text {
  font-size: $fs-base;
  margin: 0;
  color: $text-caption;
}

/* ============================================
   响应式
   ============================================ */
@media (max-width: 1100px) {
  .kpi-grid {
    grid-template-columns: 1fr 1fr;
  }

  .kpi-hero {
    grid-column: span 2;
  }

  .game-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 700px) {
  .kpi-grid {
    grid-template-columns: 1fr;
  }

  .kpi-hero {
    grid-column: span 1;
    min-height: auto;
  }

  .kpi-hero-num .num {
    font-size: 32px;
  }

  .game-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .rr-head,
  .rr-row {
    grid-template-columns: 1.4fr 0.6fr 1fr 0.6fr 1.2fr;
    font-size: 12px;
    padding: 10px $sp-3;
  }

  .review-banner {
    flex-wrap: wrap;
  }
}
</style>