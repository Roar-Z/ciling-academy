package com.wordspirit.module.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wordspirit.common.BusinessException;
import com.wordspirit.common.ResultCode;
import com.wordspirit.module.shop.entity.ShopItem;
import com.wordspirit.module.shop.entity.UserCoin;
import com.wordspirit.module.shop.entity.UserGoods;
import com.wordspirit.module.shop.mapper.ShopItemMapper;
import com.wordspirit.module.shop.mapper.UserCoinMapper;
import com.wordspirit.module.shop.mapper.UserGoodsMapper;
import com.wordspirit.module.shop.service.ShopService;
import com.wordspirit.module.shop.service.StudyResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 金币商城服务实现（虚拟装扮，无真实货币）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private final UserCoinMapper coinMapper;
    private final ShopItemMapper itemMapper;
    private final UserGoodsMapper goodsMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final StudyResourceService studyResourceService;

    @Override
    public Map<String, Object> myCoin(Long userId) {
        UserCoin coin = getUserCoin(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("coinBalance", coin.getCoinBalance());
        result.put("totalEarned", coin.getTotalEarned());
        result.put("totalSpent", coin.getTotalSpent());
        return result;
    }

    @Override
    public List<Map<String, Object>> itemList(Long userId) {
        List<ShopItem> items = itemMapper.selectList(new LambdaQueryWrapper<ShopItem>()
                .eq(ShopItem::getStatus, "on_sale")
                .orderByAsc(ShopItem::getCategory, ShopItem::getPrice));
        // 用户已拥有集合
        List<UserGoods> owned = goodsMapper.selectList(new LambdaQueryWrapper<UserGoods>()
                .eq(UserGoods::getUserId, userId));
        List<Long> ownedIds = owned.stream().map(UserGoods::getItemId).collect(Collectors.toList());
        List<Map<String, Object>> result = new ArrayList<>();
        for (ShopItem item : items) {
            Map<String, Object> m = new HashMap<>();
            m.put("item", item);
            m.put("owned", ownedIds.contains(item.getId()));
            // 资料类商品：附加考试倒计时（后端计算，防抓包篡改）
            if ("resource".equals(item.getCategory()) && item.getResourceKey() != null) {
                StudyResourceService.ResourceDef def = StudyResourceService.def(item.getResourceKey());
                if (def != null) {
                    long days = StudyResourceService.examDaysLeft(def.exam);
                    m.put("examDaysLeft", days);
                    m.put("examLabel", StudyResourceService.examLabel(def.exam));
                }
            }
            result.add(m);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> myGoods(Long userId) {
        List<UserGoods> goods = goodsMapper.selectList(new LambdaQueryWrapper<UserGoods>()
                .eq(UserGoods::getUserId, userId));
        if (goods.isEmpty()) {
            return List.of();
        }
        List<Long> itemIds = goods.stream().map(UserGoods::getItemId).collect(Collectors.toList());
        Map<Long, ShopItem> itemMap = itemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(ShopItem::getId, i -> i));
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserGoods g : goods) {
            ShopItem item = itemMap.get(g.getItemId());
            if (item == null) {
                continue;
            }
            // 备考资料不是装扮，不进入我的装扮列表
            if ("resource".equals(item.getCategory())) {
                continue;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("goodsId", g.getId());
            m.put("item", item);
            m.put("equipped", g.getEquipped() != null && g.getEquipped() == 1);
            result.add(m);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> buy(Long userId, Long itemId) {
        ShopItem item = itemMapper.selectById(itemId);
        if (item == null || !"on_sale".equals(item.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在或已下架");
        }
        // 防重复购买
        Long owned = goodsMapper.selectCount(new LambdaQueryWrapper<UserGoods>()
                .eq(UserGoods::getUserId, userId)
                .eq(UserGoods::getItemId, itemId));
        if (owned != null && owned > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "你已经拥有这件装扮啦");
        }
        UserCoin coin = getUserCoin(userId);
        int balance = coin.getCoinBalance() == null ? 0 : coin.getCoinBalance();
        if (balance < item.getPrice()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "金币不足，去小游戏赚金币吧");
        }
        // 扣款
        UserCoin updCoin = new UserCoin();
        updCoin.setId(coin.getId());
        updCoin.setCoinBalance(balance - item.getPrice());
        updCoin.setTotalSpent((coin.getTotalSpent() == null ? 0 : coin.getTotalSpent()) + item.getPrice());
        coinMapper.updateById(updCoin);
        // 入库装扮
        UserGoods goods = new UserGoods();
        goods.setUserId(userId);
        goods.setItemId(itemId);
        goods.setEquipped(0);
        goodsMapper.insert(goods);
        log.info("用户{}花费{}金币购买「{}」", userId, item.getPrice(), item.getName());

        Map<String, Object> result = new HashMap<>();
        result.put("goodsId", goods.getId());
        result.put("coinBalance", updCoin.getCoinBalance());
        result.put("itemName", item.getName());
        return result;
    }

    @Override
    public void equip(Long userId, Long goodsId, boolean equipped) {
        UserGoods goods = goodsMapper.selectById(goodsId);
        if (goods == null || !goods.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "装扮不存在");
        }
        // 资料类商品不是装扮，禁止佩戴
        ShopItem self = itemMapper.selectById(goods.getItemId());
        if (self != null && "resource".equals(self.getCategory())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "备考资料无需佩戴，下载后即可使用");
        }
        if (equipped) {
            // 同类目互斥：同分类其余全部卸下
            ShopItem item = itemMapper.selectById(goods.getItemId());
            if (item != null) {
                List<UserGoods> sameCategory = goodsMapper.selectList(new LambdaQueryWrapper<UserGoods>()
                        .eq(UserGoods::getUserId, userId));
                for (UserGoods g : sameCategory) {
                    ShopItem it = itemMapper.selectById(g.getItemId());
                    if (it != null && it.getCategory().equals(item.getCategory())) {
                        goodsMapper.update(null, new LambdaUpdateWrapper<UserGoods>()
                                .eq(UserGoods::getId, g.getId())
                                .set(UserGoods::getEquipped, 0));
                    }
                }
            }
            goodsMapper.update(null, new LambdaUpdateWrapper<UserGoods>()
                    .eq(UserGoods::getId, goodsId)
                    .set(UserGoods::getEquipped, 1));
        } else {
            goodsMapper.update(null, new LambdaUpdateWrapper<UserGoods>()
                    .eq(UserGoods::getId, goodsId)
                    .set(UserGoods::getEquipped, 0));
        }
    }

    /** 获取金币账户，不存在则创建 */
    private UserCoin getUserCoin(Long userId) {
        UserCoin coin = coinMapper.selectOne(new LambdaQueryWrapper<UserCoin>()
                .eq(UserCoin::getUserId, userId));
        if (coin == null) {
            coin = new UserCoin();
            coin.setUserId(userId);
            coin.setCoinBalance(0);
            coin.setTotalEarned(0);
            coin.setTotalSpent(0);
            coinMapper.insert(coin);
        }
        return coin;
    }

    @Override
    public Map.Entry<String, java.io.File> downloadResource(Long userId, Long itemId) {
        ShopItem item = itemMapper.selectById(itemId);
        if (item == null || !"resource".equals(item.getCategory()) || item.getResourceKey() == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资料不存在");
        }
        // 防抓包核心：必须已兑换（金币购买记录）才能下载，且不暴露任何外部直链
        Long owned = goodsMapper.selectCount(new LambdaQueryWrapper<UserGoods>()
                .eq(UserGoods::getUserId, userId)
                .eq(UserGoods::getItemId, itemId));
        if (owned == null || owned == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "请先在商城使用金币兑换该资料");
        }
        // Redis 限速：每用户每分钟最多 5 次，防止脚本刷接口
        String rateKey = "resource:dl:" + userId;
        Long cnt = stringRedisTemplate.opsForValue().increment(rateKey);
        if (cnt != null && cnt == 1) {
            stringRedisTemplate.expire(rateKey, java.time.Duration.ofMinutes(1));
        }
        if (cnt != null && cnt > 5) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "下载太频繁啦，请一分钟后再试");
        }
        StudyResourceService.ResourceDef def = StudyResourceService.def(item.getResourceKey());
        if (def == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资料内容未配置");
        }
        try {
            java.io.File pdf = studyResourceService.getPdf(item.getResourceKey());
            String fileName = item.getName() + "（词灵学园）.pdf";
            return Map.entry(fileName, pdf);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("生成资料 PDF 失败：{}", item.getResourceKey(), e);
            throw new BusinessException(ResultCode.BAD_REQUEST, "资料生成失败，请稍后再试");
        }
    }
}
