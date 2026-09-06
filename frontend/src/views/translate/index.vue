<template>
  <div class="page-container translate-page">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">翻译助手</h1>
        <p class="page-desc">支持中↔英互译 · 普通翻译免额度 · 词灵AI 翻译深度解析 · 收藏到句灵集</p>
      </div>
      <div class="header-actions">
        <el-button text type="primary" :icon="Collection" @click="go('/sentence')">
          我的句灵集
        </el-button>
      </div>
    </div>

    <!-- 模式切换 -->
    <div class="mode-bar">
      <div class="mode-tabs">
        <button
          v-for="m in modes"
          :key="m.key"
          :class="['mode-tab', { 'is-active': mode === m.key }]"
          @click="setMode(m.key)"
        >
          <span class="mode-name">{{ m.name }}</span>
          <span class="mode-desc">{{ m.desc }}</span>
        </button>
      </div>
      <div v-if="mode === 'ai'" class="quota-pill">
        <AppIcon name="zap" :size="14" />
        <span>今日剩余 <b>{{ userStore.aiQuotaRemain }}</b> 次</span>
      </div>
    </div>

    <!-- 输入 -->
    <div class="ws-card input-card">
      <div class="ic-head">
        <span class="ic-icon"><AppIcon name="edit-3" :size="18" /></span>
        <h3 class="ic-title">{{ detectedHint }}</h3>
        <div class="lang-pill">
          <span>{{ srcLangLabel }}</span>
          <AppIcon name="arrow-right" :size="12" />
          <span>{{ tgtLangLabel }}</span>
        </div>
      </div>
      <el-input
        v-model="text"
        type="textarea"
        :rows="8"
        :autosize="{ minRows: 8, maxRows: 16 }"
        resize="none"
        maxlength="2000"
        show-word-limit
        :placeholder="placeholder"
      />
      <div class="input-actions">
        <div class="left">
          <el-button text :icon="RefreshLeft" @click="clearText">清空</el-button>
          <el-button text :icon="MagicStick" @click="fillExample">示例</el-button>
        </div>
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          :disabled="!text.trim()"
          @click="handleTranslate"
        >
          {{ mode === 'ai' ? '词灵AI 翻译' : '立即翻译' }}
        </el-button>
      </div>

      <!-- 额度耗尽 / AI 不可用 -->
      <div v-if="mode === 'ai' && quotaExceeded" class="quota-warn">
        <AppIcon name="zap-off" :size="16" />
        今日词灵AI额度已用完，明天再来，或使用普通翻译
      </div>
      <div v-if="mode === 'ai' && aiUnavailable" class="quota-warn">
        <AppIcon name="cloud-off" :size="16" />
        词灵AI暂时不可用，请稍后再试
      </div>
    </div>

    <!-- 结果 -->
    <div v-if="result || loading" class="result-area">
      <div v-if="loading" class="ws-card state-card">
        <AiLoading :text="mode === 'ai' ? '词灵AI 正在打磨译文…' : '翻译中…'" />
      </div>

      <!-- 普通翻译结果（词库逐词解析 / 中文反查） -->
      <div v-else-if="result && mode === 'normal'" class="ws-card normal-card">
        <div class="nc-head">
          <span class="nc-label">
            {{ result.srcLang === 'zh' ? '词库相关词参考（中→英完整翻译请用词灵AI 翻译）' : '基于词库逐词解析（基础翻译，仅作参考）' }}
          </span>
          <el-button
            :type="isCollected ? 'success' : 'primary'"
            :plain="isCollected"
            :disabled="isCollected"
            size="small"
            :icon="isCollected ? Check : Plus"
            @click="addToSentence(result.text, result.sentenceTranslation || result.translation, result.normalWords)"
          >
            {{ isCollected ? '已收藏' : '收藏到句灵集' }}
          </el-button>
        </div>
        <p v-if="result.srcLang !== 'zh' && (result.sentenceTranslation || result.translation)" class="nc-line">
          <span class="nc-line-prefix">{{ result.sentenceTranslation ? '整句翻译：' : '基础翻译：' }}</span>{{ result.sentenceTranslation || result.translation }}
          <span class="nc-line-tip">{{ result.sentenceTranslation ? '（免费翻译引擎，供参考；写作请用「词灵AI 翻译」）' : '（词库逐词释义拼接，非完整译文，写作请用「词灵AI 翻译」）' }}</span>
        </p>
        <div v-if="!result.normalWords || result.normalWords.length === 0" class="nc-empty">
          词库暂未匹配到相关词，可切换到「词灵AI 翻译」获得完整结果
        </div>
        <div v-else class="word-grid">
          <div
            v-for="(w, i) in result.normalWords"
            :key="i"
            class="word-cell"
            :class="{ 'is-miss': !w.found }"
          >
            <div class="wc-top">
              <span class="wc-word">{{ w.word }}</span>
              <span v-if="w.phonetic" class="wc-phonetic">{{ w.phonetic }}</span>
            </div>
            <div v-if="w.pos" class="wc-pos">{{ w.pos }}</div>
            <div class="wc-meaning">{{ w.found ? w.meaning : '词库未收录' }}</div>
          </div>
        </div>
      </div>

      <!-- AI 翻译结果 -->
      <div v-else-if="result && mode === 'ai' && aiParsed" class="ai-result">
        <div v-if="aiParsed.direction === 'en2zh'" class="ws-card ai-card">
          <div class="ac-head">
            <h3 class="ac-title">标准译文</h3>
            <el-button
              :type="isCollected ? 'success' : 'primary'"
              :plain="isCollected"
              :disabled="isCollected"
              size="small"
              :icon="isCollected ? Check : Plus"
              @click="addToSentence(result.text, aiParsed.standard_cn)"
            >
              {{ isCollected ? '已收藏' : '收藏到句灵集' }}
            </el-button>
          </div>
          <p class="ac-translation">{{ aiParsed.standard_cn }}</p>

          <div v-if="aiParsed.difficulty || aiParsed.register || aiParsed.writing_friendly !== undefined" class="ac-tags">
            <el-tag v-if="aiParsed.difficulty" type="primary" effect="light" round>{{ aiParsed.difficulty }}</el-tag>
            <el-tag v-if="aiParsed.register" effect="plain" round>{{ aiParsed.register }}</el-tag>
            <el-tag v-if="aiParsed.writing_friendly" type="success" effect="light" round>适合写作</el-tag>
          </div>

          <div v-if="(aiParsed.phrases || []).length" class="ac-section">
            <div class="ac-section-title"><AppIcon name="bookmark" :size="14" />核心词块</div>
            <div class="phrase-grid">
              <div v-for="(p, i) in aiParsed.phrases" :key="i" class="phrase-item">
                <span class="p-en">{{ p.en }}</span>
                <span class="p-cn">{{ p.cn }}</span>
              </div>
            </div>
          </div>

          <div v-if="aiParsed.rewrite && (aiParsed.rewrite.standard || aiParsed.rewrite.advanced)" class="ac-section">
            <div class="ac-section-title"><AppIcon name="repeat" :size="14" />同义改写</div>
            <div v-if="aiParsed.rewrite.standard" class="rewrite-item">
              <span class="rw-label">标准版</span>
              <p class="rw-text">{{ aiParsed.rewrite.standard }}</p>
            </div>
            <div v-if="aiParsed.rewrite.advanced" class="rewrite-item">
              <span class="rw-label is-advanced">高分版</span>
              <p class="rw-text">{{ aiParsed.rewrite.advanced }}</p>
            </div>
          </div>

          <div v-if="aiParsed.mistake" class="ac-section">
            <div class="ac-section-title"><AppIcon name="alert-triangle" :size="14" />易错提醒</div>
            <p class="mistake-text">{{ aiParsed.mistake }}</p>
          </div>
        </div>

        <div v-else-if="aiParsed.direction === 'zh2en'" class="ws-card ai-card">
          <div class="ac-block">
            <div class="ac-head">
              <h3 class="ac-title">标准版译文</h3>
              <el-button
                :type="isCollected ? 'success' : 'primary'"
                :plain="isCollected"
                :disabled="isCollected"
                size="small"
                :icon="isCollected ? Check : Plus"
                @click="addToSentence(result.text, aiParsed.standard_en)"
              >
                {{ isCollected ? '已收藏' : '收藏' }}
              </el-button>
            </div>
            <p class="ac-translation">{{ aiParsed.standard_en }}</p>
          </div>
          <div class="ac-block">
            <div class="ac-head">
              <h3 class="ac-title ac-title-advanced">
                <AppIcon name="star" :size="14" />高分版译文
              </h3>
              <el-button
                :type="isCollected ? 'success' : 'primary'"
                :plain="isCollected"
                :disabled="isCollected"
                size="small"
                :icon="isCollected ? Check : Plus"
                @click="addToSentence(result.text, aiParsed.advanced_en)"
              >
                {{ isCollected ? '已收藏' : '收藏' }}
              </el-button>
            </div>
            <p class="ac-translation ac-translation-advanced">{{ aiParsed.advanced_en }}</p>
          </div>

          <div v-if="(aiParsed.phrases || []).length" class="ac-section">
            <div class="ac-section-title"><AppIcon name="bookmark" :size="14" />核心词块</div>
            <div class="phrase-grid">
              <div v-for="(p, i) in aiParsed.phrases" :key="i" class="phrase-item">
                <span class="p-en">{{ p.en }}</span>
                <span class="p-cn">{{ p.cn }}</span>
              </div>
            </div>
          </div>

          <div v-if="aiParsed.writing_tip" class="ac-section">
            <div class="ac-section-title"><AppIcon name="lightbulb" :size="14" />写作提示</div>
            <p class="tip-text">{{ aiParsed.writing_tip }}</p>
          </div>
        </div>

        <div v-else class="ws-card state-card">
          <p class="state-text">翻译结果解析失败</p>
        </div>
      </div>
    </div>

    <!-- 空状态引导 -->
    <div v-else class="ws-card empty-card">
      <AppIcon name="languages" :size="32" />
      <p class="empty-title">输入中英文文本，开启翻译</p>
      <p class="empty-desc">普通翻译免额度秒出 · 词灵 AI 翻译深度解析适合写作高分</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowRight,
  Check,
  Collection,
  MagicStick,
  Plus,
  RefreshLeft
} from '@element-plus/icons-vue'
import AiLoading from '@/components/ai/AiLoading.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { translate } from '@/api/translate'
import { addSentence, listCollectedOriginals } from '@/api/translate'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const mode = ref('normal')
const text = ref('')
const loading = ref(false)
const result = ref(null)
const aiParsed = ref(null)
const quotaExceeded = ref(false)
const aiUnavailable = ref(false)
/** 当前用户已收藏的原文集合（trim 后） */
const collectedSet = ref(new Set())

