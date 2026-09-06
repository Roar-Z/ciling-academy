<template>
  <div class="page-container sentence-page">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">句灵集</h1>
        <p class="page-desc">收藏喜欢的译文，反复对照学习，让句子真正内化</p>
      </div>
      <div class="header-actions">
        <el-button text type="primary" :icon="EditPen" @click="go('/translate')">去翻译</el-button>
      </div>
    </div>

    <div class="ws-card">
      <!-- 工具栏 -->
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索原文或译文"
          clearable
          class="toolbar-search"
          @keyup.enter="loadList"
        >
          <template #append>
            <el-button class="search-append-btn" :icon="Search" @click="loadList">查询</el-button>
          </template>
        </el-input>
        <el-button
          v-if="selectedIds.length"
          text
          type="danger"
          :icon="Delete"
          @click="batchRemove"
        >
          删除所选（{{ selectedIds.length }}）
        </el-button>
        <div class="toolbar-right">
          <span class="count-tip">共 <b>{{ total }}</b> 句</span>
        </div>
      </div>

      <!-- 列表 -->
      <div v-loading="loading" class="list-wrap" :class="{ 'is-loading': loading }">
        <div v-if="!loading && list.length === 0" class="empty">
          <AppIcon name="inbox" :size="40" />
          <p class="empty-text">句灵集还是空的 · 去翻译助手收藏喜欢的句子</p>
        </div>

        <div v-for="item in list" :key="item.id" class="card-item">
          <div class="card-head">
            <el-checkbox :model-value="selectedIds.includes(item.id)" @change="toggleSelect(item.id)" />
            <div class="meta">
              <el-tag size="small" :type="item.mode === 'ai' ? 'success' : 'info'" effect="light">
                {{ item.mode === 'ai' ? 'AI 翻译' : '普通翻译' }}
              </el-tag>
              <el-tag size="small" effect="plain">
                {{ item.direction === 'en2zh' ? '英→中' : '中→英' }}
              </el-tag>
              <span class="time">{{ formatTime(item.createdAt) }}</span>
            </div>
            <el-button text type="danger" :icon="Delete" size="small" @click="removeOne(item)">删除</el-button>
          </div>

          <div class="card-body">
            <div class="line origin">
              <span class="lang-tag">{{ item.direction === 'en2zh' ? 'EN' : 'CN' }}</span>
              <p class="text">{{ item.originalText }}</p>
            </div>
            <div class="line trans">
              <span class="lang-tag is-trans">{{ item.direction === 'en2zh' ? 'CN' : 'EN' }}</span>
              <p class="text">{{ item.translationText }}</p>
              <el-button text type="primary" size="small" :icon="CopyDocument" @click="copy(item.translationText)">
                复制
              </el-button>
              </div>
              </div>

              <div v-if="item.normalWords" class="nw-wrap">
              <div class="nw-toggle" @click="toggleWords(item.id)">
              <span>{{ expandedIds.includes(item.id) ? '收起词卡' : '展开词卡' }}</span>
              <el-icon class="nw-caret"><ArrowRight v-if="!expandedIds.includes(item.id)" /><ArrowDown v-else /></el-icon>
              </div>
              <div v-show="expandedIds.includes(item.id)" class="word-grid">
              <div
                v-for="(w, i) in parseWords(item.normalWords)"
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

              <!-- AI 整句分析（AI 翻译模式收藏，离线回看） -->
              <div v-if="item.analysisJson" class="ai-wrap">
                <div class="nw-toggle" @click="toggleAi(item.id)">
                  <span>{{ expandedAiIds.includes(item.id) ? '收起分析' : '展开 AI 分析' }}</span>
                  <el-icon class="nw-caret"><ArrowRight v-if="!expandedAiIds.includes(item.id)" /><ArrowDown v-else /></el-icon>
                </div>
                <div v-show="expandedAiIds.includes(item.id)" class="ai-analysis">
                  <AiAnalysisBlock :data="parseAi(item.analysisJson)" />
                </div>
              </div>
              </div>
      </div>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadList"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown, ArrowRight, CopyDocument, Delete, EditPen, Search } from '@element-plus/icons-vue'
import AppIcon from '@/components/common/AppIcon.vue'
import AiAnalysisBlock from '@/components/common/AiAnalysisBlock.vue'
import { listSentence, removeSentence, batchRemoveSentence } from '@/api/translate'

const router = useRouter()

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)
const selectedIds = ref([])
const expandedIds = ref([])
const expandedAiIds = ref([])

onMounted(() => loadList({ initial: true }))

async function loadList({ initial = false } = {}) {
  // 首次进入由路由层 ws-route-loading 全局动画负责反馈，
  // 这里不重复触发 .el-loading-mask，避免两个加载动画叠加。
  if (!initial) loading.value = true
  try {
    const data = await listSentence({ keyword: keyword.value, page: page.value, size: size.value })
    list.value = data.records || []
    total.value = data.total || 0
  } finally {
    if (!initial) loading.value = false
  }
}

