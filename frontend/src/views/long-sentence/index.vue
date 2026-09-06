<template>
  <div class="page-container long-sentence-page">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">长难句分析助手</h1>
        <p class="page-desc">
          AI 帮你拆主从结构、辨词块、讲明白语法，让复杂句子不再卡壳
        </p>
      </div>
      <div class="header-actions">
        <span class="quota-chip" v-if="quotaRemain !== null">
          <AppIcon name="zap" :size="13" />
          今日剩余 <b>{{ quotaRemain }}</b> 次
        </span>
        <el-button text type="primary" :icon="Collection" @click="go('/sentence')">句灵集</el-button>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="ws-card input-card">
      <div class="card-head-row">
        <span class="card-title">输入英文长难句</span>
        <span class="card-hint">每次解析消耗 1 次 AI 额度，相同句子自动缓存免扣</span>
      </div>
      <el-input
        v-model="input"
        type="textarea"
        :rows="4"
        :maxlength="500"
        show-word-limit
        resize="vertical"
        placeholder="把读不懂的英文长句粘进来，例如：Modern technology makes moving money around much easier than it used to be."
        class="input-area"
      />
      <div class="action-row">
        <div class="example-tips">
          <span class="example-label">没想好？试试：</span>
          <el-link type="primary" :underline="false" class="example-item" @click="fillExample(0)">
            {{ examples[0] }}
          </el-link>
        </div>
        <el-button
          type="primary"
          class="analyze-btn"
          :loading="loading"
          :disabled="!canAnalyze"
          @click="analyze"
        >
          {{ loading ? '分析中…' : '开始分析' }}
        </el-button>
      </div>
    </div>

    <!-- 结果区 -->
    <div v-if="result" class="ws-card result-card">
      <!-- 顶部信息：原句 + 难度 + 缓存 -->
      <div class="result-head">
        <div class="result-head-left">
          <span class="diff-badge" :class="`is-${result.difficultyKey}`">{{ result.difficulty }}</span>
          <span class="diff-line"></span>
          <span class="source-tip">来自 AI · 词灵学园</span>
          <span v-if="result.fromCache" class="cache-tip">已智能缓存 · 本次免扣</span>
        </div>
        <div class="result-head-right">
          <el-button text :icon="Refresh" @click="analyze" :disabled="loading">重新分析</el-button>
          <el-button
            type="primary"
            plain
            :icon="Collection"
            :disabled="savedId === result.sentence || saving"
            @click="saveToCollection"
          >
            {{ savedId === result.sentence ? '已加入句灵集' : '加入句灵集' }}
          </el-button>
        </div>
      </div>

      <p class="origin-line">{{ result.sentence }}</p>

      <!-- 整体翻译 -->
      <section class="block">
        <div class="block-title"><AppIcon name="languages" :size="16" />通顺译文</div>
        <p class="block-body">{{ result.analysis.cn_trans }}</p>
      </section>

      <!-- 整体结构（一句话讲明白） -->
      <section class="block">
        <div class="block-title">
          <AppIcon name="layout-template" :size="16" />整体结构
          <span class="block-label">{{ result.analysis.structure?.label }}</span>
        </div>
        <div class="block-intro">
          <AppIcon name="lightbulb" :size="14" color="#FAAD14" />
          <span>{{ result.analysis.structure?.intro }}</span>
        </div>
      </section>

      <!-- 分段解析（核心可视化） -->
      <section class="block">
        <div class="block-title"><AppIcon name="git-branch" :size="16" />逐段拆解</div>
        <div class="segments">
          <div
            v-for="(seg, idx) in result.analysis.structure?.segments || []"
            :key="idx"
            class="seg"
            :class="`seg-color-${((seg.role_color || 1) - 1) % 5 + 1}`"
          >
            <div class="seg-head">
              <span class="seg-role">{{ seg.role }}</span>
              <span class="seg-idx">第 {{ idx + 1 }} 段</span>
            </div>
            <p class="seg-text">{{ seg.text }}</p>
            <p class="seg-explain">
              <AppIcon name="message-circle" :size="13" />
              <span class="seg-explain-body" v-html="renderExplain(seg.explain)"></span>
            </p>
          </div>
        </div>
      </section>

      <!-- 词块 -->
      <section v-if="result.analysis.key_phrases?.length" class="block">
        <div class="block-title"><AppIcon name="puzzle" :size="16" />可复用词块</div>
        <div class="phrase-grid">
          <div v-for="(p, i) in result.analysis.key_phrases" :key="i" class="phrase">
            <span class="phrase-en">{{ p.phrase }}</span>
            <span class="phrase-cn">{{ p.cn }}</span>
            <el-button text size="small" :icon="CopyDocument" @click="copy(p.phrase)">复制</el-button>
          </div>
        </div>
      </section>

      <!-- 语法点 + 易错提醒 + 学习建议（双栏） -->
      <div class="bottom-grid">
        <section v-if="result.analysis.grammar_tips?.length" class="block grid-block">
          <div class="block-title"><AppIcon name="book-open" :size="16" />语法点</div>
          <ul class="tip-list is-grammar">
            <li v-for="(t, i) in result.analysis.grammar_tips" :key="i">{{ t }}</li>
          </ul>
        </section>

        <section v-if="result.analysis.common_mistakes?.length" class="block grid-block">
          <div class="block-title is-warn"><AppIcon name="alert-triangle" :size="16" />易错提醒</div>
          <ul class="tip-list is-mistake">
            <li v-for="(t, i) in result.analysis.common_mistakes" :key="i">{{ t }}</li>
          </ul>
        </section>
      </div>

      <section v-if="result.analysis.study_tip" class="block study-tip">
        <div class="block-title"><AppIcon name="sparkles" :size="16" />学习建议</div>
        <p class="study-tip-body">{{ result.analysis.study_tip }}</p>
      </section>
    </div>

    <!-- 空态 -->
    <div v-else-if="!loading" class="ws-card empty-card">
      <AppIcon name="book-marked" :size="40" color="#94A3B8" />
      <p class="empty-text">粘一段长难句，让 AI 帮你拆主干、辨从句、讲明白</p>
    </div>

    <!-- 加载态 -->
    <div v-else class="ws-card loading-card">
      <div class="loading-spinner"></div>
      <p class="loading-text">AI 正在拆解句子结构…</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Collection, CopyDocument, Refresh } from '@element-plus/icons-vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { analyzeLongSentence } from '@/api/longSentence'
