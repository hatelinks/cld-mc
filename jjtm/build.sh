#!/bin/sh
# Порт JJThunder To The Max 1.20.1 -> 1.21.1: original jar -> src/ -> dist/ (высоты 2096, 1024, 512)
set -e
cd "$(dirname "$0")"
tmp=$(mktemp -d)
unzip -q original/JJThunder_To_The_Max_1.20.1_v0.2.0.jar -d "$tmp/jt"
python3 -I tools/port.py "$tmp/jt" vanilla-1.21.1 "$tmp/pack"
python3 -I tools/tweaks.py "$tmp/pack"
cp -r "$tmp/pack/." src/
rm -f dist/*.jar dist/*.zip
for h in 2096 1024 512; do
  if [ $h = 2096 ]; then cp -r src "$tmp/h$h"; else python3 -I tools/variant.py src "$tmp/h$h" $h; fi
  # облака Cirrus: ванильные 192 (129 над морем при верхе 320) растягиваем пропорционально высоте мира
  top=$(( h - 64 )); cloud=$(( 63 + 129 * (top - 63) / 257 ))
  mkdir -p "$tmp/h$h/assets/minecraft/cirrus/cloud_dimensions"
  printf '{\n  "enabled": true,\n  "cloud_height": %s\n}\n' "$cloud" > "$tmp/h$h/assets/minecraft/cirrus/cloud_dimensions/overworld.json"
  sed -i.bak "s/version=\"0.3.3-1.21.1\"/version=\"0.3.3-1.21.1-h$h\"/" "$tmp/h$h/META-INF/neoforge.mods.toml" && rm "$tmp/h$h/META-INF/neoforge.mods.toml.bak"
  # -fast: ячейка шума 8 блоков по высоте вместо 4 (как в ванилле) — вдвое меньше расчётов, рельеф чуть глаже
  cp -r "$tmp/h$h" "$tmp/h$h-fast"
  python3 -I - "$tmp/h$h-fast" "$h" <<'PY'
import json, os, sys
d, h = sys.argv[1], sys.argv[2]
p = os.path.join(d, "data/minecraft/worldgen/noise_settings/overworld.json")
o = json.load(open(p)); o["noise"]["size_vertical"] = 2; json.dump(o, open(p, "w"), indent=2, ensure_ascii=False)
t = os.path.join(d, "META-INF/neoforge.mods.toml")
s = open(t).read().replace(f'-h{h}"', f'-h{h}-fast"'); open(t, "w").write(s)
PY
  for v in "h$h" "h$h-fast"; do
    (cd "$tmp/$v" && zip -qr -X "$OLDPWD/dist/jjtm-neoforge-1.21.1-0.3.3-$v.jar" META-INF pack.mcmeta assets data \
                  && zip -qr -X "$OLDPWD/dist/jjtm-1.21.1-datapack-$v.zip" pack.mcmeta data)
  done
done
rm -rf "$tmp"
ls dist
