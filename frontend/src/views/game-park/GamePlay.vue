<template>
  <div class="game-play-page">
    <!-- 顶部条 -->
    <div class="gp-topbar">
      <button class="back-btn" @click="router.push('/game-park')">
        <el-icon :size="15"><Back /></el-icon>
        <span>返回乐园</span>
      </button>
      <h2 class="gp-title">{{ gameName }}</h2>
      <span class="gp-hint">题目优先取自生词本 · 完成获得金币</span>
    </div>

    <!-- 游戏体 -->
    <div class="game-body">
      <component :is="currentGame" :key="gameId" />
    </div>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Back } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const gameId = computed(() => String(route.params.id))

const GAMES = {
  game1: { name: '单词消消乐', comp: () => import('./games/game1-eliminate.vue') },
  game2: { name: '气球打单词', comp: () => import('./games/game2-balloon.vue') },
  game3: { name: '字母拼拼乐', comp: () => import('./games/game3-spell.vue') },
  game4: { name: '词义连连看', comp: () => import('./games/game4-connect.vue') },
  game5: { name: '单词填空闯关', comp: () => import('./games/game5-fill.vue') },
  game6: { name: '单词快跑', comp: () => import('./games/game6-run.vue') },
  game7: { name: '单词炸弹危机', comp: () => import('./games/game7-bomb.vue') },
  game8: { name: '分类大师', comp: () => import('./games/game8-classify.vue') },
  game9: { name: '音标拼词', comp: () => import('./games/game9-phonetic.vue') },
  game10: { name: '单词故事接龙', comp: () => import('./games/game10-story.vue') }
}

const gameName = computed(() => (GAMES[gameId.value] ? GAMES[gameId.value].name : '趣味游戏'))

const currentGame = computed(() => {
  const conf = GAMES[gameId.value]
  return conf ? defineAsyncComponent(conf.comp) : null
})
</script>

<style lang="scss" scoped>
.game-play-page {
  max-width: none;
  min-height: 100%;
  padding: $sp-5 $safe-padding $sp-10;
  /* 背景图：滚动时固定在视口，与乐园首页一致 */
  background-image: linear-gradient(rgba(255, 252, 248, 0.45), rgba(255, 252, 248, 0.45)),
    url('@/assets/images/review-bg.png');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  background-attachment: fixed;
}

.gp-topbar {
  max-width: 1000px;
  margin-left: auto;
  margin-right: auto;
  display: flex;
  align-items: center;
  gap: $sp-4;
  margin-bottom: $sp-4;

  .back-btn {
    display: inline-flex;
    align-items: center;
    gap: $sp-1;
    padding: 6px 12px;
    border: 1px solid $border-base;
    border-radius: $radius-base;
    background: $bg-card;
    color: $text-body;
    font-size: $fs-base;
    cursor: pointer;
    transition: all $transition-fast;

    &:hover {
      border-color: $primary-3;
      color: $color-primary;
      background: $primary-1;
    }
  }

  .gp-title {
    font-size: $fs-3xl;
    font-weight: 700;
    color: $text-title;
    letter-spacing: -0.01em;
  }

  .gp-hint {
    margin-left: auto;
    font-size: $fs-sm;
    color: $text-disabled;
  }
}

.game-body {
  max-width: 1000px;
  margin: 0 auto;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(6px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: $radius-card;
  box-shadow: $shadow-card;
  padding: $sp-6;
  min-height: 500px;
}

@media (max-width: 700px) {
  .gp-hint {
    display: none;
  }

  .game-body {
    padding: $sp-4;
  }
}
</style>
