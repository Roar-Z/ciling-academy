<template>
  <div class="page-container tools-page">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">词灵工具</h1>
        <p class="page-desc">查词不消耗 AI 额度 · 错题回顾 · 学习工具</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="tools-tabs">
      <!-- 词典查词 -->
      <el-tab-pane label="词典查词" name="dict">
        <div class="ws-card dict-card">
          <div class="dict-search-row">
            <el-input
              v-model="word"
              placeholder="输入英文单词或中文释义查询"
              size="large"
              :prefix-icon="Search"
              class="dict-input"
              @keyup.enter="lookup"
            />
            <el-button type="primary" size="large" @click="lookup">查询</el-button>
          </div>

          <!-- 加载 -->
          <div v-if="lookupLoading" class="dict-result">
            <el-skeleton animated :rows="3" style="max-width: 460px" />
          </div>

          <!-- 结果列表 -->
          <div v-else-if="results.length" class="dict-results" id="dict-results-top">
            <div v-for="item in results" :key="item.id" class="dict-result">
              <div class="dr-head">
                <div class="drh-left">
                  <span class="dr-word">{{ item.word }}</span>
                  <span v-if="item.phonetic" class="dr-phonetic">{{ item.phonetic }}</span>
                </div>
                <div v-if="userStore.isLogin" class="drh-actions">
                  <el-button
                    size="small"
                    :disabled="addedMap[item.word]"
                    @click="addToBook(item)"
                  >
                    {{ addedMap[item.word] ? '已加入生词本' : '+ 加入生词本' }}
                  </el-button>
                  <el-button
                    size="small"
                    :loading="aiParseMap[item.word]?.loading"
                    :disabled="userStore.aiQuotaRemain <= 0"
                    @click="aiDeepParse(item, aiParseMap[item.word]?.content ? true : false)"
                  >
                    {{ aiParseMap[item.word]?.content ? '重新解析' : 'AI深度解析' }}
                  </el-button>
                  <el-button
                    v-if="aiParseMap[item.word]?.content"
                    size="small"
                    @click="saveAiNoteToBook(item)"
                  >
                    保存助记到生词本
                  </el-button>
                </div>
              </div>
              <p class="dr-meaning"><span class="dr-pos">{{ item.pos }}</span>{{ item.meaning }}</p>
              <div v-if="item.example" class="dr-example">
                <p class="dre-en">{{ item.example }}</p>
                <p v-if="item.exampleCn" class="dre-cn">{{ item.exampleCn }}</p>
              </div>
              <div v-if="aiParseMap[item.word]?.content" class="ai-parse-box" v-html="aiParseMap[item.word].content"></div>
              <div v-if="aiParseMap[item.word]?.error" class="ai-parse-error">
                <AppIcon name="info" :size="13" />
                {{ aiParseMap[item.word].error }}
              </div>
              <p class="dr-tip">
                <AppIcon name="info" :size="13" />
                基础释义 · 词典查询不消耗AI额度
              </p>
            </div>

            <!-- 分页：可查完整结果 -->
            <div v-if="total > pageSize" class="dict-pager">
              <el-pagination
                background
                layout="prev, pager, next"
                :page-count="Math.ceil(total / pageSize)"
                :current-page="page"
                :page-size="pageSize"
                @current-change="changePage"
              />
              <span class="pager-total">共 {{ total }} 个结果</span>
            </div>
          </div>

          <!-- 未找到 -->
          <div v-else-if="notFound" class="dict-result">
            <el-empty description="词典中暂未收录该词，可以尝试其他拼写" :image-size="70" />
          </div>
        </div>

        <!-- 回到顶部：卡片外，分页下方居中 -->
        <div v-if="results.length" class="dict-backtop">
          <button class="backtop-link" @click="backToTop">
            <AppIcon name="arrow-up-to-line" :size="14" />
            回到顶部
          </button>
        </div>
      </el-tab-pane>

      <!-- 错题回顾 -->
      <el-tab-pane label="错题回顾" name="wrong">
        <div class="ws-card wrong-card">
          <div class="wrong-toolbar">
            <div class="wt-left">
              <span class="wrong-count">共 {{ wrongTotal }} 道错题</span>
              <el-checkbox
                v-if="wrongList.length"
                :model-value="wrongAllChecked"
                :indeterminate="wrongSelected.length > 0 && !wrongAllChecked"
                @change="toggleWrongAll"
              >全选本页</el-checkbox>
              <el-button
                v-if="wrongSelected.length"
                type="danger"
                plain
                size="small"
                @click="handleRemoveSelected"
              >移除选中 {{ wrongSelected.length }} 题</el-button>
            </div>
            <div class="wrong-filters">
              <button
                v-for="t in filterTypes"
                :key="t.key"
                class="wf-btn"
                :class="{ active: qType === t.key }"
                @click="switchType(t.key)"
              >{{ t.label }}</button>
            </div>
          </div>

          <div v-if="!wrongList.length && !wrongLoading" class="wrong-empty">
            <p class="we-title">暂无错题</p>
            <p class="we-hint">完成练习后，答错的题目会自动收录在这里</p>
            <RouterLink class="we-link" to="/paper-generate">去让 AI 出一套题练练</RouterLink>
          </div>

          <div v-else class="wrong-list" v-loading="wrongLoading">
            <div v-for="w in wrongList" :key="w.questionId" class="wl-row">
              <div class="wl-meta">
                <el-checkbox
                  :model-value="wrongSelected.includes(w.questionId)"
                  @change="(val) => toggleWrongOne(w.questionId, val)"
                />
                <span class="wl-paper">{{ w.paperName }}</span>
                <span class="wl-type">{{ typeOptions[w.qType] || w.qType }}</span>
                <el-button class="wl-del" text size="small" @click="handleRemoveWrong(w)">
                  移除
                </el-button>
              </div>
              <p class="wl-stem">{{ w.stem }}</p>
              <p class="wl-answer">
                <span>你的答案 <em class="bad">{{ w.userAnswer }}</em></span>
                <span>正确答案 <em class="good">{{ w.correctAnswer }}</em></span>
              </p>
              <p v-if="w.analysis" class="wl-analysis">解析：{{ w.analysis }}</p>
            </div>
            <div v-if="wrongTotal > wrongSize" class="wrong-pager">
              <el-pagination
                background
                layout="prev, pager, next"
                :page-count="Math.ceil(wrongTotal / wrongSize)"
                :current-page="wrongPage"
                :page-size="wrongSize"
                @current-change="changeWrongPage"
              />
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 学习工具 -->
      <el-tab-pane label="学习工具" name="help">
        <div class="tools-list">
          <section v-for="group in toolGroups" :key="group.name" class="tools-section">
            <div class="tools-section-head">
              <span class="tsh-title">{{ group.name }}</span>
              <span v-if="group.desc" class="tsh-desc">· {{ group.desc }}</span>
            </div>
            <ul class="tools-rows">
              <li
                v-for="tool in group.items"
                :key="tool.name"
                class="tools-row"
                @click="tool.path && go(tool.path)"
              >
                <AppIcon :name="tool.icon" :size="18" class="tr-icon" />
                <div class="tr-text">
                  <div class="tr-name">{{ tool.name }}</div>
                  <div class="tr-desc">{{ tool.desc }}</div>
                </div>
                <AppIcon name="chevron-right" :size="14" class="tr-arrow" />
              </li>
            </ul>
          </section>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { dictSearch } from '@/api/user'
