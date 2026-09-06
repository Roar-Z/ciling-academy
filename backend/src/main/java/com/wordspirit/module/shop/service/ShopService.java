package com.wordspirit.module.shop.service;

import java.util.List;
import java.util.Map;

/**
 * 金币商城服务
 */
public interface ShopService {

    /** 我的金币账户 */
    Map<String, Object> myCoin(Long userId);

    /** 在售商品列表 */
    List<Map<String, Object>> itemList(Long userId);

    /** 我的已拥有装扮 */
    List<Map<String, Object>> myGoods(Long userId);

    /** 购买商品 */
    Map<String, Object> buy(Long userId, Long itemId);

    /** 佩戴/卸下装扮 */
    void equip(Long userId, Long goodsId, boolean equipped);

    /**
     * 下载已兑换的备考资料 PDF（校验兑换记录 + 限速防抓包）
     *
     * @return [文件名, PDF 文件]
     */
    java.util.Map.Entry<String, java.io.File> downloadResource(Long userId, Long itemId);
}
