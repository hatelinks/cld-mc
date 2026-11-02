#!/usr/bin/env python3
"""Генератор FTB Quests (1.21.1, NeoForge) из JSON-описания глав (data/*.json).

Особенности:
- ID квестов сохраняются (поле id), новые считаются как хеш главы и ключа k;
- все ID без старшего бита (первая hex-цифра 0..7), иначе мод показывает «Unnamed»;
- задачи на предметы не забирают предметы (consume_items: false) и засчитываются сами;
- «любой из вариантов» делается через Smart Filter из FTB Filter System;
- предметы чужих модов сверяются со списками из jar (jars/*.json).
"""
import collections
import glob
import hashlib
import json
import os
import re
import shutil
import sys
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(ROOT), "out")
QDIR = os.path.join(OUT, "ftbquests", "quests")
GROUP_ORDER = ["", "Эра механизмов", "Небо и техника", "Хозяйство и путешествия", "Мистическое земледелие", "Жизнь колонии"]
# стиль названий групп: цвет, жирный, подчёркнутый (как в aoa)
GROUP_STYLE = {"Эра механизмов": "&e&l&n", "Небо и техника": "&b&l&n", "Хозяйство и путешествия": "&6&l&n",
               "Мистическое земледелие": "&d&l&n", "Жизнь колонии": "&a&l&n"}
# таблицы наград: quest_builder/reward_tables.json
TABLES = json.load(open(os.path.join(ROOT, "reward_tables.json"), encoding="utf-8"))


def hid(*parts):
    h = hashlib.sha1("|".join(parts).encode("utf-8")).hexdigest()[:16].upper()
    return str(int(h[0], 16) & 7) + h[1:]


def esc(s):
    return s.replace("\\", "\\\\").replace('"', '\\"')


# ---------- известные ID ----------
JAR_IDS = set()
for p in glob.glob(os.path.join(ROOT, "jars", "*.json")) + glob.glob(os.path.join(ROOT, "jars_new", "*.json")):
    if os.path.basename(p).startswith("_") or p.endswith(".lang.json"):
        continue
    JAR_IDS |= set(json.load(open(p, encoding="utf-8")))
USE_FILTER = os.path.exists(os.path.join(ROOT, "USE_FILTER"))  # FTB Filter System в сборке нет
TRUSTED = set(json.load(open(os.path.join(ROOT, "trusted.json"), encoding="utf-8")))
JAR_MODS = {i.split(":")[0] for i in JAR_IDS}


VALID = set(json.load(open(os.path.join(ROOT, "jars_new", "_valid.json"), encoding="utf-8")))


CHECK_TITLES = {
    "create:glass_fluid_pipe": "Сделать стеклянную трубу: гаечный ключ по трубе",
    "create:encased_fluid_pipe": "Обшить трубу медным корпусом (ПКМ корпусом по трубе)",
    "create:pulley_magnet": "Поставить канатный шкив — магнит появится сам",
    "create:small_bogey": "Поставить малую тележку: железнодорожный корпус по рельсам",
    "create:large_bogey": "Поставить большую тележку (гаечный ключ по малой)",
    "create_connected:linked_lever": "Прикрепить передатчик сигнала к рычагу",
    "create_connected:linked_analog_lever": "Прикрепить передатчик сигнала к аналоговому рычагу",
    "create_connected:linked_oak_button": "Прикрепить передатчик сигнала к кнопке",
    "createaddition:accumulator": "Поставить аккумулятор (модульный)",
    "createaddition:liquid_blaze_burner": "Сделать жидкую горелку: ПКМ особым предметом по горелке",
    "sophisticatedstorage:storage_connector": "Соединить хранилища с контроллером",
    "storagedrawers:tape": "Упаковать ящик лентой",
}


def known(rid):
    if rid.startswith("minecraft:"):
        return True
    return rid in JAR_IDS and rid in VALID


# ---------- загрузка ----------
chapters = []
for p in glob.glob(os.path.join(ROOT, "data", "*.json")):
    chapters.append(json.load(open(p, encoding="utf-8")))
chapters.sort(key=lambda c: (GROUP_ORDER.index(c["group"]) if c["group"] in GROUP_ORDER else 99, c.get("order", 0), c["key"]))

