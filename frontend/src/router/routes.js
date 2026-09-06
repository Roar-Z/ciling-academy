/**
 * 词灵学园路由表
 * 所有页面统一嵌套在 MainLayout 全局布局内（同一布局根据登录状态渲染双态导航）
 * meta.public = true 表示游客可访问（无需登录）
 */
export const routes = [
  {
    path: '/',
    component: () => import('@/components/layout/MainLayout.vue'),
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/home/index.vue'),
        meta: { title: '首页', public: true }
      },
      {
        path: 'intro',
        name: 'intro',
        component: () => import('@/views/intro/index.vue'),
        meta: { title: '介绍', public: true }
      },
      {
        path: 'login',
        name: 'login',
        component: () => import('@/views/login/index.vue'),
        meta: { title: '登录', public: true, hideChrome: true }
      },
      {
        path: 'ai-assistant',
        name: 'ai-assistant',
        component: () => import('@/views/ai-assistant/index.vue'),
        meta: { title: '词灵AI' }
      },
      {
        path: 'ai-note',
        name: 'ai-note',
        component: () => import('@/views/ai-note/index.vue'),
        meta: { title: '我的AI笔记' }
      },
      {
        path: 'reading-helper',
        name: 'reading-helper',
        component: () => import('@/views/reading-helper/index.vue'),
        meta: { title: '阅读助手' }
      },
      {
        path: 'word-book',
        name: 'word-book',
        component: () => import('@/views/study/word-book/index.vue'),
        meta: { title: '生词本' }
      },
      {
        path: 'translate',
        name: 'translate',
        component: () => import('@/views/translate/index.vue'),
        meta: { title: '翻译助手' }
      },
      {
        path: 'long-sentence',
        name: 'long-sentence',
        component: () => import('@/views/long-sentence/index.vue'),
        meta: { title: '长难句分析助手' }
      },
      {
        path: 'sentence',
        name: 'sentence',
        component: () => import('@/views/sentence/index.vue'),
        meta: { title: '句灵集' }
      },
      {
        path: 'review',
        name: 'review',
        component: () => import('@/views/study/review/index.vue'),
        meta: { title: '背单词复习' }
      },
      {
        path: 'word-test',
        name: 'word-test',
        component: () => import('@/views/study/word-test/index.vue'),
        meta: { title: '单词测验' }
      },
      {
        path: 'paper-list',
        name: 'paper-list',
        component: () => import('@/views/exercise-paper/paper-list.vue'),
        meta: { title: '练习试卷' }
      },
      {
        path: 'paper-generate',
        name: 'paper-generate',
        component: () => import('@/views/exercise-paper/paper-generate.vue'),
        meta: { title: 'AI生成试卷' }
      },
      {
        path: 'paper-do/:id',
        name: 'paper-do',
        component: () => import('@/views/exercise-paper/paper-do.vue'),
        meta: { title: '做题' }
      },
      {
        path: 'game-park',
        name: 'game-park',
        component: () => import('@/views/game-park/index.vue'),
        meta: { title: '趣味乐园' }
      },
      {
        path: 'game/:id',
        name: 'game-play',
        component: () => import('@/views/game-park/GamePlay.vue'),
        meta: { title: '趣味游戏' }
      },
      {
        path: 'shop',
        name: 'shop',
        component: () => import('@/views/shop/index.vue'),
        meta: { title: '金币商城' }
      },
      {
        path: 'task',
        name: 'task',
        component: () => import('@/views/task/index.vue'),
        meta: { title: '今日任务' }
      },
      {
        path: 'tools',
        name: 'tools',
        component: () => import('@/views/tools/index.vue'),
        meta: { title: '工具盒' }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心' }
      },
      {
        path: 'achievements',
        name: 'achievements',
        component: () => import('@/views/achievements/index.vue'),
        meta: { title: '成就殿堂' }
      },
      {
        path: 'report',
        name: 'report',
        component: () => import('@/views/report/index.vue'),
        meta: { title: '学习报告' }
      },
      {
        path: 'settings',
        name: 'settings',
        component: () => import('@/views/settings/index.vue'),
        meta: { title: '设置' }
      },
      // 静态文档页（关于/帮助/指南/更新日志/协议/隐私/联系），游客可访问
      ...[
        ['about', '关于词灵学园'],
        ['help', '帮助中心'],
        ['guide', '学习指南'],
        ['changelog', '更新日志'],
        ['agreement', '用户协议'],
        ['privacy', '隐私政策'],
        ['contact', '联系我们']
      ].map(([slug, title]) => ({
        path: slug,
        name: `doc-${slug}`,
        component: () => import('@/views/static/index.vue'),
        meta: { title, public: true },
        props: { slug }
      })),
      { path: ':pathMatch(.*)*', redirect: '/' }
    ]
  }
]
