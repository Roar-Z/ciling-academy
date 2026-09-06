/**
 * 游戏公共组合函数
 * 统一处理：取词（艾宾浩斯优先级）→ 计时 → 保存记录与金币发放 → 回写复习计划
 */
import { ref } from 'vue'
import { spellGameWords, spellReview, reportGameResult } from '@/api/wordBook'
import { saveGameRecord } from '@/api/game'

export function useGame(gameId, gameName, wordCount = 10) {
  const words = ref([])
  const loading = ref(true)
  const error = ref(false)
  const startTime = ref(Date.now())

  async function load() {
    loading.value = true
    error.value = false
    startTime.value = Date.now()
    try {
      // 艾宾浩斯取词：生词本到期复习词 → 生词本其他词 → 高频核心词兜底（答对锁定的词不会出现；
      // 错词最多分布 2 个游戏，当前游戏的错词优先拉取）
      words.value = await spellGameWords(wordCount, null, gameId)
      if (!words.value.length) error.value = true
    } catch (e) {
      error.value = true
    } finally {
      loading.value = false
    }
  }

  function elapsedSec() {
    return Math.round((Date.now() - startTime.value) / 1000)
  }

  /** 游戏结束：保存记录并返回金币发放结果 */
  async function finish({ score, correctCount, totalCount }) {
    return saveGameRecord({
      gameId,
      gameName,
      score,
      correctCount: correctCount || 0,
      totalCount: totalCount || 0,
      durationSec: elapsedSec()
    })
  }

  /**
   * 回写复习状态：
   * - 生词本词（bookId 有效）：调 spell-review 推进艾宾浩斯曲线
   * - 所有词（含核心词）：上报游戏答题结果做跨游戏去重
   *   know → 锁定，之后不再出现在任何小游戏；forget/vague → 解锁，可在其他游戏出现
   * @param {object} word 词对象（含 word，生词本词含 bookId）
   * @param {string} result 'know' / 'vague' / 'forget'
   */
  function reportReview(word, result) {
    if (!word) return
    if (word.bookId) {
      spellReview(word.bookId, result).catch(() => {})
    }
    if (word.word) {
      reportGameResult([word.word], result, gameId).catch(() => {})
    }
  }

  return { words, loading, error, load, finish, elapsedSec, reportReview }
}