import { addSentence } from '@/api/translate'
import { escapeHtml } from '@/components/ai/AiRenderUtil'
import { useUserStore } from '@/store/user'

/**
 * 把 seg.explain 中的"主语【xxx】/动词【xxx】/宾语【xxx】..."和〈xxx〉
 * 转成彩色 chip，去掉所有【】〈】括号，避免密密麻麻中括号看着乱。
 * 没匹配到的部分原样输出。
 */
const ROLE_RULES = [
  { role: '主语', key: 'subject',     color: '#2563eb' },
  { role: '谓语', key: 'verb',        color: '#d97706' },
  { role: '动词', key: 'verb',        color: '#d97706' },
  { role: '宾语', key: 'object',      color: '#16a34a' },
  { role: '定语', key: 'attr',        color: '#9333ea' },
  { role: '状语', key: 'adverbial',   color: '#0891b2' },
  { role: '补语', key: 'complement',  color: '#dc2626' },
  { role: '表语', key: 'predicative', color: '#ca8a04' }
]

const TEXT_STYLE       = 'color:#1f2937;font-weight:400;font-size:14px;line-height:1.85;'
const COPULA_STYLE     = 'color:#9ca3af;font-weight:400;margin:0 2px;font-size:14px;line-height:1.85;'
const DOT_STYLE        = 'color:#d1d5db;margin:0 4px;font-size:14px;line-height:1.85;'
const QUOTE_STYLE      = 'color:#6b7280;font-weight:500;font-size:14px;font-style:normal;'
// 词性文字样式：只承担颜色和字重（这些 inline style 100% 生效）
const ROLE_TEXT_STYLE  = 'font-weight:600;font-size:14px;line-height:1.85;'
// 竖线样式：用真正的 SVG <rect> 画一根 2x18 的灰矩形
// SVG 是浏览器原生图形元素，100% 渲染，不受任何 CSS reset / scoped / inline style 失效影响
const SEP_SVG          = '<svg width="2" height="16" viewBox="0 0 2 16" style="display:inline-block;vertical-align:middle;margin:0 20px;" aria-hidden="true"><rect x="0" y="0" width="2" height="16" fill="#cbd5e1" rx="1"/></svg>'
// 内容文字样式
const TEXT_STYLE_BASE  = 'color:#1f2937;font-size:14px;line-height:1.85;'

