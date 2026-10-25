"""Переносит вариант jjtm (data/minecraft/...) в отдельный тип мира gdn:<tag>.

Ванильные файлы больше не перекрываются: рельеф, шумы, фичи и карверы jjtm получают
идентификаторы gdn:<tag>/<путь>, а изменения биомов становятся биом-модификаторами
gdn:patch, которые срабатывают только в мире с типом измерения gdn:<tag>.
Использование: build_data.py <variant> <vanilla> <out> <tag> <title>
"""
import json, os, sys

VAR, VAN, OUT, TAG, TITLE = sys.argv[1:6]
NS = "gdn"
MOVED = ["density_function", "noise", "noise_settings", "placed_feature", "configured_feature", "configured_carver"]
CLIMATE = ["temperature", "downfall", "has_precipitation", "temperature_modifier"]
EFFECTS = ["fog_color", "water_color", "water_fog_color", "sky_color", "foliage_color", "grass_color", "grass_color_modifier"]

mc = os.path.join(VAR, "data", "minecraft")
wg = os.path.join(mc, "worldgen")

def load(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)

def dump(o, *parts):
    p = os.path.join(OUT, *parts)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(o, f, indent=2, ensure_ascii=False)

def ids(kind):
    d = os.path.join(wg, kind)
    out = set()
    for root, _, files in os.walk(d):
        for n in files:
            if n.endswith(".json"):
                rel = os.path.relpath(os.path.join(root, n), d)[:-5].replace(os.sep, "/")
                out.add(rel)
    return out

shipped = {k: ids(k) for k in MOVED}

def new_id(kind, s):
    """minecraft:x или x -> gdn:<tag>/x, если jjtm поставляет x в этом реестре."""
    if not isinstance(s, str) or s.startswith("#"):
        return None
    path = s[len("minecraft:"):] if s.startswith("minecraft:") else (s if ":" not in s else None)
    if path is None or path not in shipped[kind]:
        return None
    return f"{NS}:{TAG}/{path}"

def block_tags(o):
    if isinstance(o, dict):
        return {k: block_tags(v) for k, v in o.items()}
    if isinstance(o, list):
        return [block_tags(v) for v in o]
    if o == "#minecraft:azalea_extra_grows_on":
        return f"#{NS}:azalea_extra_grows_on"
    return o

def rw_density(o, key=None):
    """Плотности и настройки шума: строки-ссылки на плотности и значения ключа noise."""
    if isinstance(o, dict):
        return {k: rw_density(v, k) for k, v in o.items()}
    if isinstance(o, list):
        return [rw_density(v, key) for v in o]
    if isinstance(o, str) and key != "type":
        if key == "noise":
            return new_id("noise", o) or o
        return new_id("density_function", o) or o
    return o

def rw_features(o, top=True, key=None):
    """В placed_feature верхний feature — configured; вложенные feature в configured — placed."""
    if isinstance(o, dict):
        return {k: rw_features(v, False, k) for k, v in o.items()}
    if isinstance(o, list):
        return [rw_features(v, False, key) for v in o]
    if isinstance(o, str) and key == "feature":
        return new_id("placed_feature", o) or new_id("configured_feature", o) or o
    return o

# --- перенос реестров
for kind in MOVED:
    for path in shipped[kind]:
        o = block_tags(load(os.path.join(wg, kind, path + ".json")))
        if kind in ("density_function", "noise_settings"):
            o = rw_density(o)
        elif kind == "placed_feature":
            if isinstance(o.get("feature"), str):
                o["feature"] = new_id("configured_feature", o["feature"]) or o["feature"]
            else:
                o["feature"] = rw_features(o["feature"])
        elif kind == "configured_feature":
            o = rw_features(o)
        dump(o, "data", NS, "worldgen", kind, TAG, path + ".json")

# --- тип измерения и пресет мира
dt = load(os.path.join(mc, "dimension_type", "overworld.json"))
dump(dt, "data", NS, "dimension_type", TAG + ".json")
dim = load(os.path.join(mc, "dimension", "overworld.json"))
dim["type"] = f"{NS}:{TAG}"
dim["generator"]["settings"] = f"{NS}:{TAG}/overworld"
assert "overworld" in shipped["noise_settings"]
preset = {"dimensions": {
    "minecraft:overworld": dim,
    "minecraft:the_nether": {"type": "minecraft:the_nether", "generator": {
        "type": "minecraft:noise", "settings": "minecraft:nether",
        "biome_source": {"type": "minecraft:multi_noise", "preset": "minecraft:nether"}}},
    "minecraft:the_end": {"type": "minecraft:the_end", "generator": {
        "type": "minecraft:noise", "settings": "minecraft:end",
        "biome_source": {"type": "minecraft:the_end"}}},
}}
dump(preset, "data", NS, "worldgen", "world_preset", TAG + ".json")

