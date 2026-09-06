<template>
  <div class="page-container reading-page">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">阅读助手</h1>
        <p class="page-desc">粘贴英文段落，词灵AI输出结构化解析 · 生词本已收藏单词自动高亮</p>
      </div>
    </div>

    <div class="reading-layout">
      <!-- 左侧输入 -->
      <div class="ws-card input-card">
        <div class="ic-head">
          <span class="ic-icon"><AppIcon name="file-text" :size="18" /></span>
          <h3 class="ic-title">输入英文文本</h3>
        </div>

        <el-input
          v-model="text"
          type="textarea"
          :rows="15"
          resize="none"
          maxlength="3000"
          show-word-limit
          placeholder="在此粘贴英文段落，例如一篇文章、一段新闻或阅读理解…"
        />

        <div class="input-actions">
          <el-button @click="clearText">清空</el-button>
          <el-button type="primary" :loading="loading" :disabled="!text.trim()" @click="handleExplain">
            开始解析
          </el-button>
        </div>

        <div class="ic-tips-wrap" v-if="quotaExceeded || (userStore.aiQuotaRemain <= 0 && userStore.isLogin)">
          <AiQuotaTip :remaining="userStore.aiQuotaRemain" />
        </div>
        <div class="ic-tips-wrap" v-if="aiUnavailable">
          <AiFallbackTip />
        </div>
      </div>

      <!-- 右侧结果 -->
      <div class="ws-card result-card">
        <!-- 加载态 -->
        <div v-if="loading" class="result-body">
          <AiLoading text="词灵AI正在逐句解析你的文本…" />
        </div>

        <!-- 降级态 -->
        <div v-else-if="aiUnavailable" class="state-box">
          <div class="state-icon"><AppIcon name="cloud-off" :size="32" /></div>
          <p class="state-text">词灵AI暂时不可用，请稍后再试</p>
        </div>

        <!-- 空状态 -->
        <div v-else-if="!result" class="state-box">
          <div class="state-icon"><AppIcon name="book-open" :size="32" /></div>
          <p class="state-text">粘贴英文文本，点击「开始解析」生成阅读解析</p>
        </div>

        <!-- 结果态 -->
        <template v-else>
          <div class="result-head">
            <div class="rh-left">
              <span class="result-diff">句子解析</span>
            </div>
            <el-button size="small" type="primary" plain @click="openAddDialog">加入生词本</el-button>
          </div>

          <!-- 渲染层：JSON → HTML（用户看不到原始JSON） -->
          <div class="ai-content" v-html="highlightedHtml"></div>
        </template>
      </div>
    </div>

    <!-- 生词加入弹窗 -->
    <el-dialog
      v-model="addDialogVisible"
      width="500"
      align-center
      :show-close="false"
      class="add-dialog"
    >
      <button class="add-dialog-close" aria-label="关闭" @click="addDialogVisible = false">
        <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
          <path d="M3.5 3.5l9 9M12.5 3.5l-9 9" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" fill="none"/>
        </svg>
      </button>

      <header class="add-dialog-head">
        <h3 class="add-dialog-title">加入生词本</h3>
        <p class="add-dialog-sub">
          共 {{ keyWords.length }} 个候选
          <template v-if="ownedWords.length">· {{ ownedWords.length }} 个已加入</template>
        </p>
      </header>

      <div v-if="!keyWords.length" class="add-empty">
        本次解析未提取到重点词汇
      </div>
      <ul v-else class="word-list">
        <li
          v-for="w in keyWords"
          :key="w"
          class="wl-row"
          :class="{
            'is-on': checkedWords.includes(w),
            'is-disabled': ownedWords.includes(w)
          }"
          @click="ownedWords.includes(w) ? null : toggleCheck(w)"
        >
          <span class="wl-bar" aria-hidden="true"></span>
          <span class="wl-check" aria-hidden="true">
            <svg v-if="checkedWords.includes(w) || ownedWords.includes(w)" viewBox="0 0 12 12" width="11" height="11">
              <path d="M2.5 6.3l2.4 2.4L9.5 3.6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
            </svg>
          </span>
          <div class="wl-main">
            <div class="wl-word">{{ w }}</div>
            <div v-if="wordMeaningMap[w.toLowerCase()]?.phrase" class="wl-phrase">
              {{ wordMeaningMap[w.toLowerCase()].phrase }}
            </div>
          </div>
          <span v-if="ownedWords.includes(w)" class="wl-state">已加入</span>
        </li>
      </ul>

      <template #footer>
        <div class="add-dialog-foot">
          <div class="foot-left">
            <button class="wcb-link" @click="selectAll(true)">全选</button>
            <span class="wcb-dot">·</span>
            <button class="wcb-link" @click="selectAll(false)">全不选</button>
          </div>
          <div class="foot-right">
            <button class="foot-btn-text" @click="addDialogVisible = false">取消</button>
            <button
              class="foot-btn-primary"
              :disabled="!checkedWords.length || adding"
              @click="handleAddWords"
            >
              {{ adding ? '加入中…' : '加入生词本' }}
            </button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import AiLoading from '@/components/ai/AiLoading.vue'
