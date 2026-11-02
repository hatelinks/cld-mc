#!/usr/bin/env python3
# Рисует главу квестов из out/ftbquests примерно так, как её показывает FTB Quests: список глав слева,
# формы и размеры квестов, иконки предметов, линии зависимостей, картинки главы.
# Это рендер по файлам, не скриншот из игры.
# Запуск: python3 -I quest_builder/tools/render_chapter.py out.png <ключ главы> [jar с текстурами ...]
import glob
import io
import json
import math
import os
import re
import sys
import zipfile

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import snbt  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
Q = os.path.join(ROOT, "out", "ftbquests", "quests")
RP = os.path.join(ROOT, "resourcepack", "pack")
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
MC = {"0": (0, 0, 0), "1": (0, 0, 170), "2": (0, 170, 0), "3": (0, 170, 170), "4": (170, 0, 0), "5": (170, 0, 170),
      "6": (255, 170, 0), "7": (170, 170, 170), "8": (85, 85, 85), "9": (85, 85, 255), "a": (85, 255, 85),
      "b": (85, 255, 255), "c": (255, 85, 85), "d": (255, 85, 255), "e": (255, 255, 85), "f": (255, 255, 255)}
U = 46  # пикселей на клетку


# ---------- текстуры ----------
class Tex:
    def __init__(self, jars):
        self.files = {}
        for p in jars:
            self.add(zipfile.ZipFile(p))
        for base, _, fs in os.walk(RP):
            for f in fs:
                rel = os.path.relpath(os.path.join(base, f), RP).replace(os.sep, "/")
                self.files[rel] = open(os.path.join(base, f), "rb").read

    def add(self, z):
        for n in z.namelist():
            if n.startswith("META-INF/jarjar/") and n.endswith(".jar"):
                self.add(zipfile.ZipFile(io.BytesIO(z.read(n))))
            elif n.startswith("assets/") and (n.endswith(".png") or n.endswith(".json")):
                self.files.setdefault(n[7:], (lambda z=z, n=n: z.read(n)))

    def png(self, rid):
        ns, p = rid.split(":", 1)
        k = f"{ns}/textures/{p}.png"
        if k in self.files:
            im = Image.open(io.BytesIO(self.files[k]())).convert("RGBA")
            return im.crop((0, 0, im.width, im.width)) if im.height > im.width else im
        return None

    def model(self, rid, depth=0):
        ns, p = rid.split(":", 1) if ":" in rid else ("minecraft", rid)
        k = f"{ns}/models/{p}.json"
        if k not in self.files or depth > 6:
            return {}
        d = json.loads(self.files[k]())
        tex = dict(d.get("textures", {}))
        if d.get("parent"):
            par = self.model(d["parent"], depth + 1)
            par.update(tex)
            tex = par
        return tex

    def icon(self, rid):
        ns, p = rid.split(":", 1)
        tex = self.model(f"{ns}:item/{p}")
        for key in ("layer0", "all", "front", "side", "top", "particle", "texture", "0", "1"):
            v = tex.get(key)
            while isinstance(v, str) and v.startswith("#"):
                v = tex.get(v[1:])
            if isinstance(v, str):
                im = self.png(v if ":" in v else "minecraft:" + v)
                if im:
                    return im
        return self.png(f"{ns}:item/{p}") or self.png(f"{ns}:block/{p}")


# ---------- фигуры ----------
def poly(n, cx, cy, r, rot=0):
    return [(cx + r * math.cos(rot + 2 * math.pi * i / n), cy + r * math.sin(rot + 2 * math.pi * i / n)) for i in range(n)]


def gear(cx, cy, r):
    pts = []
    for i in range(48):
        a = 2 * math.pi * i / 48
        rr = r if (i // 3) % 2 == 0 else r * 0.82
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return pts


def shape(d, kind, cx, cy, r, fill, line):
    if kind == "square":
        d.rectangle((cx - r, cy - r, cx + r, cy + r), fill=fill, outline=line, width=3)
    elif kind == "rsquare":
        d.rounded_rectangle((cx - r, cy - r, cx + r, cy + r), r * 0.35, fill=fill, outline=line, width=3)
    elif kind == "hexagon":
        d.polygon(poly(6, cx, cy, r * 1.08), fill=fill, outline=line, width=3)
    elif kind == "gear":
        d.polygon(gear(cx, cy, r * 1.08), fill=fill, outline=line, width=3)
    else:
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=fill, outline=line, width=3)


def colored(d, xy, text, font, default=(255, 255, 255)):
    # текст с кодами цвета &x
    x, y = xy
    col, bold, under = default, False, False
    parts = re.split(r"(&[0-9a-fk-or])", text)
    for part in parts:
        if re.fullmatch(r"&[0-9a-fk-or]", part):
            c = part[1]
            if c in MC:
                col, bold, under = MC[c], False, False
            elif c == "l":
                bold = True
            elif c == "n":
                under = True
            elif c == "r":
                col, bold, under = default, False, False
            continue
        if not part:
            continue
        f = ImageFont.truetype(BOLD if bold else FONT, font.size)
        d.text((x + 1, y + 1), part, font=f, fill=(20, 20, 20))
        d.text((x, y), part, font=f, fill=col)
        w = f.getlength(part)
        if under:
            d.line((x, y + font.size + 2, x + w, y + font.size + 2), fill=col, width=1)
        x += w
    return x


