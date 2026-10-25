"""Справочник по модам: русские названия, подсказки, рецепты, теги.

Данные берутся из jars_new/*.json (извлечены из jar-файлов сборки).
"""
import collections
import glob
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
J = os.path.join(ROOT, "jars_new")

IDS = set()
LANG_RU, LANG_EN = {}, {}
for p in glob.glob(os.path.join(J, "*.json")):
    b = os.path.basename(p)
    if b.startswith("_"):
        continue
    if b.endswith(".lang.json"):
        d = json.load(open(p, encoding="utf-8"))
        LANG_RU.update(d["ru"])
        LANG_EN.update(d["en"])
    else:
        IDS |= set(json.load(open(p)))
for p in glob.glob(os.path.join(ROOT, "jars", "*.json")):
    IDS |= set(json.load(open(p)))

VALID = set(json.load(open(os.path.join(J, "_valid.json"))))
IDS = {i for i in IDS if i in VALID}  # только предметы, у которых есть модель предмета (реально существуют)

RECIPES = json.load(open(os.path.join(J, "_recipes.json")))
TAGS = {k: v for k, v in json.load(open(os.path.join(J, "_tags.json"))).items()}

# рецепты по результату и по ингредиенту
BY_OUT = collections.defaultdict(list)
USED_IN = collections.defaultdict(set)


def expand(ing, depth=0):
    if not ing.startswith("#"):
        return {ing}
    if depth > 4:
        return set()
    out = set()
    for v in TAGS.get(ing[1:], []):
        out |= expand(v, depth + 1)
    return out


for r in RECIPES:
    ins, dep = set(), set()
    for i in r["ins"]:
        e = expand(i)
        ins |= e
        if not i.startswith("#") or len(e) <= 2:
            dep |= e
    r["ins_x"] = sorted(ins)
    r["ins_d"] = sorted(dep)
    for o in r["out"]:
        BY_OUT[o].append(r)
        for i in ins:
            if i != o:
                USED_IN[i].add(o)

COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
          "cyan", "purple", "blue", "brown", "green", "red", "black"]
WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo",
         "crimson", "warped", "pale_oak"]


def _raw_name(rid):
    ns, p = rid.split(":", 1)
    for d in (LANG_RU, LANG_EN):
        for t in ("item", "block"):
            k = f"{t}.{ns}.{p}"
            if k in d:
                return d[k]
    return None


def name(rid):
    n = _raw_name(rid)
    if n:
        return re.sub(r"§.", "", n)
    if rid.startswith("minecraft:"):
        return VANILLA.get(rid.split(":")[1], rid.split(":")[1].replace("_", " "))
    return rid.split(":")[1].replace("_", " ").capitalize()


def is_ru(rid):
    ns, p = rid.split(":", 1)
    return f"item.{ns}.{p}" in LANG_RU or f"block.{ns}.{p}" in LANG_RU


def _clean(s):
    s = re.sub(r"§.", "", s)
    s = re.sub(r"_([^_]+)_", r"&e\1&r", s)
    return s.strip()


def tooltip(rid):
    """Подсказка мода в стиле Create: summary + пары condition/behaviour."""
    ns, p = rid.split(":", 1)
    lines = []
    for t in ("item", "block"):
        base = f"{t}.{ns}.{p}.tooltip"
        d = LANG_RU if (base + ".summary") in LANG_RU or base in LANG_RU else LANG_EN
        if (base + ".summary") in d:
            lines.append(("summary", _clean(d[base + ".summary"])))
            for n in range(1, 6):
                c, b = d.get(f"{base}.condition{n}"), d.get(f"{base}.behaviour{n}")
                if b:
                    lines.append(("use", (_clean(c) + ": " if c else "") + _clean(b)))
                ctl, act = d.get(f"{base}.control{n}"), d.get(f"{base}.action{n}")
                if act:
                    lines.append(("use", (_clean(ctl) + ": " if ctl else "") + _clean(act)))
        elif base in d and "%" not in d[base]:
            lines.append(("summary", _clean(d[base])))
        for n in range(0, 4):
            k = f"{base}{n}" if n else None
            if k and k in d and "%" not in d[k]:
                lines.append(("summary", _clean(d[k])))
    return lines


