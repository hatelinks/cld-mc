#!/bin/sh
# «Горы до небес»: jjtm как отдельные типы мира gdn:h1024 и gdn:h512 (стандартный мир остаётся ванильным).
# Данные берутся из ../jjtm/src (порт jjtm 1.21.1), код собирается javac против библиотек NeoForge (LIBS).
set -e
cd "$(dirname "$0")"
LIBS=${LIBS:-../../libs}
VERSION=${VERSION:-1.0.0}
tmp=$(mktemp -d)
rm -rf build && mkdir -p build/classes dist
cp -r src/main/resources/. build/classes/
for h in 1024 512; do
  python3 -I ../jjtm/tools/variant.py ../jjtm/src "$tmp/h$h" $h
  # быстрая генерация: ячейка шума 8 блоков по высоте
  python3 -I - "$tmp/h$h" <<'PY'
import json, os, sys
p = os.path.join(sys.argv[1], "data/minecraft/worldgen/noise_settings/overworld.json")
o = json.load(open(p)); o["noise"]["size_vertical"] = 2; json.dump(o, open(p, "w"), indent=2)
PY
  python3 -I tools/build_data.py "$tmp/h$h" ../jjtm/vanilla-1.21.1 build/classes h$h "Горы до небес $h"
done
rm -rf "$tmp"
cp ../jjtm/src/assets/jjthunder_to_the_max/icon.png build/classes/icon.png 2>/dev/null || true
CP=$(ls "$LIBS"/neoforge-*-client.jar "$LIBS"/client-*-srg.jar; ls "$LIBS"/*.jar | grep -v -e '-client.jar$' -e '-srg.jar$')
CP=$(echo "$CP" | tr '\n' ':')
javac -proc:none --release 21 -encoding UTF-8 -nowarn -cp "$CP" -d build/classes $(find src/main/java -name '*.java')
sed -i.bak "s/\${version}/$VERSION/" build/classes/META-INF/neoforge.mods.toml && rm build/classes/META-INF/neoforge.mods.toml.bak
(cd build/classes && zip -qr -X ../../dist/gdn-neoforge-1.21.1-$VERSION.jar .)
ls -la dist
