<template>
  <div class="page-container paper-generate-page">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">AI生成试卷</h1>
        <p class="page-desc">选择单词池与出题偏好，词灵AI生成 5 种题型试卷 · 支持多次调整后导入</p>
      </div>
      <el-button @click="router.push('/paper-list')">返回试卷列表</el-button>
    </div>

    <div class="generate-grid">
      <!-- 单词池 -->
      <div class="ws-card">
        <div class="card-step">
          <span class="cs-num">1</span>
          <h3 class="card-title">选择单词池</h3>
        </div>

        <el-radio-group v-model="poolType" class="pool-type" size="small">
          <el-radio-button value="wordbook">从生词本选择</el-radio-button>
          <el-radio-button value="manual">手动输入单词</el-radio-button>
        </el-radio-group>

        <!-- 生词本选择 -->
        <div v-if="poolType === 'wordbook'" class="pool-wordbook">
          <div class="pool-toolbar">
            <el-checkbox :model-value="allSelected" @change="toggleAll">全选</el-checkbox>
            <span class="pool-count">已选 <b>{{ selectedWords.length }}</b> 词</span>
            <el-input v-model="poolKeyword" placeholder="筛选" size="small" class="pool-filter" clearable />
          </div>

          <div class="word-pool" v-loading="loadingPool">
            <div class="pool-chips">
              <button
                v-for="w in filteredPool"
                :key="w.id"
                class="word-chip"
                :class="{ 'is-on': selectedWords.includes(w.word) }"
                @click="toggleWord(w.word)"
              >{{ w.word }}</button>
            </div>
            <el-empty v-if="!filteredPool.length && !loadingPool" description="生词本是空的，先去添加单词吧" :image-size="60" />
          </div>
        </div>

        <!-- 手动输入 -->
        <div v-else>
          <el-input
            v-model="manualWords"
            type="textarea"
            :rows="6"
            placeholder="每行一个单词，或使用逗号/空格分隔，例如：&#10;abandon, ability, achieve, absorb"
          />
          <p class="manual-tip">已识别 <b>{{ getManualWords().length }}</b> 个单词</p>
        </div>
      </div>

      <!-- 出题偏好 -->
      <div class="ws-card">
        <div class="card-step">
          <span class="cs-num">2</span>
          <h3 class="card-title">出题偏好</h3>
        </div>

        <div class="pref-row">
          <span class="pref-label">题量</span>
          <el-slider v-model="questionCount" :min="8" :max="15" show-input class="pref-slider" />
        </div>

        <div class="pref-row">
          <span class="pref-label">难度</span>
          <el-radio-group v-model="difficulty" size="small">
            <el-radio-button value="简单">简单</el-radio-button>
            <el-radio-button value="中等">中等</el-radio-button>
            <el-radio-button value="较难">较难</el-radio-button>
          </el-radio-group>
        </div>

        <div class="pref-row pref-row-block">
          <span class="pref-label">题型侧重</span>
          <div class="type-check">
            <button
              v-for="(label, key) in typeOptions"
              :key="key"
              class="type-chip"
              :class="{ 'is-on': typeFocus.includes(key) }"
              @click="toggleType(key)"
            >{{ label }}</button>
          </div>
        </div>

        <el-button type="primary" size="large" class="generate-btn" :disabled="!poolReady" @click="goGenerate">
          <el-icon class="gb-icon"><MagicStick /></el-icon>
          让词灵AI生成试卷
        </el-button>
        <p v-if="!poolReady" class="pool-tip">请至少选择 5 个单词</p>
      </div>
    </div>

    <!-- 说明 -->
    <div class="ws-card generate-note">
      <h3 class="card-title">使用说明</h3>
      <ul class="note-list">
        <li>试卷仅支持 5 种题型：英译汉、汉译英、单词拼写、语境选词、词义匹配，不含听力。</li>
        <li>生成后可在对话页多次调整难度、题量与题型比例，满意后点击「导入为本套练习试卷」。</li>
        <li>导入后进入做题页面，作答由系统自动批改，错题自动收录。</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { MagicStick } from '@element-plus/icons-vue'
import { listWordBook } from '@/api/wordBook'

const router = useRouter()

const poolType = ref('wordbook')
const pool = ref([])
const loadingPool = ref(false)
const poolKeyword = ref('')
const selectedWords = ref([])
const manualWords = ref('')

const questionCount = ref(10)
const difficulty = ref('中等')
const typeFocus = ref([])

const typeOptions = {
  en2cn: '英译汉',
  cn2en: '汉译英',
  spell_fill: '拼写填空',
  context_choice: '语境选词',
  match: '词义匹配'
}

const allSelected = computed(() => filteredPool.value.length > 0 && filteredPool.value.every((w) => selectedWords.value.includes(w.word)))

const filteredPool = computed(() => {
  const kw = poolKeyword.value.trim().toLowerCase()
  if (!kw) return pool.value
  return pool.value.filter((w) => w.word.toLowerCase().includes(kw))
})

const poolReady = computed(() => {
  const count = poolType.value === 'wordbook' ? selectedWords.value.length : getManualWords().length
  return count >= 5
})

onMounted(async () => {
  await loadPool()
})

async function loadPool() {
  loadingPool.value = true
  try {
    const data = await listWordBook({ page: 1, size: 100 })
    pool.value = data.records || []
  } catch (e) {
    pool.value = []
  } finally {
    loadingPool.value = false
  }
}

