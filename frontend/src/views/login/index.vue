<template>
  <div class="login-page">
    <div class="login-card" :class="{ 'is-login-mode': activeTab === 'login' }">
      <!-- 左侧品牌区 -->
      <div class="login-brand">
        <div class="lb-top">
          <img src="/logo.webp" alt="词灵学园" class="lb-logo" />
          <h2 class="lb-title">词灵学园</h2>
          <p class="lb-sub">AI增强型英语单词学习平台</p>
        </div>

        <ul class="lb-features">
          <li>
            <span class="lbf-dot"></span>
            <span>艾宾浩斯记忆曲线，科学安排复习</span>
          </li>
          <li>
            <span class="lbf-dot"></span>
            <span>词灵AI 阅读解析 · 生词巩固 · 智能出题</span>
          </li>
          <li>
            <span class="lbf-dot"></span>
            <span>10 款单词小游戏，边玩边学赚金币</span>
          </li>
        </ul>

        <p class="lb-foot">坚持比天赋更重要</p>
      </div>

      <!-- 右侧表单区 -->
      <div class="login-form">
        <div class="lf-head">
          <h2 class="lf-title">{{ activeTab === 'login' ? '欢迎回来' : '创建账号' }}</h2>
          <p class="lf-desc">
            {{ activeTab === 'login' ? '登录后继续你的单词学习' : '填写信息，开启英语学习之旅' }}
          </p>
        </div>

        <el-tabs v-model="activeTab" class="login-tabs">
          <!-- 登录 -->
          <el-tab-pane label="登录" name="login">
            <el-form :model="loginForm" :rules="loginRules" ref="loginFormRef" size="large" @keyup.enter="handleLogin">
              <el-form-item prop="username">
                <el-input v-model="loginForm.username" placeholder="用户名" :prefix-icon="User" />
              </el-form-item>
              <el-form-item prop="password">
                <el-input v-model="loginForm.password" type="password" placeholder="密码" show-password :prefix-icon="Lock" />
              </el-form-item>
              <div class="login-aux">
                <span></span>
                <a href="javascript:;" class="forgot-link" @click="openForgotDialog">忘记密码？</a>
              </div>
              <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleLogin">
                登 录
              </el-button>
            </el-form>
          </el-tab-pane>

          <!-- 注册 -->
          <el-tab-pane label="注册" name="register">
            <el-form :model="registerForm" :rules="registerRules" ref="registerFormRef" size="large" @keyup.enter="handleRegister">
              <el-form-item prop="email">
                <el-input v-model="registerForm.email" placeholder="邮箱（用于接收验证码）" :prefix-icon="Message" />
              </el-form-item>
              <el-form-item>
                <div class="code-row">
                  <el-input
                    v-model="registerForm.emailCode"
                    placeholder="6 位邮箱验证码"
                    maxlength="6"
                    :prefix-icon="Key"
                  />
                  <el-button
                    :disabled="!canSendRegisterCode"
                    :loading="registerCodeSending"
                    @click="openRegisterCaptcha"
                  >
                    {{ registerCodeCountdown > 0 ? `${registerCodeCountdown}s 后重发` : '获取验证码' }}
                  </el-button>
                </div>
              </el-form-item>
              <el-form-item prop="nickname">
                <el-input v-model="registerForm.nickname" placeholder="昵称（选填）" :prefix-icon="Postcard" />
              </el-form-item>
              <el-form-item prop="username">
                <el-input v-model="registerForm.username" placeholder="用户名（3-20位字母/数字/下划线）" :prefix-icon="User" />
              </el-form-item>
              <el-form-item prop="password">
                <el-input v-model="registerForm.password" type="password" placeholder="密码（6-32位）" show-password :prefix-icon="Lock" />
              </el-form-item>
              <el-form-item prop="confirmPassword">
                <el-input v-model="registerForm.confirmPassword" type="password" placeholder="确认密码" show-password :prefix-icon="Lock" />
              </el-form-item>
              <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleRegister">
                注册并登录
              </el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>

        <div class="login-foot">
          <span class="link" @click="router.push('/')">返回首页</span>
          <span class="divider">·</span>
          <span class="link" @click="router.push('/intro')">了解词灵学园</span>
        </div>
      </div>
    </div>

    <!-- 底栏装饰 -->
    <div class="login-deco">
      <span>© 2026 词灵学园 · AI增强型英语单词学习平台</span>
    </div>

    <CaptchaModal ref="captchaModalRef" title="安全验证" @verified="onCaptchaVerified" />

    <!-- 忘记密码弹窗 -->
    <transition name="forgot-pop">
      <div v-if="showForgot" class="forgot-mask" @click.self="closeForgotDialog">
        <div class="forgot-dialog">
          <button class="fd-close" aria-label="关闭" @click="closeForgotDialog">
            <el-icon><Close /></el-icon>
          </button>

          <!-- 步骤一：填写信息重置 -->
          <template v-if="forgotStep === 'form'">
            <div class="fd-icon">
              <el-icon :size="28"><Lock /></el-icon>
            </div>
            <h3 class="fd-title">重置密码</h3>
            <p class="fd-desc">输入注册邮箱，我们将发送验证码帮你重置密码</p>

            <el-form
              ref="forgotFormRef"
              :model="forgotForm"
              :rules="forgotRules"
              size="large"
              @keyup.enter="handleResetPassword"
            >
              <el-form-item prop="email">
                <el-input v-model="forgotForm.email" placeholder="注册邮箱" :prefix-icon="Message" />
              </el-form-item>
              <el-form-item prop="emailCode">
                <div class="fd-code-row">
                  <el-input
                    v-model="forgotForm.emailCode"
                    placeholder="6 位邮箱验证码"
                    maxlength="6"
                    :prefix-icon="Key"
                  />
                  <el-button
                    :disabled="!canSendForgotCode"
                    :loading="forgotCodeSending"
                    @click="openForgotCaptcha"
                  >
                    {{ forgotCodeCountdown > 0 ? `${forgotCodeCountdown}s 后重发` : '获取验证码' }}
                  </el-button>
                </div>
              </el-form-item>
              <el-form-item prop="newPassword">
                <el-input
                  v-model="forgotForm.newPassword"
                  type="password"
                  placeholder="新密码（6-20位）"
                  show-password
                  :prefix-icon="Lock"
                />
              </el-form-item>
              <el-form-item prop="confirmPassword">
                <el-input
                  v-model="forgotForm.confirmPassword"
                  type="password"
                  placeholder="确认新密码"
                  show-password
                  :prefix-icon="Lock"
                />
              </el-form-item>
              <el-button type="primary" size="large" class="fd-submit" :loading="resetting" @click="handleResetPassword">
                确认重置
              </el-button>
              <p class="fd-back" @click="backToLogin">返回登录</p>
            </el-form>
          </template>

          <!-- 步骤二：重置成功 -->
          <template v-else>
            <div class="fd-icon fd-icon-success">
              <el-icon :size="30"><CircleCheckFilled /></el-icon>
            </div>
            <h3 class="fd-title">密码重置成功</h3>
            <p class="fd-desc">你的密码已更新，请使用新密码重新登录</p>
            <el-button type="primary" size="large" class="fd-submit" @click="backToLogin">
              去登录
            </el-button>
          </template>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CircleCheckFilled, Close, Key, Lock, Message, Postcard, User } from '@element-plus/icons-vue'
