<template>
  <div class="ai-analysis-block">
    <!-- ============ 长难句 ============ -->
    <template v-if="kind === 'long'">
      <!-- 整体结构 -->
      <section class="aab-section">
        <div class="aab-section-head">
          <div class="aab-section-title">
            <span class="aab-iconbox iconbox-primary"><el-icon><Connection /></el-icon></span>
            <span>整体结构</span>
          </div>
          <span v-if="structureLabel" class="aab-pill pill-primary">{{ structureLabel }}</span>
        </div>
        <div v-if="structureIntro" class="aab-overview">
          <span class="aab-iconbox iconbox-warning aab-overview-ic"><el-icon><MagicStick /></el-icon></span>
          <p class="aab-overview-text">{{ structureIntro }}</p>
        </div>
      </section>

      <!-- 逐段拆解 -->
      <section v-if="segments.length" class="aab-section">
        <div class="aab-section-head">
          <div class="aab-section-title">
            <span class="aab-iconbox iconbox-primary"><el-icon><Files /></el-icon></span>
            <span>逐段拆解</span>
          </div>
        </div>
        <div class="aab-segments">
          <div v-for="(s, i) in segments" :key="i" class="aab-segment" :class="segClass(i, s)">
            <div class="aab-segment-head">
              <span v-if="s.role" class="aab-pill" :class="segPillClass(i, s)">{{ s.role }}</span>
              <span class="aab-segment-num">第 {{ i + 1 }} 段</span>
            </div>
            <p v-if="s.text" class="aab-segment-text">{{ s.text }}</p>
            <p v-if="s.explain" class="aab-segment-explain"><span class="aab-li-dot" /><span v-html="renderExplain(s.explain)"></span></p>
          </div>
        </div>
      </section>

      <!-- 可复用词块 -->
      <section v-if="(data.key_phrases || []).length" class="aab-section">
        <div class="aab-section-head">
          <div class="aab-section-title">
            <span class="aab-iconbox iconbox-success"><el-icon><PriceTag /></el-icon></span>
            <span>可复用词块</span>
          </div>
        </div>
        <div class="aab-phrases-grid">
          <div v-for="(p, i) in data.key_phrases" :key="i" class="aab-phrase-card">
            <div class="aab-phrase-main">
              <span class="aab-phrase-en">{{ p.phrase }}</span>
              <span class="aab-phrase-cn">{{ p.cn }}</span>
            </div>
            <el-button text size="small" class="aab-phrase-copy" :icon="CopyDocument" @click="copy(p.phrase)">复制</el-button>
          </div>
        </div>
      </section>

      <!-- 语法点 + 易错提醒（两列） -->
      <section v-if="(data.grammar_tips && data.grammar_tips.length) || (data.common_mistakes && data.common_mistakes.length)" class="aab-section">
        <div class="aab-two-col">
          <div v-if="(data.grammar_tips || []).length" class="aab-col-card is-info">
            <div class="aab-col-title">
              <span class="aab-iconbox iconbox-primary"><el-icon><Reading /></el-icon></span>
              <span>语法点</span>
            </div>
            <ul class="aab-col-list">
              <li v-for="(t, i) in data.grammar_tips" :key="i"><span class="aab-li-dot" />{{ t }}</li>
            </ul>
          </div>
          <div v-if="(data.common_mistakes || []).length" class="aab-col-card is-danger">
            <div class="aab-col-title">
              <span class="aab-iconbox iconbox-danger"><el-icon><Warning /></el-icon></span>
              <span>易错提醒</span>
            </div>
            <ul class="aab-col-list">
              <li v-for="(m, i) in data.common_mistakes" :key="i"><span class="aab-li-dot" />{{ m }}</li>
            </ul>
          </div>
        </div>
      </section>

      <!-- 学习提示 -->
      <section v-if="data.study_tip" class="aab-section">
        <div class="aab-col-card is-success aab-study">
          <div class="aab-col-title">
            <span class="aab-iconbox iconbox-success"><el-icon><Star /></el-icon></span>
            <span>学习提示</span>
          </div>
          <p class="aab-col-text">{{ data.study_tip }}</p>
        </div>
      </section>
    </template>

    <!-- ============ AI 翻译 ============ -->
    <template v-else-if="kind === 'ai'">
      <section class="aab-section">
        <div class="aab-section-head">
          <div class="aab-section-title">
            <span class="aab-iconbox iconbox-primary"><el-icon><Connection /></el-icon></span>
            <span>{{ data.direction === 'zh2en' ? '译文' : '标准译文' }}</span>
          </div>
        </div>
        <div class="aab-overview">
          <span class="aab-iconbox iconbox-warning aab-overview-ic"><el-icon><MagicStick /></el-icon></span>
          <div class="aab-overview-body">
            <p v-if="data.direction !== 'zh2en' && data.standard_cn" class="aab-overview-text">{{ data.standard_cn }}</p>
            <template v-else>
              <p v-if="data.standard_en" class="aab-overview-text">{{ data.standard_en }}</p>
              <p v-if="data.advanced_en" class="aab-overview-sub">{{ data.advanced_en }}</p>
            </template>
            <div v-if="data.difficulty || data.register || data.writing_friendly" class="aab-overview-tags">
              <el-tag v-if="data.difficulty" size="small" type="primary" effect="light" round>{{ data.difficulty }}</el-tag>
              <el-tag v-if="data.register" size="small" effect="plain" round>{{ data.register }}</el-tag>
              <el-tag v-if="data.writing_friendly" size="small" type="success" effect="light" round>适合写作</el-tag>
            </div>
          </div>
        </div>
      </section>

      <section v-if="(data.phrases || []).length" class="aab-section">
        <div class="aab-section-head">
          <div class="aab-section-title">
            <span class="aab-iconbox iconbox-success"><el-icon><PriceTag /></el-icon></span>
            <span>核心词块</span>
          </div>
        </div>
        <div class="aab-phrases-grid">
          <div v-for="(p, i) in data.phrases" :key="i" class="aab-phrase-card">
            <div class="aab-phrase-main">
              <span class="aab-phrase-en">{{ p.en }}</span>
              <span class="aab-phrase-cn">{{ p.cn }}</span>
            </div>
            <el-button text size="small" class="aab-phrase-copy" :icon="CopyDocument" @click="copy(p.en)">复制</el-button>
          </div>
        </div>
      </section>

      <section v-if="data.rewrite && (data.rewrite.standard || data.rewrite.advanced)" class="aab-section">
        <div class="aab-section-head">
          <div class="aab-section-title">
            <span class="aab-iconbox iconbox-primary"><el-icon><Files /></el-icon></span>
            <span>同义改写</span>
          </div>
        </div>
        <div class="aab-segments">
          <div v-if="data.rewrite.standard" class="aab-segment seg-primary">
            <div class="aab-segment-head">
              <span class="aab-pill pill-primary">标准版</span>
              <span class="aab-segment-num">改写 1</span>
            </div>
            <p class="aab-segment-text">{{ data.rewrite.standard }}</p>
          </div>
          <div v-if="data.rewrite.advanced" class="aab-segment seg-warning">
            <div class="aab-segment-head">
              <span class="aab-pill pill-warning">高分版</span>
              <span class="aab-segment-num">改写 2</span>
            </div>
            <p class="aab-segment-text">{{ data.rewrite.advanced }}</p>
          </div>
        </div>
      </section>

      <section v-if="data.mistake || data.writing_tip" class="aab-section">
        <div class="aab-two-col">
          <div v-if="data.mistake" class="aab-col-card is-danger">
            <div class="aab-col-title">
              <span class="aab-iconbox iconbox-danger"><el-icon><Warning /></el-icon></span>
              <span>易错提醒</span>
            </div>
            <p class="aab-col-text">{{ data.mistake }}</p>
          </div>
          <div v-if="data.writing_tip" class="aab-col-card is-success">
            <div class="aab-col-title">
              <span class="aab-iconbox iconbox-success"><el-icon><Star /></el-icon></span>
              <span>写作提示</span>
            </div>
            <p class="aab-col-text">{{ data.writing_tip }}</p>
          </div>
        </div>
      </section>
    </template>

    <!-- ============ 未知结构 ============ -->
    <template v-else-if="data">
      <div v-if="extraEntries.length" class="aab-extra">
        <div v-for="[k, v] in extraEntries" :key="k" class="aab-extra-row">
          <span class="aab-extra-key">{{ k }}</span>
          <span class="aab-extra-val">{{ typeof v === 'object' ? JSON.stringify(v) : v }}</span>
        </div>
      </div>
      <p v-else class="aab-empty">无分析内容</p>
    </template>

    <p v-else class="aab-empty">无法解析 AI 分析内容</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { CopyDocument, Connection, Files, PriceTag, MagicStick, Warning, Star, Reading } from '@element-plus/icons-vue'
