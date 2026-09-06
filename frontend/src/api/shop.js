import request from './request'

/** 金币商城接口 */
export const myCoin = () => request.get('/api/shop/coin')
export const shopItems = () => request.get('/api/shop/items')
export const myGoods = () => request.get('/api/shop/my-goods')
export const buyItem = (itemId) => request.post('/api/shop/buy', null, { params: { itemId } })
export const equipGoods = (goodsId, equipped) => request.post('/api/shop/equip', null, { params: { goodsId, equipped } })
/** 下载已兑换的备考资料 PDF（blob 二进制流，后端鉴权，无外部直链） */
export const downloadResource = (itemId) =>
  request.get(`/api/shop/resources/${itemId}/download`, { responseType: 'blob' })
