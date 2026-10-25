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
  sed -i.bak "s/version=\"0.3.2-1.21.1\"/version=\"0.3.2-1.21.1-h$h\"/" "$tmp/h$h/META-INF/neoforge.mods.toml" && rm "$tmp/h$h/META-INF/neoforge.mods.toml.bak"
  (cd "$tmp/h$h" && zip -qr -X "$OLDPWD/dist/jjtm-neoforge-1.21.1-0.3.2-h$h.jar" META-INF pack.mcmeta assets data \
                 && zip -qr -X "$OLDPWD/dist/jjtm-1.21.1-datapack-h$h.zip" pack.mcmeta data)
done
rm -rf "$tmp"
ls dist
