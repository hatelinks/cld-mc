#!/usr/bin/env python3
# Рисует тематические фоны глав квестов: картинка под всей главой, дорожки под линиями квестов,
# подложки под квестами, подписи под крупными квестами и лента с названием главы по центру.
# Берёт раскладку из out/bg_layout.json (его пишет quest_builder/build.py), кладёт PNG в
# pack/assets/cldquests/textures/quests/bg/<глава>.png
# Запуск: python3 -I resourcepack/make_backgrounds.py (после python3 -I quest_builder/make.py)
import json
import math
import os
import random
import re

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))
LAYOUT = os.path.join(os.path.dirname(ROOT), "out", "bg_layout.json")
OUT = os.path.join(ROOT, "pack", "assets", "cldquests", "textures", "quests", "bg")
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
PX = 32      # пикселей на клетку в готовой картинке
SS = 2       # рисуем в 2 раза крупнее и уменьшаем, так края гладкие
MAXSIDE = 1600
QK = 1.5 / 1.75  # квест размера 1 на экране FTB Quests занимает 1.5 / 1.75 клетки сетки

# тема: верх, низ, акцент, дорожка, украшения
THEMES = {
    "andesite": ((70, 74, 78), (44, 46, 50), (190, 196, 200), "belt", "gears"),
    "copper": ((92, 58, 40), (48, 32, 26), (232, 140, 90), "pipe", "gears"),
    "brass": ((88, 70, 34), (46, 36, 20), (240, 200, 90), "rod", "gears"),
    "rail": ((60, 62, 70), (34, 36, 42), (120, 200, 210), "rail", "gears"),
    "logistics": ((84, 66, 46), (46, 36, 28), (220, 180, 120), "belt", "crates"),
    "workshop": ((34, 58, 92), (20, 32, 56), (140, 190, 240), "dashed", "blueprint"),
    "electric": ((40, 44, 70), (20, 22, 40), (250, 220, 90), "wire", "bolts"),
    "enchant": ((36, 60, 44), (18, 30, 24), (140, 255, 120), "beam", "sparkles"),
    "sky": ((92, 150, 210), (40, 80, 140), (255, 255, 255), "contrail", "clouds"),
    "sea": ((24, 90, 120), (8, 28, 54), (120, 220, 240), "bubbles", "sea"),
    "hills": ((96, 120, 70), (52, 66, 40), (230, 210, 140), "road", "hills"),
    "kitchen": ((92, 60, 40), (54, 34, 24), (255, 200, 120), "dashed", "kitchen"),
    "farm": ((84, 110, 50), (44, 62, 28), (240, 220, 120), "road", "farm"),
    "storage": ((88, 64, 40), (50, 36, 24), (220, 180, 120), "plank", "crates"),
    "meadow": ((70, 110, 70), (36, 62, 40), (250, 240, 160), "road", "farm"),
    "map": ((150, 128, 90), (100, 82, 56), (90, 60, 30), "dashed", "map"),
    "market": ((96, 54, 50), (52, 30, 30), (250, 210, 90), "dashed", "coins"),
    "mystic": ((58, 30, 80), (22, 12, 36), (220, 140, 255), "beam", "runes"),
    "colony": ((120, 100, 70), (74, 60, 42), (250, 230, 170), "road", "houses"),
    "overworld": ((62, 98, 130), (40, 64, 44), (240, 240, 200), "road", "mountains"),
}
KEY_THEME = {
    "create_andesite": "andesite", "create_fluids": "copper", "create_brass": "brass", "create_trains": "rail",
    "tr_parts": "rail", "create_logistics": "logistics", "ca_electric": "electric", "create_enchant_ch": "enchant",
    "sim_physics": "sky", "aero_fleet": "sky", "gizmos": "sky", "submarine": "sea", "off_road": "hills",
    "fd_kitchen": "kitchen", "food_factory": "kitchen", "food_sweets": "kitchen", "vd_veggies": "farm", "act_farm": "farm",
    "sb_backpacks": "storage", "ss_storage": "storage", "toms": "storage", "sd_drawers": "storage", "supp": "meadow",
    "ec_compass": "map", "act_explore": "map", "act_hunt": "map", "market": "market", "mc_taxes": "market",
    "vanilla_progress": "overworld", "tutorial": "overworld", "to_know": "overworld",
}
GROUP_THEME = {"Эра механизмов": "workshop", "Небо и техника": "sky", "Хозяйство и путешествия": "farm",
               "Мистическое земледелие": "mystic", "Жизнь колонии": "colony"}


