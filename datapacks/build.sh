#!/bin/sh
# Свои датапаки сборки: каждая папка с pack.mcmeta -> datapacks/dist/<папка>.zip
# Готовые zip кладутся в moonlight-global-datapacks (Moonlight подключает их ко всем мирам).
set -e
cd "$(dirname "$0")"
rm -rf dist && mkdir -p dist
for d in */; do
  d=${d%/}
  [ -f "$d/pack.mcmeta" ] || continue
  python3 -I -c "import json,sys,glob; [json.load(open(f)) for f in glob.glob(sys.argv[1]+'/**/*.json', recursive=True)+[sys.argv[1]+'/pack.mcmeta']]" "$d"
  (cd "$d" && zip -qr -X "../dist/$d.zip" pack.mcmeta data)
done
ls -la dist
