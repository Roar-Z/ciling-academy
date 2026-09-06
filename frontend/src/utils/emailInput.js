/**
 * 邮箱输入实时净化：只允许英文字母、数字和 @ . _ + -
 * 中文、空格等非法字符直接被拦掉（输不进去），@ 至多一个，最长 64 位
 * 用法：<el-input v-model="form.email" @input="form.email = sanitizeEmailInput($event)" />
 */
const ALLOWED = /[^A-Za-z0-9@._+-]/g

export function sanitizeEmailInput(value) {
  if (typeof value !== 'string') return ''
  let v = value.replace(ALLOWED, '')
  const at = v.indexOf('@')
  if (at !== -1) {
    v = v.slice(0, at + 1) + v.slice(at + 1).replace(/@/g, '')
  }
  return v.slice(0, 64)
}

/** 与后端一致的邮箱格式校验正则 */
export const EMAIL_PATTERN = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/
