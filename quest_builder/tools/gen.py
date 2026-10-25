"""Конструктор глав: добавляет квесты в data/*.json с авто-описаниями и авто-зависимостями.

Описание квеста собирается из:
- ручного текста (если задан) — помечается в чеклисте как «по общим знаниям»;
- подсказки мода (tooltip) — «Как работает» / «Как использовать»;
- рецепта — «Как получить»;
- обратных рецептов — «Зачем нужен» (во что идёт);
- для машин — какие рецепты они выполняют.
Зависимости берутся из ингредиентов рецепта: если ингредиент уже есть в каком-то
квесте, квест зависит от него (с транзитивным сокращением).
"""
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lex  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "data")

MACHINES = {
    "create:mechanical_press": ["create:pressing", "create:compacting"],
    "create:millstone": ["create:milling"],
    "create:crushing_wheel": ["create:crushing"],
    "create:deployer": ["create:deploying", "create:item_application"],
    "create:spout": ["create:filling"],
    "create:item_drain": ["create:emptying"],
    "create:mechanical_mixer": ["create:mixing"],
    "create:mechanical_saw": ["create:cutting"],
    "create:mechanical_crafter": ["create:mechanical_crafting"],
    "create:encased_fan": ["create:splashing", "create:haunting"],
    "farmersdelight:cutting_board": ["farmersdelight:cutting"],
    "farmersdelight:cooking_pot": ["farmersdelight:cooking"],
    "mysticalagriculture:infusion_altar": ["mysticalagriculture:infusion"],
    "mysticalagriculture:awakening_altar": ["mysticalagriculture:awakening"],
    "mysticalagriculture:seed_reprocessor": ["mysticalagriculture:reprocessor"],
    "createaddition:tesla_coil": ["createaddition:charging"],
    "createaddition:rolling_mill": ["createaddition:rolling"],
    "create_aquatic_ambitions:mechanical_conduit": ["create_aquatic_ambitions:channeling"],
    "create_enchantment_industry:mechanical_grindstone": ["create_enchantment_industry:grinding"],
}


