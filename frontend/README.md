# 词灵学园 · 前端（Ciling-Academy Frontend）

AI 增强型英语单词学习平台的前端工程。Vue 3 + Vite 5 构建，追求**加载性能**与**流量开销**的极致优化：全站 JS（含全部页面懒加载分块）仅约 1.1 MB，词灵序列帧动画经 WebP 重编码后仅 4.1 MB。

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 框架 | Vue 3.5（Composition API，纯 JavaScript 无 TS） |
| 构建 | Vite 5 + @vitejs/plugin-vue |
| UI 组件 | Element Plus（自研按需引入，无全量打包） |
| 状态 / 路由 | Pinia · Vue Router 4（路由级代码分割） |
| 样式 | SCSS（全局注入 variables / mixins） |
| 图标 | @iconify/vue 离线图标包（构建时抽取，运行时零网络请求） |

## 快速启动

```bash
# 要求 Node.js 18+
npm install
npm run dev        # 开发服务器 http://localhost:5173
npm run build      # 生产构建 → dist/
npm run preview    # 本地预览构建产物
```

开发环境代理：`/api` 与 `/uploads` 自动转发到本地后端 `http://localhost:8080`。

## 性能与流量优化

### 1. Element Plus 子路径按需引入（产物 -960 KB）

通过自定义 resolver 将每个组件映射到 `element-plus/es/components/<name>/index.mjs` 子路径，JS 与样式双按需：

- 不用官方 `ElementPlusResolver`：它从主入口 `element-plus/es` 命名导入，而主入口顶层执行
  `defaults_default.install`（闭包引用全部组件注册），导致 tree-shaking 失效、整包进入产物（960 KB）。
- 模板组件与 `ElMessage` / `ElMessageBox` / `ElNotification` 等函数式 API 由
  `unplugin-auto-import` + `unplugin-vue-components` 自动注入，无需手写 import。
- `v-loading` 指令在 `main.js` 从 loading 子路径注册。

### 2. 构建产物优化

- 路由级懒加载：全部页面与 10 款小游戏均为独立 chunk，按需加载
- `manualChunks` 抽取 `vue-vendor`（vue / vue-router / pinia）：业务迭代不影响框架缓存，回访命中率最大化
- 生产构建 `esbuild.drop` 移除全部 `console` / `debugger`
- `target: es2015` 兼顾兼容性与压缩率

### 3. 静态资源瘦身

| 资源 | 优化前 | 优化后 |
| --- | --- | --- |
| 词灵序列帧（309 帧 PNG 640×640） | 76.4 MB | **4.1 MB**（WebP 320×320，q80，按最大显示尺寸 160px @2x 重编码） |
| logo.png（favicon） | 398.7 KB | **6.1 KB**（128×128 palette PNG，兼容 Safari 图标） |
| logo（页面 `<img>`） | — | **19.6 KB**（256×256 WebP） |

### 4. 运行时加载策略

- Iconify 图标离线包：构建时用 `scripts/collect-icons.js` 从源码抽取用到的图标注册进本地缓存，运行时零图标请求
- 词灵序列帧按模式预加载（WebP 后单模式约 1 MB），页面标签页隐藏时自动暂停播放
- 首屏骨架屏内联于 `index.html`：CSS 未就绪时不白屏，Vue 挂载后平滑替换

## 目录结构

```
frontend/
├─ public/
│  ├─ frames/                  # 词灵序列帧（WebP）+ manifest.json
│  ├─ logo.webp / logo.png     # 页面 logo / favicon
│  └─ covers/ background.jpg
├─ scripts/
│  └─ collect-icons.js         # 图标离线包抽取脚本
├─ src/
│  ├─ api/                     # axios 封装与接口模块
│  ├─ assets/
│  │  ├─ icons/                # Iconify 离线图标 JSON
│  │  └─ scss/                 # reset / variables / mixins
│  ├─ components/
│  │  ├─ layout/MainLayout.vue # 全局布局（双态导航 + 抽屉菜单）
│  │  ├─ ai/LingSpirit.vue     # 词灵序列帧动画组件
│  │  └─ common/               # 验证码弹窗 / AI 分析块等
│  ├─ composables/             # useGame 等组合式函数
│  ├─ router/                  # 路由（全部懒加载 + 静态文档页）
│  ├─ store/                   # Pinia
│  ├─ views/                   # 页面 + 10 款小游戏 + 静态文档页
│  ├─ App.vue                  # 根组件（el-config-provider 中文语言包）
│  └─ main.js                  # 入口（按需样式 + v-loading 指令）
├─ index.html                  # 内联首屏骨架屏
├─ vite.config.js              # 按需引入 resolver + 构建优化
└─ package.json
```

## 图标维护

项目图标走离线包机制，新增图标后需重扫源码字面量：

```bash
node scripts/collect-icons.js
```

后端动态拼接的图标名（如成就系统）需同步维护白名单，否则图标渲染空白但不报错。

## 配套后端

后端为 SpringBoot 3 + MySQL + Redis + Qdrant（详见仓库根目录 README）。