function toggleSelect(id) {
  const i = selectedIds.value.indexOf(id)
  if (i >= 0) selectedIds.value.splice(i, 1)
  else selectedIds.value.push(id)
}

async function removeOne(row) {
  try {
    await ElMessageBox.confirm(`确定删除该句吗？`, '提示', { type: 'warning' })
  } catch (e) {
    return
  }
  await removeSentence(row.id)
  ElMessage.success('已删除')
  selectedIds.value = selectedIds.value.filter((i) => i !== row.id)
  loadList()
}

async function batchRemove() {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个句子吗？`, '提示', { type: 'warning' })
  } catch (e) {
    return
  }
  await batchRemoveSentence([...selectedIds.value])
  ElMessage.success('已删除')
  selectedIds.value = []
  loadList()
}

function copy(text) {
  navigator.clipboard?.writeText(text).then(
    () => ElMessage.success('已复制'),
    () => ElMessage.warning('复制失败，请手动复制')
  )
}

function toggleWords(id) {
  const i = expandedIds.value.indexOf(id)
  if (i >= 0) expandedIds.value.splice(i, 1)
  else expandedIds.value.push(id)
}

function toggleAi(id) {
  const i = expandedAiIds.value.indexOf(id)
  if (i >= 0) expandedAiIds.value.splice(i, 1)
  else expandedAiIds.value.push(id)
}

function parseAi(str) {
  if (!str) return null
  try {
    return typeof str === 'string' ? JSON.parse(str) : str
  } catch (e) {
    return null
  }
}

function parseWords(str) {
  if (!str) return []
  try {
    const arr = JSON.parse(str)
    return Array.isArray(arr) ? arr : []
  } catch (e) {
    return []
  }
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function go(path) {
  router.push(path)
}
</script>

<style scoped lang="scss">
@use '@/assets/scss/variables.scss' as *;

.sentence-page {
  max-width: 880px;
  margin: 0 auto;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: $sp-2;
  margin-bottom: $sp-4;

  .toolbar-search {
    width: 260px;

    .search-append-btn {
      background: transparent;
      border: none;
      color: $color-primary;
      padding: 0 $sp-4;

      &:hover {
        background: $primary-1;
        color: $color-primary-deep;
      }
    }
  }
  .toolbar-right {
    margin-left: auto;
    color: $text-caption;
    font-size: $fs-sm;
  }

  .el-button.is-text {
    padding: 8px 14px;
    border-radius: 8px;
    transition: background 0.15s;

    &:not(.el-button--danger):hover {
      background: rgba(64, 158, 255, 0.08);
    }
  }
}

.list-wrap {
  min-height: 200px;
}

.card-item {
  padding: $sp-4 $sp-5;
  background: #fff;
  border: 1px solid $border-light;
  border-radius: $radius-large;
  margin-bottom: $sp-3;
  transition: border-color 0.2s, box-shadow 0.2s;

  &:hover {
    border-color: rgba(64, 158, 255, 0.3);
    box-shadow: $shadow-sm;
  }
}

.card-head {
  display: flex;
  align-items: center;
  gap: $sp-3;
  margin-bottom: $sp-3;

  .meta {
    display: inline-flex;
    align-items: center;
    gap: $sp-2;
    flex: 1;
  }

  .time {
    font-size: $fs-xs;
    color: $text-caption;
    margin-left: 4px;
  }
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: $sp-3;
}

.line {
  display: flex;
  align-items: flex-start;
  gap: $sp-3;

  .lang-tag {
    flex-shrink: 0;
    width: 36px;
    height: 22px;
    line-height: 22px;
    text-align: center;
    font-size: 11px;
    font-weight: 600;
    border-radius: 4px;
    background: rgba(64, 158, 255, 0.08);
    color: $color-primary;
    letter-spacing: 0.5px;

    &.is-trans {
      background: rgba(0, 0, 0, 0.04);
      color: $text-caption;
    }
  }

  .text {
    margin: 0;
    flex: 1;
    font-size: 15px;
    line-height: 1.8;
    color: $text-title;
    letter-spacing: 0.2px;
  }
}

.empty {
  padding: $sp-8 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $sp-3;
  color: $text-caption;

  .empty-text {
    font-size: $fs-sm;
    margin: 0;
  }
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: $sp-4;
}

/* ---------- 词卡（句灵集离线快照） ---------- */
.nw-wrap {
  margin-top: $sp-3;
  border-top: 1px dashed $border-light;
  padding-top: $sp-3;
}

.ai-wrap {
  margin-top: $sp-3;
  border-top: 1px dashed $border-light;
  padding-top: $sp-3;
}

.ai-analysis {
  margin-top: $sp-3;
}

.nw-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: $color-primary;
  cursor: pointer;
  user-select: none;

  .nw-caret {
    transition: transform 0.2s;
  }
}

.word-grid {
  margin-top: $sp-3;
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
    background: rgba(64, 158, 255, 0.06);
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
</style>