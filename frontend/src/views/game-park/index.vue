<template>
  <div class="page-container game-park-page">
    <!-- 页头 -->
    <header class="page-header">
      <div class="ph-left">
        <h1 class="ph-title">趣味乐园</h1>
        <p class="ph-desc">10 个单词小游戏，巩固记忆</p>
      </div>
      <div class="ph-right">
        <span class="pr-balance">
          <AppIcon name="coins" :size="14" color="#FFC53D" />
          <b>{{ coinBalance }}</b> 金币
        </span>
        <span class="pr-divider"></span>
        <a class="pr-link" @click="router.push('/shop')">金币商城</a>
      </div>
    </header>

    <!-- 分组游戏 -->
    <section v-for="group in groups" :key="group.name" class="group">
      <div class="group-head">
        <h2 class="group-title">{{ group.name }}</h2>
        <span class="group-desc">{{ group.desc }}</span>
      </div>
      <div class="group-grid">
        <article
          v-for="g in getGames(group.ids)"
          :key="g.id"
          class="game-card"
          @click="play(g)"
        >
          <div class="gc-icon">
            <AppIcon :name="g.icon" :size="20" />
          </div>
          <div class="gc-main">
            <h3 class="gc-name">{{ g.name }}</h3>
            <p class="gc-desc">{{ g.desc }}</p>
          </div>
          <div class="gc-side">
            <span v-if="g.bestScore > 0" class="gc-best">最高 {{ g.bestScore }} 分</span>
            <AppIcon name="chevron-right" :size="16" class="gc-arrow" />
          </div>
        </article>
      </div>
    </section>

    <!-- 排行榜 -->
    <section v-if="rankList.length" class="rank ws-card">
      <div class="rk-head">
        <h3 class="rk-title">本周排行</h3>
        <span class="rk-sub">单词消消乐</span>
      </div>
      <ol class="rk-list">
        <li v-for="r in rankList" :key="r.rank" class="rk-item">
          <span class="rk-no" :class="{ 'is-gold': r.rank === 1, 'is-silver': r.rank === 2, 'is-bronze': r.rank === 3 }">{{ r.rank }}</span>
          <span class="rk-name">{{ r.gameName }}</span>
          <span class="rk-score">{{ r.score }} 分</span>
        </li>
      </ol>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import { myCoin } from '@/api/shop'
import { gameStats, gameRank } from '@/api/game'
import { games as GAME_LIST } from './games'

const router = useRouter()

const coinBalance = ref(0)
const rankList = ref([])

const games = GAME_LIST

const groups = [
  { name: '拼字游戏', desc: '把字母拼出来，连成单词', ids: ['game3', 'game4', 'game10'] },
  { name: '闯关游戏', desc: '一关关挑战极限', ids: ['game5', 'game1', 'game2'] },
  { name: '速答游戏', desc: '快速反应，点中正确单词', ids: ['game6', 'game7'] },
  { name: '训练游戏', desc: '专项训练，攻克难点', ids: ['game8', 'game9'] }
]

function getGames(ids) {
  return ids.map(id => games.find(g => g.id === id)).filter(Boolean)
}

onMounted(async () => {
  try {
    const coin = await myCoin()
    coinBalance.value = coin.coinBalance || 0
  } catch (e) {
    /* 忽略 */
  }
  try {
    rankList.value = await gameRank({ gameId: 'game1', limit: 5 })
  } catch (e) {
    rankList.value = []
  }
  games.forEach(async (g) => {
    try {
      const s = await gameStats({ gameId: g.id })
      g.bestScore = s.bestScore || 0
    } catch (e) {
      g.bestScore = 0
    }
  })
})

function play(g) {
  router.push(`/game/${g.id}`)
}
</script>

<style lang="scss" scoped>
.game-park-page {
  padding-top: $sp-6;
  min-height: 100%;
  /* 页面容器占满浏览器宽度，用于铺满背景图 */
  max-width: none;
  /* 半透明遮罩 + 背景图：滚动时图片固定在浏览器视口不动 */
  background-image: linear-gradient(rgba(255, 252, 248, 0.45), rgba(255, 252, 248, 0.45)),
    url('@/assets/images/review-bg.png');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  background-attachment: fixed;
}

/* 内容保持 1200px 居中，不随浏览器变宽 */
.game-park-page > .page-header,
.game-park-page > .group,
.game-park-page > .rank {
  max-width: $page-max-width;
  margin-left: auto;
  margin-right: auto;
}

