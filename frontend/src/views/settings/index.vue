<template>
  <div class="page-container settings-page">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">设置</h1>
        <p class="page-desc">账号与偏好设置</p>
      </div>
    </div>

    <!-- 偏好设置 -->
    <div class="ws-card settings-card">
      <h3 class="sc-title">偏好设置</h3>

      <div class="st-row">
        <div class="st-info">
          <div class="st-name">今日复习提醒</div>
          <div class="st-desc">每天提醒你有待复习的单词</div>
        </div>
        <el-switch v-model="notifyReview" :disabled="settingsLoading || savingNotify" @change="saveSettings('review')" />
      </div>

      <div class="st-row">
        <div class="st-info">
          <div class="st-name">AI额度提醒</div>
          <div class="st-desc">今日词灵AI额度用尽时提醒</div>
        </div>
        <el-switch v-model="notifyQuota" :disabled="settingsLoading || savingNotify" @change="saveSettings('quota')" />
      </div>

      <div class="st-row">
        <div class="st-info">
          <div class="st-name">关于词灵AI</div>
          <div class="st-desc">
            词灵AI是增强功能：关闭、超时或异常时，背单词、复习、试卷、游戏、统计等学习功能完全不受影响。
          </div>
        </div>
        <el-tag type="info" effect="light" size="small">了解</el-tag>
      </div>
    </div>

    <!-- 账号操作 -->
    <div class="ws-card settings-card">
      <h3 class="sc-title">账号操作</h3>

      <div class="st-row st-row-click" @click="openCacheDialog">
        <div class="st-info">
          <div class="st-name">清理缓存</div>
          <div class="st-desc">清除本地临时数据，登录状态与学习记录不受影响</div>
        </div>
        <span class="cache-size">
          {{ cacheSizeText }}
          <AppIcon name="chevron-right" :size="14" class="cache-chevron" />
        </span>
      </div>

      <div class="st-row">
        <div class="st-info">
          <div class="st-name">退出登录</div>
          <div class="st-desc">退出当前账号，本地学习数据不会丢失</div>
        </div>
        <el-button @click="handleLogout">退出登录</el-button>
      </div>

      <div class="st-row">
        <div class="st-info">
          <div class="st-name">注销账号</div>
          <div class="st-desc st-danger">
            永久删除账号及所有学习数据，<b>不可恢复</b>。注销后当前邮箱将被冻结 7 天，期间不可用该邮箱再次注册。
          </div>
        </div>
        <el-button type="danger" plain :disabled="!userStore.userInfo?.email" @click="openDeleteDialog">
          注销账号
        </el-button>
      </div>
      <div v-if="!userStore.userInfo?.email" class="st-hint">
        注销账号需先绑定邮箱
      </div>
    </div>

    <div class="about-card">
      <p class="ac-name">词灵学园 Word Spirit Academy</p>
      <p class="ac-version">v1.0.0 · AI增强型英语单词学习平台</p>
    </div>

    <!-- 注销确认弹窗 -->
    <el-dialog
      v-model="deleteVisible"
      title="注销账号"
      width="480px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
    >
      <div class="delete-dialog">
        <div class="dd-warn">
          <el-icon class="dd-warn-icon"><WarningFilled /></el-icon>
          <div>
            <div class="dd-warn-title">此操作不可恢复，请慎重考虑</div>
            <div class="dd-warn-sub">注销后将永久删除以下全部数据，且无法找回</div>
          </div>
        </div>

        <ul class="dd-list">
          <li>个人资料（昵称、头像、简介）</li>
          <li>学习记录（今日 / 累计 / 连续天数）</li>
          <li>生词本与所有复习进度</li>
          <li>AI 聊天记录、收藏、解析缓存</li>
          <li>试卷、题目、游戏记录</li>
          <li>金币、装扮、商城订单</li>
        </ul>

        <el-alert
          v-if="userStore.userInfo?.email"
          type="warning"
          :closable="false"
          show-icon
        >
          <template #title>
            验证码将发送至已绑定邮箱：<b>{{ userStore.userInfo.email }}</b>
          </template>
        </el-alert>

        <el-form label-position="top" class="dd-form">
          <el-form-item label="确认操作">
            <el-input
              v-model="confirmText"
              placeholder='请输入"我确认注销账号"'
              maxlength="20"
              clearable
            />
          </el-form-item>
          <el-form-item label="邮箱验证码">
            <div class="code-row">
              <el-input
                v-model="deleteCode"
                placeholder="6 位验证码"
                maxlength="6"
              >
                <template #prefix>
                  <el-icon><Key /></el-icon>
                </template>
              </el-input>
              <el-button
                :disabled="codeCountdown > 0 || codeSending"
                :loading="codeSending"
                @click="sendDeleteCode"
              >
                {{ codeCountdown > 0 ? `${codeCountdown}s 后重发` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="deleteVisible = false">取消</el-button>
        <el-button
          type="danger"
          :loading="deleteSubmitting"
          :disabled="!canSubmit"
          @click="confirmDelete"
        >
          我已知晓风险，确认注销
        </el-button>
      </template>
    </el-dialog>

    <!-- 图形验证弹层 -->
    <CaptchaModal ref="captchaModalRef" title="安全验证" @verified="onCaptchaVerified" />

    <!-- 清理缓存确认弹窗 -->
    <Teleport to="body">
      <Transition name="cache-dlg">
        <div v-if="cacheDialog.visible" class="cache-dlg-mask" @click.self="closeCacheDialog">
          <div class="cache-dlg">
            <span class="cache-dlg-icon">
              <AppIcon name="trash-2" :size="26" color="#E8833A" />
            </span>
            <h4 class="cache-dlg-title">清理本地缓存</h4>
            <p class="cache-dlg-text">
              将清除 {{ cacheSizeText }} 临时数据（输入草稿、游戏进度等），登录状态和学习记录不受影响
            </p>
            <div class="cache-dlg-btns">
              <button class="cache-dlg-btn ghost" @click="closeCacheDialog">再想想</button>
              <button class="cache-dlg-btn primary" @click="confirmClearCache">立即清理</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Key, WarningFilled } from '@element-plus/icons-vue'
import { deleteAccount, sendEmailCode, getNotifySettings, saveNotifySettings } from '@/api/user'
import CaptchaModal from '@/components/common/CaptchaModal.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

// 通知偏好（受后端持久化控制，跨设备同步）
const notifyReview = ref(true)
const notifyQuota = ref(true)
const settingsLoading = ref(true)
const savingNotify = ref(false)

onMounted(async () => {
  measureCache()
  settingsLoading.value = true
  try {
    const res = await getNotifySettings()
    if (res) {
      notifyReview.value = res.notifyReview !== false
      notifyQuota.value = res.notifyQuota !== false
    }
  } catch (e) {
    /* 接口异常静默 */
  } finally {
    settingsLoading.value = false
  }
})

async function saveSettings(source) {
  savingNotify.value = true
  try {
    await saveNotifySettings({
      notifyReview: notifyReview.value,
      notifyQuota: notifyQuota.value
    })
    // 国内主流风格：极简「已开启/已关闭 + 开关名」，不做解释性长句
    const msg = source === 'review'
      ? (notifyReview.value ? '已开启复习提醒' : '已关闭复习提醒')
      : (notifyQuota.value ? '已开启额度提醒' : '已关闭额度提醒')
    ElMessage.success(msg)
  } catch (e) {
    /* 拦截器统一提示 */
  } finally {
    savingNotify.value = false
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  await userStore.logout()
  ElMessage.success('已退出登录，期待下次见面～')
  router.push('/')
}

// ====== 清理缓存 ======
// 保留项：登录态 + 备考级别偏好；其余本地数据均视为可清理的临时缓存
const KEEP_LOCAL_KEYS = ['ws_token', 'cetLevel']

const cacheDialog = reactive({ visible: false })
const cacheBytes = ref(0)

const cacheSizeText = computed(() => bytesToText(cacheBytes.value))

function bytesToText(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

/** 统计 localStorage（排除保留键）+ sessionStorage 占用 */
function measureCache() {
  let total = 0
  try {
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i)
      if (KEEP_LOCAL_KEYS.includes(k)) continue
      total += (k.length + (localStorage.getItem(k) || '').length) * 2
    }
    for (let i = 0; i < sessionStorage.length; i++) {
      const k = sessionStorage.key(i)
      total += (k.length + (sessionStorage.getItem(k) || '').length) * 2
    }
  } catch (e) {
    /* 隐私模式等场景下无法访问，静默 */
  }
  cacheBytes.value = total
}

function openCacheDialog() {
  measureCache()
  cacheDialog.visible = true
}

function closeCacheDialog() {
  cacheDialog.visible = false
}

function confirmClearCache() {
  const freed = cacheBytes.value
  try {
    const removeKeys = []
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i)
      if (!KEEP_LOCAL_KEYS.includes(k)) removeKeys.push(k)
    }
    removeKeys.forEach((k) => localStorage.removeItem(k))
    sessionStorage.clear()
  } catch (e) {
    /* 忽略 */
  }
  cacheDialog.visible = false
  measureCache()
  ElMessage.success(freed > 0 ? `清理完成，已释放 ${bytesToText(freed)}` : '本地缓存已清空')
}