import { addWord, checkExist, saveAiNote } from '@/api/wordBook'
import { wrongList as fetchWrong, removeWrong, removeWrongBatch } from '@/api/exercise'
import { sendMessage } from '@/api/lingAi'
import { renderAiContent } from '@/components/ai/AiRenderUtil'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('dict')
const word = ref('')
const results = ref([])
const notFound = ref(false)
const lookupLoading = ref(false)
const addedMap = ref({})
const aiParseMap = ref({})
// 词典查询分页
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
// 回到顶部
const showBackTop = ref(false)
const AI_PARSE_CACHE_KEY = 'ws_ai_parse_cache_v1'
const AI_PARSE_CACHE_TTL = 7 * 24 * 60 * 60 * 1000
const MAX_AI_PARSE_CACHE = 10

const wrongList = ref([])
const wrongLoading = ref(false)
const qType = ref('')
// 错题分页与批量选择（全选只作用于当前页）
const wrongPage = ref(1)
const wrongSize = 10
const wrongTotal = ref(0)
const wrongSelected = ref([])
const wrongAllChecked = computed(() =>
  wrongList.value.length > 0 && wrongSelected.value.length === wrongList.value.length
)

const typeOptions = {
  en2cn: '英译汉单选',
  cn2en: '汉译英单选',
  spell_fill: '单词拼写填空',
  context_choice: '语境选词填空',
  match: '词义匹配题'
}

