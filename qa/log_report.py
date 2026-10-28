#!/usr/bin/env python3
"""Разбор latest.log/debug.log и crash-reports NeoForge: краш, время запуска, ошибки по модам, известные безвредные.
Запуск: python3 -I qa/log_report.py <latest.log> [папка crash-reports]"""
import re, sys
from collections import Counter
from pathlib import Path

# известный безвредный шум (см. HANDOFF.md §7)
HARMLESS = [r"alexsmobs|iceandfire|ars_nouveau|quark:|mowziesmobs", r"idas.*(bop|byg|biomesoplenty)", r"CSA config read"]
PATTERNS = {
    "mixin": r"Mixin apply failed|MixinApplyError|InvalidMixinException|Mixin transformation of .* failed",
    "missing_class": r"NoClassDefFoundError|ClassNotFoundException",
    "datapack": r"Failed to (load|parse) .*(recipe|loot|advancement|tag|function|structure|biome|worldgen)|Couldn't (load|parse) |Unknown registry key|Failed to get element",
    "tags": r"Couldn't load tag|missing references",
    "config": r"Failed loading config|config .*(corrupt|invalid)|ConfigLoadingException",
    "registry": r"Registry .* (missing|unknown)|Missing registry|Detected missing registry",
    "network": r"Mismatched mod channel|incompatible network|Channel .* rejected",
}

def main():
    log = Path(sys.argv[1])
    crash_dir = Path(sys.argv[2]) if len(sys.argv) > 2 else None
    lines = log.read_text("utf-8", "replace").splitlines() if log.exists() else []
    print(f"Лог: {log} ({len(lines)} строк)")
    done = [l for l in lines if "Done (" in l]
    print("Запуск: " + (re.search(r"Done \(([^)]*)\)", done[0]).group(1) if done else "НЕ ЗАВЕРШЁН"))
    mods = next((l for l in lines if re.search(r"Found \d+ mods|Loading \d+ mods", l)), None)
    if mods: print("Моды: " + mods.split("]: ")[-1][:120])
    cats, by_src, harmless = Counter(), Counter(), 0
    examples = {}
    for l in lines:
        if not re.search(r"/(ERROR|WARN)\]|\[(ERROR|WARN)\]|Exception", l):
            continue
        if any(re.search(h, l, re.I) for h in HARMLESS):
            harmless += 1; continue
        for k, p in PATTERNS.items():
            if re.search(p, l, re.I):
                cats[k] += 1; examples.setdefault(k, l[:300])
        if "/ERROR]" in l or "[ERROR]" in l:
            m = re.search(r"\[([\w.$/-]+)/ERROR\]|\]\s*\[([\w.$-]+)/\]", l)
            by_src[(m.group(1) or m.group(2)) if m else "?"] += 1
    print(f"Ошибок ERROR: {sum(by_src.values())}, известного безвредного шума: {harmless}")
    for src, n in by_src.most_common(15): print(f"  {n:5}  {src}")
    for k, n in cats.most_common(): print(f"[{k}] {n}× например: {examples[k]}")
    crashes = sorted(crash_dir.glob("*.txt")) if crash_dir and crash_dir.exists() else []
    for c in crashes:
        t = c.read_text("utf-8", "replace")
        desc = re.search(r"Description: (.*)", t)
        susp = re.findall(r"Suspected Mods?: (.*)", t)
        print(f"КРАШ {c.name}: {desc.group(1) if desc else '?'}; подозреваемые: {', '.join(susp) or '—'}")
    sys.exit(2 if crashes or not done else 0)

if __name__ == "__main__":
    main()
