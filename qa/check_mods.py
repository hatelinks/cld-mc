#!/usr/bin/env python3
"""Статическая проверка папки mods (и датапаков) сборки NeoForge 1.21.1 без запуска игры.

Запуск: python3 -I qa/check_mods.py <папка mods> [--datapacks <папка|zip> ...] [--android] [--server]
Проверяет: дубликаты modId, обязательные зависимости и их версии (с учётом jarjar),
несовместимости, валидность JSON/mcmeta, mixin-конфиги, датапаки (pack_format, имена папок 1.21),
риски для Android (нативные библиотеки, Sodium/Iris) и моды, которые не нужны/ломают сервер.
Выход: 0 — нет ошибок, 1 — есть ошибки. Предупреждения на код выхода не влияют.
"""
import io, json, re, sys, tomllib, zipfile, argparse
from pathlib import Path

MC, NEO = "1.21.1", "21.1.248"
DATA_FORMAT, RES_FORMAT = 48, 34
# 1.21: папки реестров в датапаке стали в единственном числе
PLURAL_DIRS = {"advancements": "advancement", "recipes": "recipe", "loot_tables": "loot_table",
               "predicates": "predicate", "item_modifiers": "item_modifier", "functions": "function",
               "structures": "structure"}
PLURAL_TAGS = {"blocks": "block", "items": "item", "entity_types": "entity_type", "fluids": "fluid",
               "game_events": "game_event", "functions": "function"}
# моды только для клиента: на выделенном сервере не нужны (часть падает при запуске)
CLIENT_ONLY = {"sodium", "iris", "entity_model_features", "entity_texture_features", "lambdynlights",
               "cirrus", "keybindbundles", "baritoe", "cld_pregen", "cld_menu", "cld_earlywindow",
               "entityculling", "immediatelyfast", "sodiumextras", "reeses_sodium_options"}
# известные проблемы на Android (Amethyst/Pojav/Zalith, мобильные рендереры)
ANDROID_BAD = {"sodium": "краши Aeronautics+Sodium на мобильных рендерерах — на Android не ставить",
               "iris": "шейдеры на мобильных рендерерах нестабильны",
               "distanthorizons": "тяжёлый; на телефоне дальность LOD не выше ~64"}

errors, warns = [], []
STRICT_JSON = False
def err(m): errors.append(m)
def warn(m): warns.append(m)

# --- версии: порт org.apache.maven ComparableVersion / VersionRange (как в FML) ---
QUALS = ["alpha", "beta", "milestone", "rc", "snapshot", "", "sp"]
ALIAS = {"ga": "", "final": "", "release": "", "cr": "rc"}

def _qkey(s):
    s = ALIAS.get(s, s)
    return str(QUALS.index(s)) if s in QUALS else f"{len(QUALS)}-{s}"

def _item(tok, followed_by_digit=False):
    if tok.isdigit():
        return int(tok)
    if followed_by_digit and len(tok) == 1:
        tok = {"a": "alpha", "b": "beta", "m": "milestone"}.get(tok, tok)
    return ("s", tok)

def _null(x):
    return x == 0 or x == ("s", "") or x == [] or (isinstance(x, tuple) and _qkey(x[1]) == _qkey(""))

def _norm(lst):
    while lst and not isinstance(lst[-1], list) and _null(lst[-1]):
        lst.pop()
    for x in lst:
        if isinstance(x, list): _norm(x)
    return lst

def parse_version(v):
    v = v.lower()
    root = lst = []
    start, digit = 0, False
    i = 0
    for i, c in enumerate(v):
        if c == ".":
            lst.append(0 if i == start else _item(v[start:i])); start = i + 1
        elif c == "-":
            lst.append(0 if i == start else _item(v[start:i])); start = i + 1
            nl = []; lst.append(nl); lst = nl
        elif c.isdigit():
            if not digit and i > start:
                lst.append(_item(v[start:i], True)); start = i
                nl = []; lst.append(nl); lst = nl
            digit = True
        else:
            if digit and i > start:
                lst.append(_item(v[start:i])); start = i
                nl = []; lst.append(nl); lst = nl
            digit = False
    if len(v) > start:
        lst.append(_item(v[start:]))
    return _norm(root)