const filterTypes = [
  { key: '', label: '全部' },
  { key: 'en2cn', label: '英译汉' },
  { key: 'cn2en', label: '汉译英' },
  { key: 'spell_fill', label: '拼写填空' },
  { key: 'context_choice', label: '选词填空' },
  { key: 'match', label: '词义匹配' }
]

const toolGroups = [
  {
    name: '学习工具',
    desc: '基础学习功能，不消耗 AI 额度',
    items: [
      { icon: 'book-open', name: '背单词复习', desc: '艾宾浩斯计划智能安排每日复习', path: '/review', tag: '基础' },
      { icon: 'library', name: '生词本', desc: '收藏与管理所有陌生单词', path: '/word-book', tag: '基础' },
      { icon: 'gamepad-2', name: '趣味乐园', desc: '10 款单词小游戏赚金币', path: '/game-park', tag: '基础' }
    ]
  },
  {
    name: 'AI 智能助手',
    desc: '消耗 AI 额度的高级功能',
    items: [
      { icon: 'file-text', name: '练习试卷', desc: 'AI 生成试卷并自动批改', path: '/paper-list', tag: 'AI' },
      { icon: 'languages', name: '阅读助手', desc: '英文段落词灵 AI 深度解析', path: '/reading-helper', tag: 'AI' },
      { icon: 'globe', name: '翻译助手', desc: '中英互译 · 词灵 AI 深度解析', path: '/translate', tag: 'AI' },
      { icon: 'git-branch', name: '长难句分析助手', desc: '拆主干 · 看从句 · 讲明白', path: '/long-sentence', tag: 'AI' }
    ]
  }
]

onMounted(() => {
  if (route.query.tab) {
    activeTab.value = route.query.tab
    if (route.query.tab === 'wrong') loadWrong()
  }
  if (route.query.word) {
    word.value = route.query.word
    lookup()
  }
  loadAiParseCache()
})

function backToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function lookup() {
  const w = word.value.trim()
  if (!w) return
  lookupLoading.value = true
  notFound.value = false
  results.value = []
  page.value = 1
  try {
    await fetchPage(w)
  } catch (e) {
    notFound.value = true
  } finally {
    lookupLoading.value = false
  }
}

/** 拉取指定页（分页查询，可查完整结果） */
async function fetchPage(w) {
  const res = await dictSearch({ keyword: w, page: page.value, size: pageSize.value })
  results.value = res.records || []
  total.value = Number(res.total || 0)
  if (!results.value.length) {
    notFound.value = true
  } else if (userStore.isLogin) {
    const exists = await checkExist(results.value.map((r) => r.word))
    addedMap.value = { ...addedMap.value, ...Object.fromEntries(exists.map((word) => [word, true])) }
  }
}