errors, doubts, notes = [], [], []
qid = {}
for ch in chapters:
    for q in ch["quests"]:
        if not q.get("id"):
            if not q.get("k"):
                errors.append(f'{ch["key"]}: квест без id и k: {q.get("title")}')
                continue
            q["id"] = hid("quest", ch["key"], q["k"])
        qid[f'{ch["key"]}/{q["k"]}' if q.get("k") else q["id"]] = q["id"]
        qid[q["id"]] = q["id"]


def resolve(ch, ref):
    if ref in qid:
        return qid[ref]
    full = f'{ch["key"]}/{ref}'
    if full in qid:
        return qid[full]
    errors.append(f'{ch["key"]}: зависимость не найдена: {ref}')
    return None


sys.path.insert(0, os.path.join(ROOT, "tools"))
import layout  # noqa: E402
for ch in chapters:
    if ch.get("layout", "auto") == "auto":
        layout.apply(ch, qid)

lang = {}
bg_layout = {}
used_tables = set()
group_ids = {}
chapter_files = {}


def item_obj(rid, comps=None):
    if comps:
        inner = ", ".join(f'"{k}": "{esc(v)}"' for k, v in comps.items())
        return f'{{ count: 1, id: "{rid}", components: {{ {inner} }} }}'
    return f'{{ count: 1, id: "{rid}" }}'