/* 页头 */
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: $sp-6;

  .ph-title {
    font-size: $fs-2xl;
    font-weight: 700;
    color: $text-title;
    line-height: 1.2;
  }

  .ph-desc {
    margin-top: $sp-2;
    font-size: $fs-md;
    color: $text-caption;
  }

  .ph-right {
    display: flex;
    align-items: center;
    gap: $sp-3;
    font-size: $fs-sm;
    color: $text-body;
  }

  .pr-balance {
    display: inline-flex;
    align-items: center;
    gap: $sp-1;
    color: $color-warning;

    b {
      font-weight: 700;
    }
  }

  .pr-divider {
    width: 1px;
    height: 12px;
    background: $gray-3;
  }

  .pr-link {
    color: $color-primary;
    cursor: pointer;

    &:hover {
      color: $color-primary-deep;
    }
  }
}

/* 分组 */
.group {
  margin-bottom: $sp-8;

  &:last-of-type {
    margin-bottom: 0;
  }
}

.group-head {
  display: flex;
  align-items: baseline;
  gap: $sp-3;
  margin-bottom: $sp-4;
}

.group-title {
  display: flex;
  align-items: center;
  gap: $sp-2;
  font-size: $fs-xl;
  font-weight: 600;
  color: $color-primary;

  &::before {
    content: '';
    width: 4px;
    height: 16px;
    border-radius: $radius-pill;
    background: $color-primary;
  }
}

.group-desc {
  font-size: $fs-sm;
  color: $text-caption;
}

.group-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $sp-3 $sp-4;
}

.game-card {
  background: $bg-card;
  border: 1px solid $border-light;
  border-radius: $radius-base;
  padding: $sp-3 $sp-4;
  cursor: pointer;
  transition: border-color $transition-fast, box-shadow $transition-fast;
  display: flex;
  align-items: center;
  gap: $sp-3;

  &:hover {
    border-color: $primary-3;
    box-shadow: $shadow-sm;

    .gc-arrow {
      color: $color-primary;
      transform: translateX(2px);
    }
  }
}

.gc-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  @include flex-center;
  background: $color-primary-soft;
  color: $color-primary;
  flex-shrink: 0;
}

.gc-main {
  flex: 1;
  min-width: 0;

  .gc-name {
    font-size: $fs-base;
    font-weight: 600;
    color: $text-title;
    line-height: 1.4;
  }

  .gc-desc {
    margin-top: 2px;
    font-size: $fs-sm;
    color: $text-caption;
    line-height: 1.4;
    @include ellipsis;
  }
}

.gc-side {
  display: flex;
  align-items: center;
  gap: $sp-2;
  flex-shrink: 0;
}

.gc-best {
  font-size: $fs-xs;
  color: $text-caption;
}

.gc-arrow {
  color: $text-disabled;
  transition: color $transition-fast, transform $transition-fast;
}

/* 排行榜 */
.rank {
  margin-top: $sp-8;
  padding: $sp-5;
}

.rk-head {
  display: flex;
  align-items: baseline;
  gap: $sp-3;
  margin-bottom: $sp-3;
}

.rk-title {
  font-size: $fs-md;
  font-weight: 600;
  color: $text-title;
}

.rk-sub {
  font-size: $fs-sm;
  color: $text-caption;
}

.rk-list {
  display: flex;
  flex-direction: column;
}

.rk-item {
  display: flex;
  align-items: center;
  gap: $sp-3;
  padding: $sp-2 0;
  font-size: $fs-md;
}

.rk-no {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  @include flex-center;
  font-size: $fs-sm;
  font-weight: 600;
  color: $text-caption;
  background: transparent;
  border: 1px solid $border-light;
  flex-shrink: 0;

  &.is-gold {
    background: #FFC53D;
    border-color: #FFC53D;
    color: #fff;
  }

  &.is-silver {
    background: #A9B4C0;
    border-color: #A9B4C0;
    color: #fff;
  }

  &.is-bronze {
    background: #DB9A5F;
    border-color: #DB9A5F;
    color: #fff;
  }
}

.rk-name {
  flex: 1;
  color: $text-body;
}

.rk-score {
  color: $color-primary;
  font-weight: 600;
}

@media (max-width: 1100px) {
  .group-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 760px) {
  .group-grid {
    grid-template-columns: 1fr;
  }
}
</style>