import AiQuotaTip from '@/components/ai/AiQuotaTip.vue'
import AiFallbackTip from '@/components/ai/AiFallbackTip.vue'
import { renderAiContent, escapeHtml } from '@/components/ai/AiRenderUtil'
import { explainText } from '@/api/readingHelper'
import { checkExist, batchAddWords } from '@/api/wordBook'
import { useUserStore } from '@/store/user'
import AppIcon from '@/components/common/AppIcon.vue'

const userStore = useUserStore()

const text = ref('')
const loading = ref(false)
const adding = ref(false)
const aiUnavailable = ref(false)
const quotaExceeded = ref(false)
const result = ref(null)
const highlightedHtml = ref('')
const addDialogVisible = ref(false)
const checkedWords = ref([])
const ownedWords = ref([])
/** word → AI 给的简短中文释义（hard_points.phrase）。没有则后端走 dict 兜底 */
const wordMeaningMap = ref({})

const keyWords = computed(() => (result.value && result.value.keyWords) || [])
const selectableCount = computed(
  () => keyWords.value.filter((w) => !ownedWords.value.includes(w)).length
)



async function handleExplain() {
  if (!text.value.trim()) return
  if (userStore.aiQuotaRemain <= 0) {
    quotaExceeded.value = true
    return
  }
  loading.value = true
  aiUnavailable.value = false
  quotaExceeded.value = false
  result.value = null
  try {
    const data = await explainText({ text: text.value })
    result.value = data
    wordMeaningMap.value = parseExplainMap(data.explainJson)
    await renderResult(data)
    if (data.keyWords && data.keyWords.length) {
      try {
        ownedWords.value = await checkExist(data.keyWords)
      } catch (e) {
        ownedWords.value = []
      }
    } else {
      ownedWords.value = []
    }
    // 同步顶部额度（立刻刷新合并值 + 后台拉细分）
    userStore.syncQuotaFromResp(data)
    const remainText = data.quotaRemain ?? userStore.aiQuotaRemain
    if (data.fromCache) {
      ElMessage.success(`命中缓存，本次不消耗额度 · 今日剩余 ${remainText} 次`)
    } else {
      ElMessage.success(`解析完成，已消耗 1 次额度 · 今日剩余 ${remainText} 次`)
    }
  } catch (e) {
    if (e.code === 600) aiUnavailable.value = true
    else if (e.code === 601) {
      quotaExceeded.value = true
      userStore.aiQuotaRemain = 0
    }
    result.value = null
  } finally {
    loading.value = false
  }
}

/** 渲染 + 高亮生词本已收藏单词 */
async function renderResult(data) {
  const rendered = renderAiContent(data.explainJson)
  let html = rendered.html
  if (data.keyWords && data.keyWords.length) {
    try {
      const owned = await checkExist(data.keyWords)
      if (owned.length) {
        for (const w of owned) {
          const escaped = escapeHtml(w)
          const re = new RegExp(`(?<![\\w])(${escaped})(?![\\w])`, 'g')
          html = html.replace(re, '<mark class="book-mark">$1</mark>')
        }
      }
    } catch (e) {
      /* 高亮失败不影响主流程 */
    }
  }
  highlightedHtml.value = html
}

/** 从 AI JSON 抽取 word → 解析要点对象（释义/语法/搭配/近义/来源句），用于加入生词本 */
function parseExplainMap(jsonStr) {
  const map = {}
  if (!jsonStr) return map
  try {
    const obj = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr
    const sentences = Array.isArray(obj?.sentences) ? obj.sentences : []
    for (const s of sentences) {
      const points = Array.isArray(s?.hard_points) ? s.hard_points : []
      const origin = s?.origin || ''
      for (const p of points) {
        if (!p || !p.word) continue
        const key = String(p.word).trim().toLowerCase()
        if (!key) continue
        const entry = map[key] || {}
        if (p.phrase) entry.phrase = String(p.phrase).trim()
        if (p.explain) entry.explain = String(p.explain).trim()
        if (p.grammar) entry.grammar = String(p.grammar).trim()
        if (p.synonym) entry.synonym = String(p.synonym).trim()
        if (p.colloc) entry.colloc = String(p.colloc).trim()
        if (origin) entry.origin = origin
        map[key] = entry
      }
    }
  } catch (e) {
    /* 解析失败不影响主流程 */
  }
  return map
}