/** 翻页：换页后回到结果区顶部 */
async function changePage(p) {
  const w = word.value.trim()
  if (!w) return
  page.value = p
  lookupLoading.value = true
  try {
    await fetchPage(w)
  } finally {
    lookupLoading.value = false
  }
  document.getElementById('dict-results-top')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function addToBook(item) {
  if (!item) return
  try {
    await addWord({
      word: item.word,
      meaning: `${item.pos} ${item.meaning}`,
      phonetic: item.phonetic,
      source: 'manual'
    })
    addedMap.value[item.word] = true
    ElMessage.success('已加入生词本')
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function loadAiParseCache() {
  try {
    const raw = localStorage.getItem(AI_PARSE_CACHE_KEY)
    if (!raw) return
    const map = JSON.parse(raw)
    const now = Date.now()
    for (const [word, entry] of Object.entries(map)) {
      if (entry.ts && now - entry.ts < AI_PARSE_CACHE_TTL) {
        aiParseMap.value[word] = {
          loading: false,
          content: entry.content,
          jsonData: entry.jsonData,
          error: '',
          ts: entry.ts
        }
      }
    }
  } catch (e) {
    /* ignore */
  }
}

function saveAiParseCache() {
  try {
    const entries = Object.entries(aiParseMap.value)
      .filter(([, v]) => v.content && !v.error && v.ts)
      .sort((a, b) => b[1].ts - a[1].ts)
      .slice(0, MAX_AI_PARSE_CACHE)
    const toSave = Object.fromEntries(
      entries.map(([word, v]) => [word, { content: v.content, jsonData: v.jsonData, ts: v.ts }])
    )
    localStorage.setItem(AI_PARSE_CACHE_KEY, JSON.stringify(toSave))
  } catch (e) {
    /* ignore */
  }
}

async function aiDeepParse(item, force = false) {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    return
  }
  if (userStore.aiQuotaRemain <= 0) {
    ElMessage.warning('今日词灵AI额度已用完')
    return
  }
  if (!force && aiParseMap.value[item.word]?.content) {
    return
  }
  aiParseMap.value[item.word] = { loading: true, content: '', jsonData: null, error: '' }
  try {
    const resp = await sendMessage({
      mode: 'word_review',
      title: `${item.word} 深度解析`,
      sourceTitle: '词典查词',
      payload: JSON.stringify([{ word: item.word, meaning: item.meaning }]),
      content: '请生成'
    })
    if (typeof resp.quotaRemain === 'number') {
      // 先展示成功消息（用乐观值），同步动作在后台进行
      ElMessage.success(`已消耗1次AI额度，今日还剩 ${resp.quotaRemain} 次`)
      // 同步顶部额度（立刻刷新合并值 + 后台拉细分）
      userStore.syncQuotaFromResp(resp)
    } else {
      // 没拿到 quotaRemain 时也要拉一次详情，避免 chip 过期
      userStore.refreshQuota()
      ElMessage.success('AI深度解析完成')
    }
    const renderData = renderAiContent(resp.message.jsonData)
    aiParseMap.value[item.word] = {
      loading: false,
      content: renderData.html,
      jsonData: resp.message.jsonData,
      error: '',
      ts: Date.now()
    }
    saveAiParseCache()
  } catch (e) {
    // 后端解析失败会自动退还本次额度，这里同步刷新，避免顶部 chip 显示扣减瞬间的旧值
    userStore.refreshQuota()
    aiParseMap.value[item.word] = {
      loading: false,
      content: '',
      jsonData: null,
      error: e.message || '解析失败，请稍后再试'
    }
  }
}

async function saveAiNoteToBook(item) {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    return
  }
  let jsonData = aiParseMap.value[item.word]?.jsonData
  if (!jsonData) {
    ElMessage.info('正在重新解析以获取完整数据...')
    await aiDeepParse(item, true)
    jsonData = aiParseMap.value[item.word]?.jsonData
    if (!jsonData) {
      ElMessage.warning('解析内容不完整，请稍后再试')
      return
    }
  }
  try {
    await saveAiNote(item.word, JSON.stringify(jsonData))
    addedMap.value[item.word] = true
    ElMessage.success('已保存/覆盖助记内容到生词本')
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function loadWrong() {
  wrongLoading.value = true
  try {
    const res = await fetchWrong({ qType: qType.value, page: wrongPage.value, size: wrongSize })
    wrongList.value = res.records || []
    wrongTotal.value = Number(res.total || 0)
    // 删除后当前页可能已空，回退一页
    if (!wrongList.value.length && wrongPage.value > 1) {
      wrongPage.value--
      return loadWrong()
    }
    // 只保留仍在当前页的选中项
    const ids = new Set(wrongList.value.map((x) => x.questionId))
    wrongSelected.value = wrongSelected.value.filter((id) => ids.has(id))
  } catch (e) {
    wrongList.value = []
    wrongTotal.value = 0
  } finally {
    wrongLoading.value = false
  }
}

function changeWrongPage(p) {
  wrongPage.value = p
  wrongSelected.value = []
  loadWrong()
}

function switchType(key) {
  if (qType.value === key) return
  qType.value = key
  wrongPage.value = 1
  wrongSelected.value = []
  loadWrong()
}

function toggleWrongAll(val) {
  wrongSelected.value = val ? wrongList.value.map((x) => x.questionId) : []
}

function toggleWrongOne(id, checked) {
  wrongSelected.value = checked
    ? [...wrongSelected.value, id]
    : wrongSelected.value.filter((x) => x !== id)
}

async function handleRemoveWrong(w) {
  try {
    await ElMessageBox.confirm('确定从错题本移除这道题吗？', '移除错题', {
      confirmButtonText: '移除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await removeWrong(w.questionId)
    wrongTotal.value = Math.max(0, wrongTotal.value - 1)
    await loadWrong()
    ElMessage.success('已移除')
  } catch (e) {
    // 业务错误已由全局拦截器提示
  }
}

async function handleRemoveSelected() {
  const count = wrongSelected.value.length
  try {
    await ElMessageBox.confirm(
      `确定移除本页选中的 ${count} 道错题吗？仅影响当前页，其他页错题不受影响。`,
      '批量移除错题',
      { confirmButtonText: '移除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await removeWrongBatch(wrongSelected.value)
    wrongTotal.value = Math.max(0, wrongTotal.value - count)
    wrongSelected.value = []
    await loadWrong()
    ElMessage.success(`已移除 ${count} 道错题`)
  } catch (e) {
    // 业务错误已由全局拦截器提示
  }
}

function go(path) {
  router.push(path)
}
</script>

<style lang="scss" scoped>
.tools-page {
  padding-top: $sp-6;
  padding-bottom: $sp-6;
  min-height: 100vh;
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
.tools-page > .page-header,
.tools-page > .tools-tabs {
  max-width: $page-max-width;
  margin-left: auto;
  margin-right: auto;
}

.tools-tabs {
  :deep(.el-tabs__nav-wrap::after) {
    height: 1px;
  }
}

/* ---------- 词典 ---------- */
.dict-search-row {
  display: flex;
  gap: $sp-2;
  margin-bottom: $sp-5;

  .dict-input {
    max-width: 420px;
  }
}

.dict-results {
  display: flex;
  flex-direction: column;
  gap: $sp-5;
}

/* 分页：居中，国内主流浅色风格 */
.dict-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $sp-4;
  padding-top: $sp-2;

  .pager-total {
    font-size: $fs-sm;
    color: $text-caption;
  }
}

/* 回到顶部：卡片外、分页下方居中。国内主流做法：纯文字链接，悬浮仅变深，无底色 */
.dict-backtop {
  display: flex;
  justify-content: center;
  margin-top: $sp-3;
}

.backtop-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  font-size: $fs-sm;
  color: $text-caption;
  cursor: pointer;
  padding: 4px 8px;
  transition: color 0.15s;

  &:hover {
    color: $color-primary;
    text-decoration: underline;
  }
}

.dict-result {
  .dr-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $sp-4;
    flex-wrap: wrap;

    .drh-left {
      display: flex;
      align-items: baseline;
      gap: $sp-2;
    }

    .dr-word {
      font-size: 32px;
      font-weight: 700;
      color: $text-title;
      letter-spacing: -0.02em;
    }

    .dr-phonetic {
      color: $text-caption;
      font-size: $fs-md;
    }

    .drh-actions {
      display: flex;
      gap: $sp-2;
      align-items: center;
      flex-shrink: 0;

      /* 国内主流（有道/百度词典）：浅灰底、无边框小按钮，悬浮变主题色 */
      :deep(.el-button) {
        border: none;
        background: #f4f5f7;
        color: #303133;
        border-radius: 4px;

        &:hover,
        &:focus {
          background: rgba(46, 125, 110, 0.1);
          color: $color-primary;
        }

        &.is-disabled {
          background: #f4f5f7;
          color: #b8bcc4;
        }
      }
    }
  }

  .dr-meaning {
    margin-top: $sp-2;
    font-size: $fs-xl;
    color: $text-title;

    .dr-pos {
      color: $color-success;
      margin-right: $sp-2;
      font-weight: 600;
    }
  }

  .dr-example {
    margin-top: $sp-4;
    padding: $sp-3 $sp-4;
    background: $bg-soft;
    border-left: 3px solid $color-primary;
    border-radius: $radius-base;

    .dre-en {
      font-size: $fs-md;
      color: $text-body;
      font-style: italic;
      line-height: 1.6;
    }

    .dre-cn {
      margin-top: $sp-1;
      font-size: $fs-base;
      color: $text-caption;
    }
  }

  .dr-tip {
    margin-top: $sp-4;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: $fs-sm;
    color: $text-disabled;
  }

  .ai-parse-box {
    margin-top: $sp-4;
    padding: $sp-4;
    background: $bg-soft;
    border: 1px solid $border-light;
    border-radius: $radius-base;
    overflow: visible;

    :deep(.ai-render) {
      line-height: 1.7;
    }

    :deep(.ai-word-cards) {
      grid-template-columns: 1fr;
      justify-items: center;
    }

    :deep(.ai-word-card) {
      width: 100%;
      max-width: 880px;
      grid-template-columns: minmax(260px, 1.2fr) minmax(220px, 1fr);
    }
  }

  .ai-parse-error {
    margin-top: $sp-4;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: $fs-sm;
    color: $color-danger;
  }
}

/* ---------- 错题 ---------- */
.wrong-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: $sp-2 $sp-3;
  padding-bottom: $sp-3;
  border-bottom: 1px solid $border-light;
  margin-bottom: $sp-1;

  .wrong-count {
    font-size: $fs-sm;
    color: $text-caption;
  }

  .wt-left {
    display: flex;
    align-items: center;
    gap: $sp-3;
  }
}

.wrong-pager {
  display: flex;
  justify-content: center;
  padding: $sp-4 0 $sp-1;
}

.wrong-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: $sp-1;

  .wf-btn {
    padding: 4px 12px;
    border: none;
    background: none;
    border-radius: $radius-pill;
    font-size: $fs-sm;
    color: $text-caption;
    cursor: pointer;
    transition: color $transition-fast, background-color $transition-fast;

    &:hover {
      color: $text-title;
    }

    &.active {
      color: $color-primary;
      background: $color-primary-soft;
      font-weight: 500;
    }
  }
}

.wrong-empty {
  min-height: calc(100vh - 360px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $sp-8 $sp-5;
  text-align: center;

  .we-title {
    font-size: $fs-md;
    color: $text-body;
  }

  .we-hint {
    margin-top: $sp-1;
    font-size: $fs-sm;
    color: $text-disabled;
  }

  .we-link {
    display: inline-block;
    margin-top: $sp-3;
    font-size: $fs-sm;
    color: $color-primary;
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}

.wrong-list {
  min-height: 100px;
}

.wl-row {
  padding: $sp-4 $sp-1;

  & + .wl-row {
    border-top: 1px solid $border-light;
  }

  .wl-meta {
    display: flex;
    align-items: baseline;
    gap: $sp-2;
    margin-bottom: $sp-1;

    .wl-del {
      margin-left: auto;
      color: $text-disabled;

      &:hover {
        color: $color-danger;
      }
    }

    .wl-paper {
      font-size: $fs-sm;
      color: $text-caption;
    }

    .wl-type {
      font-size: $fs-sm;
      color: $text-disabled;

      &::before {
        content: '·';
        margin-right: $sp-2;
      }
    }
  }

  .wl-stem {
    font-size: $fs-md;
    color: $text-title;
    line-height: 1.7;
  }

  .wl-answer {
    margin-top: $sp-2;
    display: flex;
    flex-wrap: wrap;
    gap: $sp-4;
    font-size: $fs-sm;
    color: $text-caption;

    em {
      font-style: normal;
      margin-left: 2px;

      &.bad {
        color: $color-danger;
      }

      &.good {
        color: $color-success;
      }
    }
  }

  .wl-analysis {
    margin-top: $sp-2;
    font-size: $fs-sm;
    color: $text-caption;
    line-height: 1.7;
  }
}

/* ---------- 学习工具：双栏分组（飞书/有道风格，占满整宽） ---------- */
.tools-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  align-items: start; /* 两列高度不等时不拉伸，保持自然 */
}

.tools-section {
  min-width: 0;

  .tools-section-head {
    display: flex;
    align-items: baseline;
    gap: 8px;
    padding: 0 4px 10px;
    flex-wrap: wrap;

    .tsh-title {
      font-size: 13px;
      font-weight: 600;
      color: $text-title;
      flex-shrink: 0;
    }

    .tsh-desc {
      font-size: 12px;
      color: $text-caption;
      min-width: 0;
    }
  }
}

.tools-rows {
  list-style: none;
  margin: 0;
  padding: 0;
  background: $bg-card;
  border: 1px solid $border-light;
  border-radius: $radius-base;
  overflow: hidden;
}

.tools-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  cursor: pointer;
  border-bottom: 1px solid $border-light;
  transition: background 0.15s;

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: $bg-soft;

    .tr-arrow {
      transform: translateX(2px);
    }
  }

  .tr-icon {
    width: 36px;
    height: 36px;
    @include flex-center;
    flex-shrink: 0;
    border-radius: $radius-base;
    background: $gray-2;
    color: $color-primary;
  }

  .tr-text {
    flex: 1;
    min-width: 0;
  }

  .tr-name {
    font-size: 14px;
    font-weight: 500;
    color: $text-title;
    line-height: 1.4;
  }

  .tr-desc {
    margin-top: 2px;
    font-size: 12px;
    color: $text-caption;
    line-height: 1.5;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .tr-arrow {
    color: $text-disabled;
    flex-shrink: 0;
    transition: transform 0.15s;
  }
}

@media (max-width: 860px) {
  .tools-list {
    grid-template-columns: 1fr;
  }
}
</style>
