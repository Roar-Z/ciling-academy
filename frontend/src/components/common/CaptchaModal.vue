<template>
  <el-dialog
    :model-value="visible"
    :title="title || '安全验证'"
    width="380px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    @update:model-value="onMaskClick"
  >
    <div class="captcha-modal">
      <p class="cm-tip">为保障账号安全，请完成以下验证后自动发送验证码</p>
      <div
        class="cm-img"
        :class="{ loading: !image }"
        :title="image ? '点击换一张' : ''"
        @click="refresh"
      >
        <img v-if="image" :src="image" alt="图形验证码" />
        <span v-else class="cm-loading">加载中...</span>
      </div>
      <p class="cm-hint">
        请输入图中 <b>{{ 4 }}</b> 位字符{{ image ? '，点击图片可换一张' : '' }}
      </p>
      <div class="cm-input-row">
        <el-input
          v-model="code"
          placeholder="输入图形验证码"
          maxlength="4"
          @keyup.enter="submit"
        />
        <el-button type="primary" :loading="submitting" @click="submit">
          验证并发送
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { generateCaptcha, verifyCaptcha } from '@/api/user'

const props = defineProps({
  title: { type: String, default: '安全验证' }
})
const emit = defineEmits(['verified'])

const visible = ref(false)
const image = ref('')
const captchaId = ref('')
const code = ref('')
const submitting = ref(false)

async function load() {
  image.value = ''
  // 立刻清空旧 captchaId，避免用上一次的（已被消费的）去请求后端
  captchaId.value = ''
  code.value = ''
  try {
    const res = await generateCaptcha()
    image.value = res.image
    captchaId.value = res.captchaId
    // 同时把 captchaId 存到 sessionStorage，request.js 拦截器会通过 X-Captcha-Id header 带上
    try { sessionStorage.setItem('ws_captcha_id', res.captchaId) } catch (_) {}
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function open() {
  visible.value = true
  load()
}

function close() {
  visible.value = false
  submitting.value = false
  // 关闭时也清掉，避免状态残留
  captchaId.value = ''
  code.value = ''
  image.value = ''
}

function refresh() {
  load()
}

async function submit() {
  // 防抖：提交进行中忽略重复点击（按钮禁用同步不及时生效）
  if (submitting.value) return
  // 图片必须加载完成 + captchaId 必须存在，二者缺一不允许提交
  if (!image.value || !captchaId.value) {
    ElMessage.warning('图形验证码加载中，请稍候')
    return
  }
  if (!/^[A-Za-z0-9]{4}$/.test(code.value)) {
    ElMessage.warning('请输入 4 位图形验证码')
    return
  }
  submitting.value = true
  try {
    // 图形验证独立接口：先校验图形码
    await verifyCaptcha(captchaId.value, code.value)
    // 校验通过 → 通知父组件可以发邮箱验证码（父组件再调 sendEmailCode）
    emit('verified', { captchaId: captchaId.value })
  } catch (e) {
    const msg = e?.message || ''
    // 图形码错误 / 过期 → 刷新图片重新输入
    if (msg.includes('图形验证')) {
      load()
      ElMessage.warning(msg)
    }
  } finally {
    submitting.value = false
  }
}

function onMaskClick(v) {
  if (!v) {
    visible.value = false
    submitting.value = false
  }
}

/** 供父组件在验证成功（发送成功）后调用：关窗 + 重置 */
function onVerifiedSuccess() {
  submitting.value = false
  visible.value = false
}

/** 供父组件在图形验证码错误时调用：刷新图片、清空输入、保留弹窗 */
function onVerifiedFailed() {
  submitting.value = false
  load()
}

defineExpose({ open, close, refresh, onVerifiedSuccess, onVerifiedFailed })
</script>

<style lang="scss" scoped>
.captcha-modal {
  .cm-tip {
    font-size: $fs-base;
    color: $text-regular;
    line-height: 1.5;
    margin-bottom: $sp-4;
  }

  .cm-img {
    width: 100%;
    height: 72px;
    border: 1px solid $border-base;
    border-radius: $radius-base;
    overflow: hidden;
    cursor: pointer;
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
      object-fit: cover;
      display: block;
    }
  }

  .cm-loading {
    color: $text-caption;
    font-size: $fs-sm;
  }

  .cm-hint {
    margin-top: 8px;
    font-size: $fs-sm;
    color: $text-caption;

    b {
      color: $color-primary;
    }
  }

  .cm-input-row {
    margin-top: $sp-3;
    display: flex;
    gap: $sp-2;

    .el-input {
      flex: 1;
    }

    .el-button {
      width: 110px;
      flex-shrink: 0;
    }
  }
}
</style>