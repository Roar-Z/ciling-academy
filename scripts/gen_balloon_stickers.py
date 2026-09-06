# -*- coding: utf-8 -*-
"""生成气球打单词游戏的本地贴纸: 4 色气球 + 爆炸碎片 (透明背景 PNG)"""
import math
import random
from PIL import Image, ImageDraw, ImageFilter

OUT = r"d:\WorkPlace\wordSpirit\frontend\src\assets\images\game"
S = 512  # 画布尺寸


def balloon(color, light, dark, name):
    """绘制一个带高光与底部绳结的卡通气球, 透明背景"""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    cx, cy = S // 2, int(S * 0.44)
    rx, ry = int(S * 0.30), int(S * 0.36)

    # 主体: 逐像素径向渐变 (左上受光)
    body = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    lx, ly = cx - rx * 0.45, cy - ry * 0.5  # 光源点
    for y in range(cy - ry - 4, cy + ry + 4):
        for x in range(cx - rx - 4, cx + rx + 4):
            nx = (x - cx) / rx
            ny = (y - cy) / ry
            # 气球轮廓: 上圆下略收
            d = nx * nx + ny * ny
            taper = 1.0
            if ny > 0:
                taper = 1.0 - 0.28 * ny  # 底部收窄
            if d > taper * taper:
                continue
            dist = math.sqrt(((x - lx) / rx) ** 2 + ((y - ly) / ry) ** 2)
            t = min(max(dist / 1.9, 0.0), 1.0)
            t = t ** 1.35
            if t < 0.5:
                k = t / 0.5
                r = light[0] + (color[0] - light[0]) * k
                g = light[1] + (color[1] - light[1]) * k
                b = light[2] + (color[2] - light[2]) * k
            else:
                k = (t - 0.5) / 0.5
                r = color[0] + (dark[0] - color[0]) * k
                g = color[1] + (dark[1] - color[1]) * k
                b = color[2] + (dark[2] - color[2]) * k
            # 边缘抗锯齿
            edge = taper * taper - d
            aa = min(1.0, edge / (0.06 * taper + 1e-6))
            body.putpixel((x, y), (int(r), int(g), int(b), int(255 * aa)))

    img = Image.alpha_composite(img, body)
    dr = ImageDraw.Draw(img)

    # 高光: 两个白色椭圆
    dr.ellipse([cx - rx * 0.62, cy - ry * 0.72, cx - rx * 0.18, cy - ry * 0.34],
               fill=(255, 255, 255, 150))
    dr.ellipse([cx - rx * 0.12, cy - ry * 0.82, cx + rx * 0.06, cy - ry * 0.62],
               fill=(255, 255, 255, 110))
    img_hi = img.filter(ImageFilter.GaussianBlur(6))
    img = Image.alpha_composite(img, img_hi)
    dr = ImageDraw.Draw(img)

    # 底部绳结 (小三角 + 圆点)
    ky = cy + ry - 2
    dr.polygon([(cx - 14, ky + 2), (cx + 14, ky + 2), (cx, ky + 30)], fill=dark + (255,))
    dr.ellipse([cx - 9, ky + 24, cx + 9, ky + 42], fill=dark + (255,))

    img = img.resize((256, 256), Image.LANCZOS)
    img.save(fr"{OUT}\{name}.png")
    print("saved", name)


def pop(name):
    """爆炸碎片: 中心放射的彩色碎片与圆点"""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    cx = cy = S // 2
    palette = [(244, 143, 177), (125, 211, 252), (253, 224, 71),
               (134, 239, 172), (251, 146, 60), (167, 139, 250)]
    random.seed(7)
    # 放射碎片条
    for i in range(14):
        ang = i * (2 * math.pi / 14) + random.uniform(-0.12, 0.12)
        c = palette[i % len(palette)]
        r0, r1 = 70 + random.uniform(-10, 10), 150 + random.uniform(-18, 26)
        w = math.radians(random.uniform(7, 13))
        pts = [
            (cx + r0 * math.cos(ang - w * 0.5), cy + r0 * math.sin(ang - w * 0.5)),
            (cx + r1 * math.cos(ang), cy + r1 * math.sin(ang)),
            (cx + r0 * math.cos(ang + w * 0.5), cy + r0 * math.sin(ang + w * 0.5)),
        ]
        dr.polygon(pts, fill=c + (255,))
    # 外圈圆点
    for i in range(18):
        ang = i * (2 * math.pi / 18) + 0.17
        r = 185 + random.uniform(-22, 30)
        rr = random.uniform(6, 13)
        c = palette[(i + 3) % len(palette)]
        x, y = cx + r * math.cos(ang), cy + r * math.sin(ang)
        dr.ellipse([x - rr, y - rr, x + rr, y + rr], fill=c + (235,))
    # 中心爆核
    dr.ellipse([cx - 52, cy - 52, cx + 52, cy + 52], fill=(255, 214, 10, 255))
    dr.ellipse([cx - 30, cy - 30, cx + 30, cy + 30], fill=(255, 241, 178, 255))

    img = img.filter(ImageFilter.GaussianBlur(1.2))
    img = img.resize((256, 256), Image.LANCZOS)
    img.save(fr"{OUT}\{name}.png")
    print("saved", name)


balloon((244, 143, 177), (255, 214, 232), (216, 94, 138), "balloon-pink")
balloon((96, 178, 226), (196, 232, 250), (56, 132, 184), "balloon-blue")
balloon((250, 204, 21), (255, 238, 150), (212, 160, 0), "balloon-yellow")
balloon((110, 214, 168), (196, 240, 218), (62, 168, 124), "balloon-green")
pop("balloon-pop")