const modes = [
  { key: 'normal', name: '普通翻译', desc: '免额度 · 即时翻译' },
  { key: 'ai', name: '词灵AI 翻译', desc: '1 次额度 · 深度解析' }
]

const placeholder = computed(() =>
  mode.value === 'ai'
    ? '输入需要翻译的中文或英文，AI 会给出译文 + 写作要点…'
    : '输入需要翻译的中文或英文，秒出译文…'
)

/** 自动检测语种：含中文 → zh，否则 en */
const srcLang = computed(() => {
  const t = text.value.trim()
  if (!t) return ''
  return /[\u4e00-\u9fff]/.test(t) ? 'zh' : 'en'
})
const tgtLang = computed(() => (srcLang.value === 'zh' ? 'en' : srcLang.value === 'en' ? 'zh' : ''))

const srcLangLabel = computed(() => (srcLang.value === 'zh' ? '中文' : srcLang.value === 'en' ? '英文' : '自动'))
const tgtLangLabel = computed(() => (tgtLang.value === 'zh' ? '中文' : tgtLang.value === 'en' ? '英文' : '自动'))
const detectedHint = computed(() =>
  srcLang.value ? `已识别为 ${srcLangLabel.value}，将翻译为 ${tgtLangLabel.value}` : '请输入文本'
)

