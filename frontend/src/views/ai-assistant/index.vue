<template>
  <div class="ai-assistant-page">
    <div class="chat-layout">
      <!-- ============ 左侧会话列表 ============ -->
      <aside class="session-panel">
        <div class="sp-head">
          <h2 class="sp-title">对话</h2>
        </div>
        <button class="new-chat-btn" @click="newSession">
          <el-icon :size="14"><Plus /></el-icon>
          开启新对话
        </button>

        <div class="session-list" v-loading="loadingSessions">
          <div
            v-for="s in sessions"
            :key="s.id"
            class="session-item"
            :class="{ 'is-active': currentSessionId === s.id }"
            @click="selectSession(s)"
          >
            <div class="si-body">
              <div class="si-title">{{ s.title }}</div>
              <div class="si-meta">
                <span class="si-badge">{{ modeLabel(s.mode) }}</span>
                <span class="si-time">{{ formatTime(s.lastMessageAt) }}</span>
              </div>
            </div>
            <el-icon class="si-del" @click.stop="removeSession(s)"><Delete /></el-icon>
          </div>

          <el-empty v-if="!sessions.length && !loadingSessions" description="还没有对话" :image-size="60" />
        </div>
      </aside>

      <!-- ============ 右侧聊天区 ============ -->
      <section class="chat-panel">
        <!-- 来源提示条 -->
        <transition name="fade">
          <div class="source-bar" v-if="sourceTitle">
            <el-icon class="sb-icon"><Promotion /></el-icon>
            <span class="sb-text">来自「{{ sourceTitle }}」 · 已为你预填内容，可修改后发送</span>
            <el-icon class="sb-close" @click="sourceTitle = ''"><Close /></el-icon>
          </div>
        </transition>

        <!-- 模式提示 -->
        <div class="mode-tip" v-if="currentSession?.mode === 'word_review'">
          <AppIcon name="book-open" :size="14" class="mt-icon" />
          <span><b>生词巩固</b>模式 · AI会为每个单词生成助记卡片，满意后点击「保存这份助记内容」</span>
        </div>
        <div class="mode-tip mode-tip-paper" v-else-if="currentSession?.mode === 'paper'">
          <AppIcon name="file-text" :size="14" class="mt-icon" />
          <span><b>AI试卷</b>模式 · 可多次调整难度与题量，满意后点击「导入为本套练习试卷」</span>
        </div>

        <!-- 额度不足 -->
        <div class="tip-slot" v-if="quotaExceeded || (userStore.aiQuotaRemain <= 0 && userStore.isLogin)">
          <AiQuotaTip :remaining="userStore.aiQuotaRemain" />
        </div>
        <!-- AI 不可用降级 -->
        <div class="tip-slot" v-if="aiUnavailable">
          <AiFallbackTip />
        </div>

        <!-- 消息区 -->
        <div class="message-list" ref="messageListRef">
          <!-- 空状态：词灵打招呼 -->
          <div v-if="!messages.length" class="chat-empty">
            <LingSpirit :mode="spiritState" :size="160" class="ce-spirit" />
            <p class="ce-title">你好，我是词灵AI</p>
            <p class="ce-sub">{{ emptyHint }}</p>
            <div class="ce-suggests">
              <button v-for="s in suggests" :key="s" class="suggest-chip" @click="input = s">{{ s }}</button>
            </div>
          </div>

          <!-- 消息 -->
          <div v-for="(m, idx) in messages" :key="m.id" class="msg-row" :class="m.role">
            <!-- AI 头像（词灵） -->
            <div v-if="m.role === 'assistant'" class="msg-avatar msg-avatar-ai">
              <LingSpirit :mode="spiritState" :size="40" />
            </div>

            <div class="bubble-wrap">
              <div class="bubble" :class="m.role">
                <!-- AI 消息头部（含右上角收藏） -->
                <div v-if="m.role === 'assistant'" class="bubble-head">
                  <span class="bh-label">词灵AI</span>
                  <el-tooltip :content="m.isFavorited ? '已收藏' : '收藏到AI笔记'" placement="top">
                    <span class="fav-btn" :class="{ 'is-fav': m.isFavorited }" @click="toggleFavorite(m)">
                      <el-icon :size="15">
                        <StarFilled v-if="m.isFavorited" />
                        <Star v-else />
                      </el-icon>
                    </span>
                  </el-tooltip>
                </div>

                <!-- AI 内容：JSON 结构化渲染（用户看不到原始JSON） -->
                <template v-if="m.role === 'assistant'">
                  <div v-if="m.renderData && m.renderData.html" class="ai-content" v-html="m.renderData.html"></div>
                  <div v-else class="ai-plain">{{ m.content }}</div>
                </template>
                <template v-else>
                  <div class="user-text">{{ m.content }}</div>
                </template>
              </div>

              <!-- 思考过程（深度思考时显示，紧贴气泡下方，DeepSeek 风格） -->
              <div
                v-if="m.role === 'assistant' && m.jsonObj?._ciMeta?.deepThink && m.jsonObj?.reasoning"
                class="ai-reasoning"
              >
                <button class="ar-toggle" type="button" @click="toggleReasoning(m)">
                  <el-icon :size="13"><MagicStick /></el-icon>
                  <span>已深度思考</span>
                  <el-icon class="ar-arrow" :class="{ 'is-open': reasoningExpanded.has(m.id) }">
                    <ArrowDown />
                  </el-icon>
                </button>
                <transition name="ar-fade">
                  <div v-show="reasoningExpanded.has(m.id)" class="ar-content">
                    <div class="ar-body">{{ m.jsonObj.reasoning }}</div>
                  </div>
                </transition>
              </div>

              <!-- 联网参考（联网搜索时显示，Kimi 风格） -->
              <div
                v-if="m.role === 'assistant' && m.jsonObj?._ciMeta?.webSearch && parseReferences(m.content).length"
                class="ai-references"
              >
                <div class="ref-head">
                  <el-icon :size="13"><Link /></el-icon>
                  <span>参考来源（{{ parseReferences(m.content).length }}）</span>
                </div>
                <ul class="ref-list">
                  <li v-for="(r, ri) in parseReferences(m.content)" :key="ri">
                    <a :href="r.url" target="_blank" rel="noopener noreferrer">
                      <el-icon :size="11"><Link /></el-icon>
                      <span>{{ r.title || r.url }}</span>
                    </a>
                  </li>
                </ul>
              </div>

              <!-- AI 业务按钮（仅保存/导入时提交原始JSON） -->
              <div class="bubble-actions" v-if="m.role === 'assistant' && idx > 0">
                <el-button
                  v-if="currentSession?.mode === 'word_review' && m.jsonData && m.renderType === 'cards'"
                  size="small" type="success" plain @click="saveReview(m)"
                >保存这份助记内容</el-button>

                <el-button
                  v-if="currentSession?.mode === 'paper' && m.jsonData && m.renderType === 'paper'"
                  size="small" type="primary" plain @click="importPaper(m)"
                >导入为本套练习试卷</el-button>
              </div>
            </div>

            <!-- 用户头像 -->
            <div v-if="m.role === 'user'" class="msg-avatar msg-avatar-user">我</div>
          </div>

          <!-- 加载骨架屏：词灵思考中 -->
          <div class="msg-row assistant" v-if="sending">
            <div class="msg-avatar msg-avatar-ai">
              <LingSpirit mode="thinking" :size="40" />
            </div>
            <div class="bubble-wrap">
              <div class="bubble assistant">
                <AiLoading text="词灵AI正在认真思考你的问题…" />
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区（国内主流 AI 助手风格：圆角卡片 + 附件 + 发送箭头 + 深度思考/联网搜索） -->
        <div class="chat-input-bar">
          <div class="quota-line" v-if="userStore.isLogin">
            今日剩余
            <b :class="{ 'is-zero': userStore.aiQuotaRemain <= 0 }">{{ userStore.aiQuotaRemain }}</b>
            次词灵AI额度
          </div>

          <div class="input-card">
            <div class="ic-top">
              <button class="ic-attach" title="添加附件" @click="onAttach">
                <el-icon :size="18"><Paperclip /></el-icon>
              </button>
              <el-input
                v-model="input"
                type="textarea"
                :rows="2"
                resize="none"
                class="ic-textarea"
                placeholder="给词灵AI发消息…（Ctrl + Enter 发送）"
                @keydown.ctrl.enter="send"
              />
            </div>

            <div class="ic-toolbar">
              <div class="ic-toggles">
                <button
                  class="ic-toggle" :class="{ 'is-on': deepThink }" @click="deepThink = !deepThink"
                  title="开启后本次问答将额外消耗 1 次额度（与联网搜索同开不叠加，共 2 次）"
                >
                  <el-icon :size="13"><MagicStick /></el-icon><span>深度思考</span>
                </button>
                <button
                  class="ic-toggle" :class="{ 'is-on': webSearch }" @click="webSearch = !webSearch"
                  title="开启后本次问答将额外消耗 1 次额度（与深度思考同开不叠加，共 2 次）"
                >
                  <el-icon :size="13"><Search /></el-icon><span>联网搜索</span>
                </button>
                <span class="ic-cost" v-if="currentCost > 1">
                  <el-icon :size="12"><InfoFilled /></el-icon>
                  增强模式：本次消耗 <b>{{ currentCost }}</b> 次额度
                </span>
              </div>
              <button
                class="ic-send"
                :class="{ 'is-active': input.trim() }"
                :disabled="!input.trim() || sending"
                @click="send"
              >
                <el-icon v-if="sending" class="is-spin"><Loading /></el-icon>
                <el-icon v-else :size="18"><Top /></el-icon>
              </button>
            </div>
          </div>

          <div class="ic-disclaimer">内容由 AI 生成，请仔细甄别</div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useRouter } from 'vue-router'
