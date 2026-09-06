// 生成前端本地图标静态资源：
// 1. 扫描 src 下所有代码中的字符串字面量，与 lucide 官方图标名取交集 => iconify-lucide.json
// 2. 收集显式带前缀的图标（如 noto:treasure-chest）=> iconify-noto.json
// 产物落在 src/assets/icons/，由 src/plugins/local-icons.js 注入 Iconify 本地缓存，
// 从此图标渲染零网络请求。图标有新增时重跑：node scripts/collect-icons.js
import { readFileSync, writeFileSync, readdirSync, statSync, mkdirSync } from 'node:fs'
import { join } from 'node:path'

const lucideFull = JSON.parse(readFileSync('node_modules/@iconify/json/json/lucide.json', 'utf8'))
const notoFull = JSON.parse(readFileSync('node_modules/@iconify/json/json/noto.json', 'utf8'))

const root = join(process.cwd(), 'src')
const files = []
;(function walk(dir) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    statSync(p).isDirectory() ? walk(p) : /\.(vue|js)$/.test(name) && files.push(p)
  }
})(root)

// 收集代码里出现过的所有字符串字面量（单/双引号）
const literals = new Set()
for (const f of files) {
  if (f.includes(join('src', 'assets'))) continue
  const code = readFileSync(f, 'utf8')
  for (const m of code.matchAll(/['"]([A-Za-z0-9][A-Za-z0-9:-]*)['"]/g)) literals.add(m[1])
}

function nameSet(full) {
  const set = new Set(Object.keys(full.icons))
  for (const [a, t] of Object.entries(full.aliases || {})) {
    if (typeof t === 'string' && set.has(t)) set.add(a)
  }
  return set
}
const lucideNames = nameSet(lucideFull)
const notoNames = nameSet(notoFull)

// lucide：字面量 ∩ 官方图标名（含别名），宁多勿漏
const pickedLucide = [...literals].filter((n) => lucideNames.has(n))

// 带前缀的图标（prefix:name 形式）
const prefixed = [...literals].filter((s) => /^[a-z0-9-]+:[a-z0-9-]+$/.test(s))
const pickedNoto = prefixed.filter((s) => s.startsWith('noto:')).map((s) => s.slice(5))

function buildCollection(prefix, full, names) {
  const icons = {}
  const aliases = {}
  for (const n of new Set(names)) {
    if (full.icons[n]) icons[n] = full.icons[n]
    else if (typeof full.aliases?.[n] === 'string' && full.icons[full.aliases[n]]) aliases[n] = full.aliases[n]
  }
  // 别名目标也要带上
  for (const t of Object.values(aliases)) if (!icons[t] && full.icons[t]) icons[t] = full.icons[t]
  const out = { prefix, icons }
  // 必须带上集合级默认画布尺寸（如 lucide 为 24x24），否则 Iconify 按 16x16 渲染导致图标被裁切
  if (full.width) out.width = full.width
  if (full.height) out.height = full.height
  if (full.scale) out.scale = full.scale
  if (full.transform) out.transform = full.transform
  if (Object.keys(aliases).length) out.aliases = aliases
  return out
}

mkdirSync(join(root, 'assets', 'icons'), { recursive: true })
writeFileSync(
  join(root, 'assets', 'icons', 'iconify-lucide.json'),
  JSON.stringify(buildCollection('lucide', lucideFull, pickedLucide))
)
writeFileSync(
  join(root, 'assets', 'icons', 'iconify-noto.json'),
  JSON.stringify(buildCollection('noto', notoFull, pickedNoto))
)

console.log(`lucide: ${pickedLucide.length} 个图标，noto: ${pickedNoto.length} 个图标`)
console.log('可疑未识别前缀：', prefixed.filter((s) => !s.startsWith('noto:')))