class Book:
    FAN = 4

    def __init__(self):
        self.last, self.anchors = {}, {}
        self.ch = {}
        for p in sorted(glob.glob(os.path.join(DATA, "*.json"))):
            c = json.load(open(p, encoding="utf-8"))
            self.ch[c["key"]] = c
        self.reindex()

    # ---------- индекс ----------
    def ref(self, c, q):
        return q.get("id") or f'{c["key"]}/{q["k"]}'

    def reindex(self):
        self.item2ref = {}
        self.ref2q = {}
        self.deps = {}
        for c in self.ch.values():
            for q in c["quests"]:
                r = self.ref(c, q)
                self.ref2q[r] = (c["key"], q)
                for t in q["tasks"]:
                    for i in ([t["id"]] if t.get("id") else []) + list(t.get("ids", [])):
                        self.item2ref.setdefault(i, r)
        for c in self.ch.values():
            for q in c["quests"]:
                self.deps[self.ref(c, q)] = [self.norm(c["key"], d) for d in q.get("deps", [])]

    def norm(self, ck, d):
        if d in self.ref2q:
            return d
        if f"{ck}/{d}" in self.ref2q:
            return f"{ck}/{d}"
        return d

    def ancestors(self, r, seen=None):
        seen = set() if seen is None else seen
        for d in self.deps.get(r, []):
            if d not in seen:
                seen.add(d)
                self.ancestors(d, seen)
        return seen

    def covered(self, i):
        return i in self.item2ref

    # ---------- главы ----------
    def chapter(self, key, title, subtitle, group, icon, order, intro=None):
        if key not in self.ch:
            self.ch[key] = {"key": key, "id": "", "title": title, "subtitle": subtitle, "group": group,
                            "icon": icon, "order": order, "quests": []}
        c = self.ch[key]
        c.update(title=title, subtitle=subtitle, group=group, icon=icon, order=order)
        if intro and not any(q.get("k") == "intro" for q in c["quests"]):
            t, sub, desc, deps = intro
            c["quests"].insert(0, {"k": "intro", "title": t, "sub": sub, "desc": desc, "x": 0, "y": 0,
                                   "icon": icon, "optional": False,
                                   "tasks": [{"type": "check", "title": "Прочитать вступление"}],
                                   "rewards": [{"type": "xp", "amount": 10}], "deps": deps or []})
            self.reindex()
        return c

    def root(self, key):
        c = self.ch[key]
        for q in c["quests"]:
            if q.get("k") == "intro":
                return self.ref(c, q)
        return self.ref(c, c["quests"][0])

    # ---------- описание ----------
    def describe(self, ids, text=None, rep=None, extra=None, set_mode=False):
        rep = rep or ids[0]
        lines = []
        if text:
            lines += text if isinstance(text, list) else [text]
        tip = lex.tooltip(rep)
        summ = [t for k, t in tip if k == "summary"]
        uses = [t for k, t in tip if k == "use"]
        if summ and not text:
            lines.append("&6Как работает:&r " + " ".join(summ))
        g = lex.how_to_get(rep)
        if g:
            lines.append("&6Как получить:&r " + g + ".")
        for u in uses[:3]:
            lines.append("&6Как использовать:&r " + u)
        if rep in MACHINES:
            outs = []
            n = 0
            for r in lex.RECIPES:
                if r["type"] in MACHINES[rep]:
                    n += 1
                    for o in r["out"]:
                        nm = lex.name(o)
                        if nm not in outs and (lex.is_ru(o) or o.startswith("minecraft:")):
                            outs.append(nm)
            if n:
                lines.append(f"&6Что умеет:&r рецептов в сборке — {n}. Например: " + ", ".join(outs[:5]) + ".")
        else:
            uf = [u for u in lex.used_for(rep, limit=12) if u]
            uf = list(dict.fromkeys(uf))[:5]
            if uf:
                lines.append("&6Зачем нужен:&r идёт в " + ", ".join(uf) + ".")
        if extra:
            lines += extra
        if len(ids) > 1 and not set_mode:
            lines.append(f"&7Вариантов: {len(ids)}. Засчитывается «{lex.name(ids[0])}».")
        return lines

    # ---------- квест ----------
    def add(self, key, ids, k=None, title=None, sub=None, text=None, deps=None, count=1, mode="any",
            optional=False, finale=False, xp=None, rewards=None, note=None, icon=None, extra=None, after=None,
            anchor=False, adv=None):
        """mode: any — достаточно одного из ids; set — нужны все; check — ручная галочка."""
        c = self.ch[key]
        ids = [i for i in ids if i]
        if mode != "check":
            ids = [i for i in ids if i in lex.IDS or i.startswith("minecraft:")]
            if not ids:
                return None
        k = k or (ids[0].split(":")[1] if ids else f"q{len(c['quests'])}")
        if any(q.get("k") == k for q in c["quests"]):
            k = ids[0].replace(":", "_")
            if any(q.get("k") == k for q in c["quests"]):
                return None
        rep = ids[0] if ids else None
        if mode == "set":
            tasks = [{"type": "item", "id": i, "count": count} for i in ids]
        elif mode == "check":
            tasks = [{"type": "check", "title": title}]
        elif len(ids) > 1:
            tasks = [{"type": "any", "ids": ids, "count": count, "title": lex.name(ids[0]) + " (любой вариант)"}]
        else:
            tasks = [{"type": "item", "id": ids[0], "count": count}]
        if adv:
            for a in (adv if isinstance(adv, list) else [adv]):
                if a in lex.ADV:
                    tasks.append({"type": "advancement", "id": a})
                    _, d = lex.adv_text(a)
                    if d:
                        extra = (extra or []) + ["&6Цель:&r " + d]
        if deps is None:
            deps = self.auto_deps(key, ids, after)
        else:
            deps = [self.norm(key, d) for d in deps]
        if c["quests"] and not any(d.startswith(key + "/") or self.ref2q.get(d, ("",))[0] == key for d in deps):
            deps = [self.anchor_for(key)] + deps
        q = {"k": k, "title": title or lex.name(rep), "sub": sub or "", "x": 0, "y": 0,
             "desc": self.describe(ids, text, rep, extra, mode == "set") if mode != "check" else (text or []),
             "icon": icon, "optional": optional, "tasks": tasks,
             "rewards": rewards if rewards is not None else [{"type": "xp", "amount": xp or 15}],
             "deps": deps}
        if finale:
            q["finale"] = True
        if note or (text and mode != "check"):
            q["note"] = note or "Ручное описание по общим знаниям о моде"
        c["quests"].append(q)
        r = f"{key}/{k}"
        self.ref2q[r] = (key, q)
        self.deps[r] = deps
        for i in ids:
            self.item2ref.setdefault(i, r)
        if not optional:
            self.last[key] = r
        if anchor:
            self.anchors[key] = r
        return r

    def anchor_for(self, key):
        """Куда подвесить квест без зависимостей внутри главы: к якорю раздела.
        Если у якоря уже много детей, якорем становится последний добавленный квест."""
        a = self.anchors.get(key) or self.root(key)
        n = sum(1 for r, ds in self.deps.items() if a in ds and self.ref2q.get(r, ("",))[0] == key)
        if n >= self.FAN and self.last.get(key) and self.last[key] != a:
            a = self.anchors[key] = self.last[key]
        return a

    def adv(self, key, advid, k=None, title=None, text=None, deps=None, xp=20, optional=False, icon=None):
        """Квест на достижение мода (засчитывается само)."""
        t, dsc = lex.adv_text(advid)
        c = self.ch[key]
        k = k or "adv_" + advid.split(":")[1].replace("/", "_")
        deps = [self.norm(key, d) for d in (deps or [])] or [self.root(key)]
        desc = (text if isinstance(text, list) else [text]) if text else []
        if dsc:
            desc.append("&6Цель:&r " + dsc)
        q = {"k": k, "title": title or t or advid, "sub": "Достижение", "x": 0, "y": 0, "desc": desc, "icon": icon,
             "optional": optional, "tasks": [{"type": "advancement", "id": advid}],
             "rewards": [{"type": "xp", "amount": xp}], "deps": deps}
        c["quests"].append(q)
        r = f"{key}/{k}"
        self.ref2q[r] = (key, q)
        self.deps[r] = deps
        if not optional:
            self.last[key] = r
        return r

    def custom(self, key, k, title, tasks, text=None, deps=None, xp=20, optional=False, icon=None, sub="",
               rewards=None, finale=False, anchor=False):
        """Квест с произвольными задачами (kill/biome/structure/dimension/advancement)."""
        c = self.ch[key]
        if deps is None:
            deps = [self.anchor_for(key)] if c["quests"] else []
        else:
            deps = [self.norm(key, d) for d in deps]
        q = {"k": k, "title": title, "sub": sub, "x": 0, "y": 0,
             "desc": (text if isinstance(text, list) else [text]) if text else [], "icon": icon,
             "optional": optional, "tasks": tasks,
             "rewards": rewards if rewards is not None else [{"type": "xp", "amount": xp}], "deps": deps}
        if finale:
            q["finale"] = True
        c["quests"].append(q)
        r = f"{key}/{k}"
        self.ref2q[r] = (key, q)
        self.deps[r] = deps
        if not optional:
            self.last[key] = r
        if anchor:
            self.anchors[key] = r
        return r

    def auto_deps(self, key, ids, after=None):
        ing = set()
        for i in ids[:1]:
            ing |= lex.ingredients(i)
        cands = []
        for i in ing:
            r = self.item2ref.get(i)
            if r and r not in cands and not self.ref2q.get(r, (None, {}))[1].get("optional"):
                cands.append(r)
        if after:
            cands += [self.norm(key, a) for a in (after if isinstance(after, list) else [after])]
        # транзитивное сокращение
        keep = []
        for r in cands:
            if any(r in self.ancestors(o) for o in cands if o != r):
                continue
            if r not in keep:
                keep.append(r)
        # предпочитаем зависимости из той же главы, не больше трёх
        keep.sort(key=lambda r: (self.ref2q[r][0] != key if r in self.ref2q else True))
        return keep[:3]

    def save(self):
        for c in self.ch.values():
            for q in c["quests"]:
                q.pop("_", None)
            json.dump(c, open(os.path.join(DATA, c["key"] + ".json"), "w", encoding="utf-8"),
                      ensure_ascii=False, indent=1)


def fam_groups(ids, fn):
    """Сгруппировать список ID по ключу семейства fn(id) -> ключ, сохраняя порядок."""
    out = {}
    for i in ids:
        out.setdefault(fn(i), []).append(i)
    return list(out.values())