function openAddDialog() {
  checkedWords.value = keyWords.value.filter((w) => !ownedWords.value.includes(w))
  addDialogVisible.value = true
}

/** 全选 / 全不选（仅在可选范围内） */
function selectAll(on) {
  if (on) {
    checkedWords.value = keyWords.value.filter((w) => !ownedWords.value.includes(w))
  } else {
    checkedWords.value = []
  }
}

/** 切换勾选单词 */
function toggleCheck(w) {
  if (ownedWords.value.includes(w)) return
  const idx = checkedWords.value.indexOf(w)
  if (idx >= 0) checkedWords.value.splice(idx, 1)
  else checkedWords.value.push(w)
}

async function handleAddWords() {
  if (!checkedWords.value.length) {
    ElMessage.warning('请先勾选要加入的单词')
    return
  }
  adding.value = true
  try {
    const data = await batchAddWords({
      words: checkedWords.value.map((w) => {
        // 优先用 AI 给的 phrase；没有则不传 meaning，让后端 dict 兜底
        const info = wordMeaningMap.value[w.toLowerCase()] || {}
        const payload = { word: w, source: 'reading' }
        if (info.phrase) payload.meaning = info.phrase
        // 把阅读助手的语法/搭配/近义等要点一并带入生词本
        const hasUsage = info.grammar || info.colloc || info.synonym || info.explain || info.origin
        if (hasUsage) {
          payload.usage = JSON.stringify({
            phrase: info.phrase || '',
            grammar: info.grammar || '',
            synonym: info.synonym || '',
            colloc: info.colloc || '',
            explain: info.explain || '',
            origin: info.origin || ''
          })
        }
        return payload
      })
    })
    ElMessage.success(`已加入 ${data.added} 个生词到生词本`)
    addDialogVisible.value = false
    ownedWords.value = await checkExist(keyWords.value)
    await renderResult(result.value)
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    adding.value = false
  }
}

function clearText() {
  text.value = ''
}
</script>

<style lang="scss" scoped>
.reading-page {
  padding-top: $sp-6;
}

.reading-layout {
  display: grid;
  grid-template-columns: 400px 1fr;
  gap: $sp-4;
  align-items: start;
}

/* ---------- 输入卡 ---------- */
.input-card {
  .ic-head {
    display: flex;
    align-items: center;
    gap: $sp-2;
    margin-bottom: $sp-3;

    .ic-icon {
      font-size: 16px;
    }

    .ic-title {
      font-size: $fs-lg;
      font-weight: 600;
      color: $text-title;
    }
  }

  .input-actions {
    display: flex;
    justify-content: space-between;
    margin-top: $sp-3;
  }

  .ic-tips-wrap {
    margin-top: $sp-3;
  }
}

/* ---------- 结果卡 ---------- */
.result-card {
  min-height: 460px;

  .result-body {
    padding: $sp-2 0;
  }

  .result-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $sp-4;
    padding-bottom: $sp-3;
    border-bottom: 1px solid $border-light;

    .rh-left {
      display: flex;
      align-items: center;
      gap: $sp-2;
    }

    .result-diff {
      padding: 3px $sp-3;
      border-radius: $radius-pill;
      font-size: $fs-sm;
      font-weight: 600;
      background: $primary-1;
      color: $color-primary;

      &.easy {
        background: $color-success-soft;
        color: $color-success;
      }

      &.hard {
        background: $color-danger-soft;
        color: $color-danger;
      }
    }
  }

  .ai-content {
    max-height: 620px;
    overflow-y: auto;
    padding-right: $sp-1;
    @include thin-scrollbar;
  }
}

/* ---------- 加入生词本弹窗 ---------- */
.add-dialog {
  :deep(.el-dialog__header) {
    padding: 0;
    margin: 0;
    height: auto;
  }
  :deep(.el-dialog__headerbtn) {
    display: none;
  }
  :deep(.el-dialog__body) {
    padding: 0;
  }
  :deep(.el-dialog__footer) {
    padding: 0;
    margin: 0;
  }
  :deep(.el-dialog) {
    border-radius: 14px;
    overflow: hidden;
    box-shadow: 0 24px 64px rgba(0, 0, 0, 0.14);
  }
}

/* 关闭按钮 */
.add-dialog-close {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: $text-caption;
  border-radius: 50%;
  cursor: pointer;
  transition: all $transition-fast;
  z-index: 2;
  &:hover {
    background: $bg-hover;
    color: $text-title;
  }
}

/* header —— 紧凑 */
.add-dialog-head {
  padding: 14px 18px 10px;
}
.add-dialog-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: $text-title;
  letter-spacing: -0.01em;
}
.add-dialog-sub {
  margin: 2px 0 0;
  font-size: $fs-sm;
  color: $text-caption;
  line-height: 1.4;
}

