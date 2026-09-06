<template>
  <div class="page-container shop-page">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">金币商城</h1>
        <p class="page-desc">用小游戏赚取的金币兑换虚拟装扮 · 无真实货币</p>
      </div>
      <div class="coin-box">
        <AppIcon name="coins" :size="19" color="#f5a623" class="cb-icon" />
        <b class="cb-value">{{ coinBalance }}</b>
        <span class="cb-unit">金币</span>
      </div>
    </div>

    <el-tabs v-model="activeCategory" class="shop-tabs">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="激励卡片" name="card" />
    </el-tabs>

    <!-- 全部商品（原商品列表） -->
    <div v-show="activeCategory === 'all'" v-loading="loading">
      <!-- 二级分类筛选 -->
      <div class="subcat-bar">
        <button
          v-for="tab in categoryTabs"
          :key="tab.key"
          type="button"
          :class="['subcat-tab', { active: subCategory === tab.key }]"
          @click="subCategory = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>

      <div v-if="!loading && !filteredItems.length" class="ws-card state-box">
        <div class="sb-icon">
          <AppIcon name="gift" :size="38" />
        </div>
        <p class="state-text">该分类暂时没有商品</p>
      </div>

      <div class="goods-grid">
        <div v-for="g in filteredItems" :key="g.item.id" class="goods-card ws-card" :class="{ 'resource-card': g.item.category === 'resource' }">
          <!-- 备考资料：封面图竖卡（主流资料站样式） -->
          <template v-if="g.item.category === 'resource'">
            <div class="cover-wrap">
              <img :src="`/covers/${g.item.resourceKey}.svg`" :alt="g.item.name" loading="lazy" />
              <span v-if="g.examDaysLeft >= 0" class="countdown-badge">
                距{{ g.examLabel }}仅剩 <b>{{ g.examDaysLeft }}</b> 天
              </span>
            </div>
            <div class="res-info">
              <h3 class="res-name">{{ g.item.name }}</h3>
              <p class="res-desc">{{ g.item.description }}</p>
              <div class="res-foot">
                <span class="goods-price">
                  <AppIcon name="coins" :size="15" color="#f5a623" />
                  {{ g.item.price }}
                </span>
                <el-button
                  v-if="g.owned"
                  type="primary"
                  size="small"
                  :loading="downloadingId === g.item.id"
                  @click="downloadPdf(g)"
                >
                  下载 PDF
                </el-button>
                <el-button v-else type="primary" size="small" plain @click="buy(g)">兑换</el-button>
              </div>
            </div>
          </template>
          <!-- 勋章/称号：图标横卡 -->
          <template v-else>
            <div class="goods-icon" :style="{ background: decoSoftBg(g.item.icon) }">
              <AppIcon :name="g.item.icon || 'award'" :size="34" :color="decoColor(g.item.icon)" />
            </div>
            <h3 class="goods-name">{{ g.item.name }}</h3>
            <p class="goods-desc">{{ g.item.description }}</p>
            <div class="goods-foot">
              <span class="goods-price">
                <AppIcon name="coins" :size="15" color="#f5a623" />
                {{ g.item.price }}
              </span>
              <el-tag v-if="g.owned" size="small" type="success" effect="light">已拥有</el-tag>
              <el-button v-else type="primary" size="small" plain @click="buy(g)">兑换</el-button>
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- 激励卡片：功能开发中，暂时隐藏（原抽卡/购买/收藏面板见下方注释，恢复时删除占位块即可） -->
    <div v-show="activeCategory === 'card'" class="ws-card state-box coming-soon">
      <div class="sb-icon">
        <AppIcon name="hammer" :size="38" />
      </div>
      <p class="state-text">激励卡片功能正在开发中</p>
      <p class="cs-sub">单词卡池、抽卡动线与收藏展示即将上线，敬请期待</p>
    </div>
    <!--
    <div v-show="activeCategory === 'card'" class="card-tab">
      [抽卡主面板]
      <div class="draw-panel ws-card">
        <div class="dp-head">
          <h3 class="panel-title">单词激励卡片</h3>
          <div class="dp-tags">
            <span class="prob-tag q-green">绿 60%</span>
            <span class="prob-tag q-purple">紫 25%</span>
            <span class="prob-tag q-gold">金 12%</span>
            <span class="prob-tag q-red">红 3%</span>
          </div>
        </div>

        <div class="stage-wrap">
          <div class="light-beam" :class="{ active: isDrawing }"></div>
          <div class="card-stage">
            <div v-if="!lastDrawn && !isDrawing" class="card-back">
              <div class="cb-emblem">WS</div>
              <span class="cb-tip">点击下方按钮开启抽卡</span>
            </div>
            <div
              v-else
              :key="lastDrawn?.uid || 'empty'"
              class="card-front"
              :class="['q-' + lastDrawn.quality, { fly: isDrawing }]"
            >
              <div class="cf-badge">{{ lastDrawn.qualityLabel }}</div>
              <div class="cf-word">{{ lastDrawn.word }}</div>
              <div class="cf-quote">{{ lastDrawn.quote }}</div>
              <div v-if="lastDrawn.quality === 'red'" class="cf-shine"></div>
            </div>
          </div>
        </div>

        <div class="draw-actions">
          <el-button
            type="primary"
            size="large"
            :disabled="coinBalance < 50 || isDrawing"
            @click="drawCard(1)"
          >
            抽 1 次
            <span class="da-cost">
              <AppIcon name="coins" :size="13" color="#f5a623" />
              50
            </span>
          </el-button>
          <el-button
            type="warning"
            size="large"
            plain
            :disabled="coinBalance < 450 || isDrawing"
            @click="drawCard(10)"
          >
            抽 10 次
            <span class="da-cost">
              <AppIcon name="coins" :size="13" color="#f5a623" />
              450
            </span>
            <span class="da-pity">保底紫</span>
          </el-button>
        </div>
      </div>

      [直接购买（必出对应品质）]
      <div class="ws-card buy-panel">
        <h3 class="card-title">
          <AppIcon name="tag" :size="17" />
          直接购买（必出对应品质）
        </h3>
        <div class="buy-list">
          <div
            v-for="q in qualities"
            :key="q.key"
            class="buy-item"
            :class="['q-' + q.key]"
          >
            <div class="bi-color-bar"></div>
            <div class="bi-info">
              <div class="bi-name">
                <span class="bi-dot"></span>
                {{ q.label }}
                <span v-if="q.key === 'red'" class="bi-shine">✦</span>
              </div>
              <div class="bi-meta">抽卡概率 {{ q.rate }}% · 必出</div>
              <div class="bi-price">
                <AppIcon name="coins" :size="14" color="#f5a623" />
                <b>{{ priceFor(q.key) }}</b>
                <span>金币</span>
              </div>
            </div>
            <el-button
              size="default"
              type="primary"
              :disabled="coinBalance < priceFor(q.key)"
              @click="buyDirect(q.key)"
              class="bi-btn"
            >
              立即购买
            </el-button>
          </div>
        </div>
      </div>

      [我的激励卡收藏]
      <div class="ws-card my-cards" v-if="myCards.length">
        <h3 class="card-title">
          <AppIcon name="palette" :size="17" />
          我的激励卡（{{ myCards.length }}）
        </h3>
        <div class="my-cards-grid">
          <div
            v-for="(c, i) in myCards"
            :key="c.uid || i"
            class="mc-item"
            :class="['q-' + c.quality]"
          >
            <div class="mc-quality">{{ c.qualityLabel }}</div>
            <div class="mc-word">{{ c.word }}</div>
            <div class="mc-quote">{{ c.quote }}</div>
            <div v-if="c.quality === 'red'" class="mc-shine"></div>
          </div>
        </div>
      </div>
    </div>
    -->
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { myCoin, shopItems, buyItem, downloadResource } from '@/api/shop'
import { decoColor, decoSoftBg } from '@/utils/deco-colors'

