import { createApp } from 'vue'
import { createPinia } from 'pinia'
// 注意：必须从子路径导入，从 element-plus 主入口导入会拉入全量组件（主入口
// install 闭包引用全部组件注册，tree-shaking 失效）
import { ElLoadingDirective } from 'element-plus/es/components/loading/index.mjs'
// 函数式 API（ElMessage/ElMessageBox/ElNotification）由 vite 插件按需注入 JS+样式，
// 但它们可能在任意模块最先被调用，此处兜底引入样式保证首屏前可用
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import 'element-plus/es/components/loading/style/css'
import '@/assets/scss/reset.scss'
import '@/assets/scss/explain-chips.scss'
import '@/assets/scss/mobile.scss'
import App from './App.vue'
import router from './router'
import './plugins/local-icons'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.directive('loading', ElLoadingDirective)

app.mount('#app')