def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))


def vivid(c, sat, lum):
    # сочнее и светлее: в игре фон лежит на тёмном размытом мире и выглядит блекло
    m = sum(c) / 3
    return tuple(max(0, min(255, int((m + (v - m) * sat) * lum))) for v in c)


def rgba(c, a):
    return tuple(c[:3]) + (int(a),)


# ---------- украшения ----------
def gear(d, cx, cy, r, col, teeth=10, hole=0.35, width=0):
    pts = []
    n = teeth * 4
    for i in range(n):
        a = 2 * math.pi * i / n
        rr = r if (i % 4) in (1, 2) else r * 0.8
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    if width:
        d.polygon(pts, outline=col, width=width)
        d.ellipse((cx - r * hole, cy - r * hole, cx + r * hole, cy + r * hole), outline=col, width=width)
    else:
        d.polygon(pts, fill=col)
        d.ellipse((cx - r * hole, cy - r * hole, cx + r * hole, cy + r * hole), fill=(0, 0, 0, 0))


def cloud(d, cx, cy, r, col):
    for dx, dy, k in ((0, 0, 1), (-0.9, 0.25, 0.7), (0.9, 0.25, 0.75), (-0.4, -0.35, 0.75), (0.45, -0.3, 0.7)):
        rr = r * k
        d.ellipse((cx + dx * r - rr, cy + dy * r - rr * 0.8, cx + dx * r + rr, cy + dy * r + rr * 0.8), fill=col)


def star(d, cx, cy, r, col):
    pts = []
    for i in range(8):
        a = math.pi / 4 * i
        rr = r if i % 2 == 0 else r * 0.3
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    d.polygon(pts, fill=col)


def house(d, cx, cy, r, col):
    d.rectangle((cx - r, cy - r * 0.2, cx + r, cy + r), fill=col)
    d.polygon([(cx - r * 1.2, cy - r * 0.15), (cx, cy - r * 1.1), (cx + r * 1.2, cy - r * 0.15)], fill=col)


def crate(d, cx, cy, r, col, w):
    d.rectangle((cx - r, cy - r, cx + r, cy + r), outline=col, width=w)
    d.line((cx - r, cy - r, cx + r, cy + r), fill=col, width=w)
    d.line((cx - r, cy + r, cx + r, cy - r), fill=col, width=w)


def bolt(d, cx, cy, r, col):
    d.polygon([(cx - r * 0.2, cy - r), (cx + r * 0.5, cy - r), (cx + r * 0.05, cy - r * 0.1), (cx + r * 0.45, cy - r * 0.1),
               (cx - r * 0.4, cy + r), (cx - r * 0.05, cy + r * 0.1), (cx - r * 0.45, cy + r * 0.1)], fill=col)


def wheat(d, cx, cy, r, col, w):
    d.line((cx, cy + r, cx, cy - r), fill=col, width=w)
    for k in range(4):
        y = cy - r + k * r * 0.35
        d.ellipse((cx - r * 0.32, y, cx - r * 0.02, y + r * 0.22), fill=col)
        d.ellipse((cx + r * 0.02, y, cx + r * 0.32, y + r * 0.22), fill=col)


def rune(d, cx, cy, r, col, w, rnd):
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=col, width=w)
    pts = [(cx + r * 0.7 * math.cos(a), cy + r * 0.7 * math.sin(a)) for a in [rnd.uniform(0, 6.28) for _ in range(4)]]
    d.line(pts, fill=col, width=w)


def steam(d, cx, cy, r, col, w):
    for k in (-1, 0, 1):
        pts = [(cx + k * r * 0.5 + math.sin(t / 3) * r * 0.2, cy + r - t * r / 6) for t in range(13)]
        d.line(pts, fill=col, width=w)


