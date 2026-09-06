<template>
  <div class="home-page">
    <!-- ============ Hero ============ -->
    <section class="hero">
      <div class="hero-content">
        <div class="hero-badge">
          <AppIcon name="sparkles" :size="14" />
          <span>AI 增强型英语单词学习平台</span>
        </div>
        <h1 class="hero-title">记单词，可以比想象中<br />更聪明、更轻松</h1>
        <p class="hero-desc">
          艾宾浩斯记忆曲线 × 词灵AI 智能解析，每天 10 分钟，
          让每个单词都真正被记住。
        </p>
        <div class="hero-actions">
          <el-button v-if="userStore.isLogin" type="primary" size="large" @click="go('/review?mode=new')">
            <AppIcon name="book-open" :size="17" />
            开始单词学习
          </el-button>
          <el-button v-if="userStore.isLogin" size="large" plain @click="go('/ai-assistant')">
            <AppIcon name="message-square" :size="17" />
            找词灵AI答疑
          </el-button>
          <template v-else>
            <el-button type="primary" size="large" @click="go('/login')">
              免费开始学习
              <AppIcon name="arrow-right" :size="17" />
            </el-button>
            <el-button size="large" plain @click="go('/intro')">了解更多</el-button>
          </template>
        </div>
        <div class="hero-meta">
          <span class="mi-item">
            <AppIcon name="check" :size="14" />
            免费注册
          </span>
          <span class="mi-item">
            <AppIcon name="check" :size="14" />
            核心功能永久免费
          </span>
          <span class="mi-item">
            <AppIcon name="check" :size="14" />
            学习记录云端保存
          </span>
        </div>
      </div>

      <!-- 产品示意：单词学习卡片 -->
      <div class="hero-art">
        <div class="art-backdrop"></div>

        <div class="art-card art-card-back">
          <span class="live-dot"></span>
          <AppIcon name="calendar-check" :size="16" />
          <span>今日复习 12 词</span>
        </div>

        <div class="art-card art-card-main">
          <div class="am-head">
            <span class="am-label">复习卡片</span>
            <span class="am-progress">{{ demoProgress }} / 12</span>
          </div>
          <transition name="word-swap" mode="out-in">
            <div :key="demoWord.word" class="am-word-block">
              <div class="am-word">{{ demoWord.word }}</div>
              <div class="am-phonetic">{{ demoWord.phonetic }}</div>
              <div class="am-meaning">{{ demoWord.meaning }}</div>
              <div class="am-example">
                <p class="ame-en">{{ demoWord.exampleEn }}</p>
                <p class="ame-cn">{{ demoWord.exampleCn }}</p>
              </div>
            </div>
          </transition>
          <div class="am-tags">
            <span class="amt-tag amt-blue">重点词</span>
            <span class="amt-tag amt-green">已掌握 2/4</span>
          </div>
          <div class="am-bar"><span :style="{ width: demoBarWidth }"></span></div>
        </div>

        <div class="art-card art-card-side">
          <AppIcon name="sparkles" :size="16" />
          <span>词灵AI 生成助记</span>
        </div>

        <!-- 艾宾浩斯记忆曲线：呼应核心卖点 -->
        <div class="art-chart">
          <div class="ac-head">
            <span class="ac-title">艾宾浩斯记忆曲线</span>
            <span class="ac-value">牢记率 98%</span>
          </div>
          <svg viewBox="0 0 220 86" class="ac-svg">
            <line x1="6" y1="70" x2="214" y2="70" class="grid-line" />
            <line x1="6" y1="44" x2="214" y2="44" class="grid-line" />
            <line x1="6" y1="18" x2="214" y2="18" class="grid-line" />
            <!-- 死记硬背：不复习的遗忘衰减 -->
            <path d="M10,16 C 30,54 52,66 92,69 S 180,72 212,73" class="fade-line" pathLength="1" />
            <!-- 按曲线复习：每次复习记忆率回升 -->
            <path
              id="memoPath"
              d="M10,16 C 26,46 42,58 60,62 L 68,38 C 86,50 104,56 124,58 L 132,36 C 150,48 178,51 212,52"
              class="memo-line"
              pathLength="1"
            />
            <circle cx="64" cy="60" r="3.5" class="dot d1" />
            <circle cx="128" cy="57" r="3.5" class="dot d2" />
            <circle cx="196" cy="52" r="3.5" class="dot d3" />
          </svg>
          <div class="ac-legend">
            <span><i class="lg lg-primary"></i>按曲线复习</span>
            <span><i class="lg lg-gray"></i>死记硬背</span>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 数据指标 ============ -->
    <section class="metrics">
      <div class="metrics-inner">
        <div class="metric" v-for="m in metrics" :key="m.label">
          <span class="m-icon" :style="{ background: m.color + '14' }">
            <AppIcon :name="m.icon" :size="20" :color="m.color" />
          </span>
          <div class="m-body">
            <div class="m-num" :style="{ color: m.color }">{{ displayMetric(m) }}</div>
            <div class="m-label">{{ m.label }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 核心能力 ============ -->
    <section class="section">
      <div class="section-head">
        <h2 class="section-title">三大核心能力</h2>
        <p class="section-desc">科学记忆曲线 × 大模型解析 × 游戏化训练</p>
      </div>
      <div class="feature-grid">
        <div v-for="f in features" :key="f.title" class="feature-card" @click="go(f.path)">
          <div class="fc-icon">
            <AppIcon :name="f.icon" :size="24" />
          </div>
          <h3 class="fc-title">{{ f.title }}</h3>
          <p class="fc-desc">{{ f.desc }}</p>
          <div class="fc-link">
            了解详情
            <AppIcon name="arrow-right" :size="14" />
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 双栏展示 ============ -->
    <section class="section showcase">
      <div class="showcase-row">
        <div class="sc-text">
          <div class="sc-eyebrow">
            <AppIcon name="languages" :size="14" />
            READING HELPER
          </div>
          <h2 class="sc-title">阅读遇到生词？<br />让词灵AI 帮你精读</h2>
          <p class="sc-desc">
            粘贴任意英文段落，AI 输出难度评级、逐句翻译、重点词汇与学习建议。
            生词本中已收藏的单词自动高亮，一键把新词加入生词本。
          </p>
          <ul class="sc-list">
            <li><AppIcon name="check" :size="15" /> 自动识别生词本已收藏单词</li>
            <li><AppIcon name="check" :size="15" /> 难度分级 · 长难句解析 · 学习建议</li>
            <li><AppIcon name="check" :size="15" /> 批量收藏生词，零成本接入复习计划</li>
          </ul>
          <el-button type="primary" plain @click="go('/reading-helper')">
            试试阅读助手
            <AppIcon name="arrow-right" :size="15" />
          </el-button>
        </div>
        <div class="sc-visual">
          <div class="visual-window">
            <div class="vw-bar">
              <span class="dot dot-red"></span>
              <span class="dot dot-yellow"></span>
              <span class="dot dot-green"></span>
            </div>
            <div class="vw-body">
              <div class="vw-line" style="width: 92%"></div>
              <div class="vw-line" style="width: 100%"></div>
              <div class="vw-line vw-mark" style="width: 76%"></div>
              <div class="vw-line" style="width: 88%"></div>
              <div class="vw-line" style="width: 60%"></div>
              <div class="vw-tags">
                <span class="vwt-tag">easy</span>
                <span class="vwt-tag">6 个重点词</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="showcase-row reverse">
        <div class="sc-text">
          <div class="sc-eyebrow">
            <AppIcon name="file-text" :size="14" />
            AI QUIZ
          </div>
          <h2 class="sc-title">从单词池出发，<br />一键生成练习试卷</h2>
          <p class="sc-desc">
            基于你的生词本自动出题，支持 5 种题型自由组合。
            导入后进入专门做题页，Java 后端自动批改、计分并收录错题。
          </p>
          <ul class="sc-list">
            <li><AppIcon name="check" :size="15" /> 英译汉 / 汉译英 / 拼写填空 / 语境选词 / 词义匹配</li>
            <li><AppIcon name="check" :size="15" /> 难度、题量、题型比例均可反复调整</li>
            <li><AppIcon name="check" :size="15" /> 错题自动收录，支持回看与复盘</li>
          </ul>
          <el-button type="primary" plain @click="go('/paper-generate')">
            生成一套试卷
            <AppIcon name="arrow-right" :size="15" />
          </el-button>
        </div>
        <div class="sc-visual">
          <div class="visual-window">
            <div class="vw-bar">
              <span class="dot dot-red"></span>
              <span class="dot dot-yellow"></span>
              <span class="dot dot-green"></span>
            </div>
            <div class="vw-body">
              <div class="vq-row">
                <span class="vqr-seq">Q1</span>
                <span class="vqr-line"></span>
                <span class="vqr-ok"><AppIcon name="check" :size="13" /></span>
              </div>
              <div class="vq-row">
                <span class="vqr-seq">Q2</span>
                <span class="vqr-line"></span>
                <span class="vqr-ok"><AppIcon name="check" :size="13" /></span>
              </div>
              <div class="vq-row">
                <span class="vqr-seq">Q3</span>
                <span class="vqr-line"></span>
                <span class="vqr-bad"><AppIcon name="x" :size="13" /></span>
              </div>
              <div class="vq-score">
                <AppIcon name="trending-up" :size="15" />
                得分 92 / 100
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 对比：为什么选择词灵学园 ============ -->
    <section class="section">
      <div class="section-head">
        <h2 class="section-title">为什么选择词灵学园</h2>
      </div>
      <div class="compare-card">
        <div class="cmp-col pain">
          <h3 class="cmp-title">传统背单词</h3>
          <ul class="cmp-list">
            <li v-for="p in compare.pains" :key="p">
              <AppIcon name="x" :size="15" class="cmp-x" />{{ p }}
            </li>
          </ul>
        </div>
        <div class="cmp-col gain">
          <h3 class="cmp-title">在词灵学园</h3>
          <ul class="cmp-list">
            <li v-for="g in compare.gains" :key="g">
              <AppIcon name="check" :size="15" class="cmp-check" />{{ g }}
            </li>
          </ul>
        </div>
      </div>
    </section>

    <!-- ============ 常见问题 ============ -->
    <section class="section">
      <div class="section-head">
        <h2 class="section-title">常见问题</h2>
      </div>
      <div class="faq-list">
        <div v-for="(f, i) in faqs" :key="f.q" class="faq-item" :class="{ open: faqOpen === i }">
          <button type="button" class="faq-q" @click="faqOpen = faqOpen === i ? -1 : i">
            {{ f.q }}
            <AppIcon name="chevron-down" :size="17" class="faq-toggle" />
          </button>
          <Transition name="faq">
            <p v-if="faqOpen === i" class="faq-a">{{ f.a }}</p>
          </Transition>
        </div>
      </div>
    </section>

    <!-- ============ CTA ============ -->
    <section class="cta">
      <h2 class="cta-title">今天背的单词，明天也不会忘</h2>
      <p class="cta-desc">注册送 20 次 AI 额度，背单词、复习、小游戏全部免费</p>
      <el-button class="cta-btn" @click="go(userStore.isLogin ? '/review' : '/login')">
        {{ userStore.isLogin ? '继续学习' : '免费开始学习' }}
      </el-button>
    </section>

    <!-- ============ Footer ============ -->
    <footer class="site-footer">
      <div class="footer-inner">
        <div class="footer-top">
          <!-- 品牌区 -->
          <div class="footer-brand">
            <div class="footer-logo">
              <img src="/logo.webp" alt="词灵学园" class="footer-logo-img" />
              <span>词灵学园</span>
            </div>
            <p class="footer-slogan">AI 增强型英语单词学习平台<br />让每个单词都真正被记住</p>
            <div class="footer-social">
              <a class="fs-btn" href="javascript:;" aria-label="GitHub">
                <AppIcon name="github" :size="16" />
              </a>
              <a class="fs-btn" href="javascript:;" aria-label="邮箱">
                <AppIcon name="mail" :size="16" />
              </a>
            </div>
          </div>

          <!-- 链接列 -->
          <div class="footer-cols">
            <div class="footer-col" v-for="col in footerCols" :key="col.title">
              <h4 class="fc-title">{{ col.title }}</h4>
              <ul class="fc-list">
                <li v-for="l in col.links" :key="l.label">
                  <a v-if="l.href" :href="l.href" target="_blank" rel="noopener">{{ l.label }}</a>
                  <a v-else href="javascript:;" @click="l.path && go(l.path)">{{ l.label }}</a>
                </li>
              </ul>
            </div>
          </div>
        </div>

        <div class="footer-bottom">
          <span class="fb-copy">© {{ currentYear }} 词灵学园 · WordSpirit</span>
          <span class="fb-sep">·</span>
          <span class="fb-copy">AI增强型英语单词学习平台</span>
          <span class="fb-spacer"></span>
          <a href="javascript:;" class="fb-link" @click="go('/agreement')">用户协议</a>
          <span class="fb-sep">·</span>
          <a href="javascript:;" class="fb-link" @click="go('/privacy')">隐私政策</a>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import { getPlatformStats } from '@/api/user'
import { games } from '@/views/game-park/games'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const metrics = ref([
  { icon: 'book', num: 0, suffix: '个', label: '收录单词总数', target: 0, color: '#3D9A7E' },
  { icon: 'gamepad-2', num: 0, suffix: '款', label: '单词小游戏', target: games.length, color: '#D97B4A' },
  { icon: 'sparkles', num: 0, suffix: '种', label: '词灵AI 场景', target: 4, color: '#4A8DB9' },
  { icon: 'calendar-check', num: 0, suffix: '天', label: '学习记录保存', target: 365, color: '#D9A44A' }
])

/** 指标展示：收录单词走大数缩写，其余为纯数字 + 单位后缀 */
function displayMetric(m) {
  const core = m.label === '收录单词' ? fmtNum(m.num) : String(m.num)
  return core + m.suffix
}

/** 大数缩写：12345 -> 1.2万 */
function fmtNum(n) {
  if (n >= 100000000) return (n / 100000000).toFixed(1).replace(/\.0$/, '') + '亿'
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + '万'
  return String(Math.round(n))
}

/* 数字滚动动画（count-up） */
let statsRafId = null
function countUp(metric, target, duration = 900) {
  const start = performance.now()
  const step = (now) => {
    const p = Math.min((now - start) / duration, 1)
    const eased = 1 - Math.pow(1 - p, 3)
    metric.num = Math.round(target * eased)
    if (p < 1) statsRafId = requestAnimationFrame(step)
  }
  statsRafId = requestAnimationFrame(step)
}

/* 全部指标数字滚动动画（count-up），接口失败用兜底值同样滚动 */
async function loadStats() {
  let wordCount = 12000
  try {
    const res = await getPlatformStats()
    if (res && res.wordCount != null) wordCount = res.wordCount
  } catch (e) {
    /* 兜底数据 */
  }
  metrics.value[0].target = wordCount
  metrics.value.forEach((m) => countUp(m, m.target))
}

const features = [
  {
    title: '智能背单词',
    desc: '艾宾浩斯遗忘曲线自动安排每日复习，熟悉度可视化，学习节奏一目了然。',
    icon: 'book-open',
    path: '/review'
  },
  {
    title: '词灵AI',
    desc: '阅读解析、生词巩固、AI 试卷、自由答疑四大场景，覆盖英语学习全链路。',
    icon: 'sparkles',
    path: '/ai-assistant'
  },
  {
    title: '趣味乐园',
    desc: '10 款单词小游戏巩固已学知识，获得的金币可在商城兑换勋章、称号、备考资料等。',
    icon: 'gamepad-2',
    path: '/game-park'
  }
]

/* 为什么选择词灵学园：真实功能差异对比 */
const compare = {
  pains: [
    '死记硬背，背了后面忘前面',
    '单词太枯燥，很难坚持下去',
    '长难句看不懂，一个个查词典太慢',
    '错题要手动抄，复习抓不住重点'
  ],
  gains: [
    '艾宾浩斯曲线自动排复习，到点就学',
    '10 款小游戏边玩边巩固，容易坚持',
    '词灵AI 直接解析长难句和阅读段落',
    '答错自动进错题本，还能针对性出卷'
  ]
}
const faqs = [
  {
    q: '词灵AI 是必须的吗？关闭后还能正常学习吗？',
    a: '可以。词灵AI 负责长难句解析、阅读助手、翻译和 AI 试卷这些增强功能；关闭或额度用尽时，背单词、艾宾浩斯复习、小游戏、错题本和学习统计照常能用，不影响任何学习主线。'
  },
  {
    q: 'AI 额度要花钱买吗？',
    a: '不用。注册就送 20 次 AI 额度，之后每天完成学习任务攒活跃度（75 分、100 分档位都有奖励）、连续签到、提升等级都能免费获得，坚持学习基本够用。'
  },
  {
    q: '词库覆盖哪些考试？',
    a: '目前收录中考、高考、四六级、考研核心词汇 6700+，按考纲和真题词频筛选，学新词时结合艾宾浩斯曲线安排复习节奏，避免背了就忘。'
  },
  {
    q: '小游戏只是娱乐，还是真的能帮助记单词？',
    a: '真的能巩固。10 款小游戏都从你的生词和待复习词中出题，答错的词自动进错题本，赚到的金币还能在商城兑换勋章、称号和备考资料。'
  },
  {
    q: '换设备后学习记录还在吗？',
    a: '在。学习进度、生词本、错题本、金币和已兑换的装扮都保存在云端，换手机、换电脑登录同一账号就能接着学。'
  }
]
const faqOpen = ref(-1)

const currentYear = new Date().getFullYear()

const footerCols = [
  {
    title: '产品',
    links: [
      { label: '智能背单词', path: '/review' },
      { label: '词灵AI', path: '/ai-assistant' },
      { label: 'AI 试卷', path: '/paper-generate' },
      { label: '阅读助手', path: '/reading-helper' },
      { label: '趣味乐园', path: '/game-park' }
    ]
  },
  {
    title: '学习',
    links: [
      { label: '今日任务', path: '/task' },
      { label: '生词本', path: '/word-book' },
      { label: '学习报告', path: '/report' },
      { label: '金币商城', path: '/shop' }
    ]
  },
  {
    title: '资源',
    links: [
      { label: '产品介绍', path: '/intro' },
      { label: '帮助中心', path: '/help' },
      { label: '更新日志', path: '/changelog' },
      { label: '学习指南', path: '/guide' }
    ]
  },
  {
    title: '关于',
    links: [
      { label: '关于词灵学园', path: '/about' },
      { label: '联系我们', path: '/contact' },
      { label: '用户协议', path: '/agreement' },
      { label: '隐私政策', path: '/privacy' }
    ]
  }
]

/* Hero 示意卡片：单词自动轮换 */
const demoWords = [
  {
    word: 'persistent',
    phonetic: '/pəˈsɪstənt/',
    meaning: '坚持不懈的；持续的',
    exampleEn: 'Persistent effort leads to success.',
    exampleCn: '坚持不懈的努力带来成功。'
  },
  {
    word: 'resilient',
    phonetic: '/rɪˈzɪliənt/',
    meaning: '有韧性的；适应力强的',
    exampleEn: 'Resilient people bounce back from setbacks.',
    exampleCn: '有韧性的人能从挫折中恢复。'
  },
  {
    word: 'diligent',
    phonetic: '/ˈdɪlɪdʒənt/',
    meaning: '勤奋的；勤勉的',
    exampleEn: 'Diligent practice makes perfect.',
    exampleCn: '勤勉的练习造就完美。'
  }
]
const demoIndex = ref(0)
const demoWord = computed(() => demoWords[demoIndex.value])
const demoProgress = computed(() => 3 + demoIndex.value)
const demoBarWidth = computed(() => `${Math.round((demoProgress.value / 12) * 100)}%`)
let demoTimer = null

onMounted(async () => {
  demoTimer = setInterval(() => {
    demoIndex.value = (demoIndex.value + 1) % demoWords.length
  }, 4000)
  loadStats()
  if (userStore.isLogin) {
    try {
      await userStore.fetchInfo()
    } catch (e) {
      /* 拦截器已处理 */
    }
  }
})

onBeforeUnmount(() => {
  if (statsRafId) cancelAnimationFrame(statsRafId)
  if (demoTimer) clearInterval(demoTimer)
})

function go(path) {
  router.push(path)
}
</script>

<style lang="scss" scoped>
.home-page {
  width: 100%;
}

/* ============ Hero ============ */
.hero {
  position: relative;
  max-width: $page-max-width;
  margin: 0 auto;
  padding: 64px $safe-padding 40px;
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  gap: 56px;
  align-items: center;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 14px;
  border: 1px solid $primary-2;
  border-radius: $radius-pill;
  font-size: 12px;
  color: $color-primary;
  background: $primary-1;
  font-weight: 500;
}

.hero-title {
  margin-top: 18px;
  font-size: 44px;
  font-weight: 800;
  line-height: 1.24;
  letter-spacing: -0.02em;
  color: $text-title;
}

.hero-desc {
  margin-top: 16px;
  font-size: 15px;
  line-height: 1.8;
  color: $text-body;
  max-width: 460px;
}

.hero-actions {
  display: flex;
  gap: 12px;
  margin-top: 26px;
  flex-wrap: wrap;

  .el-button {
    border-radius: $radius-base;
    padding: 11px 22px;
    font-size: 15px;
    gap: 6px;
  }
}

.hero-meta {
  margin-top: 20px;
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;

  .mi-item {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 12px;
    color: $text-caption;

    :deep(.app-icon) {
      color: $color-success;
    }
  }
}

/* Hero 产品示意 */
.hero-art {
  position: relative;
  height: 430px;
}

.art-backdrop {
  position: absolute;
  inset: 0;
  border-radius: 24px;
  background: linear-gradient(180deg, $bg-soft 0%, #eff4fa 100%);
}

.art-card {
  position: absolute;
  background: $bg-card;
  border: 1px solid $border-light;
  border-radius: 14px;
  box-shadow: $shadow-md;
  padding: 18px 20px;
}

.art-card-main {
  width: 350px;
  top: 48px;
  left: 110px;
  z-index: 2;
  transition: box-shadow 0.3s ease;

  &:hover {
    box-shadow: $shadow-lg;
  }

  .am-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 14px;

    .am-label {
      font-size: 12px;
      color: $text-caption;
      font-weight: 500;
    }

    .am-progress {
      font-size: 12px;
      color: $color-primary;
      font-weight: 600;
    }
  }

  .am-word {
    font-size: 30px;
    font-weight: 700;
    color: $text-title;
    letter-spacing: -0.02em;
    line-height: 1.2;
  }

  .am-phonetic {
    margin-top: 4px;
    font-size: 13px;
    color: $text-disabled;
  }

  .am-meaning {
    margin-top: 10px;
    font-size: 15px;
    color: $color-primary;
    font-weight: 500;
  }

  .am-example {
    margin-top: 12px;
    padding: 10px 12px;
    background: $bg-soft;
    border-radius: 8px;

    .ame-en {
      font-size: 13px;
      color: $text-body;
      font-style: italic;
      line-height: 1.6;
    }

    .ame-cn {
      margin-top: 2px;
      font-size: 12px;
      color: $text-disabled;
    }
  }

  .am-tags {
    margin-top: 12px;
    display: flex;
    gap: 8px;

    .amt-tag {
      padding: 3px 10px;
      border-radius: $radius-pill;
      font-size: 11px;
      font-weight: 600;

      &.amt-blue {
        background: $primary-1;
        color: $color-primary;
      }

      &.amt-green {
        background: $color-success-soft;
        color: $color-success;
      }
    }
  }

  /* 记忆进度条：随单词轮换平滑推进 */
  .am-bar {
    margin-top: 12px;
    height: 4px;
    border-radius: 4px;
    background: $border-light;
    overflow: hidden;

    span {
      display: block;
      height: 100%;
      border-radius: 4px;
      background: linear-gradient(90deg, $color-primary 0%, #5cb99b 100%);
      transition: width 0.7s cubic-bezier(0.22, 1, 0.36, 1);
    }
  }
}

/* 单词切换过渡：旧词下沉淡出 / 新词上浮淡入 */
.word-swap-enter-active {
  animation: wordIn 0.45s cubic-bezier(0.22, 1, 0.36, 1);
}

.word-swap-leave-active {
  animation: wordOut 0.18s ease forwards;
}

@keyframes wordIn {
  0% {
    opacity: 0;
    transform: translateY(14px);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes wordOut {
  0% {
    opacity: 1;
    transform: translateY(0);
  }
  100% {
    opacity: 0;
    transform: translateY(-10px);
  }
}

.art-card-back {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 180px;
  height: 50px;
  top: 10px;
  left: 20px;
  z-index: 1;
  font-size: 13px;
  color: $text-caption;
  padding: 12px 16px;
}

.art-card-side {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 200px;
  bottom: 16px;
  right: 6px;
  z-index: 3;
  font-size: 13px;
  color: $text-body;
  padding: 12px 16px;

  svg {
    color: $color-primary;
  }
}

/* 艾宾浩斯记忆曲线卡：呼应产品核心卖点 */
.art-chart {
  position: absolute;
  left: -46px;
  bottom: -28px;
  z-index: 3;
  width: 216px;
  background: $bg-card;
  border: 1px solid $border-light;
  border-radius: 14px;
  box-shadow: $shadow-md;
  padding: 14px 16px 12px;

  .ac-head {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .ac-title {
      font-size: 12px;
      font-weight: 600;
      color: $text-body;
    }

    .ac-value {
      font-size: 11px;
      font-weight: 600;
      color: $color-success;
      background: $color-success-soft;
      padding: 2px 8px;
      border-radius: $radius-pill;
    }
  }

  .ac-svg {
    display: block;
    width: 100%;
    margin-top: 8px;

    .grid-line {
      stroke: $border-light;
      stroke-width: 1;
      stroke-dasharray: 3 4;
    }

    /* 死记硬背：灰色虚线衰减 */
    .fade-line {
      fill: none;
      stroke: $text-disabled;
      stroke-width: 1.8;
      stroke-dasharray: 4 4;
      opacity: 0.75;
    }

    /* 按曲线复习：主色实线，循环描线绘制 */
    .memo-line {
      fill: none;
      stroke: $color-primary;
      stroke-width: 2.2;
      stroke-linecap: round;
      stroke-linejoin: round;
      stroke-dasharray: 1;
      stroke-dashoffset: 1;
      animation: lineLoop 6s ease-in-out infinite;
    }

    /* 复习节点：随描线依次浮现，循环同步 */
    .dot {
      fill: $bg-card;
      stroke: $color-primary;
      stroke-width: 2.5;
      opacity: 0;
      transform-box: fill-box;
      transform-origin: center;

      &.d1 {
        animation: dotLoop1 6s ease-in-out infinite;
      }

      &.d2 {
        animation: dotLoop2 6s ease-in-out infinite;
      }

      &.d3 {
        animation: dotLoop3 6s ease-in-out infinite;
      }
    }
  }

  .ac-legend {
    display: flex;
    gap: 14px;
    margin-top: 6px;

    span {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      font-size: 11px;
      color: $text-caption;

      .lg {
        width: 14px;
        height: 3px;
        border-radius: 2px;

        &.lg-primary {
          background: $color-primary;
        }

        &.lg-gray {
          background: $text-disabled;
        }
      }
    }
  }
}

@keyframes lineLoop {
  /* 描线段线性推进：线尖到达节点的时刻与节点弹出严格对应 */
  0% {
    stroke-dashoffset: 1;
    opacity: 1;
    animation-timing-function: linear;
  }
  33% {
    stroke-dashoffset: 0;
    opacity: 1;
  }
  82% {
    stroke-dashoffset: 0;
    opacity: 1;
  }
  92% {
    stroke-dashoffset: 0;
    opacity: 0;
  }
  100% {
    stroke-dashoffset: 1;
    opacity: 0;
  }
}

/* 节点在线尖到达其位置的瞬间弹出（按路径长度换算：d1≈8% d2≈19% d3≈30%） */
@keyframes dotLoop1 {
  0%,
  7% {
    opacity: 0;
    transform: scale(0.3);
  }
  9% {
    opacity: 1;
    transform: scale(1.35);
  }
  14% {
    opacity: 1;
    transform: scale(1);
  }
  82% {
    opacity: 1;
    transform: scale(1);
  }
  92%,
  100% {
    opacity: 0;
    transform: scale(1);
  }
}

@keyframes dotLoop2 {
  0%,
  18% {
    opacity: 0;
    transform: scale(0.3);
  }
  20% {
    opacity: 1;
    transform: scale(1.35);
  }
  25% {
    opacity: 1;
    transform: scale(1);
  }
  82% {
    opacity: 1;
    transform: scale(1);
  }
  92%,
  100% {
    opacity: 0;
    transform: scale(1);
  }
}

@keyframes dotLoop3 {
  0%,
  29% {
    opacity: 0;
    transform: scale(0.3);
  }
  31% {
    opacity: 1;
    transform: scale(1.35);
  }
  36% {
    opacity: 1;
    transform: scale(1);
  }
  82% {
    opacity: 1;
    transform: scale(1);
  }
  92%,
  100% {
    opacity: 0;
    transform: scale(1);
  }
}

/* 曲线与节点直接完整呈现 */
@media (prefers-reduced-motion: reduce) {
  .live-dot::after {
    animation: none;
  }

  .art-chart .memo-line {
    animation: none;
    stroke-dashoffset: 0;
  }

  .art-chart .dot {
    animation: none;
    opacity: 1;
  }
}

/* ============ 数据指标 ============ */
.metrics {
  max-width: $page-max-width;
  margin: 0 auto;
  padding: 8px $safe-padding;
}

.metrics-inner {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  border-top: 1px solid $border-light;
  border-bottom: 1px solid $border-light;
  padding: 28px 0;
}

.metric {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 $sp-4;

  .m-icon {
    width: 46px;
    height: 46px;
    border-radius: 13px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  /* 重心在数字：大号粗体 + 专属配色，tabular-nums 保证滚动时不抖动 */
  .m-num {
    font-size: 30px;
    font-weight: 800;
    letter-spacing: -0.02em;
    line-height: 1.15;
    font-variant-numeric: tabular-nums;
  }

  .m-label {
    font-size: 12.5px;
    color: $text-caption;
    margin-top: 3px;
  }
}

/* ============ Section ============ */
.section {
  max-width: $page-max-width;
  margin: 56px auto 0;
  padding: 0 $safe-padding;
}

.section-head {
  text-align: center;
  margin-bottom: 32px;

  .section-title {
    font-size: 27px;
    font-weight: 700;
    color: $text-title;
    letter-spacing: -0.01em;
  }

  .section-desc {
    margin-top: 8px;
    font-size: 14px;
    color: $text-caption;
  }
}

/* 特性 */
.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.feature-card {
  padding: 26px;
  border: 1px solid $border-light;
  border-radius: 12px;
  background: $bg-card;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    border-color: rgba(58, 140, 137, 0.35);
    box-shadow: $shadow-md;
    transform: translateY(-2px);

    .fc-link {
      color: $color-primary;
    }
  }

  .fc-icon {
    width: 46px;
    height: 46px;
    border-radius: 12px;
    background: $primary-1;
    color: $color-primary;
    @include flex-center;
  }

  .fc-title {
    margin-top: 16px;
    font-size: 17px;
    font-weight: 600;
    color: $text-title;
  }

  .fc-desc {
    margin-top: 8px;
    font-size: 13px;
    line-height: 1.7;
    color: $text-body;
    min-height: 68px;
  }

  .fc-link {
    margin-top: 16px;
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 13px;
    font-weight: 500;
    color: $text-caption;
    transition: color $transition-fast;
  }
}

/* 双栏展示 */
.showcase {
  display: flex;
  flex-direction: column;
  gap: 64px;
  margin-top: 72px;
}

.showcase-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 56px;
  align-items: center;

  &.reverse .sc-visual {
    order: -1;
  }
}

.sc-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.1em;
  color: $color-primary;
}

.sc-title {
  margin-top: 12px;
  font-size: 27px;
  font-weight: 700;
  line-height: 1.4;
  color: $text-title;
  letter-spacing: -0.01em;
}

.sc-desc {
  margin-top: 14px;
  font-size: 14px;
  line-height: 1.8;
  color: $text-body;
}

.sc-list {
  margin: 18px 0 24px;
  padding: 0;
  list-style: none;

  li {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    padding: 5px 0;
    font-size: 14px;
    color: $text-body;

    :deep(.app-icon) {
      color: $color-primary;
      margin-top: 3px;
      flex-shrink: 0;
    }
  }
}

/* 浏览器窗口示意 */
.visual-window {
  border: 1px solid $border-light;
  border-radius: 12px;
  background: $bg-card;
  box-shadow: $shadow-md;
  overflow: hidden;
}

.vw-bar {
  display: flex;
  gap: 6px;
  padding: 11px 16px;
  border-bottom: 1px solid $border-light;
  background: $bg-soft;

  .dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;

    &.dot-red { background: #ff5f57; }
    &.dot-yellow { background: #febc2e; }
    &.dot-green { background: #28c840; }
  }
}

.vw-body {
  padding: 22px;

  .vw-line {
    height: 9px;
    border-radius: 4px;
    background: $gray-3;
    margin-bottom: 11px;

    &.vw-mark {
      background: #fff3a3;
    }
  }

  .vw-tags {
    margin-top: 14px;
    display: flex;
    gap: 8px;

    .vwt-tag {
      padding: 3px 12px;
      border-radius: $radius-pill;
      font-size: 11px;
      font-weight: 600;
      background: $primary-1;
      color: $color-primary;
    }
  }

  .vq-row {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 9px 12px;
    border: 1px solid $border-light;
    border-radius: 8px;
    margin-bottom: 9px;

    .vqr-seq {
      font-size: 12px;
      font-weight: 700;
      color: $color-primary;
      flex-shrink: 0;
    }

    .vqr-line {
      flex: 1;
      height: 7px;
      border-radius: 4px;
      background: $gray-3;
    }

    .vqr-ok {
      color: $color-success;
      @include flex-center;
    }

    .vqr-bad {
      color: $color-danger;
      @include flex-center;
    }
  }

  .vq-score {
    margin-top: 12px;
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 13px;
    font-weight: 600;
    color: $color-success;
  }
}

/* ============ 对比模块：单卡双列对比表 ============ */
.compare-card {
  max-width: 900px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: $bg-card;
  border: 1px solid $border-light;
  border-radius: $radius-large;
  overflow: hidden;

  .cmp-col {
    padding: 24px 32px 20px;

    &.pain {
      background: $bg-soft;
      border-right: 1px solid $border-light;
    }
  }

  .cmp-title {
    font-size: 15px;
    font-weight: 700;
    text-align: center;
    padding-bottom: 12px;
    margin-bottom: 6px;
    border-bottom: 1px solid $border-light;
  }

  .cmp-list {
    list-style: none;

    li {
      display: flex;
      align-items: flex-start;
      gap: 9px;
      padding: 12.5px 2px;
      font-size: 14px;
      line-height: 1.6;

      & + li {
        border-top: 1px dashed $border-light;
      }
    }
  }

  .cmp-x {
    color: $text-disabled;
    flex-shrink: 0;
    margin-top: 4px;
  }

  .cmp-check {
    color: $color-primary;
    flex-shrink: 0;
    margin-top: 4px;
  }

  .pain {
    .cmp-title {
      color: $text-caption;
    }

    li {
      color: $text-caption;
    }
  }

  .gain {
    .cmp-title {
      color: $color-primary;
    }

    li {
      color: $text-title;
      font-weight: 500;
    }
  }
}

/* ============ 常见问题：无卡片、纯分割线的最扁形态（Apple/小米官网式） ============ */
.faq-list {
  max-width: 900px;
  margin: 0 auto;
}

.faq-item {
  border-bottom: 1px solid $border-light;

  &:last-child {
    border-bottom: none;
  }

  .faq-q {
    width: 100%;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 17px 4px;
    background: none;
    border: none;
    text-align: left;
    font-size: 14.5px;
    font-weight: 500;
    color: $text-title;
    cursor: pointer;
    transition: color $transition-fast;

    &:hover {
      color: $color-primary;
    }
  }

  .faq-toggle {
    flex-shrink: 0;
    color: $text-caption;
    transition: transform $transition-fast, color $transition-fast;

    .open & {
      transform: rotate(180deg);
      color: $color-primary;
    }
  }

  .open .faq-q {
    color: $color-primary;
  }

  .faq-a {
    padding: 0 4px 17px;
    font-size: 13.5px;
    line-height: 1.75;
    color: $text-body;
  }
}

/* 答案展开过渡 */
.faq-enter-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.faq-enter-from {
  opacity: 0;
  transform: translateY(-4px);
}

.faq-leave-active {
  display: none;
}

/* ============ CTA：无色块扁平收尾，与 FAQ 风格统一 ============ */
.cta {
  max-width: $page-max-width;
  margin: 80px auto 48px;
  padding: 0 $safe-padding;
  text-align: center;
}

.cta-title {
  font-size: 26px;
  font-weight: 700;
  color: $text-title;
  letter-spacing: -0.01em;
}

.cta-desc {
  margin-top: 12px;
  font-size: 14px;
  color: $text-body;
}

.cta-btn {
  margin-top: 28px;
  border-radius: 6px;
  padding: 11px 36px;
  font-size: 15px;
  font-weight: 500;
  background: #3d9a7e;
  border: none;
  color: #fff;
  transition: background $transition-fast;

  &:hover {
    background: #35896f;
    color: #fff;
  }
}

/* ============ Footer ============ */
.site-footer {
  margin-top: 88px;
  border-top: 1px solid $border-light;
  background: $bg-card;
}

.footer-inner {
  max-width: $page-max-width;
  margin: 0 auto;
  padding: 56px $safe-padding 24px;
}

.footer-top {
  display: grid;
  grid-template-columns: 1.2fr 2.8fr;
  gap: 64px;
  padding-bottom: 40px;
}

.footer-brand {
  min-width: 0;
}

.footer-logo {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
  font-weight: 700;
  color: $text-title;
  letter-spacing: -0.01em;
}

.footer-logo-img {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  object-fit: cover;
}

.footer-slogan {
  margin-top: 14px;
  font-size: 13px;
  line-height: 1.7;
  color: $text-caption;
}

.footer-social {
  margin-top: 18px;
  display: flex;
  gap: 8px;
}

.fs-btn {
  width: 34px;
  height: 34px;
  border: 1px solid $border-light;
  border-radius: $radius-base;
  color: $text-caption;
  @include flex-center;
  transition: all $transition-fast;
  cursor: pointer;

  &:hover {
    color: $color-primary;
    border-color: $color-primary;
  }
}

.footer-cols {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.footer-col {
  min-width: 0;

  .fc-title {
    font-size: 13px;
    font-weight: 600;
    color: $text-title;
    margin-bottom: 14px;
    letter-spacing: 0.01em;
  }

  .fc-list {
    list-style: none;
    padding: 0;
    margin: 0;
    display: flex;
    flex-direction: column;
    gap: 10px;

    li a {
      font-size: 13px;
      color: $text-caption;
      transition: color $transition-fast;
      cursor: pointer;

      &:hover {
        color: $text-body;
      }
    }
  }
}

.footer-bottom {
  padding-top: 22px;
  border-top: 1px solid $border-light;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: $text-disabled;
}

.fb-copy {
  color: $text-caption;
}

.fb-sep {
  color: $border-light;
}

.fb-link {
  color: $text-caption;
  cursor: pointer;
  transition: color $transition-fast;

  &:hover {
    color: $text-body;
  }
}

.fb-spacer {
  flex: 1;
}

/* ============ Responsive ============ */
@media (max-width: 1000px) {
  .hero {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .hero-art {
    height: 380px;
    max-width: 460px;
  }

  .showcase-row {
    grid-template-columns: 1fr;
    gap: 28px;

    &.reverse .sc-visual {
      order: 0;
    }
  }
}

@media (max-width: 760px) {
  .hero {
    padding-top: 44px;
  }

  .hero-title {
    font-size: 32px;
  }

  .metrics-inner {
    grid-template-columns: repeat(2, 1fr);
    gap: 22px 0;
  }

  .feature-grid {
    grid-template-columns: 1fr;
  }

  /* 移动端：对比表两列变上下，右列绿标题强调保留 */
  .compare-card {
    grid-template-columns: 1fr;

    .cmp-col.pain {
      border-right: none;
      border-bottom: 1px solid $border-light;
    }
  }

  .hero-art {
    display: none;
  }

  .footer-top {
    grid-template-columns: 1fr;
    gap: 36px;
  }

  .footer-cols {
    grid-template-columns: repeat(2, 1fr);
    gap: 28px 20px;
  }
}
</style>
