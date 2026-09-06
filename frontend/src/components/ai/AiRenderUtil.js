/**
 * =============================================================
 * 词灵AI 渲染工具
 * 输入：AI 返回的原始 JSON 对象（或字符串）
 * 输出：格式化 HTML 片段（卡片 / 列表 / 折叠预览 / 段落）
 *
 * 铁律：
 * 1. 用户永远看不到 {} [] "" 等原始 JSON
 * 2. 兜底降级：解析异常时自动提取有用文本，整理成干净分段文字输出
 * 3. 只有用户主动点击「保存/导入」按钮，才把原始 JSON 提交给后端接口
 *
 * 说明：所有 AI 文本均经过 escapeHtml 转义后再拼入 HTML，杜绝 XSS。
 * =============================================================
 */

/** 5 种题型中文名 */
export const TYPE_LABEL = {
  en2cn: '英译汉单选',
  cn2en: '汉译英单选',
  spell_fill: '单词拼写填空',
  context_choice: '语境选词填空',
  match: '词义匹配题'
}

const DIFF_LABEL = { easy: '简单', medium: '中等', hard: '较难' }

/**
 * 入口：把 AI JSON 渲染成 HTML 片段
 * @param {object|string} raw AI 返回的 JSON
 * @param {object} [opts] hideTitle: 去掉渲染结果里的标题 h3（外层已单独展示标题时用）
 * @returns {{type:string, html:string, plain:string}}
 */
export function renderAiContent(raw, opts = {}) {
  try {
    const json = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!json || typeof json !== 'object' || Array.isArray(json)) {
      return fallback(typeof raw === 'string' ? raw : '')
    }
    const type = detectType(json)
    let html = buildHtml(type, json)
    if (opts.hideTitle) {
      // 仅移除第一处标题（各渲染器里标题都是首个 h3）
      html = html.replace(/<h3[^>]*>[\s\S]*?<\/h3>/, '')
    }
    return { type, html, plain: buildPlain(type, json) }
  } catch (e) {
    return fallback(typeof raw === 'string' ? raw : '')
  }
}

/** 识别渲染类型 */
export function detectType(json) {
  if (json.question_list) return 'paper'
  if (json.word_list) return 'cards'
  if (json.sentences) return 'reading'
  if (json.type === 'refuse') return 'refuse'
  if (json.error) return 'error'
  return 'text'
}

/** 解析异常兜底：提取有用文本整理成干净分段文字 */
function fallback(text) {
  const lines = String(text || '')
    .split(/\n+/)
    .map((s) => s.trim())
    .filter(Boolean)
  const html =
    '<div class="ai-render ai-text">' +
    lines.map((line) => `<p>${escapeHtml(line)}</p>`).join('') +
    '</div>'
  return { type: 'text', html, plain: lines.join('\n') }
}

function buildHtml(type, json) {
  switch (type) {
    case 'paper':
      return renderPaper(json)
    case 'cards':
      return renderCards(json)
    case 'reading':
      return renderReading(json)
    case 'refuse':
      return renderRefuse(json)
    case 'error':
      return renderError(json)
    default:
      return renderText(json)
  }
}

/* ---------------- 试卷预览 ---------------- */
/** spell_fill 兜底（与后端 cleanSpellStem 一致）：去掉 "(word)" 泄露；题干没有填空位时把答案词替换成横线 */
function cleanSpellStem(stem, ans) {
  const word = String(ans ?? '').trim()
  if (!word || !stem) return stem
  const safe = word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  let out = stem.replace(new RegExp(`\\(\\s*${safe}\\s*\\)`, 'g'), ' ______ ')
  if (!out.includes('_')) {
    out = out.replace(new RegExp(`\\b${safe}\\b`, 'gi'), '______')
  }
  // 合并被替换后相邻的下划线占位（如 "__ ______"），并清理多余空格
  out = out.replace(/_{1,}\s+_{2,}/g, '______').replace(/\s+([.,!?;:])/g, '$1')
  return out.trim()
}

