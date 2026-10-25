"""Вариант jjtm с другой высотой мира.

Рельеф ниже уровня моря (Y 63) не меняется, высота суши над морем сжимается линейно,
чтобы горы из версии 2096 поместились в новую высоту. Сжимается сама «высота рельефа»
(offset+factor+jagged в overworld_surface), а вертикальный градиент плотности остаётся прежним,
поэтому толщина корки над пещерами в блоках та же, что в версии 2096.
Использование: variant.py <src> <out> <height>
"""
import json, os, shutil, sys

SRC, OUT, H = sys.argv[1], sys.argv[2], int(sys.argv[3])
MIN_Y, SEA, OLD_TOP = -64, 63, 2032
NEW_TOP = MIN_Y + H
assert H % 16 == 0 and NEW_TOP > SEA + 64
S = (OLD_TOP - SEA) / (NEW_TOP - SEA)
V_SEA = 1 - 2 * (SEA - MIN_Y) / (OLD_TOP - MIN_Y)

def remap(y):
    return y if y <= SEA else min(NEW_TOP - 1, round(SEA + (y - SEA) / S))

def surface(o):
    # overworld_surface = baseline + (offset + factor + jagged); сжимаем вторую часть выше уровня моря
    a = -V_SEA
    terrain = {"type": "minecraft:add", "argument1": o["argument1"]["argument2"], "argument2": o["argument2"]}
    return {"type": "minecraft:add",
            "argument1": o["argument1"]["argument1"],
            "argument2": {"type": "minecraft:spline", "spline": {"coordinate": terrain, "points": [
                {"location": a - 0.01, "value": a - 0.01, "derivative": 1},
                {"location": a, "value": a, "derivative": 1 / S}]}}}

def fix(o):
    if isinstance(o, dict):
        if set(o) == {"absolute"} and isinstance(o["absolute"], int):
            o["absolute"] = remap(o["absolute"])
        return {k: fix(v) for k, v in o.items()}
    if isinstance(o, list):
        return [fix(v) for v in o]
    return o

def anchor_y(a):
    if "absolute" in a: return a["absolute"]
    if "above_bottom" in a: return MIN_Y + a["above_bottom"]
    return None

def scale_counts(pf):
    # сохраняем плотность фич: число попыток пропорционально новой длине диапазона
    hr = next((p for p in pf.get("placement", []) if p.get("type") == "minecraft:height_range"), None)
    if not hr or not isinstance(hr["height"], dict) or "min_inclusive" not in hr["height"]: return pf
    lo, hi = anchor_y(hr["height"]["min_inclusive"]), anchor_y(hr["height"]["max_inclusive"])
    if lo is None or hi is None or hi <= SEA: return pf
    k = (remap(hi) - remap(lo)) / (hi - lo)
    for p in pf["placement"]:
        if p.get("type") != "minecraft:count": continue
        c = p["count"]
        if isinstance(c, int): p["count"] = max(1, round(c * k))
        elif c.get("type") == "minecraft:constant": c["value"] = max(1, round(c["value"] * k))
        elif c.get("type") == "minecraft:uniform":
            c["min_inclusive"] = max(0, round(c["min_inclusive"] * k))
            c["max_inclusive"] = max(c["min_inclusive"], round(c["max_inclusive"] * k))
    return pf

shutil.rmtree(OUT, ignore_errors=True)
shutil.copytree(SRC, OUT)
for root, _, files in os.walk(os.path.join(OUT, "data")):
    for n in files:
        p = os.path.join(root, n)
        o = json.load(open(p))
        if "/placed_feature/" in p:
            o = scale_counts(o)  # до remap: нужны старые высоты
        o = fix(o)
        if n == "overworld_surface.json":
            assert o["argument1"]["argument1"] == "minecraft:overworld_baseline", "неожиданный overworld_surface"
            o = surface(o)
        if n == "overworld.json" and "/dimension_type/" in p:
            o["height"] = o["logical_height"] = H
        if n == "overworld.json" and "/noise_settings/" in p:
            o["noise"]["height"] = H
        json.dump(o, open(p, "w"), indent=2, ensure_ascii=False)

toml = os.path.join(OUT, "META-INF", "neoforge.mods.toml")
if os.path.exists(toml):
    t = open(toml).read().replace('version="0.3.1-1.21.1"', f'version="0.3.1-1.21.1-h{H}"')
    open(toml, "w").write(t)
mc = os.path.join(OUT, "pack.mcmeta")
m = json.load(open(mc)); m["pack"]["description"] += f", высота {H}"
json.dump(m, open(mc, "w"), indent=2, ensure_ascii=False)
print(f"h{H}: верх Y {NEW_TOP - 1}, сжатие выше моря x{S:.2f}")
