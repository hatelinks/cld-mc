#!/usr/bin/env python3
"""Самопроверка qa/check_mods.py: собирает заведомо сломанные моды/датапаки и ждёт, что проверка их поймает.
Запуск: python3 -I qa/tests/selftest.py"""
import subprocess, sys, tempfile, zipfile, json
from pathlib import Path

CHECK = Path(__file__).resolve().parent.parent / "check_mods.py"

def jar(path, toml, extra=None):
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("META-INF/neoforge.mods.toml", toml)
        for n, d in (extra or {}).items():
            z.writestr(n, d)

def mod(mid, ver, deps="", mixins=""):
    return f'modLoader="javafml"\nloaderVersion="[4,)"\nlicense="x"\n[[mods]]\nmodId="{mid}"\nversion="{ver}"\n{mixins}\n{deps}'

def dep(owner, did, rng, typ="required", side="BOTH"):
    return f'[[dependencies.{owner}]]\nmodId="{did}"\ntype="{typ}"\nversionRange="{rng}"\nside="{side}"\n'

def run(*args):
    p = subprocess.run([sys.executable, "-I", str(CHECK), *map(str, args)], capture_output=True, text=True)
    return p.returncode, p.stdout

cases = []
with tempfile.TemporaryDirectory() as t:
    t = Path(t)
    ok = t / "ok"; ok.mkdir()
    jar(ok / "a.jar", mod("a", "1.0.0", dep("a", "b", "[2.0,3.0)") + dep("a", "minecraft", "[1.21,1.21.1)")))
    jar(ok / "b.jar", mod("b", "2.5.1"))
    cases.append(("исправный набор (вкл. правило FML 1.21→1.21.1)", run(ok), 0, []))

    bad = t / "bad"; bad.mkdir()
    jar(bad / "a.jar", mod("a", "1.0.0", dep("a", "b", "[3.0,)") + dep("a", "c", "[1,)") + dep("a", "d", "[1,)", "incompatible")))
    jar(bad / "b.jar", mod("b", "2.5.1"))
    jar(bad / "b2.jar", mod("b", "2.6.0"))
    jar(bad / "d.jar", mod("d", "1.2", mixins='[[mixins]]\nconfig="d.mixins.json"'),
        {"d.mixins.json": json.dumps({"package": "x.y", "mixins": ["Gone"]})})
    cases.append(("сломанный набор", run(bad), 1,
                  ["нужен b [3.0,)", "нет обязательного мода «c»", "несовместим с d", "дубликат modId «b»", "нет классов: Gone"]))

    dp = t / "dp.zip"
    with zipfile.ZipFile(dp, "w") as z:
        z.writestr("pack.mcmeta", json.dumps({"pack": {"pack_format": 15, "description": ""}}))
        z.writestr("data/x/recipes/r.json", "{}")
        z.writestr("data/x/tags/blocks/t.json", '{"values": []}')
        z.writestr("data/x/loot_table/l.json", "{ broken")
    cases.append(("сломанный датапак", run(ok, "--datapacks", dp), 1,
                  ["pack_format 15", "recipes/", "tags/blocks/", "невалидный JSON data/x/loot_table/l.json"]))

    nested = t / "nested.zip"
    with zipfile.ZipFile(nested, "w") as z:
        z.writestr("inner/pack.mcmeta", "{}")
    cases.append(("датапак с лишней папкой", run(ok, "--datapacks", nested), 1, ["нет pack.mcmeta"]))

fail = 0
for name, (code, out), want_code, needles in cases:
    miss = [n for n in needles if n not in out]
    good = code == want_code and not miss
    fail += not good
    print(("OK   " if good else "FAIL ") + name + ("" if good else f" (код {code}, не найдено: {miss})\n{out}"))
sys.exit(1 if fail else 0)
