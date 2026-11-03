#!/usr/bin/env python3
# Выгрузка данных из jar новых модов в jars_new/ для генератора квестов.
# Запуск: python3 -I quest_builder/tools/extract_jars.py путь/к/*.jar
# Вложенные jar (META-INF/jarjar) тоже разбираются. Повторный запуск заменяет данные тех же модов.
import io
import json
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
J = os.path.join(ROOT, "jars_new")
SKIP_NS = {"minecraft", "c", "neoforge", "forge", "fabric", "create"}


def load(name, default):
    p = os.path.join(J, name)
    return json.load(open(p, encoding="utf-8")) if os.path.exists(p) else default


def walk(z, out):
    for n in z.namelist():
        if n.startswith("META-INF/jarjar/") and n.endswith(".jar"):
            walk(zipfile.ZipFile(io.BytesIO(z.read(n))), out)
        else:
            out.append((z, n))


def jread(z, n):
    try:
        return json.loads(re.sub(r"^﻿", "", z.read(n).decode("utf-8", "replace")))
    except Exception:
        return None


def ids_under(x, keys, acc):
    # собрать id предметов и теги из вложенных структур рецепта
    if isinstance(x, dict):
        for k in ("item", "id"):
            if isinstance(x.get(k), str) and ":" in x[k]:
                acc.append(x[k])
        if isinstance(x.get("tag"), str):
            acc.append("#" + x["tag"])
        for k, v in x.items():
            if k in keys or isinstance(v, (dict, list)):
                ids_under(v, keys, acc)
    elif isinstance(x, list):
        for v in x:
            ids_under(v, keys, acc)


OUT_KEYS = ("result", "results", "output", "outputs")


def recipe(d, path):
    if not isinstance(d, dict) or "type" not in d:
        return None
    outs, ins = [], []
    for k in OUT_KEYS:
        if k in d:
            ids_under(d[k], (), outs)
    for k, v in d.items():
        if k not in OUT_KEYS and k != "type":
            ids_under(v, (), ins)
    outs = list(dict.fromkeys(o for o in outs if not o.startswith("#")))
    ins = sorted(set(i for i in ins if i not in outs or i.startswith("#")))
    if not outs:
        return None
    return {"type": d["type"], "out": outs, "ins": ins, "file": path}


def main(jars):
    valid = set(load("_valid.json", []))
    models = load("_models.json", {})
    recipes = load("_recipes.json", [])
    tags = load("_tags.json", {})
    adv = load("_adv.json", {})
    world = load("_world.json", {"structures": {}, "biomes": {}})
    entries = []
    for p in jars:
        walk(zipfile.ZipFile(p), entries)
    nss = set()
    for z, n in entries:
        m = re.match(r"assets/([^/]+)/models/item/([^.]+)\.json$", n)
        if m and m.group(1) not in SKIP_NS:
            nss.add(m.group(1))
    print("моды:", " ".join(sorted(nss)))
    # старые данные этих модов убрать
    recipes = [r for r in recipes if r["file"].split("/")[1] not in nss]
    valid = {v for v in valid if v.split(":")[0] not in nss}
    items = {ns: set() for ns in nss}
    lang = {ns: {"en": {}, "ru": {}} for ns in nss}
    for z, n in entries:
        m = re.match(r"assets/([^/]+)/models/item/([^.]+)\.json$", n)
        if m and m.group(1) in nss:
            items[m.group(1)].add(m.group(2))
            continue
        m = re.match(r"assets/([^/]+)/lang/(en_us|ru_ru)\.json$", n)
        if m and m.group(1) in nss:
            d = jread(z, n)
            if isinstance(d, dict):
                lang[m.group(1)]["en" if m.group(2) == "en_us" else "ru"].update(
                    {k: v for k, v in d.items() if isinstance(v, str)})
            continue
        m = re.match(r"data/([^/]+)/recipes?/(.+)\.json$", n)
        if m and "/recipe/" in n:
            r = recipe(jread(z, n), n)
            if r and (m.group(1) in nss or any(o.split(":")[0] in nss for o in r["out"])):
                recipes.append(r)
            continue
        m = re.match(r"data/([^/]+)/tags/items?/(.+)\.json$", n)
        if m and "/tags/item/" in n:
            d = jread(z, n)
            if isinstance(d, dict):
                vals = [v if isinstance(v, str) else v.get("id") for v in d.get("values", [])]
                key = f"{m.group(1)}:{m.group(2)}"
                cur = [] if d.get("replace") else tags.get(key, [])
                tags[key] = sorted(set(cur) | {v for v in vals if v})
            continue
        m = re.match(r"data/([^/]+)/advancements?/(.+)\.json$", n)
        if m and m.group(1) in nss:
            d = jread(z, n)
            if isinstance(d, dict) and d.get("display"):
                t = d["display"].get("title")
                tk = t.get("translate") if isinstance(t, dict) else None
                lst = adv.setdefault(m.group(1), [])
                if not any(a[0] == m.group(2) for a in lst):
                    lst.append([m.group(2), tk, bool(d["display"].get("hidden"))])
            continue
        m = re.match(r"data/([^/]+)/worldgen/structure/(.+)\.json$", n)
        if m:
            lst = world["structures"].setdefault(m.group(1), [])
            if m.group(2) not in lst:
                lst.append(m.group(2))
    for ns in nss:
        ids = sorted(f"{ns}:{i}" for i in items[ns] if "/" not in i)
        valid |= set(ids)
        models[ns] = sorted(i for i in items[ns] if "/" not in i)
        json.dump(ids, open(os.path.join(J, ns + ".json"), "w", encoding="utf-8"), ensure_ascii=False)
        json.dump(lang[ns], open(os.path.join(J, ns + ".lang.json"), "w", encoding="utf-8"), ensure_ascii=False)
        print(f"  {ns}: предметов {len(ids)}, en {len(lang[ns]['en'])}, ru {len(lang[ns]['ru'])}")
    json.dump(sorted(valid), open(os.path.join(J, "_valid.json"), "w", encoding="utf-8"), ensure_ascii=False)
    json.dump(models, open(os.path.join(J, "_models.json"), "w", encoding="utf-8"), ensure_ascii=False)
    json.dump(recipes, open(os.path.join(J, "_recipes.json"), "w", encoding="utf-8"), ensure_ascii=False)
    json.dump(tags, open(os.path.join(J, "_tags.json"), "w", encoding="utf-8"), ensure_ascii=False)
    json.dump(adv, open(os.path.join(J, "_adv.json"), "w", encoding="utf-8"), ensure_ascii=False)
    json.dump(world, open(os.path.join(J, "_world.json"), "w", encoding="utf-8"), ensure_ascii=False)


if __name__ == "__main__":
    main(sys.argv[1:])