def _cmp(a, b):
    """a, b — int | ('s', str) | list | None"""
    if a is None and b is None: return 0
    if a is None: return -_cmp(b, None)
    if isinstance(a, int):
        if b is None: return 0 if a == 0 else 1
        if isinstance(b, int): return (a > b) - (a < b)
        return 1
    if isinstance(a, tuple):
        if b is None: ka, kb = _qkey(a[1]), _qkey("")
        elif isinstance(b, int): return -1
        elif isinstance(b, list): return -1
        else: ka, kb = _qkey(a[1]), _qkey(b[1])
        return (ka > kb) - (ka < kb)
    # list
    if b is None: return 0 if not a else _cmp(a[0], None)
    if isinstance(b, int): return -1
    if isinstance(b, tuple): return 1
    for k in range(max(len(a), len(b))):
        r = _cmp(a[k] if k < len(a) else None, b[k] if k < len(b) else None)
        if r: return r
    return 0

def vcmp(a, b):
    return _cmp(parse_version(a), parse_version(b))

def vkey(v):
    import functools
    return functools.cmp_to_key(vcmp)(v)

def in_range(ver, rng):
    rng = (rng or "").strip()
    if not rng or rng == "*":
        return True
    parts = re.findall(r"[\[(][^\])]*[\])]", rng)
    if not parts:
        return True          # «рекомендуемая» версия без скобок: FML принимает любую
    for part in parts:
        lo_inc, hi_inc = part[0] == "[", part[-1] == "]"
        body = part[1:-1]
        if "," not in body:
            if vcmp(ver, body.strip()) == 0:
                return True
            continue
        lo, hi = (s.strip() for s in body.split(",", 1))
        ok = True
        if lo:
            c = vcmp(ver, lo); ok = ok and (c > 0 or (c == 0 and lo_inc))
        if hi:
            c = vcmp(ver, hi); ok = ok and (c < 0 or (c == 0 and hi_inc))
        if ok:
            return True
    return False

# FML 4 для 1.21.1 (VersionSupportMatrix): если диапазон не принял 1.21.1/21.1.x,
# зависимость всё равно засчитывается, если он принимает 1.21 / 21.0.166
FML_OVERRIDE = {"minecraft": "1.21", "neoforge": "21.0.166"}

def dep_ok(did, ver, rng):
    return in_range(ver, rng) or (did in FML_OVERRIDE and in_range(FML_OVERRIDE[did], rng))

# --- разбор jar -------------------------------------------------------------
def read_toml(z):
    for n in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
        if n in z.namelist():
            raw = z.read(n).decode("utf-8", "replace")
            try:
                return tomllib.loads(raw), n
            except tomllib.TOMLDecodeError as e:
                return {"__error__": str(e)}, n
    return None, None

def impl_version(z):
    try:
        mf = z.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")
        m = re.search(r"Implementation-Version:\s*(\S+)", mf)
        return m.group(1) if m else None
    except KeyError:
        return None

def loads_lenient(raw):
    """Как Gson в lenient-режиме (так читают Mixin и загрузчики данных Minecraft): комментарии допустимы."""
    txt = raw.decode("utf-8-sig", "replace")
    try:
        return json.loads(txt, strict=False)
    except ValueError:
        pass
    out, i, n, q = [], 0, len(txt), None
    while i < n:
        c = txt[i]
        if q:
            out.append(c)
            if c == "\\" and i + 1 < n: out.append(txt[i + 1]); i += 1
            elif c == q: q = None
        elif c in "\"'": q = c; out.append(c)
        elif txt.startswith("//", i) or c == "#":
            while i < n and txt[i] != "\n": i += 1
            continue
        elif txt.startswith("/*", i):
            j = txt.find("*/", i + 2); i = n if j < 0 else j + 2; continue
        else: out.append(c)
        i += 1
    txt = re.sub(r",(\s*[}\]])", r"\1", "".join(out))
    return json.loads(txt, strict=False)

