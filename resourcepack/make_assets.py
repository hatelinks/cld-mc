#!/usr/bin/env python3
# Рисует картинки ресурспака: логотип для глав, схему клавиш, иконку книги квестов.
# Запуск: python3 -I resourcepack/make_assets.py (нужен Pillow)
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))
Q = os.path.join(ROOT, "pack", "assets", "cldquests", "textures", "quests")
B = os.path.join(ROOT, "pack", "assets", "ftbquests", "textures", "item")
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def pixel_text(text, h, fill_top, fill_bot, scale):
    # текст мелким шрифтом без сглаживания, потом увеличение: выглядит пиксельно
    f = ImageFont.truetype(BOLD, h)
    w = int(f.getlength(text)) + 4
    m = Image.new("1", (w, h + 6), 0)
    ImageDraw.Draw(m).text((2, 0), text, font=f, fill=1)
    m = m.crop(m.getbbox())
    W, H = m.size
    img = Image.new("RGBA", (W + 2, H + 2), (0, 0, 0, 0))
    px = img.load()
    mp = m.load()
    # тень со сдвигом на 2 пикселя
    for y in range(H):
        for x in range(W):
            if mp[x, y]:
                px[x + 2, y + 2] = (40, 28, 20, 255)
    for y in range(H):
        t = y / max(1, H - 1)
        col = tuple(int(fill_top[i] * (1 - t) + fill_bot[i] * t) for i in range(3)) + (255,)
        for x in range(W):
            if mp[x, y]:
                px[x, y] = col
    return img.resize((img.width * scale, img.height * scale), Image.NEAREST)


def logo():
    a = pixel_text("Telipailo", 22, (200, 205, 210), (120, 125, 132), 4)
    b = pixel_text("Craft", 22, (250, 214, 110), (190, 130, 40), 4)
    img = Image.new("RGBA", (a.width + b.width + 8, max(a.height, b.height)), (0, 0, 0, 0))
    img.paste(a, (0, 0), a)
    img.paste(b, (a.width + 8, 0), b)
    img.save(os.path.join(Q, "logo.png"))
    return img.size


def keycap(d, x, y, label, f, w=56):
    d.rounded_rectangle((x, y + 4, x + w, y + 52), 8, fill=(60, 60, 66))
    d.rounded_rectangle((x, y, x + w, y + 46), 8, fill=(205, 205, 212), outline=(90, 90, 98), width=2)
    tw = f.getlength(label)
    d.text((x + (w - tw) / 2, y + 9), label, font=f, fill=(30, 30, 36))


def keys():
    W, H = 600, 150
    img = Image.new("RGBA", (W, H), (24, 24, 30, 235))
    d = ImageDraw.Draw(img)
    fk = ImageFont.truetype(BOLD, 22)
    ft = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 15)
    rows = [[("F7", "рельеф"), ("J", "карта"), ("Y", "панель")],
            [("W", "Ponder"), ("R", "рецепт"), ("U", "где нужен")]]
    for r, row in enumerate(rows):
        for c, (k, t) in enumerate(row):
            x, y = 20 + c * 195, 14 + r * 70
            keycap(d, x, y, k, fk)
            d.text((x + 66, y + 14), t, font=ft, fill=(235, 200, 110))
    img.save(os.path.join(Q, "keys.png"))


def book():
    # 16x16: книга с латунной шестерёнкой на обложке
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    cover, dark, page, brass, brass2 = (120, 72, 40, 255), (70, 40, 22, 255), (235, 225, 200, 255), (232, 188, 80, 255), (170, 120, 40, 255)
    for y in range(2, 15):
        for x in range(3, 14):
            p[x, y] = cover
        p[2, y] = dark
        p[14, y] = page
    for x in range(3, 15):
        p[x, 14] = page
        p[x, 1] = dark
    p[2, 1] = (0, 0, 0, 0)
    gear = ["..#.#..", ".#####.", "###.###", "##...##", "###.###", ".#####.", "..#.#.."]
    for gy, row in enumerate(gear):
        for gx, ch in enumerate(row):
            if ch == "#":
                p[5 + gx, 4 + gy] = brass if (gx + gy) % 3 else brass2
    for x in range(4, 13):
        p[x, 12] = dark
    img.save(os.path.join(B, "book.png"))


os.makedirs(Q, exist_ok=True)
os.makedirs(B, exist_ok=True)
print("logo", logo())
keys()
book()