import { escapeHtml } from '@/components/ai/AiRenderUtil'

/**
 * 把 seg.explain 中的"主语【xxx】/动词【xxx】/宾语【xxx】..."和〈xxx〉
 * 转成彩色 chip，去掉所有中英括号/引号，靠字体颜色和语义区分。
 * 与 long-sentence 页保持一致的解析规则。
 *
 * 颜色用 inline style 写入：浏览器解析 HTML 时直接生效，
 * 不依赖任何 CSS 类、scoped、CSS 文件加载，100% 命中。
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
// 词性 ↔ 内容之间的装饰竖线：直接画在 <b> 的右边框上（inline-block 元素的 border 100% 渲染）
const ROLE_TEXT_STYLE = 'display:inline-block;font-weight:600;font-size:14px;line-height:18px;height:18px;border-right:2px solid #cbd5e1;border-radius:1px;padding:0 20px 0 0;margin:0 20px 0 0;vertical-align:middle;'

function renderExplain(text) {
  if (!text) return ''
  let html = escapeHtml(text)
  // 〈xxx〉 → 浅色短语标签（去掉尖括号）
  html = html.replace(/〈([^〈〉]+)〉/g, `<em style="${QUOTE_STYLE}">$1</em>`)
  // ===== 格式 A：主语【xxx】、动词是【xxx】、宾语为【xxx】... =====
  // 词性后插一个 inline-block 竖线 <i>，视觉上分隔词性与内容
  ROLE_RULES.forEach(({ role, color }) => {
    const re = new RegExp(`(${role})(是|为|的)?【([^【】]+)】`, 'g')
    html = html.replace(re, (_, roleText, copula, content) => {
      const copulaHtml = copula ? `<span style="${COPULA_STYLE}">${copula}</span>` : ''
      return `<b style="color:${color};${ROLE_TEXT_STYLE}">${roleText}</b>${copulaHtml}<span style="${TEXT_STYLE}">${content}</span>`
    })
  })
  // ===== 格式 B：自然语言格式 =====
  // 实际 AI 输出多样：
  //   "主语是xxx，动词是yyy，宾语是zzz"
  //   "主语 xxx、动词 yyy、宾语 zzz"   ← 无"是"
  //   "主语的xxx，动词的yyy，宾语的zzz"
  // 一次性匹配所有角色词 + 可选"是/为/的"+ 可选空白 + 内容 + 到下一个分隔符
  const rolePattern = ROLE_RULES.map(r => r.role).join('|')
  const reB = new RegExp(`(${rolePattern})(?:是|为|的)?\\s*([^、，,。；\\n]{1,40}?)(?=[、，,。；\\n]|$)`, 'g')
  html = html.replace(reB, (match, roleText, content) => {
    // 已经被 chip 包裹的跳过（防止重复匹配已处理过的角色词）
    if (match.indexOf('<b style="color:') !== -1) return match
    const rule = ROLE_RULES.find(r => r.role === roleText)
    if (!rule) return match
    return `<b style="color:${rule.color};${ROLE_TEXT_STYLE}">${roleText}</b><span style="${TEXT_STYLE}">${content}</span>`
  })
  // 兜底：任何残留的字面竖线（半角/全角）统一替换为带间距的装饰竖线
  html = html.replace(/[|｜]/g, `<i style="display:inline-block;width:2px;height:16px;background:#cbd5e1;border-radius:1px;margin:0 20px;vertical-align:middle;"></i>`)
  // 顿号 / 句末标点统一浅灰色，弱化但保留断句节奏
  html = html.replace(/、/g, `<span style="${DOT_STYLE}">、</span>`)
  // 兜底：清理所有残留中英括号 / 引号（颜色 + 角色文字足够定位，不再需要中括号锚点）
  html = html.replace(/[\[\]【】()（）〈〉《》「」『』""'']/g, '')
  return html
}

const props = defineProps({
  data: { type: Object, default: null }
})

const kind = computed(() => {
  const d = props.data
  if (!d || typeof d !== 'object') return 'empty'
  if (d.direction || d.standard_cn || d.standard_en || d.rewrite || d.writing_tip !== undefined) return 'ai'
  if (d.structure || d.key_phrases || d.cn_trans || d.study_tip !== undefined
      || (d.grammar_tips && d.grammar_tips.length)
      || (d.common_mistakes && d.common_mistakes.length)) return 'long'
  return 'unknown'
})

const AI_KNOWN = ['direction', 'standard_cn', 'standard_en', 'advanced_en', 'difficulty', 'register', 'writing_friendly', 'phrases', 'rewrite', 'mistake', 'writing_tip']
const LONG_KNOWN = ['sentence', 'cn_trans', 'structure', 'key_phrases', 'grammar_tips', 'common_mistakes', 'study_tip']
const extraEntries = computed(() => {
  if (!props.data || typeof props.data !== 'object') return []
  const known = kind.value === 'ai' ? AI_KNOWN : (kind.value === 'long' ? LONG_KNOWN : [])
  return Object.entries(props.data).filter(([k]) => !known.includes(k))
})

// 长难句 structure 真实形状：{label, intro, segments:[{role, role_color, text, explain}]}
const structureLabel = computed(() => (props.data && props.data.structure && props.data.structure.label) || '')
const structureIntro = computed(() => (props.data && props.data.structure && props.data.structure.intro) || '')
const segments = computed(() => {
  const s = props.data && props.data.structure
  if (!s) return []
  const segs = Array.isArray(s.segments) ? s.segments : []
  return segs
})

// 段位颜色：role_color 1-5 映射；缺失则按索引循环
const COLOR_POOL = ['primary', 'warning', 'success', 'danger', 'info']
const COLOR_MAP = { '1': 'primary', '2': 'warning', '3': 'success', '4': 'danger', '5': 'info' }
function segClass(i, s) {
  const c = COLOR_MAP[String(s && s.role_color)] || COLOR_POOL[i % COLOR_POOL.length]
  return { ['seg-' + c]: true }
}
function segPillClass(i, s) {
  const c = COLOR_MAP[String(s && s.role_color)] || COLOR_POOL[i % COLOR_POOL.length]
  return { ['pill-' + c]: true }
}

function copy(text) {
  if (!text) return
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text).then(
      () => ElMessage.success('已复制'),
      () => ElMessage.warning('复制失败，请手动选择')
    )
  } else {
    ElMessage.warning('当前环境不支持复制')
  }
}
</script>

<style scoped lang="scss">
.ai-analysis-block {
  padding: 20px 22px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
  box-shadow: var(--el-box-shadow-light);
  font-size: 14px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

/* 统一分块 */
.aab-section { margin-top: 20px; }
.aab-section:first-child { margin-top: 0; }

