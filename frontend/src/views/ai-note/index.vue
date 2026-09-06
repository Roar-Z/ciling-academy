<template>
  <div class="page-container ai-note-page">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">我的AI笔记</h1>
        <p class="page-desc">收藏的词灵AI优质回答都在这里 · 在对话中点击右上角收藏按钮即可收藏</p>
      </div>
      <el-button :icon="ChatDotRound" @click="router.push('/ai-assistant')">继续对话</el-button>
    </div>

    <div v-loading="loading">
      <!-- 空状态 -->
      <div v-if="!loading && !list.length" class="ws-card state-box">
        <div class="state-icon"><AppIcon name="star" :size="32" /></div>
        <p class="state-text">还没有收藏内容，在词灵AI对话中点击收藏按钮收藏优质回答</p>
        <el-button type="primary" class="mt12" @click="router.push('/ai-assistant')">去和词灵AI聊聊</el-button>
      </div>

      <!-- 笔记列表 -->
      <div v-else class="note-list">
        <div v-for="note in list" :key="note.id" class="note-item ws-card">
          <div class="note-head">
            <span class="note-type">{{ typeLabel(note.renderType) }}</span>
            <h3 class="note-title">{{ note.title || '未命名收藏' }}</h3>
            <el-tooltip content="删除收藏" placement="top">
              <button class="note-del" @click="remove(note)">
                <el-icon :size="15"><Delete /></el-icon>
              </button>
            </el-tooltip>
          </div>

          <!-- 渲染层：JSON → 卡片（用户看不到原始JSON） -->
          <div class="ai-content" v-if="note.renderData && note.renderData.html" v-html="note.renderData.html"></div>
          <div v-else class="note-plain">{{ note.content }}</div>

          <div class="note-foot">
            <span class="note-time">{{ note.createdAt }}</span>
            <el-button text type="primary" size="small" @click="reopen(note)">回到对话</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Delete, ChatDotRound } from '@element-plus/icons-vue'
import { listFavorites, removeFavorite } from '@/api/lingAi'
import { renderAiContent } from '@/components/ai/AiRenderUtil'
import AppIcon from '@/components/common/AppIcon.vue'

const router = useRouter()

const list = ref([])
const loading = ref(true)

const TYPE_LABELS = {
  paper: 'AI试卷',
  cards: '生词巩固',
  reading: '阅读解析',
  refuse: '答疑',
  text: '答疑',
  error: '异常'
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    const data = await listFavorites()
    list.value = data.map((n) => ({
      ...n,
      // ISO 时间里的 T 换成空格，正常显示 2026-09-04 00:44:32
      createdAt: fmtTime(n.createdAt),
      // 卡片头部已展示标题，渲染内容里去掉重复的 h3 标题
      renderData: n.jsonData ? renderAiContent(n.jsonData, { hideTitle: true }) : null
    }))
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

function typeLabel(t) {
  return TYPE_LABELS[t] || '答疑'
}

/** ISO 时间（2026-09-04T00:44:32）→ 空格分隔 */
function fmtTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : ''
}

async function remove(note) {
  try {
    await ElMessageBox.confirm('确定删除这条AI笔记吗？', '提示', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await removeFavorite(note.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function reopen(note) {
  if (note.sessionId) {
    router.push({ path: '/ai-assistant', query: { sessionId: note.sessionId } })
  } else {
    router.push('/ai-assistant')
  }
}
</script>

<style lang="scss" scoped>
.ai-note-page {
  padding-top: $sp-6;
}

.mt12 {
  margin-top: $sp-3;
}

.note-list {
  display: flex;
  flex-direction: column;
  gap: $sp-3;
}

.note-item {
  padding: $sp-4 $sp-5;
  transition: all $transition-fast;

  &:hover {
    border-color: $primary-3;
    box-shadow: $shadow-md;
  }

  .note-head {
    display: flex;
    align-items: center;
    gap: $sp-2;
    margin-bottom: $sp-3;

    .note-type {
      flex-shrink: 0;
      padding: 2px $sp-2;
      border-radius: $radius-sm;
      font-size: $fs-xs;
      font-weight: 600;
      background: $primary-1;
      color: $color-primary;
    }

    .note-title {
      font-size: $fs-lg;
      font-weight: 600;
      color: $text-title;
      @include ellipsis;
    }

    .note-del {
      margin-left: auto;
      border: none;
      background: transparent;
      color: $text-disabled;
      cursor: pointer;
      @include flex-center;
      padding: $sp-1;
      border-radius: $radius-sm;
      transition: all $transition-fast;
      flex-shrink: 0;

      &:hover {
        color: $color-danger;
        background: $color-danger-soft;
      }
    }
  }

  .ai-content {
    max-height: 420px;
    overflow-y: auto;
    @include thin-scrollbar;
  }

  .note-plain {
    white-space: pre-wrap;
    color: $text-body;
    font-size: $fs-md;
  }

  .note-foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: $sp-3;
    padding-top: $sp-3;
    border-top: 1px dashed $border-light;

    .note-time {
      font-size: $fs-sm;
      color: $text-disabled;
    }
  }
}
</style>
