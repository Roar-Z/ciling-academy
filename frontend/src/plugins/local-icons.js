/**
 * 本地图标注入：把构建时抽取的 Iconify 图标数据（src/assets/icons/*.json）
 * 注册进 @iconify/vue 的本地缓存，之后所有 <AppIcon> 渲染零网络请求。
 *
 * 新增图标后需重跑：node scripts/collect-icons.js
 */
import { addCollection } from '@iconify/vue'
import lucide from '@/assets/icons/iconify-lucide.json'
import noto from '@/assets/icons/iconify-noto.json'

addCollection(lucide)
addCollection(noto)