function renderPaper(json) {
  const head =
    `<div class="ai-paper-head">` +
    `<h3 class="ai-paper-name">${escapeHtml(json.paper_name || 'AI练习试卷')}</h3>` +
    (json.paper_intro ? `<p class="ai-paper-intro">${escapeHtml(json.paper_intro)}</p>` : '') +
    (json.point_summary
      ? `<div class="ai-paper-summary"><strong>考点小结</strong><p>${escapeHtml(json.point_summary)}</p></div>`
      : '') +
    `</div>`

  const list = (json.question_list || [])
    .map((q, i) => {
      const opts = Array.isArray(q.opts) && q.opts.length
        ? `<ul class="ai-q-opts">${q.opts
            .map((opt, oi) => `<li><span class="opt-key">${String.fromCharCode(65 + oi)}.</span> ${escapeHtml(opt)}</li>`)
            .join('')}</ul>`
        : ''
      const ans = Array.isArray(q.ans) ? q.ans.join(' → ') : q.ans
      const stem = q.q_type === 'spell_fill' ? cleanSpellStem(q.stem, q.ans) : q.stem
      const answerBox =
        `<details class="ai-q-detail"><summary>查看答案与解析</summary>` +
        `<p class="ai-q-ans">答案：${escapeHtml(ans ?? '')}</p>` +
        (q.analysis ? `<p class="ai-q-analysis">解析：${escapeHtml(q.analysis)}</p>` : '') +
        `</details>`
      return (
        `<li class="ai-q-item">` +
        `<div class="ai-q-head"><span class="ai-q-seq">第 ${i + 1} 题</span>` +
        `<span class="ai-q-type">${escapeHtml(TYPE_LABEL[q.q_type] || q.q_type || '')}</span></div>` +
        `<p class="ai-q-stem">${escapeHtml(q.stem)}</p>` +
        opts + answerBox +
        `</li>`
      )
    })
    .join('')

  return `<div class="ai-render ai-paper">${head}<ol class="ai-q-list">${list}</ol></div>`
}

/* ---------------- 生词巩固包卡片 ---------------- */
function renderCards(json) {
  const head =
    `<div class="ai-cards-head">` +
    `<h3 class="ai-cards-title">${escapeHtml(json.group_title || '生词巩固包')}</h3>` +
    (json.group_note ? `<p class="ai-cards-note">${escapeHtml(json.group_note)}</p>` : '') +
    `</div>`

  const cards = (json.word_list || [])
    .map((w) => {
      const mq = w.mini_question
      const miniQ = mq
        ? `<div class="ai-mini-q"><p class="ai-mq-q">巩固练习：${escapeHtml(mq.q)}</p>` +
          `<ul>${(mq.opts || [])
            .map((o, i) => `<li><span class="opt-key">${String.fromCharCode(65 + i)}.</span> ${escapeHtml(o)}</li>`)
            .join('')}</ul>` +
          `<details><summary>答案与解析</summary><p>答案：${escapeHtml(mq.ans || '')}</p><p>${escapeHtml(mq.q_explain || '')}</p></details></div>`
        : ''
      const left = (
        `<div class="ai-wc-left">` +
        `<div class="ai-wc-top"><span class="ai-wc-word">${escapeHtml(w.word)}</span>` +
        `<span class="ai-wc-phonetic">${escapeHtml(w.phonetic || '')}</span></div>` +
        `<p class="ai-wc-meaning">${escapeHtml(w.cn_meaning || '')}</p>` +
        (w.mnemonic ? `<p class="ai-wc-line"><strong>助记：</strong>${escapeHtml(w.mnemonic)}</p>` : '') +
        (w.easy_mistake ? `<p class="ai-wc-line"><strong>易混提醒：</strong>${escapeHtml(w.easy_mistake)}</p>` : '') +
        (w.example
          ? `<p class="ai-wc-example"><strong>例句：</strong>${escapeHtml(w.example)}<br/>${escapeHtml(w.example_cn || '')}</p>`
          : '') +
        `</div>`
      )
      return (
        `<div class="ai-word-card">` +
        left +
        (miniQ ? `<div class="ai-wc-right">${miniQ}</div>` : '') +
        `</div>`
      )
    })
    .join('')

  return `<div class="ai-render ai-cards">${head}<div class="ai-word-cards">${cards}</div></div>`
}

/* ---------------- 阅读解析 ---------------- */
/** 高亮配色：浅底 + 深字 + 下划线，按 hard_points 出现顺序轮换 */
const HL_CLASSES = ['hl-1', 'hl-2', 'hl-3', 'hl-4', 'hl-5']

/** 给一段文本按 word→class 映射着色（不区分大小写、不破坏单词边界） */
function highlightOrigin(text, colorMap) {
  let html = escapeHtml(text)
  const entries = Object.entries(colorMap)
  if (!entries.length) return html
  for (const [word, cls] of entries) {
    // 先把 escapeHtml 的转义还原再做正则
    const safe = word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const re = new RegExp(`(?<![\\w])(${safe})(?![\\w])`, 'gi')
    html = html.replace(re, (m) => `<mark class="ai-hl ${cls}">${m}</mark>`)
  }
  return html
}