import { resetPassword, sendRegisterEmailCode, sendResetPasswordEmailCode } from '@/api/user'
import CaptchaModal from '@/components/common/CaptchaModal.vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeTab = ref(route.query.tab === 'register' ? 'register' : 'login')

// 响应 URL 上的 tab 参数：导航栏注册按钮、外部链接直接带 ?tab=register 进来时要能切换
watch(
  () => route.query.tab,
  (t) => {
    activeTab.value = t === 'register' ? 'register' : 'login'
  }
)
const loading = ref(false)
const loginFormRef = ref()
const registerFormRef = ref()

const loginForm = reactive({ username: '', password: '' })
const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const registerForm = reactive({
  email: '',
  emailCode: '',
  username: '',
  password: '',
  nickname: '',
  confirmPassword: ''
})
const registerRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  emailCode: [
    { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '请输入 6 位数字验证码', trigger: 'blur' }
  ],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需为6-32位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, cb) => (value !== registerForm.password ? cb(new Error('两次密码不一致')) : cb()),
      trigger: 'blur'
    }
  ]
}

// 注册阶段：邮箱验证码倒计时
const registerCodeSending = ref(false)
const registerCodeCountdown = ref(0)
const verifying = ref(false)
let registerCountdownTimer = null
const captchaModalRef = ref()

const emailPattern = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/

const canSendRegisterCode = computed(
  () => emailPattern.test(registerForm.email) && registerCodeCountdown.value === 0
)

// 当前图形验证码服务的场景：register 注册 / forgot 忘记密码
const captchaScene = ref('register')

