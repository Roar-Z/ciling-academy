<template>
  <div class="page-container paper-list-page">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">练习试卷</h1>
        <p class="page-desc">AI生成试卷导入后可反复练习 · 系统自动批改并收录错题</p>
      </div>
      <div class="header-actions">
        <el-button class="wb-btn wb-btn--primary" @click="goGenerate">AI生成试卷</el-button>
        <el-button class="wb-btn" @click="goWrong">错题收录</el-button>
      </div>
    </div>

    <div class="ws-card">
      <el-table v-loading="loading" :data="list">
        <el-table-column prop="paperName" label="试卷名称" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="paper-name-cell">
              <span class="name">{{ row.paperName }}</span>
              <el-tag v-if="row.source === 'ai'" size="small" effect="plain" round class="ai-tag">AI</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="题目" width="90" align="center">
          <template #default="{ row }">{{ row.questionCount }} 题</template>
        </el-table-column>
        <el-table-column label="总分" width="90" align="center">
          <template #default="{ row }">{{ row.totalScore }}</template>
        </el-table-column>
        <el-table-column label="最高分" width="100" align="center">
          <template #default="{ row }">
            <span class="score-num">{{ row.bestScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="作答次数" width="100" align="center">
          <template #default="{ row }">{{ row.doneCount }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="doPaper(row)">开始作答</el-button>
            <el-button text size="small" class="row-action" @click="viewResult(row)">查看结果</el-button>
            <el-button text size="small" class="row-action" @click="viewWrong(row)">错题</el-button>
            <el-button text size="small" class="row-delete" @click="removePaper(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-state">
            <div class="empty-title">暂无试卷</div>
            <div class="empty-desc">点击右上方「AI 生成试卷」创建你的第一套</div>
            <button class="empty-link" @click="goGenerate">去生成 →</button>
          </div>
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

    <!-- 作答结果弹窗 -->
    <el-dialog v-model="resultVisible" title="作答结果" width="720px" top="6vh">
      <template v-if="result">
        <!-- 得分概览 -->
        <div class="result-summary">
          <div class="rs-score">
            <span class="rss-value">{{ result.score }}</span>
            <span class="rss-total">/ {{ result.totalScore }}</span>
          </div>
          <div class="rs-meta">
            <p>答对 <b class="ok">{{ result.correctCount }}</b> / {{ result.totalCount }} 题</p>
            <p>正确率 <b class="hl">{{ result.correctRate }}%</b></p>
          </div>
          <el-button size="small" class="wb-btn wb-btn--primary" @click="doPaper(result.paperId)">重新作答</el-button>
        </div>

        <!-- 逐题详情 -->
        <div class="result-details">
          <div v-for="d in result.details" :key="d.questionId" class="detail-item">
            <div class="detail-head">
              <span class="detail-seq">第 {{ d.seq }} 题</span>
              <span class="detail-tag" :class="{ ok: d.correct, bad: !d.correct }">
                {{ d.correct ? '✓ 正确' : '✗ 错误' }}
              </span>
            </div>
            <p class="detail-stem">{{ d.stem }}</p>
            <p class="detail-line">
              你的答案：<span :class="{ wrong: !d.correct }">{{ formatAns(d.userAnswer, d) }}</span>
            </p>
            <p class="detail-line" v-if="!d.correct">
              正确答案：<span class="correct-text">{{ formatAns(d.correctAnswer, d) }}</span>
            </p>
            <p v-if="d.analysis" class="detail-analysis">
              <span class="da-label">解析</span>{{ d.analysis }}
            </p>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listPapers, removePaper as removePaperApi, paperResult, getPaper } from '@/api/exercise'

const router = useRouter()

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const resultVisible = ref(false)
const result = ref(null)

onMounted(loadList)

async function loadList() {
  loading.value = true
  try {
    const data = await listPapers({ page: page.value, size: size.value })
    list.value = data.records
    total.value = Number(data.total)
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

function goGenerate() {
  router.push('/paper-generate')
}

function goWrong() {
  router.push('/tools?tab=wrong')
}

function doPaper(row) {
  router.push(`/paper-do/${row.id}`)
}

async function viewResult(row) {
  try {
    const data = await paperResult(row.id)
    if (data && data.details && data.details.length) {
      result.value = data
      resultVisible.value = true
    } else {
      ElMessage.info('还没有作答记录，先完成一次作答吧')
    }
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function viewWrong(row) {
  try {
    const data = await getPaper(row.id, true)
    const wrongs = data.questions.filter((q) => q.isCorrect === 0 && q.userAnswer != null)
    if (!wrongs.length) {
      ElMessage.success('这套卷没有错题，太棒了！')
      return
    }
    result.value = {
      paperId: row.id,
      paperName: row.paperName,
      score: 0,
      totalScore: row.totalScore,
      correctCount: 0,
      totalCount: wrongs.length,
      correctRate: 0,
      details: wrongs.map((q) => ({
        questionId: q.id,
        seq: q.seq,
        qType: q.qType,
        stem: q.stem,
        userAnswer: q.userAnswer,
        correctAnswer: q.ans,
        analysis: q.analysis,
        correct: false
      }))
    }
    resultVisible.value = true
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function removePaper(row) {
  try {
    await ElMessageBox.confirm(`确定删除试卷「${row.paperName}」吗？`, '提示', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await removePaperApi(row.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function formatAns(ans, d) {
  if (d.qType === 'match') return ans
  return String(ans ?? '—')
}
</script>

<style lang="scss" scoped>
.paper-list-page {
  padding-top: $sp-6;
}

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
}

.wb-btn--primary {
  border-color: $color-primary;
  color: $color-primary;

  &:hover {
    background: $primary-1;
    border-color: $color-primary-deep;
    color: $color-primary-deep;
  }
}

.header-actions {
  display: flex;
  gap: $sp-2;
}

.ai-tag {
  background: $primary-1 !important;
  color: $color-primary !important;
  border-color: $primary-2 !important;
  font-weight: 500;
}

.row-action {
  color: $text-caption;
  &:hover {
    color: $color-primary !important;
  }
}

.row-delete {
  color: $text-caption;
  &:hover {
    color: $color-danger !important;
  }
}

.empty-state {
  padding: $sp-10 $sp-6;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $sp-2;

  .empty-title {
    font-size: $fs-md;
    font-weight: 500;
    color: $text-title;
  }

  .empty-desc {
    font-size: $fs-base;
    color: $text-caption;
  }

  .empty-link {
    margin-top: $sp-3;
    background: none;
    border: none;
    padding: 0;
    font-size: $fs-base;
    color: $color-primary;
    cursor: pointer;
    font-weight: 500;

    &:hover {
      color: $color-primary-deep;
    }
  }
}

.paper-name-cell {
  display: flex;
  align-items: center;
  gap: $sp-2;

  .name {
    font-weight: 600;
    color: $text-title;
  }
}

.score-num {
  color: $color-primary;
  font-size: $fs-lg;
  font-weight: 600;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: $sp-4;
}

/* ---------- 结果弹窗 ---------- */
.result-summary {
  display: flex;
  align-items: center;
  gap: $sp-6;
  padding: $sp-4 $sp-5;
  background: $bg-soft;
  border: 1px solid $border-light;
  border-radius: $radius-card;
  margin-bottom: $sp-4;

  .rs-score {
    display: flex;
    align-items: baseline;
    gap: $sp-1;

    .rss-value {
      font-size: 44px;
      font-weight: 700;
      color: $color-primary;
      line-height: 1;
    }

    .rss-total {
      font-size: $fs-md;
      color: $text-caption;
    }
  }

  .rs-meta {
    flex: 1;

    p {
      font-size: $fs-md;
      color: $text-body;
      margin-bottom: 2px;
    }

    .ok {
      color: $color-success;
    }

    .hl {
      color: $color-primary;
      font-size: $fs-lg;
      font-weight: 600;
    }
  }
}

.result-details {
  max-height: 52vh;
  overflow-y: auto;
  padding-right: $sp-1;
  @include thin-scrollbar;

  .detail-item {
    padding: $sp-3 $sp-4;
    border: 1px solid $border-light;
    border-radius: $radius-base;
    margin-bottom: $sp-2;
  }

  .detail-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: $sp-2;

    .detail-seq {
      font-size: $fs-sm;
      color: $text-caption;
      font-weight: 600;
    }

    .detail-tag {
      font-size: $fs-sm;
      font-weight: 600;

      &.ok {
        color: $color-success;
      }

      &.bad {
        color: $color-danger;
      }
    }
  }

  .detail-stem {
    font-size: $fs-md;
    color: $text-title;
    line-height: 1.6;
  }

  .detail-line {
    margin-top: $sp-1;
    font-size: $fs-base;
    color: $text-body;

    .wrong {
      color: $color-danger;
    }

    .correct-text {
      color: $color-success;
      font-weight: 600;
    }
  }

  .detail-analysis {
    margin-top: $sp-2;
    font-size: $fs-base;
    color: $text-caption;
    background: $bg-soft;
    padding: $sp-2 $sp-3;
    border-radius: $radius-base;

    .da-label {
      color: $color-primary;
      font-weight: 600;
      margin-right: $sp-1;
    }
  }
}
</style>