// ====== 注销账号 ======
const deleteVisible = ref(false)
const confirmText = ref('')
const deleteCode = ref('')
const codeCountdown = ref(0)
const codeSending = ref(false)
const deleteSubmitting = ref(false)
let countdownTimer = null
const captchaModalRef = ref()
const verifying = ref(false)

const CONFIRM_PHRASE = '我确认注销账号'

const canSubmit = computed(
  () =>
    confirmText.value === CONFIRM_PHRASE &&
    /^\d{6}$/.test(deleteCode.value) &&
    !deleteSubmitting.value
)

function openDeleteDialog() {
  if (!userStore.userInfo?.email) {
    ElMessage.warning('请先绑定邮箱后再注销账号')
    return
  }
  confirmText.value = ''
  deleteCode.value = ''
  deleteVisible.value = true
}

function sendDeleteCode() {
  const email = userStore.userInfo?.email
  if (!email) return
  captchaModalRef.value?.open()
}

/** 图形验证通过后发送注销验证码 */
async function onCaptchaVerified({ captchaId }) {
  if (verifying.value) return
  verifying.value = true
  const email = userStore.userInfo?.email
  if (!email) {
    verifying.value = false
    return
  }
  codeSending.value = true
  try {
    const remain = await sendEmailCode('delete_account', email, captchaId)
    ElNotification({
      title: '验证码已发送',
      message: `已发送至 ${email}，请查收邮件\n今日还可发送 ${remain} 次 · 同一邮箱 60 秒内只能发一次 · 10 分钟内有效`,
      type: 'success',
      duration: 5000,
      position: 'top-right'
    })
    captchaModalRef.value?.onVerifiedSuccess()
    startCountdown()
  } catch (e) {
    captchaModalRef.value?.close()
  } finally {
    codeSending.value = false
    verifying.value = false
  }
}