function openRegisterCaptcha() {
  if (!emailPattern.test(registerForm.email)) {
    ElMessage.warning('请先输入正确的邮箱')
    return
  }
  captchaScene.value = 'register'
  captchaModalRef.value?.open()
}

/** 图形验证通过后自动发送邮箱验证码 */
async function onCaptchaVerified({ captchaId }) {
  // 防抖：即使 CaptchaModal 多次 emit 也不能并发发送
  if (verifying.value) return
  verifying.value = true
  const isForgot = captchaScene.value === 'forgot'
  if (isForgot) forgotCodeSending.value = true
  else registerCodeSending.value = true
  try {
    const email = isForgot ? forgotForm.email.trim() : registerForm.email.trim()
    const remain = isForgot
      ? await sendResetPasswordEmailCode(email, captchaId)
      : await sendRegisterEmailCode(email, captchaId)
    ElNotification({
      title: '验证码已发送',
      message: `已发送至 ${email}，请查收邮件\n今日还可发送 ${remain} 次 · 同一邮箱 60 秒内只能发一次 · 10 分钟内有效`,
      type: 'success',
      duration: 5000,
      position: 'top-right'
    })
    captchaModalRef.value?.onVerifiedSuccess()
    if (isForgot) startForgotCountdown()
    else startRegisterCountdown()
  } catch (e) {
    // 邮箱格式/限流/未注册等错误 → 关闭弹窗
    captchaModalRef.value?.close()
  } finally {
    registerCodeSending.value = false
    forgotCodeSending.value = false
    verifying.value = false
  }
}

function startRegisterCountdown() {
  registerCodeCountdown.value = 60
  if (registerCountdownTimer) clearInterval(registerCountdownTimer)
  registerCountdownTimer = setInterval(() => {
    registerCodeCountdown.value--
    if (registerCodeCountdown.value <= 0) {
      clearInterval(registerCountdownTimer)
      registerCountdownTimer = null
    }
  }, 1000)
}

/* ---------- 忘记密码 ---------- */
const showForgot = ref(false)
const forgotStep = ref('form') // form 填写信息 / done 重置成功
const forgotFormRef = ref()
const resetting = ref(false)
const forgotCodeSending = ref(false)
const forgotCodeCountdown = ref(0)
let forgotCountdownTimer = null

const forgotForm = reactive({
  email: '',
  emailCode: '',
  newPassword: '',
  confirmPassword: ''
})
const forgotRules = {
  email: [
    { required: true, message: '请输入注册邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  emailCode: [
    { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '请输入 6 位数字验证码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '新密码长度需为 6-20 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, cb) =>
        value !== forgotForm.newPassword ? cb(new Error('两次密码不一致')) : cb(),
      trigger: 'blur'
    }
  ]
}

const canSendForgotCode = computed(
  () => emailPattern.test(forgotForm.email) && forgotCodeCountdown.value === 0
)

function openForgotDialog() {
  forgotForm.email = ''
  forgotForm.emailCode = ''
  forgotForm.newPassword = ''
  forgotForm.confirmPassword = ''
  forgotStep.value = 'form'
  showForgot.value = true
}

function closeForgotDialog() {
  showForgot.value = false
}

/** 重置成功/返回登录：关弹窗并切到登录 tab */
function backToLogin() {
  showForgot.value = false
  activeTab.value = 'login'
}

function openForgotCaptcha() {
  if (!emailPattern.test(forgotForm.email)) {
    ElMessage.warning('请先输入正确的注册邮箱')
    return
  }
  captchaScene.value = 'forgot'
  captchaModalRef.value?.open()
}

function startForgotCountdown() {
  forgotCodeCountdown.value = 60
  if (forgotCountdownTimer) clearInterval(forgotCountdownTimer)
  forgotCountdownTimer = setInterval(() => {
    forgotCodeCountdown.value--
    if (forgotCodeCountdown.value <= 0) {
      clearInterval(forgotCountdownTimer)
      forgotCountdownTimer = null
    }
  }, 1000)
}

async function handleResetPassword() {
  try {
    await forgotFormRef.value.validate()
  } catch (e) {
    return // 校验未通过
  }
  resetting.value = true
  try {
    await resetPassword(forgotForm.email.trim(), forgotForm.emailCode.trim(), forgotForm.newPassword)
    forgotStep.value = 'done'
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    resetting.value = false
  }
}