/* 勋章/称号 lucide 图标白名单（供离线图标包扫描收集，勿删） */
const SHOP_LUCIDE_ICONS = ['sprout', 'feather', 'mountain', 'crown', 'graduation-cap', 'lightbulb', 'trophy', 'award']

const coinBalance = ref(0)
const items = ref([])
const loading = ref(false)
const activeCategory = ref('all')
const downloadingId = ref(null)

/* 全部 tab 内的二级分类 */
const subCategory = ref('all')
const categoryTabs = [
  { key: 'all', label: '全部' },
  { key: 'medal', label: '勋章' },
  { key: 'title', label: '称号' },
  { key: 'resource', label: '备考资料' }
]
/* 全部分类下的展示顺序：勋章 → 备考资料 → 称号（称号独立在最后一行） */
const CATEGORY_ORDER = { medal: 0, resource: 1, title: 2 }
const filteredItems = computed(() => {
  if (subCategory.value === 'all') {
    return [...items.value].sort(
      (a, b) => (CATEGORY_ORDER[a.item.category] ?? 9) - (CATEGORY_ORDER[b.item.category] ?? 9)
    )
  }
  return items.value.filter((g) => g.item.category === subCategory.value)
})

/* ========== 激励卡片 ========== */
const qualities = [
  { key: 'green', label: '普通', rate: 60 },
  { key: 'purple', label: '稀有', rate: 25 },
  { key: 'gold', label: '史诗', rate: 12 },
  { key: 'red', label: '传说', rate: 3 }
]