function renderReading(json) {
  const diff = DIFF_LABEL[json.difficulty] || json.difficulty || '未知'
  const sentences = (json.sentences || [])
    .map((s) => {
      const points = (s.hard_points || [])
      // 同句内每个重点词分配一种颜色，并在 origin 中对应高亮
      const colorMap = {}
      points.forEach((p, idx) => {
        if (!p || !p.word) return
        const cls = HL_CLASSES[idx % HL_CLASSES.length]
        colorMap[p.word] = cls
      })
      const originHtml = highlightOrigin(s.origin || '', colorMap)
      const pointsHtml = points
        .map((p, idx) => {
          const cls = HL_CLASSES[idx % HL_CLASSES.length]
          const extras = []
          if (p.synonym) extras.push(`<span class="ai-s-tag is-syn">同义</span>${escapeHtml(p.synonym)}`)
          if (p.grammar) extras.push(`<span class="ai-s-tag is-gram">语法</span>${escapeHtml(p.grammar)}`)
          if (p.colloc) extras.push(`<span class="ai-s-tag is-colloc">搭配</span>${escapeHtml(p.colloc)}`)
          const extrasHtml = extras.length
            ? `<div class="ai-s-extras">${extras.join(' · ')}</div>`
            : ''
          return (
            `<li><b class="ai-hl ${cls}">${escapeHtml(p.word)}</b> — ` +
            `${escapeHtml(p.explain || '')}${extrasHtml}</li>`
          )
        })
        .join('')
      return (
        `<div class="ai-sentence">` +
        `<p class="ai-s-origin">${originHtml}</p>` +
        (s.cn_trans ? `<p class="ai-s-cn">${escapeHtml(s.cn_trans)}</p>` : '') +
        (pointsHtml ? `<ul class="ai-s-points">${pointsHtml}</ul>` : '') +
        `</div>`
      )
    })
    .join('')
  const keyWords = (json.key_words || [])
    .map((w, i) => `<span class="ai-key-chip kc-${(i % 5) + 1}">${escapeHtml(w)}</span>`)
    .join('')
  return (
    `<div class="ai-render ai-reading">` +
    `<div class="ai-diff-row"><span class="ai-diff-badge">${escapeHtml(diff)}</span>` +
    (json.diff_desc ? `<span class="ai-diff-desc">${escapeHtml(json.diff_desc)}</span>` : '') +
    `</div>` +
    `<div class="ai-sentences">${sentences}</div>` +
    (keyWords ? `<div class="ai-key-words"><span class="ai-kw-label">重点词汇：</span>${keyWords}</div>` : '') +
    (json.study_advice
      ? `<div class="ai-advice"><strong>学习建议：</strong><span>${escapeHtml(json.study_advice)}</span></div>`
      : '') +
    `</div>`
  )
}

/* ---------------- 拒绝（无关话题拦截） ---------------- */
function renderRefuse(json) {
  return (
    `<div class="ai-render ai-refuse">` +
    `<h3>${escapeHtml(json.title || '不在服务范围')}</h3>` +
    `<p>${textToHtml(json.content || '')}</p>` +
    `</div>`
  )
}

/* ---------------- 错误 ---------------- */
function renderError(json) {
  return (
    `<div class="ai-render ai-error">` +
    `<p>${escapeHtml(json.error || '词灵AI暂时无法生成内容，请换个要求再试试')}</p>` +
    `</div>`
  )
}

/* ---------------- 普通文本（自由答疑） ---------------- */
function renderText(json) {
  const title = json.title ? `<h3>${escapeHtml(json.title)}</h3>` : ''
  const content = json.content ? `<p>${textToHtml(json.content)}</p>` : ''
  const tips = Array.isArray(json.tips) && json.tips.length
    ? `<ul class="ai-tips">${json.tips.map((t) => `<li>${escapeHtml(t)}</li>`).join('')}</ul>`
    : ''
  return `<div class="ai-render ai-text">${title}${content}${tips}</div>`
}

/* ---------------- 纯文本预览（用于消息列表/收藏） ---------------- */
function buildPlain(type, json) {
  switch (type) {
    case 'paper':
      return `${json.paper_name || 'AI试卷'}\n${json.paper_intro || ''}\n共 ${(json.question_list || []).length} 题`
    case 'cards':
      return `${json.group_title || '生词巩固包'}\n${json.group_note || ''}\n包含 ${(json.word_list || []).length} 个单词`
    case 'reading':
      return `难度：${DIFF_LABEL[json.difficulty] || json.difficulty || '-'} ${json.diff_desc || ''}`
    case 'refuse':
      return `${json.title || ''}\n${json.content || ''}`
    case 'error':
      return json.error || ''
    default:
      return `${json.title ? json.title + '\n' : ''}${json.content || ''}`
  }
}

/* ---------------- HTML 安全转义 ---------------- */
export function escapeHtml(str) {
  return String(str ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** 文本转 HTML：转义 + 换行 */
function textToHtml(str) {
  return escapeHtml(str).replace(/\n/g, '<br/>')
}
