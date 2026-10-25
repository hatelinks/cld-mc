"""Данные мира TelipailoCraft: тип измерения, пресет, настройки шума и пещеры (из ванильных, под высоту 768).
Использование: build_data.py <client-extra.jar> <out>
"""
import json, os, sys, zipfile

JAR, OUT = sys.argv[1:3]
NS = "tcw"
MIN_Y, HEIGHT = -128, 768
z = zipfile.ZipFile(JAR)

def van(path):
    return json.loads(z.read("data/minecraft/" + path))

def dump(o, *parts):
    p = os.path.join(OUT, "data", *parts)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(o, f, indent=2, ensure_ascii=False)

def col(value):
    return {"type": "tcw:column", "value": value, "noise": "tcw:seed"}

def walk(o, fn):
    o = fn(o)
    if isinstance(o, dict):
        return {k: walk(v, fn) for k, v in o.items()}
    if isinstance(o, list):
        return [walk(v, fn) for v in o]
    return o

def refs(o):
    if isinstance(o, str) and o.startswith("minecraft:overworld/caves/"):
        return "tcw:caves/" + o[len("minecraft:overworld/caves/"):]
    return o

# --- пещеры
for name in ["entrances", "noodle", "pillars", "spaghetti_2d", "spaghetti_2d_thickness_modulator", "spaghetti_roughness_function"]:
    o = walk(van(f"worldgen/density_function/overworld/caves/{name}.json"), refs)
    def fix(d):
        if isinstance(d, dict):
            if d.get("type") == "minecraft:range_choice" and d.get("input") == "minecraft:y" and d.get("min_inclusive") == -60.0:
                d = dict(d, min_inclusive=float(MIN_Y + 4), max_exclusive=float(MIN_Y + HEIGHT - 40))
            if d.get("type") == "minecraft:y_clamped_gradient" and d.get("from_y") == -64 and d.get("to_y") == 320:
                d = dict(d, from_y=MIN_Y, to_y=MIN_Y + HEIGHT)
        return d
    dump(walk(o, fix), NS, "worldgen", "density_function", "caves", name + ".json")

# --- свои горные биомы (копии ванильных списков фич, чтобы не ломать их порядок)
def biome(name, src, temp, features=None, grass=None, foliage=None, extra_spawn=(), sky=None, music=None):
    b = van(f"worldgen/biome/{src}.json")
    b["temperature"] = temp
    b["has_precipitation"] = True
    if features: b["features"] = features(b["features"])
    e = b["effects"]
    if grass: e["grass_color"] = grass
    if foliage: e["foliage_color"] = foliage
    if sky: e["sky_color"] = sky
    if music:
        e["music"] = dict(e.get("music") or {"max_delay": 24000, "min_delay": 12000, "replace_current_music": False}, sound=music)
    cr = b["spawners"].setdefault("creature", [])
    have = {x["type"] for x in cr}
    for t, w, lo, hi in extra_spawn:
        if t not in have: cr.append({"type": t, "weight": w, "minCount": lo, "maxCount": hi})
    dump(b, NS, "worldgen", "biome", name + ".json")

def swap(old, new):
    return lambda fs: [[new if f == old else f for f in step] for step in fs]

def keep_veg(allowed):
    return lambda fs: [step if i != 9 else [f for f in step if f in allowed] for i, step in enumerate(fs)]

tp = van("worldgen/placed_feature/trees_taiga.json")
for pl in tp["placement"]:
    if pl["type"] == "minecraft:count":
        pl["count"] = {"type": "minecraft:weighted_list", "distribution": [{"data": 1, "weight": 4}, {"data": 2, "weight": 4}, {"data": 4, "weight": 2}]}
dump(tp, NS, "worldgen", "placed_feature", "trees_subalpine.json")

GOAT = ("minecraft:goat", 5, 1, 3)
biome("subalpine_forest", "taiga", 0.7, swap("minecraft:trees_taiga", "tcw:trees_subalpine"),
      grass=0x7FA05A, foliage=0x5E8A4E, extra_spawn=[("minecraft:sheep", 6, 2, 4)])
biome("alpine_meadow", "meadow", 0.8, grass=0x8DC25A, foliage=0x76B04A,
      extra_spawn=[GOAT, ("minecraft:sheep", 8, 2, 5)])
biome("alpine_tundra", "meadow", 0.5, keep_veg({"minecraft:glow_lichen", "minecraft:patch_grass_plain"}),
      grass=0x9DA672, foliage=0x8C9A64, extra_spawn=[GOAT], music="minecraft:music.overworld.stony_peaks")
biome("scree_slopes", "stony_peaks", 0.6, extra_spawn=[GOAT])
biome("glacier", "frozen_peaks", -0.7)
biome("volcanic_slopes", "stony_peaks", 1.0, grass=0x8A8A5A, sky=0x8C9AAE)