function getManualWords() {
  return manualWords.value
    .split(/[\s,，、\n]+/)
    .map((s) => s.trim())
    .filter(Boolean)
}

/** 切换单词选中 */
function toggleWord(word) {
  const idx = selectedWords.value.indexOf(word)
  if (idx >= 0) selectedWords.value.splice(idx, 1)
  else selectedWords.value.push(word)
}

/** 切换题型侧重 */
function toggleType(key) {
  const idx = typeFocus.value.indexOf(key)
  if (idx >= 0) typeFocus.value.splice(idx, 1)
  else typeFocus.value.push(key)
}

function toggleAll(val) {
  if (val) {
    const words = filteredPool.value.map((w) => w.word)
    selectedWords.value = Array.from(new Set([...selectedWords.value, ...words]))
  } else {
    const words = filteredPool.value.map((w) => w.word)
    selectedWords.value = selectedWords.value.filter((w) => !words.includes(w))
  }
}

function goGenerate() {
  const words = poolType.value === 'wordbook' ? selectedWords.value : getManualWords()
  if (words.length < 5) return

  // 构造单词池 JSON（模式B payload）
  const poolJson = JSON.stringify(words.map((w) => {
    const found = pool.value.find((p) => p.word === w)
    return { word: w, meaning: found ? found.meaning : '' }
  }))

  // 额外要求文本
  const focusText = typeFocus.value.length
    ? `，题型侧重：${typeFocus.value.map((t) => typeOptions[t]).join('、')}`
    : ''
  const customReq = `共${questionCount.value}题，难度${difficulty.value}${focusText}。`

  router.push({
    path: '/ai-assistant',
    query: {
      mode: 'paper',
      payload: poolJson,
      title: 'AI生成试卷',
      custom: customReq
    }
  })
}

watch(poolType, (val) => {
  if (val === 'manual') {
    selectedWords.value = []
  }
})
</script>

<style lang="scss" scoped>
.paper-generate-page {
  padding-top: $sp-6;
}

.generate-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $sp-4;
  align-items: start;
}

.card-step {
  display: flex;
  align-items: center;
  gap: $sp-2;
  margin-bottom: $sp-4;

  .cs-num {
    width: 22px;
    height: 22px;
    border-radius: 50%;
    background: $color-primary;
    color: #fff;
    font-size: $fs-sm;
    font-weight: 700;
    @include flex-center;
  }
}

.card-title {
  font-size: $fs-xl;
  font-weight: 600;
  color: $text-title;
}

.pool-type {
  margin-bottom: $sp-3;
}

/* 单词池 */
.pool-wordbook {
  .pool-toolbar {
    display: flex;
    align-items: center;
    gap: $sp-3;
    margin-bottom: $sp-3;

    .pool-count {
      font-size: $fs-base;
      color: $text-caption;

      b {
        color: $color-primary;
        font-weight: 600;
      }
    }

    .pool-filter {
      width: 150px;
      margin-left: auto;
    }
  }

  .word-pool {
    max-height: 300px;
    overflow-y: auto;
    padding: $sp-1;
    @include thin-scrollbar;
  }

  .pool-chips {
    display: flex;
    flex-wrap: wrap;
    gap: $sp-2;
  }
}

.word-chip {
  padding: 5px 12px;
  border: 1px solid $border-base;
  border-radius: $radius-pill;
  background: $bg-card;
  color: $text-body;
  font-size: $fs-base;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    border-color: $primary-3;
    color: $color-primary;
  }

  &.is-on {
    border-color: $color-primary;
    background: $primary-1;
    color: $color-primary;
    font-weight: 600;
  }
}

.manual-tip {
  margin-top: $sp-2;
  font-size: $fs-base;
  color: $text-caption;

  b {
    color: $color-primary;
  }
}

/* 偏好设置 */
.pref-row {
  display: flex;
  align-items: center;
  gap: $sp-4;
  margin-bottom: $sp-5;

  .pref-label {
    width: 62px;
    font-size: $fs-base;
    color: $text-body;
    flex-shrink: 0;
    font-weight: 500;
  }

  .pref-slider {
    flex: 1;
    max-width: 240px;
  }

  &.pref-row-block {
    align-items: flex-start;
    padding-top: 2px;
  }
}

.type-check {
  display: flex;
  flex-wrap: wrap;
  gap: $sp-2;
  flex: 1;
}

.type-chip {
  padding: 5px 12px;
  border: 1px solid $border-base;
  border-radius: $radius-pill;
  background: $bg-card;
  color: $text-body;
  font-size: $fs-base;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    border-color: $primary-3;
    color: $color-primary;
  }

  &.is-on {
    border-color: $color-primary;
    background: $primary-1;
    color: $color-primary;
    font-weight: 600;
  }
}

.generate-btn {
  width: 100%;
  margin-top: $sp-2;
  height: 44px;

  .gb-icon {
    margin-right: $sp-1;
  }
}

.pool-tip {
  margin-top: $sp-2;
  font-size: $fs-base;
  color: $color-warning;
  text-align: center;
}

/* 说明 */
.generate-note {
  margin-top: $sp-4;

  .note-list {
    padding-left: $sp-4;
    list-style: disc;

    li {
      font-size: $fs-base;
      color: $text-body;
      line-height: 1.9;
    }
  }
}

@media (max-width: 900px) {
  .generate-grid {
    grid-template-columns: 1fr;
  }
}
</style>
