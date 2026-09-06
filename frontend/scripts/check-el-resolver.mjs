// 一次性校验：模板 <el-xxx> 标签与源码 El* 标识符都能映射到
// element-plus/es/components/<dir>/index.mjs（与 vite.config.js resolver 同逻辑）
import { readFileSync, readdirSync, existsSync } from 'node:fs'
import { join } from 'node:path'

const srcDir = 'src'
const epDir = 'node_modules/element-plus/es/components'
const V2 = { ElSelectV2: 'select-v2', ElTableV2: 'table-v2', ElTreeV2: 'tree-v2' }
const SUB_MAP = {
  'form-item': 'form', 'dropdown-item': 'dropdown', 'dropdown-menu': 'dropdown',
  option: 'select', 'option-group': 'select', 'radio-group': 'radio', 'radio-button': 'radio',
  'table-column': 'table', 'tab-pane': 'tabs', 'skeleton-item': 'skeleton',
  'menu-item': 'menu', 'menu-item-group': 'menu', 'sub-menu': 'menu',
  'breadcrumb-item': 'breadcrumb', 'checkbox-button': 'checkbox', 'checkbox-group': 'checkbox',
  'collapse-item': 'collapse', 'carousel-item': 'carousel', 'descriptions-item': 'descriptions',
  'timeline-item': 'timeline', 'avatar-group': 'avatar', 'tour-step': 'tour',
  'anchor-link': 'anchor', 'splitter-panel': 'splitter'
}
// main.js 等处显式从子路径 import 的标识符不走 resolver
const EXPLICIT = new Set(['ElLoadingDirective'])

function pascalToKebab(s) {
  return s.replace(/([a-z0-9])([A-Z])/g, '$1-$2').replace(/([A-Z])([A-Z][a-z])/g, '$1-$2').toLowerCase()
}
function resolveDir(dir) {
  if (!existsSync(join(epDir, dir, 'index.mjs'))) dir = SUB_MAP[dir] || dir
  return existsSync(join(epDir, dir, 'index.mjs')) ? dir : null
}
function* walk(dir) {
  for (const f of readdirSync(dir, { withFileTypes: true })) {
    const p = join(dir, f.name)
    if (f.isDirectory()) yield* walk(p)
    else if (f.name.endsWith('.vue') || f.name.endsWith('.js')) yield p
  }
}

const tags = new Set(), comps = new Set()
for (const file of walk(srcDir)) {
  const s = readFileSync(file, 'utf8')
  for (const m of s.matchAll(/<el-([a-z0-9-]+)/g)) tags.add('el-' + m[1])
  for (const m of s.matchAll(/\bEl([A-Z][A-Za-z0-9]*)\b(?!\w)/g)) comps.add('El' + m[1])
}

let bad = []
for (const t of tags) {
  const dir = resolveDir(t.slice(3))
  if (!dir) { bad.push(t + ' -> no dir'); continue }
  if (!existsSync(join(epDir, dir, 'style', 'css.mjs'))) bad.push(t + ' -> missing style: ' + dir + '/style/css.mjs')
}
for (const c of comps) {
  if (!/^El[A-Z]/.test(c) || EXPLICIT.has(c)) continue
  const dir = resolveDir(V2[c] || pascalToKebab(c.slice(2)))
  if (!dir) { bad.push(c + ' -> no dir'); continue }
  if (!existsSync(join(epDir, dir, 'style', 'css.mjs'))) bad.push(c + ' -> missing style: ' + dir + '/style/css.mjs')
}
console.log('checked tags:', tags.size, '| checked El* ids:', comps.size)
if (bad.length) { console.log('PROBLEMS:'); bad.forEach(b => console.log(' -', b)); process.exitCode = 1 }
else console.log('ALL OK: every el-* tag and El* id maps to a valid subpath + style')