OWN = ["subalpine_forest", "alpine_meadow", "alpine_tundra", "scree_slopes", "glacier", "volcanic_slopes"]
ids = [f"tcw:{b}" for b in OWN]
for tag in ["is_overworld", "is_mountain"]:
    dump({"replace": False, "values": ids}, "minecraft", "tags", "worldgen", "biome", tag + ".json")
dump({"replace": False, "values": ids}, "c", "tags", "worldgen", "biome", "is_mountain.json")
dump({"replace": False, "values": ["tcw:subalpine_forest"]}, "minecraft", "tags", "worldgen", "biome", "is_taiga.json")
dump({"replace": False, "values": ["tcw:subalpine_forest"]}, "minecraft", "tags", "worldgen", "biome", "is_forest.json")

def blk(name, **props):
    st = {"Name": name}
    if props: st["Properties"] = props
    return {"type": "minecraft:block", "result_state": st}

def cond(c, then): return {"type": "minecraft:condition", "if_true": c, "then_run": then}
def seq(*r): return {"type": "minecraft:sequence", "sequence": list(r)}
def biomes(*b): return {"type": "minecraft:biome", "biome_is": [f"tcw:{x}" for x in b]}
def noise(lo, hi, n="minecraft:surface"): return {"type": "minecraft:noise_threshold", "noise": n, "min_threshold": lo, "max_threshold": hi}
FLOOR = {"type": "minecraft:stone_depth", "offset": 0, "surface_type": "floor", "add_surface_depth": False, "secondary_depth_range": 0}
UNDER = {"type": "minecraft:stone_depth", "offset": 0, "surface_type": "floor", "add_surface_depth": True, "secondary_depth_range": 0}
WATER_OK = {"type": "minecraft:water", "offset": -1, "surface_depth_multiplier": 0, "add_stone_depth": False}
own_rules = [
    cond(biomes("scree_slopes"), cond(UNDER, seq(
        cond(noise(-0.05, 9, "minecraft:gravel"), blk("minecraft:gravel")),
        cond(noise(0.35, 9), blk("minecraft:andesite")),
        blk("minecraft:stone")))),
    cond(biomes("alpine_tundra"), cond(FLOOR, cond(WATER_OK, seq(
        cond(noise(0.3, 9), blk("minecraft:coarse_dirt")),
        cond(noise(-9, -0.45), blk("minecraft:moss_block")))))),
    cond(biomes("volcanic_slopes"), cond(UNDER, seq(
        cond(noise(0.15, 9), blk("minecraft:tuff")),
        cond(noise(-9, -0.25), blk("minecraft:basalt", axis="y")),
        cond(noise(-0.1, 0.05, "minecraft:gravel"), blk("minecraft:blackstone")),
        blk("minecraft:smooth_basalt")))),
    cond(biomes("glacier"), seq(cond(FLOOR, blk("minecraft:snow_block")), cond(UNDER, blk("minecraft:packed_ice")))),
]

# --- настройки шума
ns = van("worldgen/noise_settings/overworld.json")
ns["noise"] = {"min_y": MIN_Y, "height": HEIGHT, "size_horizontal": 1, "size_vertical": 2}
r = ns["noise_router"]
surface = col("surface")
for k in ["continents", "erosion", "ridges", "temperature", "vegetation", "depth"]:
    r[k] = col(k)

def final(d):
    if d == "minecraft:overworld/sloped_cheese":
        return surface
    if isinstance(d, dict):
        t = d.get("type")
        if t == "minecraft:y_clamped_gradient":
            if d.get("from_y") == -64 and d.get("to_y") == -40:
                d = dict(d, from_y=MIN_Y, to_y=MIN_Y + 24)
            elif d.get("from_y") == 240 and d.get("to_y") == 256:
                d = dict(d, from_y=MIN_Y + HEIGHT - 24, to_y=MIN_Y + HEIGHT - 8)
        if t == "minecraft:noise" and d.get("noise") == "minecraft:cave_cheese":
            d = dict(d, xz_scale=0.6, y_scale=0.45)
        if t == "minecraft:add" and d.get("argument1") == 0.27:
            d = dict(d, argument1=0.22)
    return refs(d)

# арки и карнизы, подрезки под карнизами — одно значение rock без интерполяции, с точностью до блока:
# max(min(F, rock + 2), rock - 2), см. Rocks.combined
rock = col("rock")  # cache_once здесь портит разрешение (значения ложатся ячейками 4×8)
r["final_density"] = {"type": "minecraft:max", "argument2": {"type": "minecraft:add", "argument1": rock, "argument2": -2}, "argument1": {
    "type": "minecraft:min", "argument2": {"type": "minecraft:add", "argument1": rock, "argument2": 2}, "argument1": {
        "type": "minecraft:min", "argument1": walk(r["final_density"], final),
        "argument2": {"type": "minecraft:interpolated", "argument": col("caves")}}}}