function startCountdown() {
  codeCountdown.value = 60
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    codeCountdown.value--
    if (codeCountdown.value <= 0) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

async function confirmDelete() {
  if (!canSubmit.value) return
  deleteSubmitting.value = true
  try {
    await deleteAccount(deleteCode.value.trim())
    ElMessage.success('账号已注销')
    deleteVisible.value = false
    // 清空本地登录状态，跳回登录页
    await userStore.logout()
    router.push('/login')
  } catch (e) {
    /* 拦截器提示 */
  } finally {
    deleteSubmitting.value = false
  }
}

onBeforeUnmount(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>

<style lang="scss" scoped>
.settings-page {
  padding-top: $sp-6;
}

.settings-card {
  margin-bottom: $sp-4;
  padding: $sp-5 $sp-6;

  .sc-title {
    font-size: $fs-lg;
    font-weight: 600;
    color: $text-title;
    margin-bottom: $sp-2;
  }
}

.st-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-5;
  padding: $sp-4 $sp-3;
  margin: 0 (-$sp-3);
  border-bottom: 1px solid $border-light;
  border-radius: $radius-base;
  transition: background 0.15s ease;

  &:hover {
    background: $bg-soft;
  }

  &:last-child {
    border-bottom: none;
  }

  .st-name {
    font-size: $fs-md;
    font-weight: 600;
    color: $text-title;
  }

  .st-desc {
    margin-top: 2px;
    font-size: $fs-base;
    color: $text-caption;
    max-width: 560px;
    line-height: 1.6;

    &.st-danger {
      color: $text-regular;

      b {
        color: $color-danger;
        font-weight: 600;
      }
    }
  }
}

.st-hint {
  margin-top: -8px;
  padding: 6px 0 4px;
  font-size: $fs-sm;
  color: $color-warning;
}

/* 可点击行（清理缓存）：浅绿底强化可点反馈 */
.st-row-click {
  cursor: pointer;

  &:hover {
    background: $color-primary-soft;

    .cache-size {
      color: $color-primary;

      .cache-chevron {
        color: $color-primary;
      }
    }
  }
}

.cache-size {
  display: flex;
  align-items: center;
  gap: 2px;
  font-size: $fs-sm;
  color: $text-caption;
  transition: color 0.2s ease;

  .cache-chevron {
    color: $text-disabled;
    transition: color 0.2s ease;
  }
}

/* 清理缓存确认弹窗（与个人中心 C 端弹窗同风格） */
.cache-dlg-mask {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 32px;
  background: rgba(15, 24, 22, 0.5);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.cache-dlg {
  width: 300px;
  padding: 30px 24px 20px;
  text-align: center;
  background: #fff;
  border-radius: 20px;
  box-shadow: $shadow-lg;
}

.cache-dlg-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 58px;
  height: 58px;
  margin: 0 auto 14px;
  border-radius: 50%;
  background: rgba(232, 131, 58, 0.1);
}

.cache-dlg-title {
  margin-bottom: 8px;
  font-size: 17px;
  font-weight: 600;
  color: $text-primary;
  line-height: 1.4;
}

.cache-dlg-text {
  margin-bottom: 22px;
  font-size: 13px;
  color: $text-caption;
  line-height: 1.65;
}

.cache-dlg-btns {
  display: flex;
  gap: 10px;
}

.cache-dlg-btn {
  flex: 1;
  height: 42px;
  font-size: 14px;
  font-weight: 500;
  border: none;
  border-radius: $radius-pill;
  cursor: pointer;
  transition: all $transition-fast;

  &.ghost {
    background: $bg-soft;
    color: $text-secondary;

    &:hover {
      background: #eceff0;
      color: $text-primary;
    }

    &:active {
      transform: scale(0.97);
    }
  }

  &.primary {
    background: $gradient-primary;
    color: #fff;
    box-shadow: $shadow-primary;

    &:hover {
      opacity: 0.92;
      box-shadow: 0 6px 18px rgba(58, 140, 137, 0.28);
    }

    &:active {
      transform: scale(0.97);
      box-shadow: 0 2px 8px rgba(58, 140, 137, 0.2);
    }
  }
}

/* 弹窗过渡动画 */
.cache-dlg-enter-active {
  transition: opacity 0.24s ease;

  .cache-dlg {
    transition: transform 0.24s cubic-bezier(0.34, 1.4, 0.64, 1);
  }
}

.cache-dlg-leave-active {
  transition: opacity 0.18s ease;

  .cache-dlg {
    transition: transform 0.18s ease;
  }
}

.cache-dlg-enter-from,
.cache-dlg-leave-to {
  opacity: 0;

  .cache-dlg {
    transform: scale(0.88);
  }
}

.code-row {
  display: flex;
  gap: $sp-2;
  width: 100%;

  .el-input {
    flex: 1;
  }

  .el-button {
    width: 120px;
  }
}

.about-card {
  text-align: center;
  padding: $sp-8 0 $sp-2;

  .ac-name {
    font-size: $fs-md;
    color: $text-caption;
    font-weight: 500;
  }

  .ac-version {
    margin-top: $sp-1;
    font-size: $fs-sm;
    color: $text-disabled;
  }
}

/* 注销弹窗 */
.delete-dialog {
  .dd-warn {
    display: flex;
    gap: $sp-3;
    padding: $sp-3 $sp-4;
    background: $color-danger-soft;
    border-radius: $radius-base;
    margin-bottom: $sp-4;
  }

  .dd-warn-icon {
    color: $color-danger;
    font-size: 22px;
    flex-shrink: 0;
  }

  .dd-warn-title {
    font-size: $fs-md;
    font-weight: 600;
    color: $color-danger;
  }

  .dd-warn-sub {
    margin-top: 2px;
    font-size: $fs-sm;
    color: $text-regular;
  }

  .dd-list {
    margin: 0 0 $sp-4;
    padding-left: 20px;
    color: $text-body;
    font-size: $fs-base;
    line-height: 1.9;
  }

  .dd-form {
    margin-top: $sp-4;
  }
}
</style>