function renderExplain(text) {
  if (!text) return ''
  let html = escapeHtml(text)
  // 〈xxx〉 → 浅色 italic（去掉尖括号，保留短语）
  html = html.replace(/〈([^〈〉]+)〉/g, `<em style="${QUOTE_STYLE}">$1</em>`)
  // 主语【xxx】、动词是【xxx】、宾语为【xxx】... → 彩色 chip + 词性后插入独立竖线
  ROLE_RULES.forEach(({ role, color }) => {
    const re = new RegExp(`(${role})(是|为|的)?【([^【】]+)】`, 'g')
    html = html.replace(re, (_, roleText, copula, content) => {
      const copulaHtml = copula ? `<span style="${COPULA_STYLE}">${copula}</span>` : ''
      return `<b style="color:${color};${ROLE_TEXT_STYLE}">${roleText}</b>${SEP_SVG}${copulaHtml}<span style="${TEXT_STYLE_BASE}">${content}</span>`
    })
  })
  // 格式 B：自然语言格式（兼容"主语是xxx"、"主语 xxx"、"主语的xxx"等多种变体）
  const rolePattern = ROLE_RULES.map(r => r.role).join('|')
  const reB = new RegExp(`(${rolePattern})(?:是|为|的)?\\s*([^、，,。；\\n]{1,40}?)(?=[、，,。；\\n]|$)`, 'g')
  html = html.replace(reB, (match, roleText, content) => {
    if (match.indexOf('<b style="color:') !== -1) return match
    const rule = ROLE_RULES.find(r => r.role === roleText)
    if (!rule) return match
    return `<b style="color:${rule.color};${ROLE_TEXT_STYLE}">${roleText}</b>${SEP_SVG}<span style="${TEXT_STYLE_BASE}">${content}</span>`
  })
  // 兜底：任何残留的字面竖线（半角/全角）统一替换为带间距的装饰竖线
  html = html.replace(/[|｜]/g, SEP_SVG)
  html = html.replace(/、/g, `<span style="${DOT_STYLE}">、</span>`)
  html = html.replace(/[\[\]【】()（）〈〉《》「」『』""'']/g, '')
  return html
}

const router = useRouter()
const userStore = useUserStore()

const input = ref('')
const loading = ref(false)
const saving = ref(false)
const savedId = ref('') // 已加入收藏的原文 key
const result = ref(null) // { sentence, difficulty, analysis, fromCache }

// 直接复用全局额度（store 顶层 aiQuotaRemain），由 syncQuotaFromResp 实时同步，
// 这样导航栏 AI 徽标 / 页面顶部 chip / disabled 状态三处自动一致
const quotaRemain = computed(() => userStore.aiQuotaRemain)

const examples = [
  'Modern technology makes moving money around much easier than it used to be.'
]

const canAnalyze = computed(() => input.value.trim().length >= 5 && !loading.value)

function fillExample(i) {
  input.value = examples[i]
}