def check_json_in(z, label):
    if not STRICT_JSON:
        return
    bad = 0
    for n in z.namelist():
        if n.endswith((".json", ".mcmeta")) and not n.startswith("META-INF/jarjar"):
            try:
                loads_lenient(z.read(n))
            except Exception as e:
                bad += 1
                if bad <= 5:
                    warn(f"{label}: невалидный JSON {n}: {str(e)[:80]}")
    if bad > 5:
        warn(f"{label}: ещё {bad - 5} невалидных JSON")

def check_datapack_dirs(names, label):
    seen = set()
    for n in names:
        p = n.split("/")
        if len(p) >= 4 and p[0] == "data":
            d = p[2]
            if d in PLURAL_DIRS and (d, p[1]) not in seen:
                seen.add((d, p[1]))
                warn(f"{label}: data/{p[1]}/{d}/ — в 1.21 папка называется {PLURAL_DIRS[d]}/ (файлы не загрузятся, если это не совместимость со старыми версиями)")
            if d == "tags" and len(p) >= 5 and p[3] in PLURAL_TAGS and ("t" + p[3], p[1]) not in seen:
                seen.add(("t" + p[3], p[1]))
                warn(f"{label}: data/{p[1]}/tags/{p[3]}/ — в 1.21 это tags/{PLURAL_TAGS[p[3]]}/")

def all_names(z):
    names = set(z.namelist())
    for n in list(names):
        if n.endswith(".jar") and n.startswith("META-INF/"):
            try: names |= all_names(zipfile.ZipFile(io.BytesIO(z.read(n))))
            except zipfile.BadZipFile: pass
    return names

def read_any(z, name):
    if name in z.namelist(): return z.read(name)
    for n in z.namelist():
        if n.endswith(".jar") and n.startswith("META-INF/"):
            r = read_any(zipfile.ZipFile(io.BytesIO(z.read(n))), name)
            if r is not None: return r
    return None

def check_mixins(z, toml, label):
    names = all_names(z)
    for mx in toml.get("mixins", []) or []:
        cfg = mx.get("config")
        if not cfg:
            continue
        if cfg not in names:
            err(f"{label}: mixin-конфиг {cfg} указан в mods.toml, но его нет в jar")
            continue
        try:
            j = loads_lenient(read_any(z, cfg))
        except Exception as e:
            err(f"{label}: битый {cfg}: {e}"); continue
        pkg = j.get("package", "").replace(".", "/")
        missing = [c for k in ("mixins", "client", "server") for c in j.get(k, []) or []
                   if f"{pkg}/{c.replace('.', '/')}.class" not in names]
        if missing:
            err(f"{label}: в {cfg} нет классов: {', '.join(missing[:5])}")

def scan_jar(data, label, mods, nested=False):
    z = zipfile.ZipFile(io.BytesIO(data))
    toml, tname = read_toml(z)
    ids = []
    if toml and "__error__" in toml:
        err(f"{label}: не читается {tname}: {toml['__error__']}")
    elif toml:
        for m in toml.get("mods", []):
            mid, ver = m.get("modId"), str(m.get("version", "0"))
            if "${" in ver:
                ver = impl_version(z) or "0"
            ids.append(mid)
            mods.setdefault(mid, []).append({"ver": ver, "file": label, "nested": nested,
                "deps": (toml.get("dependencies") or {}).get(mid, []), "zip": z})
        if not nested:
            check_mixins(z, toml, label)
    if not nested:
        check_json_in(z, label)
        natives = [n for n in z.namelist() if n.endswith((".so", ".dylib", ".dll", ".jnilib"))]
        if natives:
            mods.setdefault("__natives__", []).append((label, natives))
    for n in z.namelist():
        if n.startswith("META-INF/") and n.endswith(".jar"):
            inner = z.read(n)
            scan_jar(inner, f"{label} → {n.rsplit('/', 1)[1]}", mods, nested=True)
            iz = zipfile.ZipFile(io.BytesIO(inner))
            natives = [x for x in iz.namelist() if x.endswith((".so", ".dylib", ".dll", ".jnilib"))]
            if natives:
                mods.setdefault("__natives__", []).append((f"{label} → {n.rsplit('/', 1)[1]}", natives))
    return ids