const cardPool = {
  green: [
    { word: '坚持', quote: '每天进步一点点' },
    { word: '专注', quote: '心无旁骛，词过无痕' },
    { word: '积累', quote: '量变终将引起质变' },
    { word: '重复', quote: '熟能生巧，巧能生精' },
    { word: '耐心', quote: '慢慢来，比较快' },
    { word: '出发', quote: '从今天开始就不晚' }
  ],
  purple: [
    { word: '觉醒', quote: '今天的你比昨天更强' },
    { word: '超车', quote: '学习是最快的逆袭捷径' },
    { word: '破冰', quote: '突破舒适区的勇气' },
    { word: '记忆宫殿', quote: '让每个单词都有家' },
    { word: '心流', quote: '沉浸其中，效率翻倍' }
  ],
  gold: [
    { word: '坚持 30 天', quote: '你已是真正的学霸' },
    { word: '词汇破千', quote: '一览众山小' },
    { word: '全勤战士', quote: '无一字敷衍，无一日偷懒' },
    { word: '冲刺王', quote: '距离终点只剩一步' }
  ],
  red: [
    { word: '词神降临', quote: '横扫千军，所向披靡' },
    { word: '传奇之证', quote: '凡人不可及的高度' },
    { word: '天选之人', quote: '你被命运眷顾' }
  ]
}

/* 直接购买价格档 */
const buyPrices = {
  green: 200,
  purple: 800,
  gold: 3000,
  red: 8000
}
function priceFor(q) {
  return buyPrices[q] || 0
}

const myCards = ref(loadCards())
const lastDrawn = ref(null)
const isDrawing = ref(false)

const SINGLE_COST = 50
const TEN_COST = 450

function genOneCard(quality) {
  const pool = cardPool[quality]
  const pick = pool[Math.floor(Math.random() * pool.length)]
  return {
    ...pick,
    quality,
    qualityLabel: qualities.find((q) => q.key === quality).label,
    uid: Date.now() + Math.random()
  }
}

function rollQuality() {
  const r = Math.random() * 100
  if (r < 3) return 'red'
  if (r < 15) return 'gold'
  if (r < 40) return 'purple'
  return 'green'
}

function rollQualityOrAbove(minQuality) {
  // 10 连保底：从 minQuality 及以上随机
  const order = ['green', 'purple', 'gold', 'red']
  const idx = order.indexOf(minQuality)
  const slice = order.slice(idx)
  return slice[Math.floor(Math.random() * slice.length)]
}

function drawCard(times) {
  times = times || 1
  const cost = times === 10 ? TEN_COST : SINGLE_COST
  if (coinBalance.value < cost) {
    ElMessage.warning('金币不足，去小游戏赚金币吧')
    return
  }
  coinBalance.value -= cost
  isDrawing.value = true

  const newCards = []
  for (let i = 0; i < times; i++) {
    let q = rollQuality()
    // 10 连保底：第 1 张若为绿，则升至紫或以上
    if (times === 10 && i === 0 && q === 'green') {
      q = rollQualityOrAbove('purple')
    }
    newCards.push(genOneCard(q))
  }
  // 展示最后一张
  lastDrawn.value = newCards[newCards.length - 1]
  // 全部加入收藏（最新的在前）
  for (let i = newCards.length - 1; i >= 0; i--) {
    myCards.value.unshift(newCards[i])
  }
  saveCards()

  const reds = newCards.filter((c) => c.quality === 'red').length
  const golds = newCards.filter((c) => c.quality === 'gold').length
  if (reds > 0) {
    ElMessage.success(`恭喜！${times === 10 ? '10 连' : ''}抽出 ${reds} 张传说卡`)
  } else if (golds > 0) {
    ElMessage.success(`获得 ${golds} 张史诗卡`)
  } else if (times === 10) {
    ElMessage.success('10 连抽卡完成')
  } else {
    ElMessage.success(`获得${lastDrawn.value.qualityLabel}卡「${lastDrawn.value.word}」`)
  }

  setTimeout(() => {
    isDrawing.value = false
  }, 600)
}

