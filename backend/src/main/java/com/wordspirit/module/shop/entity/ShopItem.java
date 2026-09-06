package com.wordspirit.module.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 商城商品实体
 */
@Data
@TableName("shop_item")
public class ShopItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /** medal勋章/avatar_frame头像框/chat_bubble聊天气泡/resource备考资料 */
    private String category;
    private String icon;
    private Integer price;
    private String description;
    private String status;
    /** 资料类商品的内容 key（对应 StudyResourceService 注册表），非资料为 NULL */
    private String resourceKey;
}