def coin(d, cx, cy, r, col, w):
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=col)
    d.ellipse((cx - r * 0.6, cy - r * 0.6, cx + r * 0.6, cy + r * 0.6), outline=(0, 0, 0, 60), width=w)


def contour(d, cx, cy, r, col, w, rnd):
    for k in range(3):
        rr = r * (1 - k * 0.28)
        pts = []
        ph = [rnd.uniform(0, 6.28) for _ in range(3)]
        for i in range(37):
            a = 2 * math.pi * i / 36
            m = 1 + 0.18 * math.sin(3 * a + ph[0]) + 0.1 * math.sin(5 * a + ph[1])
            pts.append((cx + rr * m * math.cos(a), cy + rr * 0.7 * m * math.sin(a)))
        d.line(pts, fill=col, width=w)


# ---------- главная функция ----------
def draw(key, ch):
    theme = ch.get("theme") or KEY_THEME.get(key) or GROUP_THEME.get(ch["group"], "overworld")
    top, bot, acc, path, decor = THEMES[theme]
    top, bot = vivid(top, 1.35, 1.15), vivid(bot, 1.35, 1.1)
    x0, y0, x1, y1 = ch["box"]
    px = min(PX, MAXSIDE / max(x1 - x0, y1 - y0))
    S = px * SS
    W, H = int(round((x1 - x0) * S)), int(round((y1 - y0) * S))
    rnd = random.Random(key)

    def P(x, y):
        return (x - x0) * S, (y - y0) * S

    # фон: градиент и мягкий шум
    small = Image.new("RGB", (max(2, W // 16), max(2, H // 16)))
    sp = small.load()
    for yy in range(small.height):
        base = mix(top, bot, yy / max(1, small.height - 1))
        for xx in range(small.width):
            n = rnd.uniform(-7, 7)
            sp[xx, yy] = tuple(max(0, min(255, int(c + n))) for c in base)
    img = small.resize((W, H), Image.BICUBIC).convert("RGBA")
    deco = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(deco)

    qs = ch["quests"]
    byid = {q["id"]: q for q in qs}
    # точки, где нельзя ставить украшения: квесты и линии
    busy = [(q["x"], q["y"], q["size"] * QK * 0.5 + 0.9) for q in qs]
    for a, b in ch["edges"]:
        qa, qb = byid[a], byid[b]
        L = math.hypot(qb["x"] - qa["x"], qb["y"] - qa["y"])
        for k in range(int(L * 2) + 1):
            t = k / max(1, int(L * 2))
            busy.append((qa["x"] + (qb["x"] - qa["x"]) * t, qa["y"] + (qb["y"] - qa["y"]) * t, 0.55))
    busy.append(((x0 + x1) / 2, y0 + 1.5, (x1 - x0) * 0.3))

    # гребёнки: от родителя к стволу левее сетки листьев, от ствола по строкам сетки
    segs = [(byid[a], byid[b]) for a, b in ch["edges"]]
    segs = [((qa["x"], qa["y"]), (qb["x"], qb["y"])) for qa, qb in segs]
    joints = []
    for hub, leaves in ch.get("combs", []):
        h = byid[hub]
        ls = [byid[i] for i in leaves]
        tx = min(q["x"] for q in ls) - 0.75
        rows = sorted({q["y"] for q in ls})
        segs.append(((h["x"], h["y"]), (tx, h["y"])))
        segs.append(((tx, min(rows + [h["y"]])), (tx, max(rows + [h["y"]]))))
        for y in rows:
            segs.append(((tx, y), (max(q["x"] for q in ls if q["y"] == y), y)))
            joints.append((tx, y))
        joints.append((tx, h["y"]))
    for A, B in segs[len(ch["edges"]):]:
        L = math.hypot(B[0] - A[0], B[1] - A[1])
        for k in range(int(L * 2) + 1):
            t = k / max(1, int(L * 2))
            busy.append((A[0] + (B[0] - A[0]) * t, A[1] + (B[1] - A[1]) * t, 0.55))

    def free(x, y, r):
        return all(math.hypot(x - bx, y - by) > br + r for bx, by, br in busy)

    # ---------- украшения на свободных местах ----------
    spots = []
    for _ in range(int((x1 - x0) * (y1 - y0) * 1.2)):
        x, y = rnd.uniform(x0 + 0.6, x1 - 0.6), rnd.uniform(y0 + 0.6, y1 - 0.6)
        r = rnd.uniform(0.35, 1.2)
        if free(x, y, r) and all(math.hypot(x - sx, y - sy) > r + sr + 0.2 for sx, sy, sr in spots):
            spots.append((x, y, r))
    lw = max(2, int(S * 0.06))
    for x, y, r in spots:
        cx, cy = P(x, y)
        R = r * S
        a = rnd.randint(40, 80)
        if decor == "gears":
            gear(d, cx, cy, R, rgba(acc, a), teeth=rnd.choice((8, 10, 12)))
        elif decor == "blueprint":
            gear(d, cx, cy, R, rgba(acc, a + 30), teeth=rnd.choice((8, 12)), width=lw)
        elif decor == "crates":
            crate(d, cx, cy, R * 0.7, rgba(acc, a), lw)
        elif decor == "bolts":
            bolt(d, cx, cy, R, rgba(acc, a + 10))
        elif decor == "sparkles" or decor == "runes" and rnd.random() < 0.5:
            star(d, cx, cy, R * 0.6, rgba(acc, a + 30))
        elif decor == "runes":
            rune(d, cx, cy, R * 0.7, rgba(acc, a + 20), lw, rnd)
        elif decor == "clouds":
            cloud(d, cx, cy, R * 0.8, rgba((255, 255, 255), a + 40))
        elif decor == "sea":
            if rnd.random() < 0.6:
                d.ellipse((cx - R * 0.3, cy - R * 0.3, cx + R * 0.3, cy + R * 0.3), outline=rgba(acc, a + 30), width=lw)
            else:
                pts = [(cx + math.sin(t / 2.0) * R * 0.25, cy + R - t * R / 6) for t in range(13)]
                d.line(pts, fill=rgba((60, 160, 90), a + 40), width=lw * 2)
        elif decor == "hills" or decor == "mountains":
            col = rgba(mix(bot, (20, 20, 20), 0.3), a + 30)
            d.polygon([(cx - R, cy + R * 0.5), (cx, cy - R * 0.6), (cx + R, cy + R * 0.5)], fill=col)
            if decor == "mountains":
                d.polygon([(cx - R * 0.3, cy - R * 0.25), (cx, cy - R * 0.6), (cx + R * 0.3, cy - R * 0.25)], fill=rgba((240, 240, 240), a + 40))
        elif decor == "kitchen":
            if rnd.random() < 0.5:
                steam(d, cx, cy, R * 0.6, rgba((255, 255, 255), a), lw)
            else:
                d.ellipse((cx - R * 0.6, cy - R * 0.35, cx + R * 0.6, cy + R * 0.35), outline=rgba(acc, a + 20), width=lw)
        elif decor == "farm":
            wheat(d, cx, cy, R * 0.7, rgba(acc, a + 20), lw)
        elif decor == "map":
            contour(d, cx, cy, R, rgba(acc, a + 30), lw, rnd)
        elif decor == "coins":
            coin(d, cx, cy, R * 0.4, rgba(acc, a + 30), lw)
        elif decor == "houses":
            house(d, cx, cy, R * 0.5, rgba(mix(bot, (30, 20, 10), 0.4), a + 40))

    # ---------- дорожки под линиями квестов ----------
    pw = S * 0.32
    for a_, b_ in segs:
        A, B = P(*a_), P(*b_)
        L = math.hypot(B[0] - A[0], B[1] - A[1]) or 1
        nx, ny = -(B[1] - A[1]) / L, (B[0] - A[0]) / L
        if path == "belt":
            d.line((A, B), fill=(30, 30, 34, 200), width=int(pw))
            for k in range(0, int(L), int(S * 0.3)):
                t = k / L
                cx, cy = A[0] + (B[0] - A[0]) * t, A[1] + (B[1] - A[1]) * t
                d.line((cx - nx * pw * 0.45, cy - ny * pw * 0.45, cx + nx * pw * 0.45, cy + ny * pw * 0.45), fill=(70, 70, 76, 200), width=lw)
        elif path == "pipe":
            d.line((A, B), fill=rgba(mix(acc, (0, 0, 0), 0.45), 220), width=int(pw))
            d.line((A, B), fill=rgba(acc, 120), width=int(pw * 0.35))
        elif path == "rod":
            d.line((A, B), fill=rgba(mix(acc, (0, 0, 0), 0.5), 200), width=int(pw * 0.7))
            d.line((A, B), fill=rgba(acc, 140), width=int(pw * 0.25))
        elif path == "rail":
            for k in range(0, int(L), int(S * 0.28)):
                t = k / L
                cx, cy = A[0] + (B[0] - A[0]) * t, A[1] + (B[1] - A[1]) * t
                d.line((cx - nx * pw * 0.7, cy - ny * pw * 0.7, cx + nx * pw * 0.7, cy + ny * pw * 0.7), fill=(90, 64, 40, 220), width=int(lw * 1.6))
            for s_ in (-0.4, 0.4):
                d.line(((A[0] + nx * pw * s_, A[1] + ny * pw * s_), (B[0] + nx * pw * s_, B[1] + ny * pw * s_)), fill=(170, 170, 180, 230), width=lw)
        elif path == "road" or path == "plank":
            col = (120, 92, 60, 170) if path == "road" else (130, 96, 60, 200)
            d.line((A, B), fill=col, width=int(pw * 1.1))
        elif path == "beam" or path == "wire" or path == "contrail":
            glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            ImageDraw.Draw(glow).line((A, B), fill=rgba(acc, 110), width=int(pw * 1.4))
            deco.alpha_composite(glow.filter(ImageFilter.GaussianBlur(S * 0.15)))
            d.line((A, B), fill=rgba(acc, 170 if path != "contrail" else 120), width=max(2, int(pw * 0.2)))
        elif path == "bubbles":
            for k in range(0, int(L), int(S * 0.35)):
                t = k / L
                cx, cy = A[0] + (B[0] - A[0]) * t, A[1] + (B[1] - A[1]) * t
                r = S * rnd.uniform(0.06, 0.12)
                d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=rgba(acc, 160), width=lw)
        else:
            d.line((A, B), fill=rgba(acc, 90), width=int(pw * 0.5))

    for jx, jy in joints:
        cx, cy = P(jx, jy)
        r = pw * 0.45
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=rgba(acc, 200))

    # ---------- подложки под квестами ----------
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    for q in qs:
        cx, cy = P(q["x"], q["y"])
        r = S * (q["size"] * QK * 0.5 + 0.16)
        g.ellipse((cx - r, cy - r, cx + r, cy + r), fill=rgba(acc, 70 if not q["optional"] else 35))
    deco.alpha_composite(glow.filter(ImageFilter.GaussianBlur(S * 0.12)))
    for q in qs:
        cx, cy = P(q["x"], q["y"])
        r = S * (q["size"] * QK * 0.5 + 0.1)
        d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=rgba(acc, 120 if not q["optional"] else 60), width=lw)

    # ---------- подписи под крупными квестами, по центру ----------
    fsz = int(S * 0.3)
    f = ImageFont.truetype(BOLD, fsz)
    placed = []
    for q in qs:
        t = re.sub(r"&.", "", q["title"])
        if not t:
            continue
        if len(t) > 26:
            t = t[:25] + "…"
        tw = f.getlength(t)
        hw, hh = tw / S / 2 + 0.15, 0.5

        def hits(lx, ly):
            # подпись не должна лезть на другие квесты и другие подписи
            for o in qs:
                if o is q:
                    continue
                r = o["size"] * QK * 0.5 + 0.1
                if abs(o["x"] - lx) < hw + r and abs(o["y"] - (ly + hh / 2)) < hh / 2 + r:
                    return True
            return any(abs(px_ - lx) < hw + pw_ and abs(py_ - ly) < hh for px_, py_, pw_ in placed)

        ly = None
        for cand in (q["y"] + q["size"] * QK * 0.5 + 0.12, q["y"] - q["size"] * QK * 0.5 - 0.62):
            if not hits(q["x"], cand):
                ly = cand
                break
        if ly is None:
            continue
        placed.append((q["x"], ly, hw))
        cx, _ = P(q["x"], q["y"])
        ty = P(0, ly)[1]
        d.rounded_rectangle((cx - tw / 2 - fsz * 0.4, ty - fsz * 0.15, cx + tw / 2 + fsz * 0.4, ty + fsz * 1.2), fsz * 0.4, fill=(0, 0, 0, 110))
        d.text((cx - tw / 2 + 2, ty + 2), t, font=f, fill=(0, 0, 0, 200))
        d.text((cx - tw / 2, ty), t, font=f, fill=(255, 255, 255, 235))

    # ---------- лента с названием главы по центру (отдельный слой, края его не гасят) ----------
    img.alpha_composite(deco)
    mask = Image.new("L", (W, H), 0)
    m = ImageDraw.Draw(mask)
    edge = int(S * 0.9)
    m.rounded_rectangle((edge, edge, W - edge, H - edge), S * 0.6, fill=250)
    mask = mask.filter(ImageFilter.GaussianBlur(edge * 0.6))
    img.putalpha(mask)
    deco = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(deco)
    title = re.sub(r"&.", "", ch["title"]).strip()
    tf = ImageFont.truetype(BOLD, int(S * 0.62))
    sf = ImageFont.truetype(REG, int(S * 0.3))
    tw = tf.getlength(title)
    sub = re.sub(r"&.", "", ch.get("subtitle", ""))
    if sf.getlength(sub) > (x1 - x0) * S * 0.8:
        while sub and sf.getlength(sub + "…") > (x1 - x0) * S * 0.8:
            sub = sub[:-1]
        sub += "…"
    bw = max(tw, sf.getlength(sub)) + S * 1.4
    cx, cy = W / 2, S * 1.5
    ribbon = rgba(mix(acc, bot, 0.55), 215)
    d.polygon([(cx - bw / 2 - S * 0.45, cy - S * 0.55), (cx - bw / 2, cy), (cx - bw / 2 - S * 0.45, cy + S * 0.55),
               (cx - bw / 2 + S * 0.3, cy + S * 0.55), (cx - bw / 2 + S * 0.3, cy - S * 0.55)], fill=rgba(mix(acc, bot, 0.75), 215))
    d.polygon([(cx + bw / 2 + S * 0.45, cy - S * 0.55), (cx + bw / 2, cy), (cx + bw / 2 + S * 0.45, cy + S * 0.55),
               (cx + bw / 2 - S * 0.3, cy + S * 0.55), (cx + bw / 2 - S * 0.3, cy - S * 0.55)], fill=rgba(mix(acc, bot, 0.75), 215))
    d.rounded_rectangle((cx - bw / 2, cy - S * 0.62, cx + bw / 2, cy + S * 0.62), S * 0.15, fill=ribbon, outline=rgba(acc, 230), width=lw)
    d.text((cx - tw / 2 + 3, cy - S * 0.56 + 3), title, font=tf, fill=(0, 0, 0, 180))
    d.text((cx - tw / 2, cy - S * 0.56), title, font=tf, fill=(255, 255, 255, 255))
    sw = sf.getlength(sub)
    d.text((cx - sw / 2, cy + S * 0.14), sub, font=sf, fill=(235, 235, 235, 230))

    img.alpha_composite(deco)
    img = img.resize((W // SS, H // SS), Image.LANCZOS)
    # 256 цветов: файл в несколько раз меньше, на глаз разницы нет
    img.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE).save(os.path.join(OUT, key + ".png"), optimize=True)
    return img.size


def main():
    os.makedirs(OUT, exist_ok=True)
    data = json.load(open(LAYOUT, encoding="utf-8"))
    total = 0
    for key, ch in data.items():
        size = draw(key, ch)
        total += os.path.getsize(os.path.join(OUT, key + ".png"))
        print(key, size)
    print("всего", round(total / 1e6, 1), "МБ")


if __name__ == "__main__":
    main()