async function analyze() {
  if (!canAnalyze.value) return
  loading.value = true
  result.value = null
  try {
    const data = await analyzeLongSentence({ sentence: input.value.trim() })
    let parsed = null
    try { parsed = JSON.parse(data.analysisJson) } catch (e) { parsed = null }
    result.value = {
      sentence: data.sentence,
      difficulty: data.difficulty || '通用',
      difficultyKey: (data.difficulty || '').toLowerCase(),
      analysis: parsed || {},
      fromCache: !!data.fromCache
    }
    if (typeof data.quotaRemain === 'number') {
      // 同步到全局 store：导航栏 AI 徽标、翻译助手、阅读助手等都共用同一份数据
      userStore.syncQuotaFromResp(data)
    }
  } catch (e) {
    ElMessage.error(e?.message || '分析失败，请稍后再试')
  } finally {
    loading.value = false
  }
}

async function saveToCollection() {
  if (!result.value || saving.value) return
  saving.value = true
  try {
    await addSentence({
      originalText: result.value.sentence,
      translationText: result.value.analysis.cn_trans || '',
      direction: 'en2zh',
      mode: 'ai',
      source: 'long_sentence',
      sentenceTranslation: result.value.analysis.cn_trans || '',
      analysisJson: JSON.stringify(result.value.analysis)
    })
    savedId.value = result.value.sentence
    ElMessage.success('已加入句灵集')
  } catch (e) {
    ElMessage.error(e?.message || '加入句灵集失败')
  } finally {
    saving.value = false
  }
}

function copy(text) {
  navigator.clipboard?.writeText(text).then(
    () => ElMessage.success('已复制'),
    () => ElMessage.warning('复制失败，请手动复制')
  )
}

function go(path) {
  router.push(path)
}
</script>

<style lang="scss" scoped>
@use '@/assets/scss/variables.scss' as *;

.long-sentence-page {
  max-width: 920px;
  margin: 0 auto;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: $sp-3;
}

.quota-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: $primary-1;
  color: $color-primary-deep;
  font-size: $fs-xs;

  b {
    font-weight: 700;
    color: $color-primary;
  }
}

/* ---------- 输入卡 ---------- */
.input-card {
  padding: $sp-5;
}

.card-head-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: $sp-3;

  .card-title {
    font-weight: 600;
    font-size: $fs-md;
    color: $text-title;
  }
  .card-hint {
    font-size: $fs-xs;
    color: $text-caption;
  }
}

.input-area {
  :deep(.el-textarea__inner) {
    font-size: 15px;
    line-height: 1.7;
    padding: $sp-3 $sp-4;
    border-radius: $radius-base;
    border-color: $border-base;
    background: $bg-soft;
    transition: border-color 0.15s, background 0.15s;

    &:focus {
      background: $gray-1;
      border-color: $color-primary;
    }
  }
}

.action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
  margin-top: $sp-3;

  .example-tips {
    display: flex;
    align-items: center;
    gap: $sp-2;
    flex-wrap: wrap;
    min-width: 0;
  }
  .example-label {
    font-size: $fs-sm;
    color: $text-caption;
    flex-shrink: 0;
  }
  .example-item {
    font-size: $fs-sm;
    color: $color-primary;
    background: $primary-1;
    padding: 4px 10px;
    border-radius: 999px;
    transition: background 0.15s;

    &:hover {
      background: $primary-2;
    }
  }
  .analyze-btn {
    padding: 10px 22px;
    font-weight: 600;
    border-radius: $radius-base;
    box-shadow: 0 4px 12px rgba(58, 140, 137, 0.18);
  }
}

/* ---------- 结果卡 ---------- */
.result-card {
  padding: $sp-5 $sp-6;
}