for ch in chapters:
    cid = ch.get("id") or hid("chapter", ch["key"])
    gid = ""
    if ch["group"]:
        gid = group_ids.setdefault(ch["group"], hid("group", ch["group"]))
        lang[f"chapter_group.{gid}.title"] = GROUP_STYLE.get(ch["group"], "") + ch["group"]
    lang[f"chapter.{cid}.title"] = ch.get("color", "") + ch["title"]
    if ch.get("subtitle"):
        lang[f"chapter.{cid}.chapter_subtitle"] = ch["subtitle"]

    seen_pos = {}
    qtexts = []
    for q in ch["quests"]:
        i = q["id"]
        tag = f'{ch["key"]}/{q.get("k", i)}'
        pos = (q["x"], q["y"])
        if pos in seen_pos:
            errors.append(f"{tag}: позиция {pos} занята {seen_pos[pos]}")
        seen_pos[pos] = tag
        if q.get("note"):
            notes.append((ch["title"], q["title"], q["note"]))

        lang[f"quest.{i}.title"] = q["title"]
        if q.get("sub"):
            lang[f"quest.{i}.quest_subtitle"] = q["sub"]
        if q.get("desc"):
            lang[f"quest.{i}.quest_desc"] = q["desc"]

        tasks_s = []
        for n, t in enumerate(q["tasks"]):
            tid = hid("task", i, str(n))
            kind = t["type"]
            head = f'\t\t\t\t{{\n\t\t\t\t\tid: "{tid}"\n'
            if kind == "item":
                rid = t["id"]
                if known(rid):
                    tasks_s.append(head + f'\t\t\t\t\ttype: "item"\n\t\t\t\t\titem: {item_obj(rid)}\n'
                                   f'\t\t\t\t\tcount: {t["count"]}L\n\t\t\t\t\tconsume_items: false\n\t\t\t\t}}')
                else:
                    doubts.append((ch["title"], q["title"], rid))
                    lang[f"task.{tid}.title"] = CHECK_TITLES.get(rid, "Получить: " + q["title"])
                    tasks_s.append(head + '\t\t\t\t\ttype: "checkmark"\n\t\t\t\t}')
            elif kind == "any" and not USE_FILTER:
                bad = [r for r in t["ids"] if not known(r)]
                if bad:
                    errors.append(f"{tag}: неизвестные ID в группе: {bad}")
                lang[f"task.{tid}.title"] = t["title"]
                tasks_s.append(head + f'\t\t\t\t\ttype: "item"\n\t\t\t\t\titem: {item_obj(t["ids"][0])}\n'
                               f'\t\t\t\t\tcount: {t["count"]}L\n\t\t\t\t\tconsume_items: false\n\t\t\t\t}}')
            elif kind == "any":
                bad = [r for r in t["ids"] if not known(r)]
                if bad:
                    errors.append(f"{tag}: неизвестные ID в группе: {bad}")
                flt = "or(" + ",".join(f"item({r})" for r in t["ids"]) + ")"
                lang[f"task.{tid}.title"] = t["title"]
                tasks_s.append(head + '\t\t\t\t\ttype: "item"\n'
                               f'\t\t\t\t\titem: {item_obj("ftbfiltersystem:smart_filter", {"ftbfiltersystem:filter": flt})}\n'
                               f'\t\t\t\t\tcount: {t["count"]}L\n\t\t\t\t\tconsume_items: false\n\t\t\t\t}}')
            elif kind == "check":
                lang[f"task.{tid}.title"] = t["title"]
                tasks_s.append(head + '\t\t\t\t\ttype: "checkmark"\n\t\t\t\t}')
            elif kind == "kill":
                lang[f"task.{tid}.title"] = t.get("title", "")
                tasks_s.append(head + f'\t\t\t\t\ttype: "kill"\n\t\t\t\t\tentity: "{t["id"]}"\n\t\t\t\t\tvalue: {t["count"]}L\n\t\t\t\t}}')
            elif kind in ("biome", "structure", "advancement", "dimension"):
                if t.get("title"):
                    lang[f"task.{tid}.title"] = t["title"]
                crit = '\t\t\t\t\tcriterion: ""\n' if kind == "advancement" else ""
                tasks_s.append(head + f'\t\t\t\t\ttype: "{kind}"\n\t\t\t\t\t{kind}: "{t["id"]}"\n' + crit + '\t\t\t\t}')
            elif kind == "stat":
                lang[f"task.{tid}.title"] = t.get("title", "")
                tasks_s.append(head + f'\t\t\t\t\ttype: "stat"\n\t\t\t\t\tstat: "{t["id"]}"\n\t\t\t\t\tvalue: {t["count"]}\n\t\t\t\t}}')
            else:
                errors.append(f"{tag}: неизвестный тип задачи {kind}")

        rewards_s = []
        for n, r in enumerate(q.get("rewards", [])):
            rid_ = hid("reward", i, str(n))
            if r["type"] == "xp":
                rewards_s.append(f'\t\t\t\t{{\n\t\t\t\t\tid: "{rid_}"\n\t\t\t\t\ttype: "xp"\n\t\t\t\t\txp: {r["amount"]}\n\t\t\t\t}}')
            elif r["type"] == "xp_levels":
                rewards_s.append(f'\t\t\t\t{{\n\t\t\t\t\tid: "{rid_}"\n\t\t\t\t\ttype: "xp_levels"\n\t\t\t\t\txp_levels: {r["amount"]}\n\t\t\t\t}}')
            elif r["type"] in ("random", "choice", "loot", "all_table"):
                if r["table"] not in TABLES:
                    errors.append(f'{tag}: нет таблицы наград {r["table"]}')
                    continue
                used_tables.add(r["table"])
                tnum = int(hid("table", r["table"]), 16)
                ex = "\t\t\t\t\texclude_from_claim_all: true\n" if r["type"] == "random" else ""
                rewards_s.append(f'\t\t\t\t{{\n\t\t\t\t\tid: "{rid_}"\n{ex}\t\t\t\t\ttable_id: {tnum}L\n\t\t\t\t\ttype: "{r["type"]}"\n\t\t\t\t}}')
            elif r["type"] == "item":
                if not known(r["id"]):
                    errors.append(f'{tag}: награда с неизвестным ID {r["id"]}')
                rewards_s.append(f'\t\t\t\t{{\n\t\t\t\t\tid: "{rid_}"\n\t\t\t\t\ttype: "item"\n'
                                 f'\t\t\t\t\titem: {item_obj(r["id"])}\n\t\t\t\t\tcount: {r["count"]}\n\t\t\t\t}}')

        deps = [resolve(ch, d) for d in q.get("deps", [])]
        deps = [d for d in deps if d]
        s = f'\t\t{{\n\t\t\tid: "{i}"\n\t\t\tx: {float(q["x"])}d\n\t\t\ty: {float(q["y"])}d\n'
        ic = q.get("icon")
        if q.get("icon_tex"):
            s += f'\t\t\ticon: {{ id: "ftbquests:custom_icon", components: {{ "ftbquests:icon": "{q["icon_tex"]}" }} }}\n'
        elif ic:
            if known(ic):
                s += f'\t\t\ticon: {{ id: "{ic}" }}\n'
            else:
                doubts.append((ch["title"], q["title"], ic + " (иконка)"))
        if q.get("optional"):
            s += "\t\t\toptional: true\n"
        if q.get("shape"):
            s += f'\t\t\tshape: "{q["shape"]}"\n'
        if q.get("size"):
            s += f'\t\t\tsize: {float(q["size"])}d\n'
        if q.get("hide_lines"):
            s += "\t\t\thide_dependency_lines: true\n"
        # прочие поля квеста как есть: hide_until_deps_visible, invisible_until_tasks и т.п.
        for fk, fv in q.get("flags", {}).items():
            fv = "true" if fv is True else "false" if fv is False else f'"{fv}"' if isinstance(fv, str) else fv
            s += f"\t\t\t{fk}: {fv}\n"
        s += "\t\t\ttasks: [\n" + "\n".join(tasks_s) + "\n\t\t\t]\n"
        if rewards_s:
            s += "\t\t\trewards: [\n" + "\n".join(rewards_s) + "\n\t\t\t]\n"
        if deps:
            s += "\t\t\tdependencies: [" + " ".join(f'"{d}"' for d in deps) + "]\n"
        s += "\t\t}"
        qtexts.append(s)

    icon = ch.get("icon", "minecraft:book")
    if not known(icon):
        doubts.append((ch["title"], "(глава)", icon + " (иконка главы)"))
        icon = "minecraft:book"
    order = chapters.index(ch)
    body = f'{{\n\tid: "{cid}"\n\tgroup: "{gid}"\n\torder_index: {order}\n\tfilename: "{ch["key"]}"\n'
    body += f'\tprogression_mode: "{ch.get("progression_mode", "linear")}"\n'
    # картинки-украшения рядом с первым квестом главы (координаты известны только после раскладки)
    if ch.get("deco") and ch["quests"]:
        r0 = ch["quests"][0]
        for im in ch["deco"]:
            ch.setdefault("images", []).append({"image": im[0], "x": r0["x"] + im[1], "y": r0["y"] + im[2],
                                                "width": im[3], "height": im[4]})
    # тематический фон под всей главой: картинку рисует resourcepack/make_backgrounds.py по bg_layout.json
    if ch["quests"] and not ch.get("no_bg"):
        xs0 = [q["x"] - float(q.get("size", 1)) / 2 for q in ch["quests"]] + [i["x"] - i["width"] / 2 for i in ch.get("images", [])]
        xs1 = [q["x"] + float(q.get("size", 1)) / 2 for q in ch["quests"]] + [i["x"] + i["width"] / 2 for i in ch.get("images", [])]
        ys0 = [q["y"] - float(q.get("size", 1)) / 2 for q in ch["quests"]] + [i["y"] - i["height"] / 2 for i in ch.get("images", [])]
        ys1 = [q["y"] + float(q.get("size", 1)) / 2 + (0.6 if float(q.get("size", 1)) >= 1.5 else 0) for q in ch["quests"]] + [i["y"] + i["height"] / 2 for i in ch.get("images", [])]
        bx0, bx1, by0, by1 = min(xs0) - 1.5, max(xs1) + 1.5, min(ys0) - 3.0, max(ys1) + 1.5
        bx0, bx1, by0, by1 = [round(v * 2) / 2 for v in (bx0, bx1, by0, by1)]
        bg_layout[ch["key"]] = {
            "box": [bx0, by0, bx1, by1], "group": ch["group"], "title": ch["title"], "subtitle": ch.get("subtitle", ""),
            "color": ch.get("color", ""), "theme": ch.get("theme", ""),
            "quests": [{"id": q["id"], "x": q["x"], "y": q["y"], "size": float(q.get("size", 1)), "shape": q.get("shape", "circle"),
                        "title": q["title"] if float(q.get("size", 1)) >= 1.5 else "", "optional": bool(q.get("optional"))}
                       for q in ch["quests"]],
            "edges": [[qid.get(d) or qid.get(f'{ch["key"]}/{d}'), q["id"]] for q in ch["quests"] for d in q.get("deps", [])
                      if (qid.get(d) or qid.get(f'{ch["key"]}/{d}')) in {x["id"] for x in ch["quests"]}],
        }
        ch["images"] = [{"image": f"cldquests:textures/quests/bg/{ch['key']}.png", "x": (bx0 + bx1) / 2, "y": (by0 + by1) / 2,
                         "width": bx1 - bx0, "height": by1 - by0, "order": -10}] + ch.get("images", [])
    if ch.get("images"):
        body += "\timages: [\n"
        for n, im in enumerate(ch["images"]):
            body += (f'\t\t{{\n\t\t\tid: "{hid("image", ch["key"], str(n))}"\n\t\t\timage: "{im["image"]}"\n'
                     f'\t\t\tx: {float(im["x"])}d\n\t\t\ty: {float(im["y"])}d\n\t\t\twidth: {float(im["width"])}d\n'
                     f'\t\t\theight: {float(im["height"])}d\n\t\t\trotation: 0.0d\n'
                     + (f'\t\t\torder: {im["order"]}\n' if "order" in im else "") + '\t\t}\n')
        body += "\t]\n"
    body += f'\ticon: {{ id: "{icon}" }}\n\tquests: [\n' + "\n".join(qtexts) + "\n\t]\n}\n"
    chapter_files[ch["key"]] = (cid, body)

