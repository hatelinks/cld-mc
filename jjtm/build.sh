#!/bin/sh
# Порт JJThunder To The Max 1.20.1 -> 1.21.1: original jar -> src/ -> dist/
set -e
cd "$(dirname "$0")"
tmp=$(mktemp -d)
unzip -q original/JJThunder_To_The_Max_1.20.1_v0.2.0.jar -d "$tmp/jt"
python3 -I tools/port.py "$tmp/jt" vanilla-1.21.1 "$tmp/pack"
cp -r "$tmp/pack/." src/
(cd "$tmp/pack" && zip -qr -X "$OLDPWD/dist/jjtm-1.21.1-datapack.zip.new" pack.mcmeta data)
mv dist/jjtm-1.21.1-datapack.zip.new dist/jjtm-1.21.1-datapack.zip
(cd src && zip -qr -X ../dist/jjtm-neoforge-1.21.1-0.2.0.jar.new META-INF pack.mcmeta assets data)
mv dist/jjtm-neoforge-1.21.1-0.2.0.jar.new dist/jjtm-neoforge-1.21.1-0.2.0.jar
echo "dist/ обновлён"