/** 当前翻译结果是否已收藏（用于按钮"已收藏"状态） */
const isCollected = computed(() => {
  if (!result.value || !result.value.text) return false
  return collectedSet.value.has(result.value.text.trim())
})

function setMode(m) {
  mode.value = m
  result.value = null
  aiParsed.value = null
}

function clearText() {
  text.value = ''
  result.value = null
  aiParsed.value = null
}

function fillExample() {
  text.value = mode.value === 'ai'
    ? 'It is well known that exercise contributes greatly to physical and mental health.'
    : 'The quick brown fox jumps over the lazy dog.'
}

/** 收藏到句灵集（带整句翻译快照 + 词卡，回看纯离线不调 API） */
async function addToSentence(original, translation, normalWords) {
  if (!original) return
  const tt = translation || (mode.value === 'normal' ? '[词库逐词解析]' : '')
  // AI 翻译模式把整句分析 JSON 一并存进句灵集，回看离线可展开（普通翻译无此项）
  const aiJson = mode.value === 'ai' && aiParsed.value ? JSON.stringify(aiParsed.value) : ''
  try {
    await addSentence({
      originalText: original,
      translationText: tt,
      direction: srcLang.value === 'zh' ? 'zh2en' : 'en2zh',
      mode: mode.value,
      sentenceTranslation: tt,
      normalWords: normalWords && normalWords.length ? JSON.stringify(normalWords) : '',
      analysisJson: aiJson
    })
    collectedSet.value.add(original.trim())
    ElMessage.success('已收藏到句灵集')
  } catch (e) {
    /* 错误由 request 拦截器统一提示 */
  }
}