# ---------- уникальность и ID ----------
allids = []
for cid, body in chapter_files.values():
    allids += re.findall(r'id: "([0-9A-F]{16})"', body)
for k, v in collections.Counter(allids).items():
    if v > 1:
        errors.append(f"повтор ID {k}")
if any(i[0] in "89ABCDEF" for i in allids) or any(g[0] in "89ABCDEF" for g in group_ids.values()):
    errors.append("есть ID со старшим битом")

# ---------- циклы ----------
graph = {}
for ch in chapters:
    for q in ch["quests"]:
        graph[q["id"]] = [resolve(ch, d) for d in q.get("deps", [])]
state = {}


def dfs(n, path):
    if state.get(n) == 1:
        errors.append("цикл зависимостей: " + " -> ".join(path + [n]))
        return
    if state.get(n) == 2 or n not in graph:
        return
    state[n] = 1
    for m in graph[n]:
        if m:
            dfs(m, path + [n])
    state[n] = 2


for n in list(graph):
    dfs(n, [])

# ---------- запись ----------
if os.path.isdir(os.path.join(OUT, "ftbquests")):
    shutil.rmtree(os.path.join(OUT, "ftbquests"))
os.makedirs(os.path.join(QDIR, "chapters"))
os.makedirs(os.path.join(QDIR, "lang"))
for key, (cid, body) in chapter_files.items():
    open(os.path.join(QDIR, "chapters", key + ".snbt"), "w", encoding="utf-8").write(body)