function buyDirect(quality) {
  const cost = priceFor(quality)
  if (coinBalance.value < cost) {
    ElMessage.warning('金币不足，去小游戏赚金币吧')
    return
  }
  coinBalance.value -= cost
  const drawn = genOneCard(quality)
  myCards.value.unshift(drawn)
  saveCards()
  ElMessage.success(`已购入${drawn.qualityLabel}卡「${drawn.word}」`)
}

const STORAGE_KEY = 'wordspirit:study-cards'
function loadCards() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
  } catch {
    return []
  }
}
function saveCards() {
  try {
    const arr = myCards.value.slice(0, 200)
    localStorage.setItem(STORAGE_KEY, JSON.stringify(arr))
  } catch {
    /* 忽略 */
  }
}

/* ========== 全部商品（原逻辑） ========== */
onMounted(loadAll)

async function loadAll() {
  loading.value = true
  try {
    const [coin, itemData] = await Promise.all([
      myCoin(),
      shopItems()
    ])
    coinBalance.value = coin.coinBalance || 0
    items.value = itemData || []
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

async function buy(g) {
  try {
    await ElMessageBox.confirm(
      `确定用 ${g.item.price} 金币兑换「${g.item.name}」吗？`,
      '确认兑换',
      {
        confirmButtonText: '兑换',
        cancelButtonText: '再想想',
        type: 'info'
      }
    )
  } catch (e) {
    return
  }
  try {
    const data = await buyItem(g.item.id)
    ElMessage.success(`兑换成功，剩余 ${data.coinBalance} 金币`)
    await loadAll()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

/* 下载已兑换的备考资料 PDF（blob 流，后端鉴权） */
async function downloadPdf(g) {
  downloadingId.value = g.item.id
  try {
    const blob = await downloadResource(g.item.id)
    // 后端业务错误以 JSON 返回（未兑换/限速），此时 blob 实际是错误信息
    if (blob && blob.type && blob.type.includes('application/json')) {
      const text = await blob.text()
      let msg = '下载失败，请稍后再试'
      try {
        msg = JSON.parse(text).message || msg
      } catch {
        /* 忽略 */
      }
      ElMessage.error(msg)
      return
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${g.item.name}（词灵学园）.pdf`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('资料已就绪，请在浏览器下载列表查看')
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    downloadingId.value = null
  }
}
</script>

<style lang="scss" scoped>
.shop-page {
  padding-top: $sp-6;
}

.coin-box {
  display: inline-flex;
  align-items: center;
  gap: $sp-2;
  padding: $sp-2 $sp-4;
  background: $color-warning-soft;
  border: 1px solid #ffe0b2;
  border-radius: $radius-pill;

  .cb-icon {
    color: #f5a623;
  }

  .cb-value {
    font-size: $fs-3xl;
    font-weight: 700;
    color: #f5a623;
    line-height: 1;
  }

  .cb-unit {
    font-size: $fs-sm;
    color: #f5a623;
  }
}

.shop-tabs {
  :deep(.el-tabs__nav-wrap::after) {
    height: 1px;
  }
}

.sb-icon {
  color: $text-disabled;
  margin-bottom: $sp-2;
}

/* 激励卡片开发中占位 */
.coming-soon {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 64px 24px;

  .sb-icon {
    color: $color-primary;
    opacity: 0.55;
  }

  .state-text {
    font-size: $fs-md;
    font-weight: 600;
    color: $text-body;
  }

  .cs-sub {
    margin-top: $sp-2;
    font-size: $fs-sm;
    color: $text-caption;
  }
}

/* ================= 全部商品 ================= */
.subcat-bar {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  background: $bg-soft;
  border: 1px solid $border-light;
  border-radius: $radius-pill;
  margin-bottom: $sp-3;
}

.subcat-tab {
  padding: 6px 16px;
  border: 0;
  background: transparent;
  border-radius: $radius-pill;
  font-size: $fs-sm;
  color: $text-body;
  cursor: pointer;
  transition: all $transition-fast;
  font-family: inherit;

  &.active {
    background: #fff;
    color: $color-primary;
    box-shadow: $shadow-sm;
    font-weight: 600;
  }

  &:hover:not(.active) {
    color: $text-title;
  }
}
.goods-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $sp-3;
}

.goods-card {
  position: relative;
  text-align: center;
  padding: $sp-5 $sp-4;
  transition: all $transition-fast;

  &:hover {
    border-color: $primary-3;
    box-shadow: $shadow-md;
    transform: translateY(-2px);
  }

  /* 备考资料：封面图竖卡（国内主流资料站样式） */
  &.resource-card {
    padding: 0;
    overflow: hidden;
    text-align: left;
    display: flex;
    flex-direction: column;

    .cover-wrap {
      position: relative;
      aspect-ratio: 16 / 9;
      overflow: hidden;
      background: #f2f5f4;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        display: block;
        transition: transform 0.35s ease;
      }
    }

    &:hover {
      img {
        transform: scale(1.05);
      }

      border-color: $primary-3;
    }

    .countdown-badge {
      position: absolute;
      top: 10px;
      right: 10px;
      padding: 3px 9px;
      border-radius: $radius-pill;
      background: rgba(255, 255, 255, 0.92);
      backdrop-filter: blur(4px);
      box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
      font-size: 11px;
      color: #d9820a;
      white-space: nowrap;

      b {
        font-weight: 700;
        margin: 0 1px;
      }
    }

    .res-info {
      display: flex;
      flex-direction: column;
      flex: 1;
      padding: 12px 14px 14px;
    }

    .res-name {
      margin: 0;
      font-size: 14px;
      font-weight: 700;
      color: $text-primary;
      line-height: 1.4;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .res-desc {
      margin: 4px 0 0;
      font-size: 12px;
      color: $text-secondary;
      line-height: 1.5;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .res-foot {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: auto;
      padding-top: 12px;
    }
  }

  .goods-icon {
    width: 64px;
    height: 64px;
    margin: 0 auto;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: transform $transition-fast;
  }

  &:hover .goods-icon {
    transform: scale(1.08);
  }

  .goods-name {
    margin-top: $sp-3;
    font-size: $fs-md;
    font-weight: 600;
    color: $text-title;
  }

  .goods-desc {
    margin-top: $sp-1;
    font-size: $fs-sm;
    color: $text-caption;
    min-height: 34px;
    @include ellipsis-multi(2);
  }

  .goods-foot {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: $sp-2;
    margin-top: $sp-3;
    min-height: 28px;

    .goods-price {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: $fs-md;
      font-weight: 600;
      color: #f5a623;
    }
  }
}

/* ================= 激励卡片 ================= */
.card-tab {
  display: flex;
  flex-direction: column;
  gap: $sp-4;
}

/* ---------- 抽卡主面板 ---------- */
.draw-panel {
  padding: $sp-5 $sp-6 $sp-6;

  .dp-head {
    text-align: center;
    margin-bottom: $sp-3;
  }

  .panel-title {
    font-size: $fs-xl;
    font-weight: 700;
    color: $text-title;
    margin-bottom: $sp-2;
  }

  .dp-tags {
    display: inline-flex;
    gap: 6px;
    flex-wrap: wrap;
    justify-content: center;
  }

  .prob-tag {
    font-size: $fs-xs;
    padding: 2px 8px;
    border-radius: $radius-pill;
    color: #fff;
    font-weight: 600;

    &.q-green { background: linear-gradient(135deg, #34d399, #059669); }
    &.q-purple { background: linear-gradient(135deg, #a78bfa, #7c3aed); }
    &.q-gold { background: linear-gradient(135deg, #fcd34d, #d97706); }
    &.q-red { background: linear-gradient(135deg, #f87171, #b91c1c); }
  }
}

.stage-wrap {
  position: relative;
  margin: $sp-4 auto $sp-5;
  width: 200px;
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.light-beam {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 160px;
  height: 0;
  background: linear-gradient(
    to top,
    rgba(255, 255, 255, 0.85),
    rgba(255, 255, 255, 0)
  );
  filter: blur(8px);
  border-radius: 50% 50% 0 0;
  opacity: 0;
  pointer-events: none;

  &.active {
    animation: beam 0.55s ease-out;
  }
}

@keyframes beam {
  0% {
    height: 0;
    opacity: 0;
  }
  40% {
    height: 220px;
    opacity: 1;
  }
  100% {
    height: 240px;
    opacity: 0;
  }
}

.card-stage {
  width: 180px;
  height: 240px;
  perspective: 1200px;
  position: relative;
  z-index: 2;
}

.card-back {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #3a8c89, #5ca9a5);
  border-radius: $radius-large;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-shadow: $shadow-lg;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    inset: 8px;
    border: 1px dashed rgba(255, 255, 255, 0.45);
    border-radius: $radius-card;
  }

  .cb-emblem {
    font-size: 56px;
    font-weight: 900;
    color: #fff;
    letter-spacing: 4px;
    opacity: 0.92;
    text-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  }

  .cb-tip {
    margin-top: $sp-3;
    color: rgba(255, 255, 255, 0.85);
    font-size: $fs-xs;
  }
}

.card-front {
  position: relative;
  width: 100%;
  height: 100%;
  border-radius: $radius-large;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $sp-3 $sp-2;
  box-shadow: $shadow-lg;
  color: #fff;
  text-align: center;
  overflow: hidden;
  animation: cardIn 0.5s ease-out;

  &.q-green { background: linear-gradient(135deg, #34d399, #059669); }
  &.q-purple { background: linear-gradient(135deg, #a78bfa, #7c3aed); }
  &.q-gold { background: linear-gradient(135deg, #fcd34d, #d97706); }
  &.q-red {
    background: linear-gradient(135deg, #f87171, #b91c1c);
    box-shadow:
      0 0 40px rgba(248, 113, 113, 0.6),
      0 0 80px rgba(248, 113, 113, 0.3),
      $shadow-lg;
  }

  &.fly {
    animation: cardFly 0.55s ease-out;
  }

  .cf-badge {
    padding: 1px 8px;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.25);
    font-size: $fs-xs;
    font-weight: 600;
    margin-bottom: $sp-2;
    backdrop-filter: blur(4px);
  }

  .cf-word {
    font-size: 24px;
    font-weight: 700;
    letter-spacing: 1px;
    text-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
  }

  .cf-quote {
    margin-top: $sp-2;
    font-size: $fs-xs;
    opacity: 0.95;
    line-height: 1.5;
    padding: 0 4px;
  }

  .cf-shine {
    position: absolute;
    inset: 0;
    background: linear-gradient(
      110deg,
      transparent 30%,
      rgba(255, 255, 255, 0.45) 50%,
      transparent 70%
    );
    background-size: 200% 100%;
    animation: shine 1.8s linear infinite;
    pointer-events: none;
  }
}

@keyframes cardIn {
  0% {
    opacity: 0;
    transform: rotateY(180deg) scale(0.7);
  }
  100% {
    opacity: 1;
    transform: rotateY(0) scale(1);
  }
}

@keyframes cardFly {
  0% {
    opacity: 0;
    transform: translateY(-80px) scale(0.6) rotateZ(-15deg);
  }
  60% {
    opacity: 1;
    transform: translateY(0) scale(1.05) rotateZ(2deg);
  }
  100% {
    opacity: 1;
    transform: translateY(0) scale(1) rotateZ(0);
  }
}

@keyframes shine {
  from { background-position: 200% 0; }
  to { background-position: -200% 0; }
}

.draw-actions {
  display: flex;
  gap: $sp-3;
  justify-content: center;
  flex-wrap: wrap;

  .da-cost {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    margin-left: $sp-2;
    font-size: $fs-xs;
    color: $text-caption;
  }

  .da-pity {
    margin-left: $sp-2;
    font-size: $fs-xs;
    color: #ef4444;
    background: rgba(239, 68, 68, 0.1);
    padding: 1px 6px;
    border-radius: $radius-pill;
    font-weight: 600;
  }
}

/* ---------- 直接购买（横向紧凑卡片） ---------- */
.buy-panel {
  .card-title {
    display: flex;
    align-items: center;
    gap: $sp-2;
    font-size: $fs-lg;
    font-weight: 600;
    color: $text-title;
    margin-bottom: $sp-3;

    :deep(.app-icon) {
      color: $color-primary;
    }
  }

  .buy-list {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: $sp-3;
  }

  .buy-item {
    position: relative;
    display: flex;
    align-items: center;
    gap: $sp-3;
    padding: $sp-3;
    padding-left: $sp-4;
    border-radius: $radius-base;
    background: $bg-soft;
    border: 1px solid $border-light;
    overflow: hidden;

    .bi-color-bar {
      position: absolute;
      left: 0;
      top: 0;
      bottom: 0;
      width: 4px;
    }

    &.q-green .bi-color-bar { background: linear-gradient(180deg, #34d399, #059669); }
    &.q-purple .bi-color-bar { background: linear-gradient(180deg, #a78bfa, #7c3aed); }
    &.q-gold .bi-color-bar { background: linear-gradient(180deg, #fcd34d, #d97706); }
    &.q-red .bi-color-bar { background: linear-gradient(180deg, #f87171, #b91c1c); }

    .bi-info {
      flex: 1;
      min-width: 0;
    }

    .bi-name {
      font-weight: 600;
      font-size: $fs-md;
      color: $text-title;
      display: flex;
      align-items: center;
      gap: 6px;

      .bi-dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
      }

      .bi-shine {
        color: #ef4444;
      }
    }

    &.q-green .bi-name .bi-dot { background: #10b981; }
    &.q-purple .bi-name .bi-dot { background: #7c3aed; }
    &.q-gold .bi-name .bi-dot { background: #d97706; }
    &.q-red .bi-name .bi-dot {
      background: #dc2626;
      box-shadow: 0 0 6px rgba(220, 38, 38, 0.6);
    }

    .bi-meta {
      font-size: $fs-xs;
      color: $text-caption;
      margin-top: 2px;
    }

    .bi-price {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      margin-top: 4px;
      color: #f5a623;
      font-size: $fs-sm;

      b {
        font-size: $fs-md;
        font-weight: 700;
      }

      span {
        color: $text-caption;
      }
    }

    .bi-btn {
      flex-shrink: 0;
    }
  }
}

/* ---------- 我的激励卡收藏 ---------- */
.my-cards {
  .card-title {
    display: flex;
    align-items: center;
    gap: $sp-2;
    font-size: $fs-lg;
    font-weight: 600;
    color: $text-title;
    margin-bottom: $sp-3;

    :deep(.app-icon) {
      color: $color-primary;
    }
  }

  .my-cards-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: $sp-3;
  }

  .mc-item {
    position: relative;
    padding: $sp-4 $sp-3;
    border-radius: $radius-base;
    color: #fff;
    text-align: center;
    overflow: hidden;
    min-height: 110px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4px;

    &.q-green { background: linear-gradient(135deg, #34d399, #059669); }
    &.q-purple { background: linear-gradient(135deg, #a78bfa, #7c3aed); }
    &.q-gold { background: linear-gradient(135deg, #fcd34d, #d97706); }
    &.q-red {
      background: linear-gradient(135deg, #f87171, #b91c1c);
      box-shadow: 0 0 20px rgba(248, 113, 113, 0.45);
    }

    .mc-quality {
      font-size: $fs-xs;
      opacity: 0.85;
      padding: 0 8px;
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.25);
    }

    .mc-word {
      font-weight: 700;
      font-size: $fs-md;
    }

    .mc-quote {
      font-size: $fs-xs;
      opacity: 0.92;
      line-height: 1.4;
    }

    .mc-shine {
      position: absolute;
      inset: 0;
      background: linear-gradient(
        110deg,
        transparent 30%,
        rgba(255, 255, 255, 0.35) 50%,
        transparent 70%
      );
      background-size: 200% 100%;
      animation: shine 2.2s linear infinite;
      pointer-events: none;
    }
  }
}

@media (max-width: 1000px) {
  .goods-grid,
  .my-cards-grid,
  .buy-list {
    grid-template-columns: repeat(3, 1fr);
  }

  .buy-list .buy-item {
    flex-direction: column;
    text-align: center;
    padding-left: $sp-3;

    .bi-btn {
      width: 100%;
    }
  }
}

@media (max-width: 700px) {
  .goods-grid,
  .my-cards-grid,
  .buy-list {
    grid-template-columns: repeat(2, 1fr);
  }

  .card-stage {
    width: 160px;
    height: 200px;
  }

  .card-front .cf-word {
    font-size: 22px;
  }

  .stage-wrap {
    width: 180px;
    height: 240px;
  }
}
</style>