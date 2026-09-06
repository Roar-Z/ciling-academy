<template>
  <el-popover trigger="hover" placement="top" width="290" :show-after="200" :hide-after="100" popper-class="word-popover">
    <template #reference>
      <span class="word-link">{{ word }}</span>
    </template>

    <div class="wp-loading" v-if="loading">
      <el-skeleton animated :rows="2" />
    </div>

    <div class="wp-content" v-else-if="info">
      <div class="wp-head">
        <b class="wp-word">{{ info.word }}</b>
        <span class="wp-phonetic">{{ info.phonetic }}</span>
      </div>
      <p class="wp-pos">{{ info.pos }}</p>
      <p class="wp-meaning">{{ info.meaning }}</p>
      <div v-if="info.example" class="wp-example">
        <p class="wpe-en">{{ info.example }}</p>
        <p v-if="info.exampleCn" class="wpe-cn">{{ info.exampleCn }}</p>
      </div>
      <p class="wp-source">基础释义 · 词典查询不消耗AI额度</p>
    </div>

    <div class="wp-empty" v-else>词典中暂无该词释义</div>
  </el-popover>
</template>

<script setup>
import { ref, watch } from 'vue'
import { dictLookup } from '@/api/user'

const props = defineProps({
  word: { type: String, required: true }
})

const loading = ref(false)
const info = ref(null)

let timer = null

watch(
  () => props.word,
  (val) => {
    if (!val) return
    clearTimeout(timer)
    // 防抖查询
    timer = setTimeout(() => load(val), 300)
  },
  { immediate: true }
)

async function load(word) {
  loading.value = true
  info.value = null
  try {
    info.value = await dictLookup(word)
  } catch (e) {
    info.value = null
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.word-link {
  color: $color-primary;
  border-bottom: 1px dashed rgba(58, 140, 137, 0.45);
  cursor: help;
  padding: 0 1px;
}

.wp-head {
  display: flex;
  align-items: baseline;
  gap: $sp-2;

  .wp-word {
    font-size: $fs-xl;
    color: $text-title;
  }

  .wp-phonetic {
    color: $text-disabled;
    font-size: $fs-base;
  }
}

.wp-pos {
  color: $color-success;
  font-size: $fs-sm;
  font-weight: 600;
  margin-top: 2px;
}

.wp-meaning {
  font-size: $fs-md;
  color: $text-body;
  margin-top: $sp-1;
}

.wp-example {
  margin-top: $sp-2;
  padding: $sp-2 $sp-3;
  background: $bg-soft;
  border-radius: $radius-base;

  .wpe-en {
    font-size: $fs-base;
    color: $text-caption;
    font-style: italic;
  }

  .wpe-cn {
    font-size: $fs-sm;
    color: $text-disabled;
    margin-top: 1px;
  }
}

.wp-source {
  margin-top: $sp-2;
  padding-top: $sp-2;
  border-top: 1px dashed $border-light;
  font-size: $fs-xs;
  color: $text-disabled;
}

.wp-loading,
.wp-empty {
  padding: $sp-2 0;
  text-align: center;
  color: $text-caption;
  font-size: $fs-base;
}
</style>