.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
  flex-wrap: wrap;
  padding-bottom: $sp-4;
  border-bottom: 1px solid $border-light;
  margin-bottom: $sp-4;

  .result-head-left {
    display: flex;
    align-items: center;
    gap: $sp-3;
    flex-wrap: wrap;
  }
  .result-head-right {
    display: flex;
    gap: $sp-2;
  }
  .diff-badge {
    display: inline-flex;
    align-items: center;
    height: 24px;
    padding: 0 10px;
    border-radius: 999px;
    font-size: $fs-xs;
    font-weight: 600;
    background: $primary-1;
    color: $color-primary;

    &.is-cet4 { background: #E6F4FF; color: #1677FF; }
    &.is-cet6 { background: #FFF7E6; color: #D48806; }
    &.is-考研 { background: #F9F0FF; color: #722ED1; }
  }
  .diff-line {
    width: 1px;
    height: 14px;
    background: $border-light;
  }
  .source-tip {
    font-size: $fs-sm;
    color: $text-caption;
  }
  .cache-tip {
    font-size: $fs-xs;
    padding: 2px 8px;
    border-radius: 4px;
    background: $color-success-soft;
    color: $color-success;
  }
}

.origin-line {
  margin: 0 0 $sp-5;
  padding: $sp-3 $sp-4;
  background: $bg-soft;
  border-left: 3px solid $color-primary;
  border-radius: 4px;
  font-size: 17px;
  line-height: 1.7;
  font-weight: 500;
  color: $text-title;
  letter-spacing: 0.3px;
}

/* ---------- 通用 block ---------- */
.block {
  margin-bottom: $sp-5;

  &:last-child {
    margin-bottom: 0;
  }
}

.block-title {
  display: flex;
  align-items: center;
  gap: $sp-2;
  margin-bottom: $sp-3;
  font-weight: 600;
  font-size: $fs-md;
  color: $text-title;

  &.is-warn {
    color: $color-warning;
  }
  .block-label {
    margin-left: $sp-2;
    font-size: $fs-sm;
    font-weight: 500;
    color: $color-primary;
    background: $primary-1;
    padding: 2px 10px;
    border-radius: 999px;
  }
}

.block-body {
  margin: 0;
  padding: $sp-3 $sp-4;
  background: $bg-soft;
  border-radius: $radius-base;
  font-size: 15px;
  line-height: 1.85;
  color: $text-body;
}

.block-intro {
  display: flex;
  align-items: flex-start;
  gap: $sp-2;
  padding: $sp-3 $sp-4;
  background: linear-gradient(90deg, $primary-1 0%, transparent 80%);
  border-radius: $radius-base;
  font-size: 15px;
  line-height: 1.7;
  color: $text-title;
  font-weight: 500;
}

/* ---------- 分段解析 ---------- */
.segments {
  display: flex;
  flex-direction: column;
  gap: $sp-3;
}

.seg {
  position: relative;
  padding: $sp-4 $sp-4 $sp-4 $sp-5;
  border-radius: $radius-base;
  border: 1px solid $border-base;
  background: $gray-1;
  transition: box-shadow 0.15s, transform 0.15s;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 12px;
    bottom: 12px;
    width: 3px;
    border-radius: 0 2px 2px 0;
  }

  &:hover {
    box-shadow: $shadow-sm;
    transform: translateY(-1px);
  }

  .seg-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $sp-2;
  }
  .seg-role {
    font-size: $fs-sm;
    font-weight: 600;
    padding: 2px 10px;
    border-radius: 4px;
    background: $bg-soft;
  }
  .seg-idx {
    font-size: $fs-xs;
    color: $text-caption;
  }
  .seg-text {
    margin: 0 0 $sp-2;
    font-size: 16px;
    line-height: 1.7;
    color: $text-title;
    font-weight: 500;
  }
  .seg-explain {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    margin: 0;
    font-size: $fs-sm;
    line-height: 1.7;
    color: $text-body;

    .seg-explain-body {
      flex: 1;
      min-width: 0;
    }

    /* 行内语义着色：仅用字体颜色 + 字重标记角色，无背景块，保持专注力 */
    /* v-html 注入内容不带 scoped 的 data-v，Vue :deep() 编译产物对顶级独立
       选择器不可靠（实测颜色规则丢失）。chip 样式已移到下方独立的全局 <style> 块。 */
  }

  /* 5 种配色循环 */
  &.seg-color-1 {
    .seg-role { background: $primary-1; color: $color-primary; }
    &::before { background: $color-primary; }
  }
  &.seg-color-2 {
    .seg-role { background: #E6F4FF; color: #1677FF; }
    &::before { background: #1677FF; }
  }
  &.seg-color-3 {
    .seg-role { background: #FFF7E6; color: #D48806; }
    &::before { background: #D48806; }
  }
  &.seg-color-4 {
    .seg-role { background: #F9F0FF; color: #722ED1; }
    &::before { background: #722ED1; }
  }
  &.seg-color-5 {
    .seg-role { background: $color-success-soft; color: $color-success; }
    &::before { background: $color-success; }
  }
}

/* ---------- 词块 ---------- */
.phrase-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: $sp-3;
}

/* 词块卡片：纵向排版（英文在上、中文在下），复制按钮固定在右上角。
   避免长英文短语把中文挤成竖排导致卡片变形 */
.phrase {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-height: 74px;
  padding: $sp-3 $sp-4;
  background: $bg-soft;
  border-radius: $radius-base;
  border-left: 3px solid $color-primary;
  transition: background 0.15s, box-shadow 0.15s;

  &:hover {
    background: $primary-1;
    box-shadow: $shadow-sm;
  }
  .phrase-en {
    /* 预留右上角复制按钮的位置，长短语正常换行 */
    width: 100%;
    padding-right: 52px;
    font-weight: 600;
    color: $text-title;
    font-size: $fs-md;
    line-height: 1.5;
    word-break: break-word;
  }
  .phrase-cn {
    width: 100%;
    padding-right: 52px;
    color: $text-body;
    font-size: $fs-sm;
    line-height: 1.5;
  }
  :deep(.el-button) {
    position: absolute;
    top: 8px;
    right: 8px;
    color: $text-caption;
    padding: 2px 6px;
    font-size: $fs-xs;

    &:hover {
      color: $color-primary;
    }
  }
}

/* ---------- 双栏：语法点 + 易错 ---------- */
.bottom-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $sp-4;

  @media (max-width: 720px) {
    grid-template-columns: 1fr;
  }
}

.grid-block {
  margin-bottom: 0;
}

.tip-list {
  margin: 0;
  padding: 0;
  list-style: none;

  li {
    position: relative;
    padding: $sp-2 0 $sp-2 $sp-5;
    font-size: $fs-sm;
    line-height: 1.75;
    color: $text-body;
    border-bottom: 1px dashed $border-light;

    &:last-child {
      border-bottom: none;
    }

    &::before {
      content: '';
      position: absolute;
      left: 4px;
      top: 14px;
      width: 6px;
      height: 6px;
      border-radius: 50%;
    }
  }

  &.is-grammar li::before { background: $color-primary; }
  &.is-mistake li::before { background: $color-warning; }
}

/* ---------- 学习建议 ---------- */
.study-tip {
  margin-bottom: 0;
  padding: $sp-4;
  background: linear-gradient(135deg, $primary-1 0%, transparent 70%);
  border-radius: $radius-base;

  .block-title {
    color: $color-primary-deep;
  }
  .study-tip-body {
    margin: 0;
    font-size: 15px;
    line-height: 1.8;
    color: $text-title;
  }
}

/* ---------- 加载 / 空态 ---------- */
.empty-card,
.loading-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $sp-12 $sp-5;
  gap: $sp-3;
  color: $text-caption;
}

.empty-text {
  margin: 0;
  font-size: $fs-sm;
}

.loading-spinner {
  width: 28px;
  height: 28px;
  border: 2.5px solid $border-base;
  border-top-color: $color-primary;
  border-radius: 50%;
  animation: long-spin 0.75s linear infinite;
}

.loading-text {
  margin: 0;
  font-size: $fs-sm;
  color: $text-body;
}

@keyframes long-spin {
  to { transform: rotate(360deg); }
}
</style>