RTYPE = {
    "crafting_shaped": "верстак", "crafting_shapeless": "верстак", "stonecutting": "камнерез",
    "smelting": "печь", "blasting": "плавильная печь", "smoking": "коптильня",
    "campfire_cooking": "костёр", "smithing_transform": "кузнечный стол",
    "create:milling": "жернова", "create:crushing": "дробильные колёса", "create:deploying": "автономный активатор",
    "create:filling": "заправщик", "create:item_application": "применение предмета к блоку (ПКМ)",
    "create:mixing": "механический смешиватель над чашей", "create:splashing": "промывка вентилятором через воду",
    "create:pressing": "механический пресс", "create:cutting": "механическая пила",
    "create:mechanical_crafting": "механический крафтер", "create:haunting": "вентилятор через огонь душ",
    "create:compacting": "механический пресс над чашей", "create:sequenced_assembly": "последовательная сборка",
    "create:emptying": "опустошитель", "create:sandpaper_polishing": "наждачная бумага",
    "mysticalagriculture:infusion": "алтарь инфузии", "mysticalagriculture:awakening": "алтарь пробуждения",
    "mysticalagriculture:reprocessor": "переработчик семян",
    "farmersdelight:cutting": "разделочная доска", "farmersdelight:cooking": "кухонный котёл",
    "createaddition:charging": "зарядник", "createaddition:rolling": "прокатный стан",
    "create_aquatic_ambitions:channeling": "механический источник", "create_dragons_plus:ending": "вентилятор через драконье дыхание",
    "create_dragons_plus:freezing": "вентилятор через снег", "create_enchantment_industry:grinding": "механический точильщик",
    "sophisticatedstorage:storage_tier_upgrade": "верстак (улучшение хранилища)",
    "sophisticatedcore:upgrade_next_tier": "верстак (следующий уровень)",
    "cucumber:shaped_transfer_damage": "верстак", "cucumber:shaped_no_mirror": "верстак",
    "cucumber:shaped_transfer_components": "верстак",
}
SKIP_TYPES = ("tconstruct:", "immersiveengineering:", "mekanism:")


def rtype(t):
    t2 = t.replace("minecraft:", "")
    return RTYPE.get(t, RTYPE.get(t2, None))


def own_recipes(rid):
    ns = rid.split(":")[0]
    out = []
    for r in BY_OUT.get(rid, []):
        if r["type"].startswith(SKIP_TYPES) or rid in r["ins_x"]:
            continue
        rns = r["file"].split("/")[1]
        if rns != ns and not (ns == "minecraft"):
            continue
        out.append(r)
    return out


def best_recipe(rid):
    rs = own_recipes(rid)
    if not rs:
        return None
    ns = rid.split(":")[0]
    foreign = lambda r: sum(1 for i in r["ins_d"] if i.split(":")[0] not in (ns, "minecraft", "c"))
    return sorted(rs, key=lambda r: (rtype(r["type"]) is None, foreign(r), len(r["ins"])))[0]


def how_to_get(rid, limit=5):
    r = best_recipe(rid)
    if not r:
        return None
    ins = []
    for i in r["ins"]:
        if i.startswith("#"):
            v = [x for x in TAGS.get(i[1:], []) if not x.startswith("#")]
            ins.append(name(v[0]) if v else None)
        else:
            ins.append(name(i))
    ins = [i for i in ins if i]
    where = rtype(r["type"]) or "особый рецепт"
    txt = where
    if ins:
        txt += ": " + ", ".join(dict.fromkeys(ins[:limit]))
    return txt


def used_for(rid, pool=None, limit=5):
    us = [u for u in sorted(USED_IN.get(rid, ())) if (pool is None or u in pool) and (is_ru(u) or u.split(":")[1] in VANILLA)]
    return [name(u) for u in us[:limit]]


def ingredients(rid):
    """Все ID, участвующие в рецептах этого предмета (с раскрытием тегов)."""
    s = set()
    r = best_recipe(rid)
    if r:
        s |= set(r["ins_d"])
    s.discard(rid)
    return s


import sys as _s
_s.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from vanilla_ru import V as VANILLA


ADV = {}
try:
    for ns, lst in json.load(open(os.path.join(J, "_adv.json"), encoding="utf-8")).items():
        for path, tkey, hidden in lst:
            ADV[f"{ns}:{path}"] = (tkey, hidden)
except FileNotFoundError:
    pass


def adv_text(advid):
    tkey, _ = ADV.get(advid, (None, False))
    if not tkey:
        return None, None
    t = LANG_RU.get(tkey) or LANG_EN.get(tkey)
    base = tkey[:-6] if tkey.endswith(".title") else tkey
    d = None
    for k in (base + ".desc", base + ".description", tkey + ".desc", tkey.replace(".title", ".description")):
        d = LANG_RU.get(k) or LANG_EN.get(k)
        if d:
            break
    clean = lambda x: re.sub(r"§.", "", x).split("\n")[0].strip() if x else x
    return clean(t), clean(d)