# таблицы наград, только те, что где-то используются
os.makedirs(os.path.join(QDIR, "reward_tables"))
for n, key in enumerate(sorted(used_tables)):
    t = TABLES[key]
    tid = hid("table", key)
    lang[f"reward_table.{tid}.title"] = t["title"]
    rows = []
    for m, (rid, cnt) in enumerate(t["items"]):
        if not known(rid):
            errors.append(f"таблица {key}: неизвестный предмет {rid}")
            continue
        rows.append(f'\t\t{{\n\t\t\tcount: {cnt}\n\t\t\tid: "{hid("trow", key, str(m))}"\n\t\t\titem: {item_obj(rid)}\n\t\t}}')
    for m, xp in enumerate(t.get("xp", [])):
        rows.append(f'\t\t{{\n\t\t\tid: "{hid("txp", key, str(m))}"\n\t\t\ttype: "xp"\n\t\t\txp: {xp}\n\t\t}}')
    open(os.path.join(QDIR, "reward_tables", key + ".snbt"), "w", encoding="utf-8").write(
        f'{{\n\tid: "{tid}"\n\tloot_size: 1\n\torder_index: {n}\n\trewards: [\n' + "\n".join(rows) + "\n\t]\n}\n")
with open(os.path.join(QDIR, "chapter_groups.snbt"), "w", encoding="utf-8") as f:
    f.write("{\n\tchapter_groups: [\n")
    for gid in group_ids.values():
        f.write(f'\t\t{{\n\t\t\tid: "{gid}"\n\t\t}}\n')
    f.write("\t]\n}\n")


