<template>
  <div class="page-container wordbook-page">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">生词本</h1>
        <p class="page-desc">收藏的单词都会出现在这里 · 勾选单词可一键交给词灵AI生成助记巩固包</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus" @click="addDialogVisible = true">添加生词</el-button>
      </div>
    </div>

    <div class="ws-card wordbook-card">
      <!-- 工具栏 -->
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="单词 ｜ 按 Enter 键查询"
          :prefix-icon="Search"
          clearable
          class="toolbar-search"
          @keyup.enter="loadList"
        />
        <div class="toolbar-right">
          <template v-if="!selectedIds.length">
            <span class="count-tip">共 <b>{{ total }}</b> 个生词</span>
            <el-button text type="primary" @click="go('/review')">去复习</el-button>
          </template>
          <template v-else>
            <span class="count-tip">已选 <b>{{ selectedIds.length }}</b> 项</span>
            <el-button text type="primary" @click="goAiReview">AI 巩固</el-button>
            <el-button text type="danger" @click="batchRemove">删除</el-button>
            <el-divider direction="vertical" />
            <el-button text @click="clearSelection">取消选择</el-button>
          </template>
        </div>
      </div>

      <!-- 列表 -->
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="list"
        class="word-table"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column label="单词" min-width="150">
          <template #default="{ row }">
            <div class="word-cell">
              <span class="word">{{ row.word }}</span>
              <span v-if="row.phonetic" class="phonetic">{{ row.phonetic }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="meaning" label="释义" min-width="180" show-overflow-tooltip />
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="sourceType(row.source)" effect="light">{{ sourceLabel(row.source) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="解析要点" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="usageSummary(row.wordUsage)" class="usage-text">{{ usageSummary(row.wordUsage) }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="熟悉度" width="130">
          <template #default="{ row }">
            <div class="familiarity-cell">
              <el-progress :percentage="row.familiarity" :stroke-width="6" :show-text="false" />
              <span class="familiarity-text">{{ row.familiarity }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="下次复习" width="105">
          <template #default="{ row }">
            <span :class="{ 'is-overdue': isOverdue(row.nextReviewAt) }">{{ formatTime(row.nextReviewAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="155" fixed="right" align="center">
          <template #default="{ row }">
            <div class="op-actions">
              <el-button text type="primary" size="small" @click="openReviewPack(row)">助记</el-button>
              <el-button text type="warning" size="small" @click="markFamiliar(row, true)">✓</el-button>
              <el-button text type="danger" size="small" @click="remove(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="生词本还是空的，去阅读或背单词时添加吧" />
        </template>
      </el-table>

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

    <!-- 手动添加 -->
    <el-dialog v-model="addDialogVisible" title="添加生词" width="440px">
      <el-form label-width="70px">
        <el-form-item label="单词">
          <el-input v-model="newWord" placeholder="输入英文单词" @keyup.enter="lookupWord" />
        </el-form-item>
        <el-form-item v-if="dictResult" label="释义">
          <div class="dict-preview">
            <div class="dp-head">
              <b>{{ dictResult.word }}</b>
              <span class="dp-phonetic">{{ dictResult.phonetic }}</span>
            </div>
            <p class="dp-meaning">{{ dictResult.pos }} {{ dictResult.meaning }}</p>
            <p v-if="dictResult.example" class="dp-example">{{ dictResult.example }}</p>
            <p v-if="dictResult.exampleCn" class="dp-example-cn">{{ dictResult.exampleCn }}</p>
          </div>
        </el-form-item>
        <el-form-item v-else-if="dictNotFound" label="提示">
          <span class="dict-miss">词典暂无该词，仍可手动填写释义</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="adding" @click="handleAdd">加入生词本</el-button>
      </template>
    </el-dialog>

    <!-- 助记巩固包弹窗 -->
    <el-dialog v-model="reviewDialogVisible" title="生词助记巩固包" width="760px">
      <div class="ai-content" v-if="reviewDialogHtml" v-html="reviewDialogHtml"></div>
      <el-empty v-else description="该单词暂无已保存的巩固包，可勾选后交给词灵AI生成" />
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { listWordBook, removeWord, reviewWord, addWord, batchRemoveWords } from '@/api/wordBook'
import { listReviewContents } from '@/api/lingAi'
import { dictLookup } from '@/api/user'
import { renderAiContent } from '@/components/ai/AiRenderUtil'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)
const selectedIds = ref([])
const selectedRows = ref([])
const tableRef = ref(null)

const addDialogVisible = ref(false)
const newWord = ref('')
const dictResult = ref(null)
const dictNotFound = ref(false)
const adding = ref(false)

const reviewDialogVisible = ref(false)
const reviewDialogHtml = ref('')

onMounted(async () => {
  await loadList()
  // 从AI助手保存巩固包后跳回，展示对应巩固包
  if (route.query.reviewId) {
    await loadReviewPacks()
  }
})

watch(
  () => route.query.reviewId,
  async (val) => {
    if (val) await loadReviewPacks()
  }
)

async function loadList() {
  loading.value = true
  try {
    const data = await listWordBook({ keyword: keyword.value, page: page.value, size: size.value })
    list.value = data.records
    total.value = Number(data.total)
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows) {
  selectedRows.value = rows
  selectedIds.value = rows.map((r) => r.id)
}

/** 取消所有勾选（批量操作栏「取消选择」） */
function clearSelection() {
  tableRef.value?.clearSelection()
}

/** 跳转词灵AI生词巩固（模式B） */
function goAiReview() {
  const payload = JSON.stringify(
    selectedRows.value.map((r) => ({ word: r.word, meaning: r.meaning, phonetic: r.phonetic }))
  )
  router.push({
    path: '/ai-assistant',
    query: {
      mode: 'word_review',
      payload,
      title: `生词巩固 · ${selectedRows.value.length}词`
    }
  })
}

function sourceLabel(s) {
  return {
    manual: '手动',
    reading: '阅读',
    review: '复习',
    test: '测验',
    dict_test: '测验',
    ai: 'AI',
    new: '新词学习',
    new_forget: '新词·不记得',
    new_vague: '新词·模糊'
  }[s] || s || '自动'
}

function sourceType(s) {
  return {
    manual: 'info',
    reading: 'primary',
    review: 'warning',
    test: 'success',
    dict_test: 'success',
    ai: 'success',
    new: 'primary',
    new_forget: 'danger',
    new_vague: 'warning'
  }[s] || 'success'
}

function isOverdue(date) {
  if (!date) return false
  return new Date(date) < new Date(new Date().toDateString())
}

// ISO 时间转本地展示格式：2026-09-08T16:32:21 → 2026-09-08 16:32
function formatTime(date) {
  if (!date) return '—'
  const d = new Date(date)
  if (Number.isNaN(d.getTime())) return date
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 把生词本里的解析要点JSON拼成一句话摘要：语法 / 搭配 / 近义 */
function usageSummary(usage) {
  if (!usage) return ''
  let u
  try {
    u = typeof usage === 'string' ? JSON.parse(usage) : usage
  } catch (e) {
    return ''
  }
  const parts = []
  if (u.grammar) parts.push('语法：' + u.grammar)
  if (u.colloc) parts.push('搭配：' + u.colloc)
  if (u.synonym) parts.push('近义：' + u.synonym)
  return parts.join('　｜　')
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定从生词本删除「${row.word}」吗？`, '提示', {
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await removeWord(row.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function batchRemove() {
  const ids = selectedIds.value
  const words = selectedRows.value.map((r) => r.word).join('、')
  try {
    await ElMessageBox.confirm(
      `确定从生词本删除选中的 ${ids.length} 个单词吗？<div style="color:#999;font-size:12px;margin-top:4px">${words}</div>`,
      '批量删除',
      {
        type: 'warning',
        dangerouslyUseHTMLString: true,
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }
    )
  } catch (e) {
    return
  }
  try {
    await batchRemoveWords(ids)
    ElMessage.success(`已删除 ${ids.length} 个单词`)
    selectedIds.value = []
    selectedRows.value = []
    loadList()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function markFamiliar(row, familiar) {
  try {
    await reviewWord(row.id, familiar)
    ElMessage.success('复习完成，已更新艾宾浩斯计划')
    loadList()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function lookupWord() {
  const word = newWord.value.trim()
  if (!word) return
  dictResult.value = null
  dictNotFound.value = false
  try {
    const data = await dictLookup(word)
    if (data) dictResult.value = data
    else dictNotFound.value = true
  } catch (e) {
    dictNotFound.value = true
  }
}

async function handleAdd() {
  const word = newWord.value.trim()
  if (!word) {
    ElMessage.warning('请输入单词')
    return
  }
  adding.value = true
  try {
    await addWord({
      word,
      meaning: dictResult.value ? `${dictResult.value.pos} ${dictResult.value.meaning}` : '',
      phonetic: dictResult.value ? dictResult.value.phonetic : '',
      source: 'manual'
    })
    ElMessage.success('已加入生词本')
    addDialogVisible.value = false
    newWord.value = ''
    dictResult.value = null
    dictNotFound.value = false
    loadList()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    adding.value = false
  }
}

/** 在已保存的巩固包中寻找包含该单词的包并展示 */
async function loadReviewPacks() {
  try {
    const packs = await listReviewContents()
    const pack = packs.find((p) => p.id === Number(route.query.reviewId)) || packs[0]
    if (pack) {
      reviewDialogHtml.value = renderAiContent(pack.reviewJson).html
      reviewDialogVisible.value = true
    }
  } catch (e) {
    /* 忽略 */
  }
}

async function openReviewPack(row) {
  try {
    const packs = await listReviewContents()
    const pack = packs.find((p) => p.words && p.words.split(',').includes(row.word))
    reviewDialogHtml.value = pack ? renderAiContent(pack.reviewJson).html : ''
  } catch (e) {
    reviewDialogHtml.value = ''
  }
  reviewDialogVisible.value = true
}

function go(path) {
  router.push(path)
}
</script>

<style lang="scss" scoped>
.wordbook-page {
  padding-top: $sp-6;
}

.header-actions {
  display: flex;
  gap: $sp-2;

  .wb-btn {
    height: 36px;
    padding: 0 $sp-4;
    border-radius: $radius-base;
    border: 1px solid $border-base;
    background: $bg-card;
    color: $text-body;
    font-weight: 500;
    transition: all $transition-fast;

    &:hover {
      border-color: $gray-5;
      color: $text-title;
    }

    &.is-disabled,
    &:disabled {
      background: $bg-soft;
      border-color: $border-light;
      color: $text-disabled;
      cursor: not-allowed;
    }
  }

  .wb-btn--primary {
    border-color: $color-primary;
    color: $color-primary;

    &:hover:not(.is-disabled) {
      background: $primary-1;
      border-color: $color-primary-deep;
      color: $color-primary-deep;
    }
  }

  .wb-btn--danger {
    &:hover:not(.is-disabled) {
      border-color: $color-danger;
      color: $color-danger;
      background: $bg-card;
    }
  }
}

.wordbook-card {
  .toolbar {
    display: flex;
    align-items: center;
    gap: $sp-2;
    margin-bottom: $sp-4;

    .toolbar-search {
      width: 240px;
    }

    .toolbar-right {
      margin-left: auto;
      display: flex;
      align-items: center;
      gap: $sp-3;

      .count-tip {
        font-size: $fs-base;
        color: $text-caption;

        b {
          color: $text-title;
          font-weight: 600;
        }
      }
    }
  }
}

.word-cell {
  display: flex;
  align-items: baseline;
  gap: $sp-2;

  .word {
    font-size: 15px;
    font-weight: 600;
    color: $text-title;
  }

  .phonetic {
    font-size: $fs-sm;
    color: $text-disabled;
  }
}

.familiarity-cell {
  display: flex;
  align-items: center;
  gap: $sp-2;

  .familiarity-text {
    font-size: $fs-xs;
    color: $text-caption;
    min-width: 32px;
  }
}

.muted {
  color: $text-disabled;
  font-size: $fs-sm;
}

.usage-text {
  font-size: $fs-sm;
  color: $text-body;
  line-height: 1.5;
}

.is-overdue {
  color: $color-danger;
  font-weight: 500;
}

.op-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  white-space: nowrap;

  :deep(.el-button) {
    padding: 0 6px;
  }

  :deep(.el-button + .el-button) {
    margin-left: 2px;
  }
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: $sp-4;
}

.word-table {
  :deep(.el-checkbox__inner) {
    border-radius: 0;
  }

  :deep(.el-checkbox__input.is-indeterminate) {
    .el-checkbox__inner {
      background-color: var(--el-checkbox-bg-color);
      border-color: var(--el-checkbox-input-border-color);

      &::before {
        content: none;
      }
    }
  }
}

.dict-preview {
  width: 100%;
  padding: $sp-3 $sp-4;
  background: $bg-soft;
  border: 1px solid $border-light;
  border-radius: $radius-base;

  .dp-head {
    display: flex;
    align-items: baseline;
    gap: $sp-2;

    b {
      font-size: $fs-lg;
      color: $text-title;
    }

    .dp-phonetic {
      color: $text-disabled;
      font-size: $fs-base;
    }
  }

  .dp-meaning {
    margin-top: $sp-1;
    font-size: $fs-md;
    color: $text-body;
  }

  .dp-example {
    margin-top: $sp-2;
    font-size: $fs-base;
    color: $text-caption;
    font-style: italic;
  }

  .dp-example-cn {
    font-size: $fs-base;
    color: $text-disabled;
  }
}

.dict-miss {
  color: $color-warning;
  font-size: $fs-base;
}

.ai-content {
  max-height: 60vh;
  overflow-y: auto;
  padding-right: $sp-1;

  :deep(.ai-word-cards) {
    grid-template-columns: 1fr;
  }

  :deep(.ai-word-card) {
    width: 100%;
    max-width: 100%;
    box-sizing: border-box;
    border-color: $border-base;
    box-shadow: $shadow-sm;
    grid-template-columns: minmax(240px, 1.2fr) minmax(200px, 1fr);
    gap: $sp-4;
    padding: $sp-4;
  }

  :deep(.ai-wc-right) {
    padding-left: $sp-3;
    border-left: 1px solid $border-light;
  }
}

@media (max-width: 700px) {
  .toolbar {
    flex-wrap: wrap;
  }

  .toolbar-right {
    margin-left: 0;
  }
}
</style>