.aab-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.aab-section-title {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  letter-spacing: 0.2px;
}

/* 彩色图标方块 */
.aab-iconbox {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 7px;
  font-size: 14px;
  flex: 0 0 auto;
  &.iconbox-primary { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
  &.iconbox-warning { background: var(--el-color-warning-light-9); color: var(--el-color-warning); }
  &.iconbox-success { background: var(--el-color-success-light-9); color: var(--el-color-success); }
  &.iconbox-danger  { background: var(--el-color-danger-light-9);  color: var(--el-color-danger); }
  &.iconbox-info    { background: var(--el-color-info-light-9);    color: var(--el-color-info); }
}

/* pill 标签 */
.aab-pill {
  display: inline-block;
  padding: 3px 12px;
  font-size: 12.5px;
  font-weight: 600;
  border-radius: 999px;
  flex: 0 0 auto;
  &.pill-primary { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
  &.pill-warning { background: var(--el-color-warning-light-9); color: var(--el-color-warning); }
  &.pill-success { background: var(--el-color-success-light-9); color: var(--el-color-success); }
  &.pill-danger  { background: var(--el-color-danger-light-9);  color: var(--el-color-danger); }
  &.pill-info    { background: var(--el-color-info-light-9);    color: var(--el-color-info); }
}

/* 整体结构 💡 卡片 */
.aab-overview {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 14px 16px;
  background: var(--el-color-warning-light-9);
  border-left: 4px solid var(--el-color-warning);
  border-radius: 10px;
}
.aab-overview-ic { margin-top: 1px; }
.aab-overview-body { flex: 1 1 auto; }
.aab-overview-text {
  margin: 0;
  font-size: 14.5px;
  color: var(--el-text-color-primary);
  line-height: 1.85;
}
.aab-overview-sub {
  margin: 8px 0 0;
  padding-top: 8px;
  border-top: 1px dashed var(--el-color-warning-light-7);
  font-size: 14px;
  color: var(--el-text-color-regular);
  line-height: 1.8;
}
.aab-overview-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

/* 逐段拆解 */
.aab-segments { display: flex; flex-direction: column; gap: 12px; }
.aab-segment {
  padding: 14px 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  &.seg-primary { border-left: 4px solid var(--el-color-primary); }
  &.seg-warning { border-left: 4px solid var(--el-color-warning); }
  &.seg-success { border-left: 4px solid var(--el-color-success); }
  &.seg-danger  { border-left: 4px solid var(--el-color-danger); }
  &.seg-info    { border-left: 4px solid var(--el-color-info); }
}
.aab-segment-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.aab-segment-num {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.aab-segment-text {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  line-height: 1.7;
}
.aab-segment-explain {
  margin: 10px 0 0;
  display: flex;
  gap: 8px;
  align-items: flex-start;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.8;
}

/* 行内语义着色：仅用字体颜色 + 字重标记角色，无背景块，保持专注力 */
/* 注意：renderExplain 经由 v-html 注入，v-html 内容不带 scoped data-v 属性，
   Vue 的 :deep() 编译产物对顶级独立选择器并不可靠（实测颜色规则丢失）。
   这些 chip 样式已移到下方独立的 <style>（不带 scoped）块，全局生效。 */
.aab-li-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-primary);
  flex: 0 0 auto;
  margin-top: 9px;
}

/* 词块网格（可复制） */
.aab-phrases-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 12px;
}
.aab-phrase-card {
  position: relative;
  padding: 12px 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-left: 4px solid var(--el-color-primary);
  border-radius: 10px;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
  &:hover {
    border-color: var(--el-color-primary-light-5);
    border-left-color: var(--el-color-primary);
    box-shadow: var(--el-box-shadow-lighter);
  }
}
.aab-phrase-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding-right: 50px;
  min-height: 38px;
}
.aab-phrase-en {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  line-height: 1.5;
}
.aab-phrase-cn {
  font-size: 12.5px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
.aab-phrase-copy {
  position: absolute;
  top: 8px;
  right: 8px;
  color: var(--el-color-primary);
  font-size: 12px;
  padding: 2px 6px;
}

/* 语法 / 易错 / 提示 两列卡片 */
.aab-two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  @media (max-width: 640px) { grid-template-columns: 1fr; }
}
.aab-col-card {
  padding: 14px 16px;
  border-radius: 12px;
  &.is-info    { background: var(--el-color-primary-light-9); }
  &.is-danger  { background: var(--el-color-danger-light-9); }
  &.is-success { background: var(--el-color-success-light-9); }
  &.aab-study { grid-column: 1 / -1; }
}
.aab-col-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  .is-info &    { color: var(--el-color-primary); }
  .is-danger &  { color: var(--el-color-danger); }
  .is-success & { color: var(--el-color-success); }
}
.aab-col-text {
  margin: 10px 0 0;
  font-size: 13.5px;
  color: var(--el-text-color-regular);
  line-height: 1.85;
}
.aab-col-list {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
  li {
    display: flex;
    gap: 8px;
    align-items: flex-start;
    font-size: 13.5px;
    color: var(--el-text-color-regular);
    line-height: 1.85;
    margin-bottom: 4px;
  }
  .is-info .aab-li-dot   { background: var(--el-color-primary); }
  .is-danger .aab-li-dot { background: var(--el-color-danger); }
}

/* 兜底 */
.aab-extra {
  border-radius: 10px;
  overflow: hidden;
  background: var(--el-fill-color-light);
}
.aab-extra-row {
  display: flex;
  gap: 10px;
  padding: 8px 12px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
  &:last-child { border-bottom: none; }
}
.aab-extra-key {
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}
.aab-extra-val {
  font-size: 13px;
  color: var(--el-text-color-regular);
  word-break: break-word;
}

.aab-empty {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
