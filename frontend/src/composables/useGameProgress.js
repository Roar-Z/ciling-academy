/*
 * @Author: Roar-Z 2175994007@qq.com
 * @Date: 2026-09-03 21:20:14
 * @LastEditors: Roar-Z 2175994007@qq.com
 * @LastEditTime: 2026-09-05 17:31:34
 * @FilePath: \wordSpirit\frontend\src\composables\useGameProgress.js
 * @Description: 这是默认设置,请设置`customMade`, 打开koroFileHeader查看配置 进行设置: https://github.com/OBKoro1/koro1FileHeader/wiki/%E9%85%8D%E7%BD%AE
 */
import { watch } from 'vue'

/**
 * 游戏进度持久化（sessionStorage）。
 * 把一组响应式 ref 自动存到 sessionStorage，退出再进来可续上；
 * 通关（endGame）或点"再来一局"（restart）时清档。
 *
 * @param {string} key 游戏标识，如 'game1'
 * @param {Record<string, import('vue').Ref>} refs 需要持久化的响应式引用集合
 * @returns {{ save: () => void, restore: () => boolean, clear: () => void }}
 */
export function useGameProgress(key, refs) {
  const STORAGE_KEY = `game_progress_${key}`
  // restore() 之前禁止存档：否则 load() 拉新词触发的 watch 会用初始零值覆盖旧存档
  let ready = false

  function save() {
    if (!ready) return
    try {
      const data = {}
      for (const k of Object.keys(refs)) data[k] = refs[k].value
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(data))
    } catch (e) {
      /* 隐私模式 / 容量满时忽略 */
    }
  }

  function restore() {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY)
      if (!raw) return false
      const data = JSON.parse(raw)
      // 上一局已结算（或已爆炸/失败）：不恢复，丢弃存档让调用方开新局
      if (data.gameOver === true) {
        sessionStorage.removeItem(STORAGE_KEY)
        return false
      }
      for (const k of Object.keys(refs)) {
        if (k in data) refs[k].value = data[k]
      }
      return true
    } catch (e) {
      return false
    } finally {
      ready = true
    }
  }

  function clear() {
    try { sessionStorage.removeItem(STORAGE_KEY) } catch (e) {}
    // gameOver/reward 等变更触发的 watch 是异步 flush，会在 clear 之后把
    // "已结算"状态回写成脏存档；这里先停写，等这批微任务过完再恢复
    ready = false
    setTimeout(() => {
      ready = true
      try { sessionStorage.removeItem(STORAGE_KEY) } catch (e) {}
    }, 0)
  }

  // 任意 ref（含数组 / 对象内部）变化即存档
  watch(() => Object.keys(refs).map((k) => refs[k].value), save, { deep: true })

  return { save, restore, clear }
}