def resolve(mods):
    """Как JarJar: из вложенных берётся самая новая версия; верхнеуровневые jar важнее."""
    res = {}
    for mid, lst in mods.items():
        if mid.startswith("__"):
            continue
        top = [x for x in lst if not x["nested"]]
        if len(top) > 1:
            err(f"дубликат modId «{mid}»: " + ", ".join(f"{x['file']} ({x['ver']})" for x in top))
        pick = top[0] if top else max(lst, key=lambda x: vkey(x["ver"]))
        res[mid] = pick
    res.setdefault("minecraft", {"ver": MC, "file": "minecraft", "deps": []})
    res.setdefault("neoforge", {"ver": NEO, "file": "neoforge", "deps": []})
    return res

def check_deps(res, side):
    for mid, m in sorted(res.items()):
        for d in m["deps"]:
            # FML 4 (NeoForge 21.1) смотрит только на type, старое поле mandatory игнорируется
            did, typ = d.get("modId"), str(d.get("type", "required")).lower()
            if d.get("mandatory") is False and "type" not in d:
                warn(f"{mid}: зависимость {did} помечена mandatory=false без type — FML 4 считает её обязательной")
            rng, dside = d.get("versionRange", ""), str(d.get("side", "BOTH")).upper()
            if (side == "server" and dside == "CLIENT") or (side == "client" and dside == "SERVER"):
                continue
            have = res.get(did)
            where = f"{mid} ({m['file']})"
            if typ == "required":
                if not have:
                    err(f"{where}: нет обязательного мода «{did}» {rng}")
                elif not dep_ok(did, have["ver"], rng):
                    err(f"{where}: нужен {did} {rng}, стоит {have['ver']}")
            elif typ in ("optional", "discouraged") and have and rng and not dep_ok(did, have["ver"], rng):
                (err if typ == "optional" else warn)(f"{where}: {did} {have['ver']} вне диапазона {rng} ({typ})")
            elif typ == "incompatible" and have and in_range(have["ver"], rng or "*"):
                err(f"{where}: несовместим с {did} {have['ver']} ({rng}) {d.get('reason', '')}".rstrip())