import { Plus, Delete, Star, StarFilled, Close, Promotion, Paperclip, MagicStick, Search, Top, Loading, InfoFilled, ArrowDown, Link } from '@element-plus/icons-vue'
import AiLoading from '@/components/ai/AiLoading.vue'
import AiQuotaTip from '@/components/ai/AiQuotaTip.vue'
import AiFallbackTip from '@/components/ai/AiFallbackTip.vue'
import LingSpirit from '@/components/ai/LingSpirit.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { renderAiContent } from '@/components/ai/AiRenderUtil'
import {
  listSessions, createSession, deleteSession, listMessages, sendMessage,
  favoriteMessage, saveReviewContent
} from '@/api/lingAi'
import * as exerciseApi from '@/api/exercise'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const sessions = ref([])
const currentSessionId = ref(null)
const messages = ref([])
const input = ref('')
const sending = ref(false)
const loadingSessions = ref(false)
const quotaExceeded = ref(false)
const aiUnavailable = ref(false)
const sourceTitle = ref('')
const messageListRef = ref(null)

/** 输入区增强：深度思考 / 联网搜索（已接通后端，开启会额外消耗 1 次额度） */
const deepThink = ref(false)
const webSearch = ref(false)

/** 本次发送消耗额度：基础1次；开启深度思考 / 联网搜索为2次（两者同开不叠加） */
const currentCost = computed(() => ((deepThink.value || webSearch.value) ? 2 : 1))

