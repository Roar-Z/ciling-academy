import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import AutoImport from 'unplugin-auto-import/vite'
import { existsSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'

/* ============ Element Plus 子路径按需 resolver ============
 * 不能用官方 ElementPlusResolver：它从主入口 element-plus/es 命名导入，
 * 而主入口顶层执行 `defaults_default.install`（闭包引用全部组件注册），
 * 导致 tree-shaking 失效、整包打进产物（~1MB）。
 * 这里改为从 components/<name>/index.mjs 子路径导入，真正按需打包。
 */
const epComponentsDir = fileURLToPath(
  new URL('./node_modules/element-plus/es/components', import.meta.url)
)
const EP_SPECIAL_DIRS = {
  ElSelectV2: 'select-v2',
  ElTableV2: 'table-v2',
  ElTreeV2: 'tree-v2'
}

/* 子组件目录只有 style/ 没有 index.mjs（JS 在父组件目录 re-export），
 * 这里按 element-plus 官方固定结构映射到父组件目录 */
const EP_SUB_MAP = {
  'form-item': 'form',
  'dropdown-item': 'dropdown',
  'dropdown-menu': 'dropdown',
  option: 'select',
  'option-group': 'select',
  'radio-group': 'radio',
  'radio-button': 'radio',
  'table-column': 'table',
  'tab-pane': 'tabs',
  'skeleton-item': 'skeleton',
  'menu-item': 'menu',
  'menu-item-group': 'menu',
  'sub-menu': 'menu',
  'breadcrumb-item': 'breadcrumb',
  'checkbox-button': 'checkbox',
  'checkbox-group': 'checkbox',
  'collapse-item': 'collapse',
  'carousel-item': 'carousel',
  'descriptions-item': 'descriptions',
  'timeline-item': 'timeline',
  'avatar-group': 'avatar',
  'tour-step': 'tour',
  'anchor-link': 'anchor',
  'splitter-panel': 'splitter'
}

function kebabCase(str) {
  return str
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .replace(/([A-Z])([A-Z][a-z])/g, '$1-$2')
    .toLowerCase()
}

function elementPlusOnDemandResolver() {
  return {
    type: 'component',
    resolve(name) {
      if (!/^El[A-Z]/.test(name)) return
      let dir = EP_SPECIAL_DIRS[name] || kebabCase(name.slice(2))
      // 子组件目录无 index.mjs 时回退到父组件目录
      if (!existsSync(`${epComponentsDir}/${dir}/index.mjs`)) {
        dir = EP_SUB_MAP[dir] || dir
      }
      if (!existsSync(`${epComponentsDir}/${dir}/index.mjs`)) return
      return {
        name,
        from: `element-plus/es/components/${dir}/index.mjs`,
        sideEffects: `element-plus/es/components/${dir}/style/css.mjs`
      }
    }
  }
}

// 词灵学园前端构建配置
export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需引入：el-* 组件与 ElMessage 等函数式 API 自动按需导入
    //（JS + 样式），无需手写 element-plus 的 import
    AutoImport({
      // ElMessage 指向全局包装：相同文案 3 秒内只弹一次（样式由包装模块自行引入）
      imports: [{ '@/utils/el-message': ['ElMessage'] }],
      resolvers: [elementPlusOnDemandResolver()],
      dts: false
    }),
    Components({
      resolvers: [elementPlusOnDemandResolver()],
      dts: false
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        // 预注入变量与混入，所有 .vue/.scss 都可直接 @include
        additionalData: `
          @use "@/assets/scss/variables.scss" as *;
          @use "@/assets/scss/mixins.scss" as *;
        `
      }
    }
  },
  esbuild: {
    // 生产环境移除 console 与 debugger，dev 模式不受影响
    drop: ['console', 'debugger']
  },
  build: {
    target: 'es2015',
    chunkSizeWarningLimit: 1024,
    rollupOptions: {
      output: {
        // 第三方库分包：稳定内容长效缓存，业务代码更新不影响框架缓存
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia']
        }
      }
    }
  },
  server: {
    port: 5173,
    open: false,
    // 空闲时预编译常用页面，避免 dev 模式下首次点击每个页面都要现场编译（转圈数秒）
    warmup: {
      clientFiles: [
        '/src/views/home/index.vue',
        '/src/views/login/index.vue',
        '/src/views/task/index.vue',
        '/src/views/study/review/index.vue',
        '/src/views/ai-assistant/index.vue',
        '/src/views/shop/index.vue',
        '/src/views/profile/index.vue'
      ]
    },
    // 开发环境代理到后端 SpringBoot
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