def check_datapack(path):
    p = Path(path)
    if p.is_dir() and not (p / "pack.mcmeta").exists():
        for c in sorted(p.iterdir()):
            if c.suffix == ".zip" or (c.is_dir() and (c / "pack.mcmeta").exists()):
                check_datapack(c)
        return
    if p.is_dir():
        names = [str(f.relative_to(p)) for f in p.rglob("*") if f.is_file()]
        read = lambda n: (p / n).read_bytes()
    else:
        z = zipfile.ZipFile(p); names = z.namelist(); read = z.read
    label = f"датапак {p.name}"
    if "pack.mcmeta" not in names:
        err(f"{label}: нет pack.mcmeta в корне (может быть лишняя папка внутри архива)")
        return
    try:
        meta = json.loads(read("pack.mcmeta").decode("utf-8-sig"))["pack"]
        fmt = meta.get("pack_format")
        sup = meta.get("supported_formats")
        ok = fmt == DATA_FORMAT
        if isinstance(sup, list) and len(sup) == 2: ok |= sup[0] <= DATA_FORMAT <= sup[1]
        elif isinstance(sup, dict): ok |= sup.get("min_inclusive", 99) <= DATA_FORMAT <= sup.get("max_inclusive", 0)
        elif isinstance(sup, int): ok |= sup == DATA_FORMAT
        if not ok:
            warn(f"{label}: pack_format {fmt} (для 1.21.1 нужен {DATA_FORMAT}) — игра покажет «несовместим»")
    except Exception as e:
        err(f"{label}: битый pack.mcmeta: {e}")
    bad = 0
    for n in names:
        if n.endswith((".json", ".mcmeta")):
            try:
                json.loads(read(n).decode("utf-8-sig"))
            except Exception as e:
                try:
                    loads_lenient(read(n)); warn(f"{label}: {n} читается только в lenient-режиме (комментарии/лишние запятые)"); continue
                except Exception: pass
                bad += 1
                if bad <= 10: err(f"{label}: невалидный JSON {n}: {str(e)[:80]}")
    check_datapack_dirs(names, label)
    print(f"  {label}: {len(names)} файлов")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mods")
    ap.add_argument("--datapacks", nargs="*", default=[])
    ap.add_argument("--android", action="store_true", help="проверить набор для телефона")
    ap.add_argument("--server", action="store_true", help="проверить набор для выделенного сервера")
    ap.add_argument("--list-client-only", action="store_true", help="только вывести файлы клиентских модов (для qa/server.sh)")
    ap.add_argument("--strict-json", action="store_true", help="проверять JSON и внутри чужих модов")
    a = ap.parse_args()
    global STRICT_JSON
    STRICT_JSON = a.strict_json
    mods = {}
    jars = sorted(Path(a.mods).glob("*.jar"))
    if not a.list_client_only: print(f"Модов (jar): {len(jars)}")
    for j in jars:
        try:
            ids = scan_jar(j.read_bytes(), j.name, mods)
            if not ids:
                warn(f"{j.name}: нет neoforge.mods.toml — не мод NeoForge (Fabric/библиотека?)")
        except zipfile.BadZipFile:
            err(f"{j.name}: битый jar")
    res = resolve(mods)
    if a.list_client_only:
        for mid in sorted(set(res) & CLIENT_ONLY):
            if not res[mid].get("nested"): print(res[mid]["file"])
        return
    side = "server" if a.server else "client"
    check_deps(res, side)
    if a.server:
        for mid in sorted(set(res) & CLIENT_ONLY):
            warn(f"сервер: {mid} ({res[mid]['file']}) — клиентский мод, на сервер не класть")
    for label, natives in mods.get("__natives__", []):
        plat = {"android-arm64" if re.search(r"android|aarch64-linux-android|arm64-v8a", n, re.I) else
                "mac" if n.endswith((".dylib", ".jnilib")) else "win" if n.endswith(".dll") else "linux"
                for n in natives}
        msg = f"{label}: нативные библиотеки ({', '.join(sorted(plat))})"
        if a.android and "android-arm64" not in plat:
            warn(msg + " — сборки под Android нет, на телефоне нужна подмена (как sable-android-support)")
        else:
            print("  " + msg)
    if a.android:
        for mid, why in ANDROID_BAD.items():
            if mid in res:
                warn(f"Android: {mid} — {why}")
        if "sable" in res and "sable_android_support" not in res and not any("sable" in k and "android" in k for k in res):
            err("Android: стоит Sable без sable-android-support — на телефоне не загрузится нативка Rapier")
    for d in a.datapacks:
        check_datapack(d)
    print(f"\nМодов (modId, вкл. вложенные): {len(res) - 2}")
    for w in warns: print("ПРЕДУПРЕЖДЕНИЕ:", w)
    for e in errors: print("ОШИБКА:", e)
    print(f"\nИтог: ошибок {len(errors)}, предупреждений {len(warns)}")
    sys.exit(1 if errors else 0)

if __name__ == "__main__":
    main()