onBeforeUnmount(() => {
  if (registerCountdownTimer) clearInterval(registerCountdownTimer)
  if (forgotCountdownTimer) clearInterval(forgotCountdownTimer)
})

async function handleLogin() {
  try {
    await loginFormRef.value.validate()
  } catch (e) {
    return // 校验未通过
  }
  loading.value = true
  try {
    await userStore.login({ username: loginForm.username, password: loginForm.password })
    ElMessage.success('欢迎回来！')
    router.replace(route.query.redirect || '/')
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  try {
    await registerFormRef.value.validate()
  } catch (e) {
    return // 校验未通过
  }
  loading.value = true
  try {
    await userStore.register({
      username: registerForm.username,
      password: registerForm.password,
      nickname: registerForm.nickname,
      email: registerForm.email,
      emailCode: registerForm.emailCode
    })
    ElMessage.success('注册成功，欢迎加入词灵学园！')
    router.replace(route.query.redirect || '/')
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: url('/background.jpg') center / cover no-repeat fixed;
  padding: $sp-10 $sp-5 $sp-8;

  // 底部白色蒙层，避免装饰文字被背景图压住看不清
  &::after {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 140px;
    background: linear-gradient(to top, rgba(255, 255, 255, 0.78), rgba(255, 255, 255, 0));
    pointer-events: none;
  }
}

.login-card {
  width: 100%;
  max-width: 880px;
  display: grid;
  grid-template-columns: 0.85fr 1fr;
  background: $bg-card;
  border: 1px solid $border-light;
  border-radius: $radius-large;
  box-shadow: $shadow-md;
  overflow: hidden;
  min-height: 440px;

  // 登录 tab 用原尺寸（不压缩）
  &.is-login-mode {
    min-height: 520px;
  }
}

/* ---------- 左侧品牌 ---------- */
.login-brand {
  background: $color-primary;
  color: #fff;
  padding: $sp-8 $sp-6;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;

  &::after {
    content: '';
    position: absolute;
    width: 240px;
    height: 240px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.08);
    right: -80px;
    bottom: -80px;
  }

  .lb-top {
    position: relative;
    z-index: 1;
  }

  .lb-logo {
    width: 44px;
    height: 44px;
    border-radius: 12px;
    object-fit: contain;
    background: #fff;
    padding: 6px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
    margin-bottom: $sp-4;
  }

  .lb-title {
    font-size: $fs-4xl;
    font-weight: 700;
    letter-spacing: -0.01em;
  }

  .lb-sub {
    margin-top: $sp-1;
    font-size: $fs-base;
    opacity: 0.85;
  }

  .lb-features {
    margin-top: auto;
    padding-top: $sp-6;
    position: relative;
    z-index: 1;

    li {
      display: flex;
      align-items: flex-start;
      gap: $sp-2;
      padding: $sp-2 0;
      font-size: $fs-base;
      opacity: 0.92;
      line-height: 1.6;
    }

    .lbf-dot {
      width: 5px;
      height: 5px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.7);
      margin-top: 8px;
      flex-shrink: 0;
    }
  }

  .lb-foot {
    margin-top: $sp-5;
    padding-top: $sp-4;
    border-top: 1px solid rgba(255, 255, 255, 0.18);
    font-size: $fs-base;
    opacity: 0.8;
    position: relative;
    z-index: 1;
  }
}

/* ---------- 右侧表单 ---------- */
.login-form {
  padding: $sp-6 $sp-8 $sp-4;
  display: flex;
  flex-direction: column;

  // 登录 tab 还原原始 padding
  .is-login-mode & {
    padding: $sp-8 $sp-8 $sp-6;
  }

  .lf-head {
    margin-bottom: $sp-3;
  }

  .is-login-mode & .lf-head {
    margin-bottom: $sp-5;
  }

  .lf-title {
    font-size: $fs-4xl;
    font-weight: 700;
    color: $text-title;
    letter-spacing: -0.01em;
  }

  .lf-desc {
    margin-top: $sp-1;
    font-size: $fs-base;
    color: $text-caption;
  }

  .login-tabs {
    flex: 1;

    :deep(.el-tabs__item) {
      font-size: $fs-lg;
    }

    :deep(.el-tabs__nav-wrap::after) {
      height: 1px;
    }

    // 压缩每个表单项的纵向间距，让 6 项注册表也能塞进卡片
    :deep(.el-form-item) {
      margin-bottom: 14px;
    }

    // 登录 tab 还原原始间距
    .is-login-mode & :deep(.el-form-item) {
      margin-bottom: 22px;
    }
  }

  .submit-btn {
    width: 100%;
    height: 40px;
    margin-top: $sp-1;
    font-size: $fs-lg;
  }

  .is-login-mode & .submit-btn {
    height: 44px;
    margin-top: $sp-2;
  }

  .code-row {
    display: flex;
    gap: $sp-2;
    width: 100%;

    .el-input {
      flex: 1;
    }

    .el-button {
      width: 110px;
      flex-shrink: 0;
    }
  }
}