def lang_text():
    out = "{\n"
    for k, v in lang.items():
        if isinstance(v, list):
            flat = []
            for x in v:
                flat += [p.strip() for p in str(x).replace("\r", "").split("\n") if p.strip()]
            out += f"\t{k}: [" + ", ".join(f'"{esc(x)}"' for x in flat) + "]\n"
        else:
            out += f'\t{k}: "{esc(" ".join(str(v).split()))}"\n'
    out += "}\n"
    # проверка: каждая строка — ровно одна запись, без переносов внутри строк
    for n, line in enumerate(out.split("\n")[1:-2], 2):
        if not re.match(r'^\t[a-z_]+\.[0-9A-F]{16}\.[a-z_]+: (".*"|\[.*\])$', line):
            errors.append(f"lang: строка {n} повреждена: {line[:80]}")
    return out


for name in ("ru_ru.snbt", "en_us.snbt"):
    open(os.path.join(QDIR, "lang", name), "w", encoding="utf-8").write(lang_text())

for key, (cid, body) in chapter_files.items():
    stripped = re.sub(r'"(?:\\.|[^"\\])*"', '""', body)
    for a, b in ("{}", "[]"):
        if stripped.count(a) != stripped.count(b):
            errors.append(f"{key}: не сходятся скобки {a}{b}")

# ---------- чеклист ----------
with open(os.path.join(OUT, "checklist.md"), "w", encoding="utf-8") as f:
    f.write("# Чеклист сомнений\n\n## Предметы, которых не удалось подтвердить\n\n")
    if doubts:
        f.write("Эти задачи заменены на ручное подтверждение (checkmark).\n\n| Глава | Квест | ID |\n|---|---|---|\n")
        for d in doubts:
            f.write(f"| {d[0]} | {d[1]} | `{d[2]}` |\n")
    else:
        f.write("Таких предметов нет.\n")
    f.write("\n## Места, где описание написано по общим знаниям о моде\n\n")
    if notes:
        f.write("Проверьте эти описания в игре. Если нашли неточность, скажите, и я поправлю.\n\n| Глава | Квест | Что проверить |\n|---|---|---|\n")
        for n in notes:
            f.write(f"| {n[0]} | {n[1]} | {n[2]} |\n")
    else:
        f.write("Нет.\n")
    f.write("\n## Квесты с вариантами\n\n")
    if USE_FILTER:
        f.write("Эти квесты принимают любой вариант через Smart Filter из FTB Filter System. Проверьте один такой квест в игре.\n\n")
    else:
        f.write("FTB Filter System в сборке нет, поэтому квест «любой вариант» засчитывает только первый (базовый) вариант — он указан в описании квеста. "
                "Если поставить FTB Filter System, сборщик может переключиться на режим «любой вариант».\n\n")
    for ch in chapters:
        for q in ch["quests"]:
            if any(t["type"] == "any" for t in q["tasks"]):
                f.write(f'- {ch["title"]}: {q["title"]}\n')

total = sum(len(c["quests"]) for c in chapters)
print(f"глав: {len(chapters)}, квестов: {total}, сомнений: {len(doubts)}, примечаний: {len(notes)}")
for e in errors:
    print("ОШИБКА:", e)
if errors:
    sys.exit(1)

json.dump(bg_layout, open(os.path.join(OUT, "bg_layout.json"), "w", encoding="utf-8"), ensure_ascii=False)
zpath = os.path.join(OUT, "ftbquests_config.zip")
with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
    for base, _, files in os.walk(os.path.join(OUT, "ftbquests")):
        for fn in files:
            p = os.path.join(base, fn)
            z.write(p, os.path.join("config", os.path.relpath(p, OUT)))
print("архив:", zpath)