/* 列表区 */
.word-list {
  list-style: none;
  margin: 0;
  padding: 2px 12px 6px;
  max-height: 320px;
  overflow-y: auto;
  @include thin-scrollbar;
}

.wl-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px 8px 16px;
  border-radius: $radius-base;
  cursor: pointer;
  user-select: none;
  transition: background $transition-fast;

  & + .wl-row {
    margin-top: 2px;
  }

  &:hover:not(.is-disabled):not(.is-on) {
    background: $bg-hover;
  }

  &.is-on {
    background: $primary-1;
    .wl-word { color: $color-primary-deep; font-weight: 600; }
  }

  &.is-disabled {
    cursor: default;
    .wl-word { color: $text-disabled; }
    .wl-phrase { color: $text-disabled; }
    .wl-bar { opacity: 0; }
    .wl-check {
      background: $gray-5;
      border-color: $gray-5;
      color: #fff;
    }
  }
}

/* 左侧主色竖条：选中态显示 */
.wl-bar {
  position: absolute;
  left: 4px;
  top: 8px;
  bottom: 8px;
  width: 2px;
  border-radius: 1px;
  background: $color-primary;
  opacity: 0;
  transition: opacity $transition-fast;
  .is-on & { opacity: 1; }
}

/* checkbox */
.wl-check {
  flex: 0 0 auto;
  width: 16px;
  height: 16px;
  border: 1.5px solid $border-base;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all $transition-fast;
  color: #fff;
  background: transparent;
  .is-on & {
    background: $color-primary;
    border-color: $color-primary;
  }
}

/* 主内容：词 + 短语 */
.wl-main {
  flex: 1 1 auto;
  min-width: 0;
}
.wl-word {
  font-size: $fs-md;
  font-weight: 500;
  color: $text-title;
  line-height: 1.4;
  letter-spacing: -0.005em;
}
.wl-phrase {
  margin-top: 2px;
  font-size: $fs-sm;
  color: $text-caption;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 右侧「已加入」标签 */
.wl-state {
  flex: 0 0 auto;
  font-size: $fs-xs;
  color: $text-disabled;
  padding: 2px 8px;
  border-radius: $radius-sm;
  background: $bg-soft;
}

/* 空状态 */
.add-empty {
  margin: 4px 22px 14px;
  padding: $sp-5;
  background: $bg-soft;
  border-radius: $radius-base;
  color: $text-caption;
  font-size: $fs-base;
  text-align: center;
}

/* footer：左侧全选/全不选 + 右侧取消 + 主按钮（去 AI 味） */
.add-dialog-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px 12px;
  border-top: 1px solid $border-light;
}
.foot-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.wcb-link {
  background: none;
  border: none;
  padding: 0;
  font-size: $fs-sm;
  color: $text-caption;
  cursor: pointer;
  transition: color $transition-fast;
  &:hover { color: $text-title; }
}
.wcb-dot {
  color: $border-base;
  font-size: $fs-sm;
  user-select: none;
}
.foot-right {
  display: flex;
  align-items: center;
  gap: 4px;
}
/* 取消：纯文字按钮，无视觉重量 */
.foot-btn-text {
  height: 30px;
  padding: 0 12px;
  background: none;
  border: none;
  color: $text-body;
  font-size: $fs-md;
  cursor: pointer;
  border-radius: 4px;
  transition: background $transition-fast, color $transition-fast;
  &:hover { background: $bg-hover; color: $text-title; }
}
/* 主操作——国内大厂「白底 + 主色描边 + 主色文字」克制风格
   参考：微信 / 飞书 / 语雀 / 掘金 / 知乎 弹窗 */
.foot-btn-primary {
  height: 30px;
  padding: 0 14px;
  background: #fff;                          // 白底（非实色品牌色）
  color: $color-primary;                     // 主色文字
  border: 1px solid $color-primary;          // 主色 1px 描边
  border-radius: 4px;                        // 圆角 4（更克制）
  font-size: $fs-md;
  font-weight: 500;
  letter-spacing: 0.01em;
  cursor: pointer;
  transition: background $transition-fast, color $transition-fast;

  &:hover:not(:disabled) {
    background: $primary-1;                  // hover 浅绿底
    color: $color-primary-deep;
    border-color: $color-primary-deep;
  }
  &:active:not(:disabled) {
    background: $primary-2;
  }
  &:disabled {
    color: $text-disabled;
    border-color: $border-base;
    background: transparent;
    cursor: not-allowed;
  }
}

@media (max-width: 1000px) {
  .reading-layout {
    grid-template-columns: 1fr;
  }
}
</style>
