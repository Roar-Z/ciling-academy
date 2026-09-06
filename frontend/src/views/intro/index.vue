<template>
  <div class="page-container intro-page">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">关于词灵学园</h1>
        <p class="page-desc">面向大学生的英语单词学习平台 · 读得懂、记得牢、练得准</p>
      </div>
      <el-button type="primary" @click="go('/login')">立即开始学习</el-button>
    </div>

    <!-- 理念 -->
    <section class="intro-section">
      <h3 class="is-title">产品理念</h3>
      <p class="is-text">
        词灵学园是一个面向大学生的英语单词学习平台。我们坚持
        <strong>AI增强而非AI驱动</strong>的设计思路：AI 仅负责内容生成（解析、助记、出题），
        而背单词、艾宾浩斯复习、试卷批改、游戏与统计等核心功能全部由程序实现。
        即使关闭词灵AI，整个学习系统依然完整可用。
      </p>
    </section>

    <!-- 词灵AI 功能 -->
    <section class="intro-section">
      <h3 class="is-title">词灵AI 四大功能</h3>
      <div class="method-grid">
        <div v-for="f in aiFeatures" :key="f.title" class="method-item">
          <div class="mi-icon"><AppIcon :name="f.icon" :size="26" /></div>
          <h4 class="mi-title">{{ f.title }}</h4>
          <p class="mi-desc">{{ f.desc }}</p>
        </div>
      </div>
    </section>

    <!-- 学习方法 -->
    <section class="intro-section">
      <h3 class="is-title">学习方法</h3>
      <div class="method-grid">
        <div v-for="m in methods" :key="m.title" class="method-item">
          <div class="mi-icon"><AppIcon :name="m.icon" :size="26" /></div>
          <h4 class="mi-title">{{ m.title }}</h4>
          <p class="mi-desc">{{ m.desc }}</p>
        </div>
      </div>
    </section>

    <!-- 趣味乐园 -->
    <section class="intro-section">
      <h3 class="is-title">趣味乐园</h3>
      <p class="is-text">
        内置消消乐、气球打单词、字母拼拼乐等 10 款轻量单词小游戏，题目优先取自你的生词本，
        完成游戏可获得金币，在商城兑换虚拟勋章、头像框与聊天气泡装扮。
      </p>
    </section>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'

const router = useRouter()

const aiFeatures = [
  { icon: 'book-open', title: '阅读助手', desc: '粘贴英文段落，AI 输出难度评级、句子翻译、重点词汇与学习建议，一键收藏生词。' },
  { icon: 'brain', title: '生词巩固', desc: '从生词本勾选单词，AI 生成音标、释义、助记、易混提醒、例句与巩固小题的助记卡片。' },
  { icon: 'file-stack', title: 'AI生成试卷', desc: '基于单词池自动生成 5 种题型练习卷，导入后进入做题页面，自动批改并收录错题。' },
  { icon: 'messages-square', title: '自由答疑', desc: '仅限英语学习内容的问答助手，内置 RAG 知识库减少幻觉，优质回答可收藏到我的AI笔记。' }
]

const methods = [
  { icon: 'timer', title: '艾宾浩斯复习', desc: '按照 1/2/4/7/15/30 天间隔自动安排复习，对抗遗忘曲线。' },
  { icon: 'book-open', title: '阅读精读', desc: '阅读助手逐句解析，长难句、重点词一网打尽。' },
  { icon: 'gamepad-2', title: '游戏化练习', desc: '在游戏中反复接触单词，轻松加深印象。' },
  { icon: 'chart-line', title: '数据反馈', desc: '学习报告追踪进度、正确率与连续天数，进步看得见。' }
]

function go(path) {
  router.push(path)
}
</script>

<style lang="scss" scoped>
.intro-page {
  padding-top: $sp-6;
}

.intro-section {
  padding: $sp-5 0;
  border-top: 1px solid $border-light;

  &:first-of-type {
    border-top: 0;
    padding-top: 0;
  }

  .is-title {
    font-size: $fs-xl;
    font-weight: 600;
    color: $text-title;
    margin-bottom: $sp-3;
  }

  .is-text {
    font-size: $fs-md;
    color: $text-body;
    line-height: 1.8;

    strong {
      color: $color-primary;
      font-weight: 600;
    }
  }
}

.method-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $sp-5 $sp-4;
}

.method-item {
  padding: $sp-3 $sp-2;
  background: transparent;
  border: 0;
  border-radius: 0;
  text-align: center;
  transition: transform $transition-fast;

  &:hover {
    transform: translateY(-2px);
  }

  .mi-icon {
    @include flex-center;
    color: $color-primary;
  }

  .mi-title {
    margin-top: $sp-2;
    font-size: $fs-md;
    font-weight: 600;
    color: $text-title;
  }

  .mi-desc {
    margin-top: $sp-1;
    font-size: $fs-sm;
    color: $text-caption;
    line-height: 1.6;
  }
}

@media (max-width: 900px) {
  .method-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
