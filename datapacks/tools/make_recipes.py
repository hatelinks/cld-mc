#!/usr/bin/env python3
# Генерирует рецепты датапаков cld_waystones и cld_dupes.
# Запуск: python3 -I datapacks/tools/make_recipes.py
import json
import os
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def cond(*mods):
    return [{"type": "neoforge:mod_loaded", "modid": m} for m in mods]


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def mcmeta(d, text):
    write(os.path.join(ROOT, d, "pack.mcmeta"), {"pack": {"pack_format": 48, "description": text}})


# ---------- путевые камни на механическом крафтере ----------
STONES = {"waystone": "minecraft:stone_bricks", "mossy_waystone": "minecraft:mossy_stone_bricks",
          "sandy_waystone": "minecraft:chiseled_sandstone", "blackstone_waystone": "minecraft:blackstone",
          "deepslate_waystone": "minecraft:deepslate", "end_stone_waystone": "minecraft:end_stone_bricks",
          "mud_bricks_waystone": "minecraft:mud_bricks", "prismarine_waystone": "minecraft:prismarine",
          "purpur_waystone": "minecraft:purpur_block", "red_nether_bricks_waystone": "minecraft:red_nether_bricks"}
W = os.path.join(ROOT, "cld_waystones")
shutil.rmtree(os.path.join(W, "data"), ignore_errors=True)
mcmeta("cld_waystones", "Путевые камни собираются на механическом крафтере Create")
for name, stone in STONES.items():
    # обычный рецепт верстака выключен
    write(os.path.join(W, "data", "waystones", "recipe", name + ".json"),
          {"neoforge:conditions": [{"type": "neoforge:false"}], "type": "minecraft:crafting_shaped",
           "pattern": ["#"], "key": {"#": {"item": "minecraft:stone"}}, "result": {"id": "waystones:" + name}})
    write(os.path.join(W, "data", "cld_waystones", "recipe", name + ".json"),
          {"neoforge:conditions": cond("waystones", "create"), "type": "create:mechanical_crafting", "accept_mirrored": True,
           "category": "misc", "pattern": [" S ", "SWS", "CGC", "OOO"],
           "key": {"S": {"item": stone}, "W": {"item": "waystones:warp_stone"}, "C": {"item": "create:andesite_casing"},
                   "G": {"item": "create:cogwheel"}, "O": {"item": "minecraft:obsidian"}},
           "result": {"count": 1, "id": "waystones:" + name}})

# ---------- обмен одинаковых предметов разных модов ----------
# каждая группа: предметы по кругу, a -> b -> c -> a (для двух предметов в обе стороны)
GROUPS = [
    ["farmersdelight:onion", "minecolonies:onion"],
    ["farmersdelight:tomato", "minecolonies:tomato"],
    ["farmersdelight:cabbage", "minecolonies:cabbage"],
    ["farmersdelight:rice", "minecolonies:rice"],
    ["farmersdelight:cooked_rice", "minecolonies:cooked_rice"],
    ["farmersdelight:fried_rice", "minecolonies:fried_rice"],
    ["farmersdelight:apple_pie", "minecolonies:apple_pie"],
    ["veggiesdelight:garlic", "minecolonies:garlic"],
    ["createfood:butter", "minecolonies:butter"],
    ["createfood:cheese_pizza", "minecolonies:cheese_pizza"],
    ["createfood:mushroom_pizza", "minecolonies:mushroom_pizza"],
    ["createfood:raw_pumpkin_pie", "minecolonies:raw_pumpkin_pie"],
    ["createfood:hamburger", "farmersdelight:hamburger"],
    ["createfood:pumpkin_pie_slice", "farmersdelight:pumpkin_pie_slice"],
    ["createfood:cake_base", "createaddition:cake_base"],
    ["createfood:marshmallow", "create_confectionery:marshmallow"],
    ["createfood:cocoa_powder", "create_confectionery:cocoa_powder"],
    ["createfood:bar_of_white_chocolate", "create_confectionery:bar_of_white_chocolate"],
    ["createfood:hot_chocolate_bottle", "create_confectionery:hot_chocolate_bottle"],
    ["supplementaries:rope", "farmersdelight:rope", "createornithopterglider:rope"],
    ["copycats:copycat_beam", "create_connected:copycat_beam"],
    ["copycats:copycat_box", "create_connected:copycat_box"],
    ["copycats:copycat_catwalk", "create_connected:copycat_catwalk"],
    ["copycats:copycat_vertical_step", "create_connected:copycat_vertical_step"],
    ["copycats:copycat_board", "create_connected:copycat_board"],
]
D = os.path.join(ROOT, "cld_dupes")
shutil.rmtree(os.path.join(D, "data"), ignore_errors=True)
mcmeta("cld_dupes", "Обмен одинаковых предметов разных модов в верстаке")
n = 0
for g in GROUPS:
    pairs = [(g[0], g[1]), (g[1], g[0])] if len(g) == 2 else [(g[i], g[(i + 1) % len(g)]) for i in range(len(g))]
    for a, b in pairs:
        name = a.replace(":", "_") + "_to_" + b.split(":")[0]
        write(os.path.join(D, "data", "cld_dupes", "recipe", name + ".json"),
              {"neoforge:conditions": cond(a.split(":")[0], b.split(":")[0]), "type": "minecraft:crafting_shapeless",
               "category": "misc", "group": "cld_dupes_" + g[0].split(":")[1],
               "ingredients": [{"item": a}], "result": {"count": 1, "id": b}})
        n += 1
print("путевых камней", len(STONES), "обменов", n)