# предварительная поверхность (водоносные слои, правила поверхности): без пещер
r["initial_density_without_jaggedness"] = surface
s = json.dumps(r)
assert "overworld/" not in s, [x for x in s.split('"') if "overworld/" in x][:5]
sr = ns["surface_rule"]["sequence"]
above = next(r for r in sr if r.get("if_true", {}).get("type") == "minecraft:above_preliminary_surface")
above["then_run"]["sequence"][0:0] = own_rules
dump(ns, NS, "worldgen", "noise_settings", "telipailo.json")
dump({"firstOctave": -7, "amplitudes": [1.0]}, NS, "worldgen", "noise", "seed.json")

# --- тип измерения и пресет
dt = van("dimension_type/overworld.json")
dt.update(min_y=MIN_Y, height=HEIGHT, logical_height=HEIGHT)
dump(dt, NS, "dimension_type", "telipailo.json")
preset = van("worldgen/world_preset/normal.json")
preset["dimensions"]["minecraft:overworld"] = {
    "type": "tcw:telipailo",
    "generator": {"type": "minecraft:noise", "settings": "tcw:telipailo",
                  "biome_source": {"type": "tcw:telipailo", "preset": "minecraft:overworld"}}}
dump(preset, NS, "worldgen", "world_preset", "telipailo.json")
dump({"values": ["tcw:telipailo"]}, "minecraft", "tags", "worldgen", "world_preset", "normal.json")

# --- фичи
for f, step in [("caves", "lakes"), ("water", "lakes"), ("rock", "local_modifications"), ("snow", "top_layer_modification")]:
    dump({"type": f"tcw:{f}", "config": {}}, NS, "worldgen", "configured_feature", f + ".json")
    dump({"feature": f"tcw:{f}", "placement": []}, NS, "worldgen", "placed_feature", f + ".json")
dump({"type": "tcw:world", "dimension_type": "tcw:telipailo", "biomes": "#minecraft:is_overworld",
      "features": [{"step": "lakes", "feature": "tcw:caves"}, {"step": "lakes", "feature": "tcw:water"}, {"step": "local_modifications", "feature": "tcw:rock"},
                   {"step": "top_layer_modification", "feature": "tcw:snow"}],
      "remove_carvers": ["bettercaves:better_cave", "bettercaves:surface_cave"]},
     NS, "neoforge", "biome_modifier", "world.json")
# --- WWOO: «заплатки» из мёртвых кораллов, бетона и терракоты вне бесплодных земель (во всех мирах)
W = "wythers:"
patches = ["terrain/extended/dead_coral_stone", "terrain/extended/dead_coral_stone_2", "terrain/local/beach_mossy_stone",
           "terrain/local/disk_dead_coral_shore", "terrain/local/fungal_coral", "terrain/local/fungal_coral_2",
           "terrain/local/fungal_coral_mycelium", "terrain/local/fungal_fire_coral_shore", "terrain/local/fungal_fire_coral_shore_2",
           "terrain/local/fungal_powder", "vegetation/local/other/fungal_powder_spores",
           "terrain/feature/lake_salt", "terrain/local/crust_salt", "terrain/local/pamukkale_diorite",
           "terrain/local/replace_volcanics_flower_forest", "terrain/extended/onsen_calcite", "terrain/extended/onsen_deepslate",
           "terrain/extended/badlands_edge", "terrain/feature/danakil_desert_mounds",
           "terrain/local/danakil_orange", "terrain/local/danakil_red", "terrain/local/danakil_terracotta",
           "terrain/local/danakil_water", "terrain/local/danakil_yellow", "terrain/local/danakil_yellow_2",
           "terrain/local/thermal_savanna_brown", "terrain/local/thermal_savanna_gray", "terrain/local/thermal_savanna_mud",
           "terrain/local/thermal_savanna_soul", "terrain/local/thermal_steppe_brown", "terrain/local/thermal_steppe_gray",
           "terrain/local/thermal_steppe_mud", "terrain/local/thermal_steppe_soul", "terrain/local/thermal_taiga_disk_terracotta_orange",
           "terrain/local/thermal_taiga_disk_terracotta_white", "terrain/local/thermal_taiga_disk_terracotta_yellow",
           "decor/dense_steam", "decor/sparse_steam", "terrain/extended/gravelify_packed_mud",
           "terrain/extended/volcanic_flows_ore", "vegetation/extended/trees/forest_badlands"]
dump({"neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "wwoo"}],
      "type": "neoforge:remove_features", "biomes": "#minecraft:is_overworld",
      "features": [W + p for p in patches]},
     NS, "neoforge", "biome_modifier", "wwoo_no_patches.json")
print("ok")