/** 加载当前用户已收藏的原文集合 */
async function loadCollected() {
  try {
    const data = await listCollectedOriginals()
    const set = new Set()
    if (Array.isArray(data)) {
      for (const t of data) if (t) set.add(t.trim())
    }
    collectedSet.value = set
  } catch (e) {
    /* 错误统一处理 */
  }
}

onMounted(() => {
  // 支持 ?mode=ai 直接进入 AI 翻译模式（搜索跳转等场景）
  if (route.query.mode === 'ai') {
    mode.value = 'ai'
  }
  loadCollected()
})

async function handleTranslate() {
  const t = text.value.trim()
  if (!t) return
  if (mode.value === 'ai' && userStore.aiQuotaRemain <= 0) {
    quotaExceeded.value = true
    return
  }
  loading.value = true
  result.value = null
  aiParsed.value = null
  quotaExceeded.value = false
  aiUnavailable.value = false
  try {
    const data = await translate({
      text: t,
      mode: mode.value,
      srcLang: 'auto',
      tgtLang: 'auto'
    })
    result.value = data
    if (mode.value === 'ai') {
      try {
        aiParsed.value = typeof data.aiJson === 'string' ? JSON.parse(data.aiJson) : data.aiJson
      } catch (e) {
        aiParsed.value = null
      }
      // 同步顶部额度（立刻刷新合并值 + 后台拉细分）
      userStore.syncQuotaFromResp(data)
    }
  } catch (e) {
    if (e.code === 600) aiUnavailable.value = true
    else if (e.code === 601) quotaExceeded.value = true
  } finally {
    loading.value = false
  }
}

function go(path) {
  router.push(path)
}

watch(text, () => {
  // 文本变化时清空旧结果
  result.value = null
  aiParsed.value = null
})
</script>

<style scoped lang="scss">
@use '@/assets/scss/variables.scss' as *;

.translate-page {
  max-width: 920px;
  margin: 0 auto;
}

/* ---------- 模式切换 ---------- */
.mode-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $sp-5;
  gap: $sp-3;
}

.mode-tabs {
  display: inline-flex;
  background: $bg-soft;
  padding: 4px;
  border-radius: $radius-large;
  gap: 4px;
}

.mode-tab {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 8px 18px;
  border: none;
  background: transparent;
  border-radius: $radius-base;
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;

  .mode-name {
    font-size: 15px;
    font-weight: 600;
    color: $text-body;
  }
  .mode-desc {
    font-size: $fs-xs;
    color: $text-caption;
    margin-top: 2px;
  }

  &:hover {
    background: rgba(255, 255, 255, 0.5);
  }

  &.is-active {
    background: #fff;
    box-shadow: $shadow-sm;

    .mode-name {
      color: $color-primary;
    }
  }
}

.quota-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: rgba(64, 158, 255, 0.08);
  color: $color-primary;
  border-radius: 999px;
  font-size: $fs-sm;

  b {
    font-weight: 600;
  }
}

/* ---------- 输入卡片 ---------- */
.input-card {
  padding: $sp-5 $sp-6;
}

.ic-head {
  display: flex;
  align-items: center;
  gap: $sp-2;
  margin-bottom: $sp-4;

  .ic-icon {
    display: inline-flex;
    color: $color-primary;
  }
  .ic-title {
    font-size: 16px;
    font-weight: 600;
    color: $text-title;
    margin: 0;
  }
}

.lang-pill {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: $bg-soft;
  border-radius: 999px;
  font-size: $fs-xs;
  color: $text-caption;
}

.input-actions {
  margin-top: $sp-4;
  display: flex;
  justify-content: space-between;
  align-items: center;

  .left :deep(.el-button + .el-button) {
    margin-left: $sp-2;
  }
}

.quota-warn {
  margin-top: $sp-4;
  padding: $sp-3 $sp-4;
  background: rgba(230, 162, 60, 0.08);
  color: #b88227;
  border-radius: $radius-base;
  display: inline-flex;
  align-items: center;
  gap: $sp-2;
  font-size: $fs-sm;
}

/* ---------- 普通翻译结果 ---------- */
.normal-card {
  margin-top: $sp-5;
  padding: $sp-5 $sp-6;
}

.nc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $sp-3;
}

.nc-label {
  font-size: 13px;
  color: $text-caption;
  letter-spacing: 0.5px;
}

.nc-line {
  margin: 0;
  padding: 14px 16px;
  font-size: 15px;
  line-height: 1.85;
  color: $text-title;
  letter-spacing: 0.2px;
  background: $bg-soft;
  border-radius: $radius-base;
  border-left: 3px solid $color-primary;
}