function onAttach() {
  ElMessage.info('附件功能开发中，敬请期待')
}

/** 从 URL 读取业务参数（模式B跳转） */
const urlMode = computed(() => (route.query.mode || 'chat'))
const urlPayload = computed(() => (route.query.payload || ''))
const urlTitle = computed(() => (route.query.title || ''))
/** 额外出题要求（试卷生成页携带） */
const urlCustom = computed(() => (route.query.custom || ''))

const currentSession = computed(() =>
  sessions.value.find((s) => s.id === currentSessionId.value) || null
)

/** AI 回复展示中（说话动画）开关 */
const aiTalking = ref(false)
let aiTalkingTimer = null
/** 刚新建的会话（首次进入时显示问候动画，之后切回变 holding） */
const isNewJustCreated = ref(false)
/** 第二次及以后主动刷新页面时，空会话显示欢迎动画（greeting） */
const forceGreet = ref(false)

/**
 * 词灵动作状态
 * - AI 回复展示中 → talking（模型在"说话"）
 * - 用户点击发送、AI 思考中 → thinking
 * - 首次进入 / 左侧没有任何对话（chat-empty 问候）→ greet
 * - 进入已有对话、空闲陪伴 → holding
 */
const spiritState = computed(() => {
  if (aiTalking.value) return 'talking'
  if (sending.value || input.value.trim()) return 'thinking'
  if (messages.value.length) return 'holding'
  // messages 为空：已选中会话（含切回新对话）→ 陪伴；其余（第二次主动刷新 / 刚新建 / 无会话）→ 欢迎
  if (forceGreet.value) return 'greet'
  if (isNewJustCreated.value || !currentSessionId.value) return 'greet'
  return 'holding'
})

/** 同步当前会话到 URL：切走页面再切回时自动恢复选中 */
watch(currentSessionId, (sid) => {
  const nextQuery = { ...route.query }
  if (sid) nextQuery.sessionId = String(sid)
  else delete nextQuery.sessionId
  router.replace({ query: nextQuery })
})

/**
 * 输入框内容持久化（按会话隔离）
 * 目的：用户在当前对话输入到一半，刷新页面 / 切走再切回时，输入内容仍在
 * - key：`ws_ai_input_<sessionId || 'new'>`，每个会话/新对话独立一份
 * - 切会话：恢复目标 key 的内容
 * - 输入：debounce 200ms 写 sessionStorage；清空则删除 key
 * - 删除会话：清掉对应 key，避免脏数据
 * - 发送成功后 input 置空，watch 会自然 removeItem
 */
const inputStorageKey = computed(() => `ws_ai_input_${currentSessionId.value || 'new'}`)

let inputSaveTimer = null
watch(input, (v) => {
  if (inputSaveTimer) clearTimeout(inputSaveTimer)
  inputSaveTimer = setTimeout(() => {
    if (v && v.length) {
      try { sessionStorage.setItem(inputStorageKey.value, v) } catch (e) { /* 容量满等异常忽略 */ }
    } else {
      try { sessionStorage.removeItem(inputStorageKey.value) } catch (e) { /* ignore */ }
    }
  }, 200)
})

// 切换会话时：恢复目标会话的输入内容（immediate 让 onMounted 之前也安全）
// 模式B 带预填进入时置 true 跳过一次恢复，否则异步 watch 会用空的 sessionStorage 覆盖掉预填内容
let skipInputRestore = false
watch(currentSessionId, () => {
  if (skipInputRestore) {
    skipInputRestore = false
    return
  }
  try {
    input.value = sessionStorage.getItem(inputStorageKey.value) || ''
  } catch (e) { input.value = '' }
})

const emptyHint = computed(() => {
  if (urlMode.value === 'word_review') return '已为你勾选生词，直接发送即可生成巩固材料'
  if (urlMode.value === 'paper') return '已为你勾选单词池，直接发送即可生成试卷'
  return '可以问我任何英语学习问题'
})

