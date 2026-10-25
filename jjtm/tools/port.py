import json, os, shutil, sys, zipfile
SRC, VAN, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
FLAT = {"minecraft:uniform", "minecraft:clamped_normal", "minecraft:trapezoid",
        "minecraft:biased_to_bottom", "minecraft:very_biased_to_bottom", "minecraft:clamped"}
log = []
def flat(o, f):
    if isinstance(o, dict):
        if o.get("type") in FLAT and isinstance(o.get("value"), dict):
            v = o.pop("value"); o.update(v); log.append(f"flatten {o['type']} in {f}")
        for x in o.values(): flat(x, f)
    elif isinstance(o, list):
        for x in o: flat(x, f)
    return o
shutil.rmtree(OUT, ignore_errors=True)
for root, _, files in os.walk(os.path.join(SRC, "data")):
    for n in files:
        p = os.path.join(root, n); rel = os.path.relpath(p, SRC)
        dst = rel.replace("tags/blocks/", "tags/block/")
        o = flat(json.load(open(p)), rel)
        if "/worldgen/biome/" in rel:
            v = os.path.join(VAN, rel)
            if os.path.exists(v):
                vb = json.load(open(v))
                if o.get("spawners") != vb["spawners"]: log.append(f"spawners 1.21.1 in {n}")
                o["spawners"] = vb["spawners"]
                if "creature_spawn_probability" in vb: o["creature_spawn_probability"] = vb["creature_spawn_probability"]
                else: o.pop("creature_spawn_probability", None)
        os.makedirs(os.path.dirname(os.path.join(OUT, dst)), exist_ok=True)
        json.dump(o, open(os.path.join(OUT, dst), "w"), indent=2, ensure_ascii=False)
json.dump({"pack": {"pack_format": 48, "description": "JJThunder To The Max (порт на 1.21.1)"}},
          open(os.path.join(OUT, "pack.mcmeta"), "w"), indent=2, ensure_ascii=False)
print("\n".join(sorted(set(log))))