.nc-line-prefix {
  margin-right: 4px;
  font-size: 12px;
  font-weight: 600;
  color: $color-primary;
}

.nc-line-tip {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: $text-caption;
}

.nc-empty {
  margin-top: $sp-3;
  padding: $sp-6;
  text-align: center;
  font-size: 13px;
  color: $text-caption;
  background: $bg-soft;
  border-radius: $radius-base;
}

.word-grid {
  margin-top: $sp-4;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: $sp-3;
}

.word-cell {
  padding: $sp-3 $sp-4;
  background: $bg-soft;
  border-radius: $radius-base;
  border-left: 3px solid rgba(58, 140, 137, 0.4);
  transition: background 0.2s;

  &:hover {
    background: $primary-1;
  }

  &.is-miss {
    border-left-color: $border-light;
    opacity: 0.7;
  }

  .wc-top {
    display: flex;
    align-items: baseline;
    gap: $sp-2;
  }

  .wc-word {
    font-size: $fs-md;
    font-weight: 600;
    color: $text-title;
  }

  .wc-phonetic {
    font-size: $fs-xs;
    color: $text-caption;
  }

  .wc-pos {
    font-size: $fs-xs;
    color: $color-primary;
    margin-top: 2px;
  }

  .wc-meaning {
    font-size: $fs-sm;
    color: $text-body;
    margin-top: $sp-1;
    line-height: 1.5;
  }
}

/* ---------- AI 翻译结果 ---------- */
.ai-result {
  margin-top: $sp-5;
  display: flex;
  flex-direction: column;
  gap: $sp-4;
}

.ai-card {
  padding: $sp-5 $sp-6;
}

.ac-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $sp-3;
}

.ac-title {
  font-size: 17px;
  font-weight: 600;
  color: $text-title;
  margin: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;

  &.ac-title-advanced {
    color: $color-primary;
  }
}

.ac-translation {
  margin: 0 0 $sp-4;
  font-size: 16px;
  line-height: 1.85;
  color: $text-title;
  letter-spacing: 0.2px;

  &.ac-translation-advanced {
    color: $text-title;
    font-weight: 500;
  }
}

.ac-tags {
  display: flex;
  flex-wrap: wrap;
  gap: $sp-2;
  margin-bottom: $sp-4;
}

.ac-section {
  margin-top: $sp-5;
  padding-top: $sp-4;
  border-top: 1px dashed $border-light;
}

.ac-section-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: $text-caption;
  margin-bottom: $sp-3;
  letter-spacing: 0.5px;
}

.phrase-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: $sp-3;
}

.phrase-item {
  padding: $sp-3 $sp-4;
  background: $bg-soft;
  border-radius: $radius-base;
  display: flex;
  flex-direction: column;
  gap: 4px;

  .p-en {
    font-size: $fs-md;
    font-weight: 500;
    color: $text-title;
  }
  .p-cn {
    font-size: $fs-sm;
    color: $text-caption;
  }
}

.rewrite-item {
  padding: $sp-3 0;

  & + .rewrite-item {
    border-top: 1px dashed $border-light;
    padding-top: $sp-3;
    margin-top: $sp-2;
  }
}

.rw-label {
  display: inline-block;
  padding: 2px 10px;
  font-size: 12px;
  background: rgba(64, 158, 255, 0.08);
  color: $color-primary;
  border-radius: 999px;
  margin-bottom: $sp-2;

  &.is-advanced {
    background: rgba(230, 162, 60, 0.12);
    color: #b88227;
  }
}

.rw-text {
  margin: 0;
  font-size: 15px;
  line-height: 1.75;
  color: $text-body;
}

.mistake-text,
.tip-text {
  margin: 0;
  padding: $sp-3 $sp-4;
  background: rgba(245, 108, 108, 0.04);
  border-left: 3px solid rgba(245, 108, 108, 0.6);
  border-radius: $radius-base;
  font-size: $fs-md;
  line-height: 1.7;
  color: $text-body;
}

.tip-text {
  background: rgba(64, 158, 255, 0.04);
  border-left-color: rgba(64, 158, 255, 0.5);
}

/* ---------- 加载 / 空状态 ---------- */
.state-card,
.empty-card {
  margin-top: $sp-5;
  padding: $sp-8 $sp-6;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $sp-3;
  color: $text-caption;
}

.empty-title {
  font-size: 16px;
  font-weight: 500;
  color: $text-body;
  margin: 0;
}

.empty-desc {
  font-size: $fs-sm;
  margin: 0;
}
</style>