/* ---------- 底栏装饰 ---------- */
.login-deco {
  position: absolute;
  bottom: $sp-5;
  left: 0;
  right: 0;
  z-index: 1;
  text-align: center;
  font-size: $fs-sm;
  color: $text-title;
  font-weight: 500;
  opacity: 0.85;
  letter-spacing: 0.02em;
  pointer-events: none;
  text-shadow: 0 1px 2px rgba(255, 255, 255, 0.6);
}

.login-foot {
  margin-top: $sp-5;
  text-align: center;
  font-size: $fs-base;

  .link {
    color: $text-caption;
    cursor: pointer;
    transition: color $transition-fast;

    &:hover {
      color: $color-primary;
    }
  }

  .divider {
    margin: 0 $sp-2;
    color: $border-base;
  }
}

/* ---------- 忘记密码弹窗 ---------- */
.forgot-mask {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $sp-5;
  background: rgba(17, 24, 39, 0.45);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.forgot-dialog {
  width: 340px;
  max-width: 100%;
  background: $bg-card;
  border-radius: 20px;
  padding: $sp-8 $sp-6 $sp-6;
  position: relative;
  text-align: center;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.18);

  .fd-close {
    position: absolute;
    top: 14px;
    right: 14px;
    width: 30px;
    height: 30px;
    border: none;
    border-radius: 50%;
    background: $gray-2;
    color: $text-caption;
    cursor: pointer;
    @include flex-center;
    transition: background $transition-fast, color $transition-fast;

    &:hover {
      background: $border-light;
      color: $text-regular;
    }
  }

  .fd-icon {
    width: 58px;
    height: 58px;
    margin: 0 auto;
    border-radius: 50%;
    background: rgba(61, 154, 126, 0.12);
    color: $color-primary;
    @include flex-center;
  }

  .fd-icon-success {
    background: rgba(82, 196, 26, 0.12);
    color: #52c41a;
    animation: fdPop 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  }

  .fd-title {
    margin-top: $sp-4;
    font-size: 20px;
    font-weight: 700;
    color: $text-title;
  }

  .fd-desc {
    margin-top: $sp-1;
    margin-bottom: $sp-5;
    font-size: $fs-sm;
    color: $text-caption;
    line-height: 1.6;
  }

  :deep(.el-form-item) {
    margin-bottom: 14px;
  }

  .fd-code-row {
    display: flex;
    gap: $sp-2;
    width: 100%;

    .el-input {
      flex: 1;
    }

    .el-button {
      width: 112px;
      flex-shrink: 0;
      padding-left: 0;
      padding-right: 0;
    }
  }

  .fd-submit {
    width: 100%;
    height: 44px;
    margin-top: $sp-2;
    border-radius: 999px;
    font-size: $fs-md;
  }

  .fd-back {
    margin-top: $sp-4;
    font-size: $fs-sm;
    color: $text-caption;
    cursor: pointer;
    transition: color $transition-fast;

    &:hover {
      color: $color-primary;
    }
  }
}

/* 弹窗入场/退场 */
.forgot-pop-enter-active {
  transition: opacity 0.25s ease;

  .forgot-dialog {
    animation: forgotSpring 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
  }
}

.forgot-pop-leave-active {
  transition: opacity 0.2s ease;

  .forgot-dialog {
    transition: transform 0.2s ease;
  }
}

.forgot-pop-enter-from,
.forgot-pop-leave-to {
  opacity: 0;

  .forgot-dialog {
    transform: scale(0.88);
  }
}

@keyframes forgotSpring {
  0% {
    transform: scale(0.88) translateY(12px);
  }
  100% {
    transform: scale(1) translateY(0);
  }
}

@keyframes fdPop {
  0% {
    transform: scale(0.5);
    opacity: 0;
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

@media (max-width: 860px) {
  .login-card {
    grid-template-columns: 1fr;
    max-width: 440px;
  }

  .login-brand {
    display: none;
  }

  .login-form {
    padding: $sp-6 $sp-5;
  }
}
</style>