def load_lang():
    out = {}
    for line in open(os.path.join(Q, "lang", "ru_ru.snbt"), encoding="utf-8"):
        m = re.match(r'\t([a-z_]+\.[0-9A-F]{16}\.[a-z_]+): (".*"|\[.*\])$', line.rstrip("\n"))
        if m:
            v = m.group(2)
            out[m.group(1)] = json.loads(v) if v.startswith("[") else json.loads(v)
    return out


def main(out, key, jars):
    tex = Tex(jars)
    lang = load_lang()
    chs = [snbt.parse(open(p, encoding="utf-8").read()) for p in glob.glob(os.path.join(Q, "chapters", "*.snbt"))]
    chs.sort(key=lambda c: c["order_index"])
    groups = [g["id"] for g in snbt.parse(open(os.path.join(Q, "chapter_groups.snbt"), encoding="utf-8").read())["chapter_groups"]]
    ch = next(c for c in chs if c["filename"] == key)

    qs = ch["quests"]
    xs = [q["x"] for q in qs] + [im["x"] for im in ch.get("images", [])]
    ys = [q["y"] for q in qs] + [im["y"] for im in ch.get("images", [])]
    PANEL = 300
    minx, maxx, miny, maxy = min(xs) - 2, max(xs) + 2, min(ys) - 2, max(ys) + 2
    W = int(PANEL + (maxx - minx) * U)
    H = max(int((maxy - miny) * U) + 60, 700)
    img = Image.new("RGBA", (W, H), (33, 33, 38, 255))
    d = ImageDraw.Draw(img)
    for gx in range(PANEL, W, U // 2):
        for gy in range(60, H, U // 2):
            d.point((gx, gy), fill=(55, 55, 62))

    def pos(x, y):
        return PANEL + (x - minx) * U, 60 + (y - miny) * U

    # заголовок главы
    f16 = ImageFont.truetype(FONT, 16)
    f13 = ImageFont.truetype(FONT, 13)
    d.rectangle((PANEL, 0, W, 50), fill=(24, 24, 28))
    colored(d, (PANEL + 14, 8), lang.get(f'chapter.{ch["id"]}.title', key), ImageFont.truetype(FONT, 20))
    sub = lang.get(f'chapter.{ch["id"]}.chapter_subtitle', "")
    d.text((PANEL + 14, 33), sub, font=f13, fill=(170, 170, 170))

    # список глав слева
    d.rectangle((0, 0, PANEL - 1, H), fill=(22, 22, 26))
    y = 10
    last_group = None
    for c in chs:
        g = c["group"]
        if g != last_group and g:
            y += 6
            colored(d, (10, y), lang.get(f"chapter_group.{g}.title", g), f16)
            y += 24
        last_group = g
        if c is ch:
            d.rectangle((4, y - 2, PANEL - 6, y + 19), fill=(60, 60, 75))
        ic = tex.icon(c["icon"]["id"]) if "icon" in c else None
        if ic:
            img.alpha_composite(ic.resize((16, 16), Image.NEAREST), (14 if not g else 22, y))
        colored(d, ((34 if not g else 42), y), lang.get(f'chapter.{c["id"]}.title', c["filename"]), f13)
        y += 21
        if y > H - 20:
            break

    # картинки главы
    for im in ch.get("images", []):
        ns, path = im["image"].split(":", 1)
        k = f"{ns}/{path}"
        if k not in tex.files and f"assets/{k}" in tex.files:
            k = f"assets/{k}"
        if k in tex.files:
            p = Image.open(io.BytesIO(tex.files[k]())).convert("RGBA")
            w, h = int(im["width"] * U), int(im["height"] * U)
            cx, cy = pos(im["x"], im["y"])
            img.alpha_composite(p.resize((w, h), Image.NEAREST), (int(cx - w / 2), int(cy - h / 2)))

    # линии зависимостей
    byid = {q["id"]: q for q in qs}
    for q in qs:
        for dep in q.get("dependencies", []):
            if dep in byid:
                a, b = pos(byid[dep]["x"], byid[dep]["y"]), pos(q["x"], q["y"])
                d.line((a, b), fill=(140, 140, 150), width=3)

    # квесты
    for q in qs:
        cx, cy = pos(q["x"], q["y"])
        s = float(q.get("size", 1.0))
        r = U * 0.42 * s
        opt = q.get("optional")
        fill = (58, 58, 66) if not opt else (48, 48, 54)
        line = (200, 200, 210) if not opt else (120, 120, 130)
        shape(d, q.get("shape", "circle"), cx, cy, r, fill, line)
        rid = None
        if "icon" in q:
            rid = q["icon"].get("id")
        else:
            for t in q["tasks"]:
                if t.get("type") == "item":
                    rid = t["item"]["id"]
                    break
        ic = tex.icon(rid) if rid else None
        if ic is None and all(t.get("type") == "checkmark" for t in q["tasks"]):
            ic = tex.icon("minecraft:writable_book")
        if ic:
            n = int(r * 1.15)
            img.alpha_composite(ic.resize((n, n), Image.NEAREST), (int(cx - n / 2), int(cy - n / 2)))
        t = lang.get(f'quest.{q["id"]}.title', "")
        if s >= 1.5 and t and "--labels" in sys.argv:
            tw = ImageFont.truetype(FONT, 12).getlength(re.sub(r"&.", "", t))
            colored(d, (cx - tw / 2, cy + r + 4), t, ImageFont.truetype(FONT, 12))
    img.convert("RGB").save(out)
    print(out, img.size)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3:])