const suggests = computed(() => {
  if (urlMode.value === 'word_review') {
    return ['开始生成', '简短一点', '带上词根词缀', '编个小故事串联']
  }
  if (urlMode.value === 'paper') {
    return ['生成10题', '难度中等', '多出一些英译汉', '少一点拼写题']
  }
  return ['abandon 和 give up 的区别', '帮我润色这段英语作文', '怎么制定背单词计划']
})

onMounted(async () => {
  isNewJustCreated.value = false
  // 主动刷新计数：第二次及以后刷新空会话显示欢迎动画（greeting）；切走切回属 navigation 不计入
  const navEntry = performance.getEntriesByType('navigation')[0]
  if (navEntry && navEntry.type === 'reload') {
    const n = Number(sessionStorage.getItem('ws_ai_reload_count') || '0') + 1
    sessionStorage.setItem('ws_ai_reload_count', String(n))
    // 首次主动刷新显示欢迎（greeting），第二次及以后显示陪伴（holding）
    forceGreet.value = n === 1
  } else {
    sessionStorage.removeItem('ws_ai_reload_count')
    forceGreet.value = false
  }
  userStore.refreshQuota()
  await loadSessions()
  // 模式B：从业务页面带参进入
  if (route.query.mode) {
    const session = await createSession({
      mode: route.query.mode,
      title: route.query.title || (route.query.mode === 'word_review' ? '生词巩固' : 'AI生成试卷'),
      sourceTitle: route.query.title
    })
    sessions.value.unshift(session)
    // 跳过一次会话切换恢复，防止异步 watch 用空 sessionStorage 覆盖下面的预填
    skipInputRestore = true
    currentSessionId.value = session.id
    isNewJustCreated.value = true
    sourceTitle.value = route.query.title || ''
    input.value = buildPrefill(route.query.mode, route.query.payload)
    // 清理模式B 参数，避免切回重复创建
    router.replace({ query: { sessionId: String(session.id) } })
  } else if (route.query.sessionId) {
    const sid = Number(route.query.sessionId)
    currentSessionId.value = sid
    isNewJustCreated.value = false
    await loadMessages(sid)
  }
  // 非模式B场景：恢复当前会话的输入框暂存（覆盖首次进入无 sessionId 时 watch 不触发的场景）
  if (!route.query.mode) {
    try {
      input.value = sessionStorage.getItem(inputStorageKey.value) || ''
    } catch (e) { /* ignore */ }
  }
})

onBeforeUnmount(() => {
  userStore.clearQuotaTimer()
  if (aiTalkingTimer) clearTimeout(aiTalkingTimer)
})

watch(
  () => route.query.mode,
  () => {
    // 同一页面二次跳转（从业务页再次进入）
    if (route.query.mode) {
      window.location.reload()
    }
  }
)

function buildPrefill(mode, payload) {
  let words = []
  try {
    const arr = JSON.parse(payload || '[]')
    words = Array.isArray(arr) ? arr.map((w) => (typeof w === 'string' ? w : w.word)).filter(Boolean) : []
  } catch (e) {
    words = []
  }
  const list = words.slice(0, 12).join('、')
  if (mode === 'word_review') {
    return list ? `请为这些单词生成巩固材料：${list}。` : '请为我的生词本生成巩固材料。'
  }
  if (mode === 'paper') {
    const custom = urlCustom.value ? ` ${urlCustom.value}` : ' 默认10题，难度中等。'
    return list ? `请基于这些单词生成一套试卷：${list}。${custom}` : `请为我的单词池生成一套试卷。${custom}`
  }
  return ''
}

/** 解析后端 jsonData 字符串为对象（解析失败返回 null，不影响渲染） */
function parseJsonSafe(s) {
  if (!s) return null
  try { return JSON.parse(s) } catch (e) { return null }
}

/** 已展开的思维链消息 ID（reactive Set，自动触发响应式更新） */
const reasoningExpanded = reactive(new Set())
function toggleReasoning(m) {
  if (reasoningExpanded.has(m.id)) reasoningExpanded.delete(m.id)
  else reasoningExpanded.add(m.id)
}

/**
 * 解析消息正文中的参考链接（联网搜索时）
 * 1) 优先识别 Markdown 链接 [标题](url)
 * 2) 提取正文里所有纯 URL 去重
 */
function parseReferences(content) {
  if (!content) return []
  const refs = []
  const seen = new Set()
  const mdRe = /\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g
  let m
  while ((m = mdRe.exec(content)) !== null) {
    const title = (m[1] || '').trim()
    const url = m[2]
    if (seen.has(url)) continue
    seen.add(url)
    refs.push({ title, url })
  }
  const urlRe = /(https?:\/\/[^\s)<>\]]+)/g
  while ((m = urlRe.exec(content)) !== null) {
    let url = m[1].replace(/[.,;:!?)]+$/, '')
    if (seen.has(url)) continue
    seen.add(url)
    refs.push({ title: '', url })
  }
  return refs
}

