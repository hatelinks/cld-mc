"""Наши правки поверх порта jjtm (общие для всех высот).

1. Пещеры: толще «корка» над пещерами, чтобы они реже выходили на поверхность.
   Если стоит YUNG's Better Caves, убираем его карвер surface_cave — он нарочно прорезает входы с поверхности.
3. Скорость без изменения рельефа: 2D-шум overworld_determiner считается раз на столбец (flat_cache),
   повторно используемые 3D-шумы пещер — раз на точку (cache_once).
2. Структуры: каждый наземный биом получает деревни, аванпосты, особняки, руины и т.п.
   по климату. Теги has_structure дописываются (replace: false), ванильные значения сохраняются.
Использование: tweaks.py <pack-dir>
"""
import json, os, sys

PACK = sys.argv[1]
DATA = os.path.join(PACK, "data", "minecraft")

# --- 1. пещеры ---------------------------------------------------------------
# overworld_combined: сплайн по overworld_surface. До 0.025 — сама поверхность, с 0.05 — пещеры.
# 1 единица плотности ≈ 1048 блоков, т.е. было ~26/52 блока, станет ~47/94.
CRUST = {0.025: 0.045, 0.05: 0.09}
p = os.path.join(DATA, "worldgen/density_function/overworld_combined.json")
d = json.load(open(p))
for pt in d["spline"]["points"]:
    pt["location"] = CRUST.get(pt["location"], pt["location"])
json.dump(d, open(p, "w"), indent=2)

mod_dir = os.path.join(PACK, "data", "jjthunder_to_the_max", "neoforge", "biome_modifier")
os.makedirs(mod_dir, exist_ok=True)
json.dump({
    "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "bettercaves"}],
    "type": "neoforge:remove_carvers",
    "biomes": "#minecraft:is_overworld",
    "carvers": "bettercaves:surface_cave",
}, open(os.path.join(mod_dir, "remove_bettercaves_surface_cave.json"), "w"), indent=2)

# --- 2. структуры ------------------------------------------------------------
SNOWY = ["snowy_plains", "ice_spikes", "snowy_taiga", "grove", "snowy_slopes", "frozen_peaks", "jagged_peaks", "snowy_beach"]
HOT = ["desert", "badlands", "eroded_badlands", "wooded_badlands"]
SAVANNA = ["savanna", "savanna_plateau", "windswept_savanna"]
TAIGA = ["taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "windswept_hills", "windswept_gravelly_hills",
         "windswept_forest", "stony_peaks"]
TEMPERATE = ["plains", "sunflower_plains", "meadow", "forest", "flower_forest", "birch_forest", "old_growth_birch_forest",
             "dark_forest", "cherry_grove", "jungle", "sparse_jungle", "bamboo_jungle", "swamp", "mangrove_swamp",
             "mushroom_fields", "stony_shore", "beach"]
JUNGLE = ["jungle", "sparse_jungle", "bamboo_jungle"]
SWAMP = ["swamp", "mangrove_swamp"]
LAND = SNOWY + HOT + SAVANNA + TAIGA + TEMPERATE

ADD = {
    "village_snowy": SNOWY,
    "village_desert": HOT,
    "village_savanna": SAVANNA,
    "village_taiga": TAIGA,
    "village_plains": TEMPERATE,
    "pillager_outpost": LAND,
    "woodland_mansion": LAND,
    "trail_ruins": LAND,
    "ruined_portal_standard": LAND,
    "igloo": SNOWY,
    "desert_pyramid": HOT + SAVANNA,
    "jungle_temple": JUNGLE + SWAMP,
    "swamp_hut": SWAMP,
    "mineshaft": LAND + ["deep_dark"],
    "buried_treasure": ["stony_shore", "snowy_beach"],
    "shipwreck_beached": ["stony_shore", "snowy_beach"],
}
tag_dir = os.path.join(DATA, "tags/worldgen/biome/has_structure")
os.makedirs(tag_dir, exist_ok=True)
for tag, biomes in ADD.items():
    vals = sorted(set("minecraft:" + b for b in biomes))
    json.dump({"replace": False, "values": vals}, open(os.path.join(tag_dir, tag + ".json"), "w"), indent=2)
# --- 3. кэш шумов (результат тот же, вычислений меньше) ----------------------
DF = os.path.join(DATA, "worldgen/density_function")
for name, cache in [("overworld_determiner", "minecraft:flat_cache"),  # y_scale 0: от Y не зависит
                    ("cave_barrier", "minecraft:cache_once"), ("cave_barrier_2", "minecraft:cache_once"),
                    ("old_caves_small", "minecraft:cache_once"), ("old_caves_med", "minecraft:cache_once"),
                    ("old_caves_large", "minecraft:cache_once")]:
    p = os.path.join(DF, name + ".json")
    d = json.load(open(p))
    assert d["type"] == "minecraft:noise", name
    if cache == "minecraft:flat_cache":
        assert d["y_scale"] == 0, name
    json.dump({"type": cache, "argument": d}, open(p, "w"), indent=2)

print(f"пещеры: корка {CRUST}; структуры: {len(ADD)} тегов, {len(set(LAND))} наземных биомов")
