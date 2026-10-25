import sys, os, re, json, glob
sys.path.insert(0, os.path.dirname(__file__))
import snbt
src, dst = sys.argv[1], sys.argv[2]
q = os.path.join(src, "config/ftbquests/quests")
lang = snbt.parse(open(q + "/lang/ru_ru.snbt", encoding="utf-8").read())
groups = {}
for k, v in lang.items():
    m = re.fullmatch(r"chapter_group\.(\w+)\.title", k)
    if m:
        groups[m.group(1)] = v
total = 0
for f in sorted(glob.glob(q + "/chapters/*.snbt")):
    ch = snbt.parse(open(f, encoding="utf-8").read())
    cid = ch["id"]
    out = {
        "key": ch["filename"], "id": cid, "title": lang["chapter.%s.title" % cid],
        "subtitle": lang.get("chapter.%s.chapter_subtitle" % cid, ""),
        "group": groups.get(ch["group"], ""), "icon": ch["icon"]["id"], "order": ch["order_index"], "quests": [],
    }
    for qq in ch["quests"]:
        qid = qq["id"]
        tasks = []
        for t in qq["tasks"]:
            tid = t["id"]
            if t["type"] == "item":
                tasks.append({"type": "item", "id": t["item"]["id"], "count": t["count"]})
            elif t["type"] == "checkmark":
                tasks.append({"type": "check", "title": lang.get("task.%s.title" % tid, "")})
            elif t["type"] == "kill":
                tasks.append({"type": "kill", "id": t["entity"], "count": t["value"], "title": lang.get("task.%s.title" % tid, "")})
            elif t["type"] in ("advancement", "structure", "biome", "dimension"):
                tasks.append({"type": t["type"], "id": t[t["type"]]})
            elif t["type"] == "stat":
                tasks.append({"type": "stat", "id": t["stat"], "count": t["value"], "title": lang.get("task.%s.title" % tid, "")})
            else:
                raise SystemExit("unknown task " + t["type"])
        rew = []
        for r in qq.get("rewards", []):
            if r["type"] == "xp":
                rew.append({"type": "xp", "amount": r["xp"]})
            elif r["type"] == "item":
                rew.append({"type": "item", "id": r["item"]["id"], "count": r["count"]})
        d = lang.get("quest.%s.quest_desc" % qid, [])
        out["quests"].append({
            "id": qid, "title": lang.get("quest.%s.title" % qid, ""), "sub": lang.get("quest.%s.quest_subtitle" % qid, ""),
            "desc": d if isinstance(d, list) else [d], "x": qq["x"], "y": qq["y"],
            "icon": (qq.get("icon") or {}).get("id"), "optional": bool(qq.get("optional")),
            "tasks": tasks, "rewards": rew, "deps": qq.get("dependencies", []),
        })
        total += 1
    json.dump(out, open(os.path.join(dst, ch["filename"] + ".json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("chapters", len(glob.glob(dst + "/*.json")), "quests", total)
