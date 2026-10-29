#!/bin/sh
# Собрать папку LIBS для build.sh без Prism: установщик NeoForge 21.1.248 (клиент) + библиотеки ванили 1.21.1.
# Нужен доступ к maven.neoforged.net, piston-meta.mojang.com и libraries.minecraft.net.
# ./tools/fetch_libs.sh [папка]   (по умолчанию ../libs рядом с клоном)
set -e
OUT=${1:-$(dirname "$0")/../../libs}
WORK=$(mktemp -d)
mkdir -p "$OUT"
curl -sSfo "$WORK/installer.jar" https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.248/neoforge-21.1.248-installer.jar
mkdir -p "$WORK/mc" && echo '{"profiles":{}}' > "$WORK/mc/launcher_profiles.json"
(cd "$WORK" && java -jar installer.jar --install-client mc > install.log 2>&1) || { tail -20 "$WORK/install.log"; exit 1; }
python3 -I - "$WORK/mc/versions/1.21.1/1.21.1.json" "$OUT" <<'PY'
import json, os, sys, urllib.request
j = json.load(open(sys.argv[1]))
for l in j['libraries']:
    a = l.get('downloads', {}).get('artifact')
    if not a or any(r.get('os', {}).get('name') in ('osx', 'windows') for r in l.get('rules', []) if r['action'] == 'allow'):
        continue
    p = os.path.join(sys.argv[2], os.path.basename(a['path']))
    if not os.path.exists(p):
        urllib.request.urlretrieve(a['url'], p)
PY
find "$WORK/mc/libraries" -name '*.jar' ! -name '*-slim.jar' -exec cp {} "$OUT/" \;
rm -rf "$WORK"
ls "$OUT" | wc -l
