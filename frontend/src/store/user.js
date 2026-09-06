import { defineStore } from 'pinia'
import {
  login as apiLogin,
  register as apiRegister,
  getInfo,
  getAiQuota,
  logout as apiLogout
} from '@/api/user'

/** AI 额度详情默认值 */
const DEFAULT_QUOTA_DETAIL = {
  dailyQuota: 0,    // 每日免费总额
  dailyRemain: 0,   // 每日剩余
  dailyUsed: 0,     // 每日已用
  bonusRemain: 0,   // 永久奖励剩余
  totalRemain: 0,   // 总剩余（兼容字段 = 每日剩余 + 永久剩余）
  resetAt: ''       // 下次重置时间
}

/**
 * 用户状态：登录状态、用户信息、AI 额度详情（定时刷新）
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('ws_token') || '',
    userInfo: null,
    /** 兼容旧字段：总剩余额度（=每日剩余 + 永久剩余），各页面仍在用 */
    aiQuotaRemain: 0,
    /** AI 额度详情（弹窗展示用） */
    aiQuotaDetail: { ...DEFAULT_QUOTA_DETAIL },
    /** 上次刷新额度时间戳（弹窗实时显示用） */
    aiQuotaLastUpdate: 0,
    /**
     * 额度数据是否已从后端加载过（用于防止"未加载时默认 0 被当作耗尽"造成 UI 闪烁）
     * - false：默认值 0 不可信，UI 不应据此判断"已耗尽"
     * - true ：本会话内至少有一次成功拉取（登录 / fetchInfo / refreshQuota / syncQuotaFromResp）
     */
    quotaLoaded: false,
    quotaTimer: null
  }),

  getters: {
    isLogin: (state) => !!state.token,
    /** 当日已用百分比（0-100） */
    dailyUsedPercent: (state) => {
      const { dailyQuota, dailyUsed } = state.aiQuotaDetail
      if (!dailyQuota) return 0
      return Math.min(100, Math.round((dailyUsed / dailyQuota) * 100))
    }
  },

  actions: {
    async login(form) {
      const data = await apiLogin(form)
      this.applyAuth(data)
      return data
    },

    async register(form) {
      const data = await apiRegister(form)
      this.applyAuth(data)
      return data
    },

    /** 登录/注册成功后写入本地状态 */
    applyAuth(data) {
      this.token = data.token
      this.userInfo = data
      this.aiQuotaRemain = data.aiQuotaRemain ?? 0
      // 详情字段如果后端没返回，按默认值填充
      this.aiQuotaDetail = {
        ...DEFAULT_QUOTA_DETAIL,
        dailyQuota: data.aiQuotaDetail?.dailyQuota ?? 0,
        dailyRemain: data.aiQuotaRemain ?? 0,
        dailyUsed: data.aiQuotaDetail?.dailyUsed ?? 0,
        bonusRemain: data.aiQuotaDetail?.bonusRemain ?? 0,
        totalRemain: data.aiQuotaRemain ?? 0,
        resetAt: data.aiQuotaDetail?.resetAt ?? ''
      }
      this.aiQuotaLastUpdate = Date.now()
      this.quotaLoaded = true  // 登录响应里带了额度，视为可信
      localStorage.setItem('ws_token', data.token)
    },

    /** 拉取用户信息 */
    async fetchInfo() {
      const data = await getInfo()
      this.userInfo = data
      this.aiQuotaRemain = data.aiQuotaRemain ?? 0
      // 无论后端有没有返回 aiQuotaDetail，都要保证 totalRemain = aiQuotaRemain
      // （防止 header 读 detail.totalRemain = 0 但 aiQuotaRemain = N 的"显示不同步"）
      this.aiQuotaDetail = {
        ...DEFAULT_QUOTA_DETAIL,
        ...(data.aiQuotaDetail || {}),
        totalRemain: data.aiQuotaRemain ?? 0
      }
      this.aiQuotaLastUpdate = Date.now()
      // fetchInfo 走完 → 额度已可信
      this.quotaLoaded = true
      return data
    },

    /**
     * 刷新 AI 额度详情（弹窗打开时 / 后台定时调用）
     * @returns {Promise<boolean>} true=成功刷新，false=跳过（未登录）
     */
    async refreshQuota() {
      if (!this.isLogin) return false
      try {
        const data = await getAiQuota()
        this.aiQuotaDetail = {
          ...DEFAULT_QUOTA_DETAIL,
          ...data,
          // 兜底：totalRemain = dailyRemain + bonusRemain
          totalRemain: data.totalRemain ?? (data.dailyRemain ?? 0) + (data.bonusRemain ?? 0)
        }
        // 兼容字段（其它页面还在用）
        this.aiQuotaRemain = this.aiQuotaDetail.totalRemain
        this.aiQuotaLastUpdate = Date.now()
        this.quotaLoaded = true  // 拉取成功 → 后续判断可信
        return true
      } catch (e) {
        /* 刷新失败静默处理 */
        return false
      }
    },

    /**
     * AI 调用成功后同步额度——所有调用 AI 的页面 send 成功后调用本方法
     * 1. 立即基于 resp.quotaRemain 乐观同步顶部 chip（< 1ms 反馈）
     * 2. 后台拉一次 refreshQuota() 覆盖 detail（每日/永久细分），dropdown 用
     *
     * @param {{ quotaRemain?: number }} resp AI 接口响应
     */
    syncQuotaFromResp(resp) {
      if (resp && typeof resp.quotaRemain === 'number') {
        this.aiQuotaRemain = resp.quotaRemain
        this.aiQuotaDetail = {
          ...this.aiQuotaDetail,
          totalRemain: resp.quotaRemain
        }
        this.aiQuotaLastUpdate = Date.now()
        // 接口返回了真实额度 → 立即标记可信，不必等后台 refreshQuota
        this.quotaLoaded = true
      }
      // 后台异步拉一次详细数据，不阻塞主流程（失败静默）
      this.refreshQuota()
    },

    /** 启动额度定时刷新（60s） */
    startQuotaTimer() {
      this.clearQuotaTimer()
      this.quotaTimer = setInterval(() => this.refreshQuota(), 60000)
    },

    clearQuotaTimer() {
      if (this.quotaTimer) {
        clearInterval(this.quotaTimer)
        this.quotaTimer = null
      }
    },

    async logout() {
      try {
        await apiLogout()
      } catch (e) {
        /* 忽略 */
      }
      this.clearQuotaTimer()
      this.token = ''
      this.userInfo = null
      this.aiQuotaRemain = 0
      this.aiQuotaDetail = { ...DEFAULT_QUOTA_DETAIL }
      this.aiQuotaLastUpdate = 0
      this.quotaLoaded = false  // 登出后额度回到"未加载"状态
      localStorage.removeItem('ws_token')
    }
  }
})