function modeLabel(mode) {
  return { chat: '答疑', word_review: '生词巩固', paper: 'AI试卷' }[mode] || mode
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t.replace(/-/g, '/'))
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  const pad = (n) => String(n).padStart(2, '0')
  if (sameDay) return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  return `${d.getMonth() + 1}/${d.getDate()}`
}

async function loadSessions() {
  loadingSessions.value = true
  try {
    sessions.value = await listSessions()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loadingSessions.value = false
  }
}

async function newSession() {
  try {
    const session = await createSession({ mode: 'chat', title: '新对话' })
    sessions.value.unshift(session)
    currentSessionId.value = session.id
    isNewJustCreated.value = true
    messages.value = []
    input.value = ''
    sourceTitle.value = ''
    quotaExceeded.value = false
    aiUnavailable.value = false
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function selectSession(s) {
  if (currentSessionId.value === s.id) return
  currentSessionId.value = s.id
  isNewJustCreated.value = false
  forceGreet.value = false
  quotaExceeded.value = false
  aiUnavailable.value = false
  sourceTitle.value = s.sourceTitle || ''
  await loadMessages(s.id)
}

async function loadMessages(sessionId) {
  try {
    const list = await listMessages(sessionId)
    messages.value = list.map((m) => ({
      ...m,
      jsonObj: parseJsonSafe(m.jsonData),
      renderData: m.role === 'assistant' && m.jsonData ? renderAiContent(m.jsonData) : null
    }))
    scrollToBottom()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function removeSession(s) {
  try {
    await ElMessageBox.confirm(`确定删除会话「${s.title}」吗？`, '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await deleteSession(s.id)
    sessions.value = sessions.value.filter((x) => x.id !== s.id)
    if (currentSessionId.value === s.id) {
      currentSessionId.value = null
      messages.value = []
      try { sessionStorage.removeItem(`ws_ai_input_${s.id}`) } catch (e) { /* ignore */ }
    }
    ElMessage.success('会话已删除')
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function send() {
  const content = input.value.trim()
  if (!content || sending.value) return
  // 增强模式（深度思考/联网搜索）需 2 次额度，不足时明确提示还剩几次
  const cost = currentCost.value
  if (userStore.aiQuotaRemain < cost) {
    quotaExceeded.value = true
    ElMessage.warning(`本次需要 ${cost} 次额度（已开启增强能力），当前剩余 ${userStore.aiQuotaRemain} 次`)
    return
  }
  sending.value = true
  quotaExceeded.value = false
  aiUnavailable.value = false

  // 本地乐观显示用户消息
  const userMsg = { id: Date.now(), role: 'user', content, renderData: null }
  messages.value.push(userMsg)
  isNewJustCreated.value = false
  forceGreet.value = false
  input.value = ''
  scrollToBottom()

  try {
    const resp = await sendMessage({
      sessionId: currentSessionId.value,
      mode: (currentSession && currentSession.value && currentSession.value.mode) || urlMode.value,
      payload: urlPayload.value,
      title: currentSession?.value?.title,
      sourceTitle: sourceTitle.value,
      content,
      deepThink: deepThink.value,
      webSearch: webSearch.value
    })

    // 新会话场景：后端自动创建后回传 sessionId
    if (resp.sessionId && currentSessionId.value !== resp.sessionId) {
      currentSessionId.value = resp.sessionId
      await loadSessions()
    }

    const aiMsg = resp.message
    messages.value.push({
      ...aiMsg,
      jsonObj: parseJsonSafe(aiMsg.jsonData),
      renderData: aiMsg.jsonData ? renderAiContent(aiMsg.jsonData) : null
    })
    // 模型进入"说话"状态：按回复长度展示 talking 动画，结束后回到 holding
    aiTalking.value = true
    if (aiTalkingTimer) clearTimeout(aiTalkingTimer)
    const dur = Math.min(Math.max((aiMsg.content?.length || 0) * 50, 1200), 5000)
    aiTalkingTimer = window.setTimeout(() => { aiTalking.value = false }, dur)
    // 同步额度到顶部导航 chip + 页面内 quota-line
    // （syncQuotaFromResp 会同时更新 aiQuotaRemain 和 aiQuotaDetail.totalRemain，并后台拉一次详情）
    userStore.syncQuotaFromResp(resp)
    scrollToBottom()
  } catch (e) {
    // 按错误码降级提示
    if (e.code === 601) {
      quotaExceeded.value = true
      userStore.aiQuotaRemain = 0
    } else if (e.code === 600) {
      aiUnavailable.value = true
    } else if (e.code === 603) {
      aiUnavailable.value = false
    }
  } finally {
    sending.value = false
  }
}

async function toggleFavorite(m) {
  try {
    if (m.isFavorited) {
      // 已收藏则提示到AI笔记查看
      ElMessage.info('已收藏的内容可在「我的AI笔记」中查看')
      return
    }
    await favoriteMessage({ messageId: m.id })
    m.isFavorited = 1
    ElMessage.success('已收藏到我的AI笔记')
  } catch (e) {
    /* 拦截器已提示 */
  }
}

/** 保存生词巩固包 */
async function saveReview(m) {
  try {
    const res = await saveReviewContent({ jsonData: m.jsonData, messageId: m.id })
    ElMessageBox.confirm('助记内容已保存到生词本页面，是否前往查看？', '保存成功', {
      confirmButtonText: '去查看',
      cancelButtonText: '继续聊天',
      type: 'success'
    }).then(() => {
      router.push({ path: '/word-book', query: { reviewId: res.id } })
    }).catch(() => {})
  } catch (e) {
    ElMessage.error(e.message || '保存失败，请重新生成')
  }
}

/** 导入AI试卷 */
async function importPaper(m) {
  try {
    const res = await exerciseApi.importPaper({ jsonData: m.jsonData, messageId: m.id })
    ElMessageBox.confirm('试卷「' + res.paperName + '」导入成功，共' + res.questionCount + '题，是否立即开始作答？', '导入成功', {
      confirmButtonText: '去作答',
      cancelButtonText: '继续聊天',
      type: 'success'
    }).then(() => {
      router.push(`/paper-do/${res.id}`)
    }).catch(() => {})
  } catch (e) {
    // 604：同一份试卷已导入过，引导去查看，不再重复创建
    if (e.code === 604) {
      ElMessageBox.confirm(e.message || '这份试卷已经导入过了', '已导入过', {
        confirmButtonText: '去查看试卷',
        cancelButtonText: '知道了',
        type: 'warning'
      }).then(() => {
        router.push('/paper-list')
      }).catch(() => {})
      return
    }
    // 业务错误（如603格式异常）已由全局拦截器提示，不重复弹
    if (!e.code) {
      ElMessage.error('导入失败，请重新生成')
    }
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}
</script>

<style lang="scss" scoped>
.ai-assistant-page {
  height: calc(100vh - #{$nav-height} - 32px);
  min-height: 520px;
  max-width: $page-max-width;
  margin: 0 auto;
  padding: $sp-2 $safe-padding $sp-4;
  width: 100%;
}

.chat-layout {
  height: 100%;
  display: flex;
  gap: $sp-3;
  min-height: 0;
}

/* ============ 左侧会话列表 ============ */
.session-panel {
  width: 250px;
  background: $bg-card;
  border: 1px solid $border-light;
  border-radius: $radius-card;
  display: flex;
  flex-direction: column;
  padding: $sp-4 $sp-3;
  flex-shrink: 0;

  .sp-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $sp-3;
    padding: 0 $sp-1;

    .sp-title {
      font-size: $fs-lg;
      font-weight: 600;
      color: $text-title;
    }

    .new-chat-btn {
      border-radius: $radius-base;
    }
  }

  /* Kimi/豆包式：列表顶部全宽浅色描边按钮 */
  .new-chat-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 5px;
    width: 100%;
    padding: 8px 0;
    margin-bottom: $sp-3;
    border: 1px solid $border-base;
    border-radius: $radius-base;
    background: $bg-soft;
    font-size: $fs-sm;
    color: $text-body;
    cursor: pointer;
    transition: all $transition-fast;

    &:hover {
      color: $color-primary;
      border-color: $primary-3;
      background: $primary-1;
    }
  }

  .session-list {
    flex: 1;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 2px;
    @include thin-scrollbar;
  }

  .session-item {
    display: flex;
    align-items: center;
    gap: $sp-2;
    padding: $sp-2 $sp-3;
    border-radius: $radius-base;
    cursor: pointer;
    transition: background $transition-fast;

    &:hover {
      background: $bg-hover;

      .si-del {
        opacity: 1;
      }
    }

    &.is-active {
      background: $primary-1;

      .si-title {
        color: $color-primary;
        font-weight: 600;
      }
    }

    .si-body {
      flex: 1;
      min-width: 0;
    }

    .si-title {
      font-size: $fs-base;
      color: $text-title;
      @include ellipsis;
    }

    .si-meta {
      display: flex;
      align-items: center;
      gap: $sp-2;
      margin-top: 2px;
    }

    .si-badge {
      font-size: $fs-xs;
      color: $color-primary;
      background: #fff;
      border: 1px solid $primary-2;
      padding: 0 5px;
      border-radius: 3px;
    }

    .si-time {
      font-size: $fs-xs;
      color: $text-disabled;
    }

    .si-del {
      opacity: 0;
      font-size: 14px;
      color: $text-disabled;
      transition: all $transition-fast;
      flex-shrink: 0;

      &:hover {
        color: $color-danger;
      }
    }
  }
}

/* ============ 右侧聊天区 ============ */
.chat-panel {
  flex: 1;
  background: $bg-card;
  border: 1px solid $border-light;
  border-radius: $radius-card;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

/* 来源提示条 */
.source-bar {
  margin: $sp-3 $sp-4 0;
  padding: $sp-2 $sp-3;
  display: flex;
  align-items: center;
  gap: $sp-2;
  background: $primary-1;
  border: 1px solid $primary-2;
  border-radius: $radius-base;
  font-size: $fs-base;
  color: $color-primary;

  .sb-icon {
    font-size: 15px;
    flex-shrink: 0;
  }

  .sb-text {
    @include ellipsis;
  }

  .sb-close {
    margin-left: auto;
    cursor: pointer;
    font-size: 14px;
    flex-shrink: 0;

    &:hover {
      color: $primary-6;
    }
  }
}

/* 模式提示 */
.mode-tip {
  display: flex;
  align-items: center;
  gap: $sp-2;
  margin: $sp-3 $sp-4 0;
  padding: $sp-2 $sp-3;
  background: $color-warning-soft;
  border: 1px solid #ffe0b2;
  border-radius: $radius-base;
  font-size: $fs-base;
  color: #874d00;

  .mt-icon {
    color: $color-primary;
    flex-shrink: 0;
  }

  b {
    color: #b26a00;
  }

  &.mode-tip-paper {
    background: $primary-1;
    border-color: $primary-2;
    color: $color-primary;

    b {
      color: $primary-6;
    }
  }
}

/* 提示条插槽 */
.tip-slot {
  margin: $sp-3 $sp-4 0;
}

/* 消息区 */
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: $sp-4 $sp-4 $sp-2;
  @include thin-scrollbar;
}

/* 空状态 */
.chat-empty {
  text-align: center;
  /* 顶部留白多一些，让词灵整体在消息区偏下、视觉更居中 */
  padding: 56px $sp-5 $sp-8;

  .ce-spirit {
    display: inline-flex;
    /* 序列帧 PNG 底部有透明留白，用负 margin 抵消，缩小与标题的间隙 */
    margin-bottom: -26px;
  }

  .ce-icon {
    font-size: 40px;
  }

  .ce-title {
    margin-top: $sp-3;
    font-size: $fs-xl;
    font-weight: 600;
    color: $text-title;
  }

  .ce-sub {
    margin-top: $sp-1;
    font-size: $fs-base;
    color: $text-caption;
  }

  .ce-suggests {
    margin-top: $sp-5;
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: $sp-2;
  }

  .suggest-chip {
    padding: 6px 14px;
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
      background: $primary-1;
    }
  }
}

/* 消息行 */
.msg-row {
  display: flex;
  gap: $sp-2;
  margin-bottom: $sp-5;

  &.user {
    justify-content: flex-end;
  }

  .msg-avatar {
    width: 40px;
    border-radius: 9px;
    @include flex-center;
    font-size: $fs-sm;
    font-weight: 600;
    flex-shrink: 0;

    &.msg-avatar-ai {
      // 容纳词灵 3D 模型，去掉文字背景；高度由 LingSpirit 内部按模型比例决定
      background: transparent;
      color: #fff;
      overflow: visible;
      align-items: flex-start;
    }

    &.msg-avatar-user {
      background: $gray-3;
      color: $text-caption;
      width: 36px;
      height: 36px;
    }
  }

  .bubble-wrap {
    max-width: 74%;
    min-width: 0;

    /* 含 AI 单词卡的消息适当放宽气泡，双列布局收窄一些更显挺拔 */
    &:has(:deep(.ai-word-card)) {
      max-width: 82%;
    }
  }

  .bubble {
    padding: $sp-3 $sp-4;
    border-radius: $radius-card;
    font-size: $fs-md;
    line-height: $line-height-base;

    &.assistant {
      background: $bg-soft;
      border: 1px solid $border-light;
      color: $text-title;
      border-top-left-radius: $radius-sm;
    }

    &.user {
      background: $color-primary;
      color: #fff;
      border-top-right-radius: $radius-sm;
    }

    /* 防止 AI 词卡等宽内容撑破气泡 */
    :deep(.ai-content) {
      width: 100%;
      min-width: 0;
      max-width: 100%;
    }

    :deep(.ai-word-cards) {
      grid-template-columns: 1fr;
    }

    :deep(.ai-word-card) {
      width: 100%;
      gap: $sp-2;
      grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
    }

    :deep(.ai-wc-right) {
      padding-left: 0;
    }
  }

  /* AI 气泡头部 */
  .bubble-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $sp-3;
    margin-bottom: $sp-2;
    padding-bottom: $sp-2;
    border-bottom: 1px solid $border-light;

    .bh-label {
      font-size: $fs-xs;
      font-weight: 600;
      color: $color-primary;
      background: $primary-1;
      padding: 2px $sp-2;
      border-radius: $radius-sm;
    }

    .fav-btn {
      cursor: pointer;
      color: $text-disabled;
      @include flex-center;
      transition: all $transition-fast;

      &:hover {
        color: $color-warning;
        transform: scale(1.12);
      }

      &.is-fav {
        color: $color-warning;
      }
    }
  }

  .ai-plain {
    white-space: pre-wrap;
    color: $text-body;
  }

  .user-text {
    white-space: pre-wrap;
  }

  .bubble-actions {
    display: flex;
    align-items: center;
    gap: $sp-2;
    margin-top: $sp-2;
    flex-wrap: wrap;
  }
}

/* AI 思考过程（DeepSeek 风格：胶囊触发 + 展开思维链） */
.ai-reasoning {
  margin-top: 8px;
  max-width: 100%;

  .ar-toggle {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 5px 10px;
    border: 1px solid $border-base;
    border-radius: $radius-base;
    background: $bg-soft;
    color: $color-primary;
    font-size: $fs-sm;
    cursor: pointer;
    transition: all $transition-fast;

    &:hover {
      border-color: $primary-3;
      background: $primary-1;
    }

    .ar-arrow {
      transition: transform $transition-fast;

      &.is-open {
        transform: rotate(180deg);
      }
    }
  }

  .ar-content {
    margin-top: 6px;
    padding: 10px 12px;
    background: $bg-soft;
    border: 1px solid $border-light;
    border-radius: $radius-base;
    font-size: $fs-sm;
    line-height: $line-height-base;
    color: $text-body;
    max-height: 280px;
    overflow-y: auto;
    @include thin-scrollbar;
  }
}

/* AI 联网参考（Kimi 风格：链接列表） */
.ai-references {
  margin-top: 8px;
  padding: 8px 10px;
  background: $bg-soft;
  border: 1px solid $border-light;
  border-radius: $radius-base;
  max-width: 100%;

  .ref-head {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: $fs-xs;
    color: $text-caption;
    margin-bottom: 6px;
  }

  .ref-list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;

    li a {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: $fs-xs;
      color: $color-primary;
      text-decoration: none;
      word-break: break-all;

      &:hover {
        text-decoration: underline;
      }
    }
  }
}

/* 思维链展开动画 */
.ar-fade-enter-active,
.ar-fade-leave-active {
  transition: opacity $transition-fast, transform $transition-fast;
}

.ar-fade-enter-from,
.ar-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* 输入区（国内主流 AI 助手风格） */
.chat-input-bar {
  padding: $sp-3 $sp-4 $sp-3;
  border-top: 1px solid $border-light;
  background: $bg-card;

  .quota-line {
    font-size: $fs-sm;
    color: $text-caption;
    margin-bottom: $sp-2;

    b {
      color: $color-primary;
      font-weight: 600;
    }

    .is-zero {
      color: $color-danger;
    }
  }

  .input-card {
    display: flex;
    flex-direction: column;
    gap: $sp-2;
    padding: $sp-2 $sp-3;
    background: $bg-soft;
    border: 1px solid $border-light;
    border-radius: $radius-card;
    transition: border-color $transition-fast, box-shadow $transition-fast;

    &:focus-within {
      border-color: $primary-3;
      box-shadow: 0 0 0 3px $primary-1;
    }

    .ic-top {
      display: flex;
      align-items: flex-start;
      gap: $sp-2;
    }

    .ic-attach {
      flex-shrink: 0;
      width: 34px;
      height: 34px;
      margin-top: 2px;
      display: flex;
      align-items: center;
      justify-content: center;
      border: none;
      border-radius: $radius-base;
      background: transparent;
      color: $text-caption;
      cursor: pointer;
      transition: all $transition-fast;

      &:hover {
        color: $color-primary;
        background: $primary-1;
      }
    }

    .ic-textarea {
      flex: 1;
      min-width: 0;

      :deep(.el-textarea__inner) {
        background: transparent;
        border: none;
        box-shadow: none;
        padding: 6px 0;
        resize: none;
        font-size: $fs-md;
        line-height: $line-height-base;
        color: $text-title;
      }
    }

    .ic-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: $sp-2;
    }

    .ic-toggles {
      display: flex;
      align-items: center;
      gap: $sp-2;
    }

    .ic-toggle {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      height: 30px;
      padding: 0 $sp-3;
      border: 1px solid $border-base;
      border-radius: $radius-pill;
      background: $bg-card;
      color: $text-caption;
      font-size: $fs-sm;
      cursor: pointer;
      transition: all $transition-fast;

      &:hover {
        color: $color-primary;
        border-color: $primary-3;
      }

      &.is-on {
        color: $color-primary;
        border-color: $primary-3;
        background: $primary-1;
        font-weight: 600;
      }
    }

    /* 增强模式额度提示 */
    .ic-cost {
      display: inline-flex;
      align-items: center;
      gap: 3px;
      font-size: $fs-xs;
      color: #874d00;
      background: $color-warning-soft;
      padding: 2px $sp-2;
      border-radius: $radius-pill;

      b {
        font-weight: 600;
      }
    }

    .ic-send {
      flex-shrink: 0;
      width: 38px;
      height: 38px;
      display: flex;
      align-items: center;
      justify-content: center;
      border: none;
      border-radius: 50%;
      background: $gray-3;
      color: #fff;
      cursor: not-allowed;
      transition: all $transition-fast;

      &.is-active {
        background: $color-primary;
        cursor: pointer;

        &:hover {
          background: $primary-6;
        }
      }

      .is-spin {
        animation: ic-spin 0.9s linear infinite;
      }
    }
  }

  .ic-disclaimer {
    margin-top: $sp-2;
    text-align: center;
    font-size: $fs-xs;
    color: $text-disabled;
  }
}

@keyframes ic-spin {
  to { transform: rotate(360deg); }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity $transition-normal;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 900px) {
  .session-panel {
    display: none;
  }

  .msg-row .bubble-wrap {
    max-width: 88%;
  }
}
</style>
