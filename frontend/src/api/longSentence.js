/**
 * 长难句分析 API
 *
 * 复用词灵AI 调用通道，1 次额度/调用，命中缓存不扣额度。
 */
import request from './request'

/**
 * 分析一段英文长难句
 * @param {{ sentence: string }} data
 * @returns {Promise<{
 *   sentence: string,
 *   difficulty: string,
 *   analysisJson: string,
 *   fromCache: boolean,
 *   quotaRemain: number
 * }>}
 */
export function analyzeLongSentence(data) {
  return request({
    url: '/api/ai/long-sentence/analyze',
    method: 'post',
    data
  })
}