# --- биомы -> модификаторы, действующие только в этом типе мира
def feature_steps(b):
    return [list(s) for s in b.get("features", [])]

def carver_stages(b):
    c = b.get("carvers", {})
    if isinstance(c, list):
        c = {"air": c}
    elif isinstance(c, str):
        c = {"air": [c]}
    return {k: ([v] if isinstance(v, str) else list(v)) for k, v in c.items()}

skipped_effects = set()
bdir = os.path.join(wg, "biome")
for n in sorted(os.listdir(bdir)):
    jj = load(os.path.join(bdir, n))
    van = load(os.path.join(VAN, "data", "minecraft", "worldgen", "biome", n))
    patch = {"type": f"{NS}:patch", "dimension_type": f"{NS}:{TAG}", "biomes": "minecraft:" + n[:-5]}
    steps = []
    vs, js = feature_steps(van), feature_steps(jj)
    for i in range(max(len(vs), len(js))):
        v = vs[i] if i < len(vs) else []
        j = js[i] if i < len(js) else []
        jm = [new_id("placed_feature", x) or x for x in j]
        remove = [x for x in v if x not in j or new_id("placed_feature", x)]
        add = [x for x in jm if x not in v]
        if remove or add:
            steps.append({"step": i, "remove": remove, "add": add})
    if steps:
        patch["features"] = steps
    stages = []
    vc, jc = carver_stages(van), carver_stages(jj)
    for st in sorted(set(vc) | set(jc)):
        v, j = vc.get(st, []), jc.get(st, [])
        jm = [new_id("configured_carver", x) or x for x in j]
        remove = [x for x in v if x not in j or new_id("configured_carver", x)]
        add = [x for x in jm if x not in v]
        if remove or add:
            stages.append({"stage": st, "remove": remove, "add": add})
    if stages:
        patch["carvers"] = stages
    climate = {k: jj[k] for k in CLIMATE if k in jj and jj.get(k) != van.get(k)}
    if climate:
        patch["climate"] = climate
    je, ve = jj.get("effects", {}), van.get("effects", {})
    eff = {k: je[k] for k in EFFECTS if k in je and je.get(k) != ve.get(k)}
    skipped_effects |= {k for k in set(je) | set(ve) if k not in EFFECTS and je.get(k) != ve.get(k)}
    if eff:
        patch["effects"] = eff
    dump(patch, "data", NS, "neoforge", "biome_modifier", TAG, n)

# собственные биом-модификаторы jjtm (например, убрать поверхностные пещеры Better Caves)
bm = os.path.join(VAR, "data", "jjthunder_to_the_max", "neoforge", "biome_modifier")
for n in sorted(os.listdir(bm)) if os.path.isdir(bm) else []:
    o = load(os.path.join(bm, n))
    assert o["type"] == "neoforge:remove_carvers", o["type"]
    carvers = o["carvers"] if isinstance(o["carvers"], list) else [o["carvers"]]
    patch = {"type": f"{NS}:patch", "dimension_type": f"{NS}:{TAG}", "biomes": o["biomes"],
             "carvers": [{"stage": "air", "remove": carvers, "add": []}]}
    if "neoforge:conditions" in o:
        patch = {"neoforge:conditions": o["neoforge:conditions"], **patch}
    dump(patch, "data", NS, "neoforge", "biome_modifier", TAG, "jjtm_" + n)

# --- теги (общие для всех высот)
for root, _, files in os.walk(os.path.join(mc, "tags")):
    for n in files:
        rel = os.path.relpath(os.path.join(root, n), os.path.join(mc, "tags"))
        o = load(os.path.join(root, n))
        if rel.startswith(os.path.join("block", "azalea_extra_grows_on")):
            dump(o, "data", NS, "tags", "block", "azalea_extra_grows_on.json")
        elif rel.startswith(os.path.join("worldgen", "biome", "has_structure")):
            o = {"values": o["values"]}
            dump(o, "data", NS, "tags", "worldgen", "biome", "has_structure", n)
        else:
            raise SystemExit("неизвестный тег " + rel)

# --- название в списке «Тип мира»
for lang in ("ru_ru", "en_us"):
    p = os.path.join(OUT, "assets", NS, "lang", lang + ".json")
    o = load(p) if os.path.exists(p) else {}
    o[f"generator.{NS}.{TAG}"] = TITLE
    dump(o, "assets", NS, "lang", lang + ".json")

if skipped_effects:
    print("не перенесены эффекты биомов:", sorted(skipped_effects), file=sys.stderr)
print(TAG, {k: len(v) for k, v in shipped.items()})
