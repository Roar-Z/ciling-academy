# 前端说明

Vue 3 + Vite 5 的单页应用。这个项目在构建层面做过一轮比较认真的优化，重点在加载性能
和流量上，这里把做法和数字都写清楚。

## 环境

Node 18+，其他没了。

## 启动

```bash
npm install
npm run dev        # http://localhost:5173
npm run build      # 产物在 dist/
npm run preview    # 本地预览构建产物
```

开发时 `/api` 和 `/uploads` 会自动代理到 `http://localhost:8080`，需要后端也在跑。

## 做过哪些优化

**Element Plus 按需引入（省得最多的一处）**

没有用官方的 `ElementPlusResolver`，因为它从 `element-plus/es` 主入口做命名导入，而主
入口顶层会执行组件全量注册，tree-shaking 对它无效，960KB 的组件库整包进产物。

现在的做法是自定义 resolver，把每个组件映射到 `element-plus/es/components/<name>/index.mjs`
子路径，JS 和样式都是真的按需。中间踩过一个坑：`form-item`、`tab-pane` 这类子组件目录
里只有 style 没有 index.mjs，它们的 JS 是从父组件目录 re-export 的，所以维护了一张
父子映射表兜底。模板组件和 `ElMessage` 这类函数式 API 由 unplugin 自动注入，业务代码
里不用手写 import。

**构建配置**

- 路由全部懒加载，10 款小游戏都是独立 chunk，用不到就不下载
- `manualChunks` 把 vue/vue-router/pinia 拆成 vue-vendor，业务代码迭代不影响框架缓存
- 生产构建删掉全部 console 和 debugger
- 最终产物：全站 JS 合计约 1.1MB（含所有懒加载分块），最大的单块 112KB

**静态资源**

| 资源 | 之前 | 之后 |
| --- | --- | --- |
| 词灵序列帧（309 帧，PNG 640×640） | 76.4 MB | 4.1 MB（WebP 320×320） |
| logo.png（favicon 用） | 398.7 KB | 6.1 KB |
| logo（页面 img 用） | 同上 | 19.6 KB（logo.webp） |

序列帧压得动的原因：组件里最大显示尺寸是 160px，按 2 倍屏算 320px 就够了，640px 是
浪费。转 WebP 之后单模式预加载只要 1MB 左右，观感没有差别。

**运行时**

- 图标走 Iconify 离线包，构建时用 `scripts/collect-icons.js` 从源码扫出用到的图标，
  运行时一个图标请求都不发。新增图标后记得重跑这个脚本
- 首屏骨架屏直接内联在 index.html 里，CSS 没就绪也不会白屏

## 目录结构

```
frontend/
├─ public/
│  ├─ frames/                  # 序列帧（WebP）+ manifest.json
│  ├─ logo.webp / logo.png
│  └─ covers/
├─ scripts/
│  └─ collect-icons.js         # 图标离线包抽取脚本
├─ src/
│  ├─ api/                     # axios 封装和接口模块
│  ├─ assets/
│  │  ├─ icons/                # Iconify 离线图标 JSON
│  │  └─ scss/                 # reset、variables、mixins
│  ├─ components/
│  │  ├─ layout/MainLayout.vue
│  │  ├─ ai/LingSpirit.vue     # 词灵序列帧组件
│  │  └─ common/
│  ├─ composables/             # useGame 之类
│  ├─ router/                  # 全部懒加载
│  ├─ store/                   # Pinia
│  ├─ views/                   # 页面、小游戏、静态文档页
│  └─ main.js
├─ index.html
├─ vite.config.js
└─ package.json
```

## 加图标注意事项

图标是离线包机制，新增图标后跑一遍：

```bash
node scripts/collect-icons.js
```

后端动态下发的图标名（比如成就系统）扫不到，需要在对应页面的图标白名单数组里手动
加一下，不然图标位置会空白，而且不报错，只能肉眼发现。
