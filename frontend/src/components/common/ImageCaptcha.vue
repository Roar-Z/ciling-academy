<template>
  <div class="image-captcha">
    <div class="captcha-img-wrap" :class="{ loading: !image }" @click="refresh" title="点击换一张">
      <img v-if="image" :src="image" alt="验证码" />
      <span v-else class="captcha-loading">加载中...</span>
    </div>
    <el-input
      :model-value="code"
      placeholder="请输入图形验证码"
      maxlength="4"
      class="captcha-input"
      @update:model-value="onCodeChange"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { generateCaptcha } from '@/api/user'

const code = defineModel('code', { type: String, default: '' })
const captchaId = defineModel('captchaId', { type: String, default: '' })

const image = ref('')

async function refresh() {
  try {
    const res = await generateCaptcha()
    image.value = res.image
    captchaId.value = res.captchaId
    code.value = ''
  } catch (e) {
    /* 忽略 */
  }
}

function onCodeChange(val) {
  code.value = val
}

defineExpose({ refresh })
onMounted(refresh)
</script>

<style lang="scss" scoped>
.image-captcha {
  display: flex;
  align-items: center;
  gap: $sp-2;
  width: 100%;
}

.captcha-img-wrap {
  width: 130px;
  height: 40px;
  border: 1px solid $border-base;
  border-radius: $radius-base;
  overflow: hidden;
  cursor: pointer;
  flex-shrink: 0;
  background: $gray-2;
  @include flex-center;
  transition: border-color $transition-fast;

  &:hover {
    border-color: $color-primary;
  }

  &.loading {
    color: $text-caption;
    font-size: $fs-sm;
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: contain;
    display: block;
  }
}

.captcha-loading {
  color: $text-caption;
  font-size: $fs-sm;
}

.captcha-input {
  flex: 1;
}
</style>