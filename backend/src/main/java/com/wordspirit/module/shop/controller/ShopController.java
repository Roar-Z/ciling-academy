package com.wordspirit.module.shop.controller;

import com.wordspirit.common.Result;
import com.wordspirit.common.UserContext;
import com.wordspirit.module.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 金币商城接口
 */
@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    /** 我的金币 */
    @GetMapping("/coin")
    public Result<Map<String, Object>> myCoin() {
        return Result.ok(shopService.myCoin(UserContext.requireUserId()));
    }

    /** 在售商品列表 */
    @GetMapping("/items")
    public Result<List<Map<String, Object>>> items() {
        return Result.ok(shopService.itemList(UserContext.requireUserId()));
    }

    /** 我的装扮 */
    @GetMapping("/my-goods")
    public Result<List<Map<String, Object>>> myGoods() {
        return Result.ok(shopService.myGoods(UserContext.requireUserId()));
    }

    /** 购买 */
    @PostMapping("/buy")
    public Result<Map<String, Object>> buy(@RequestParam Long itemId) {
        return Result.ok(shopService.buy(UserContext.requireUserId(), itemId));
    }

    /** 佩戴/卸下 */
    @PostMapping("/equip")
    public Result<Void> equip(@RequestParam Long goodsId, @RequestParam Boolean equipped) {
        shopService.equip(UserContext.requireUserId(), goodsId, equipped);
        return Result.ok();
    }

    /**
     * 下载已兑换的备考资料 PDF（流式输出，无外部直链，防抓包）
     */
    @GetMapping("/resources/{itemId}/download")
    public void downloadResource(@PathVariable Long itemId, jakarta.servlet.http.HttpServletResponse response) throws Exception {
        var entry = shopService.downloadResource(UserContext.requireUserId(), itemId);
        String fileName = java.net.URLEncoder.encode(entry.getKey(), java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        response.setContentLengthLong(entry.getValue().length());
        try (var in = new java.io.FileInputStream(entry.getValue());
             var out = response.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        }
    }
}
