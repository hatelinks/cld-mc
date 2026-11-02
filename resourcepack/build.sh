#!/bin/sh
# Собирает ресурспак сборки: resourcepack/dist/cld_resources.zip
# Картинки перерисовывает make_assets.py (нужен Pillow), готовые PNG уже лежат в pack/.
set -e
cd "$(dirname "$0")"
rm -rf dist && mkdir -p dist
(cd pack && zip -qrX ../dist/cld_resources.zip pack.mcmeta assets)
echo "dist/cld_resources.zip"
