#!/bin/sh
# Сборка без Gradle: javac против библиотек NeoForge 21.1.248 из Prism (LIBS=папка с jar).
# Это не мод, а сервис FML: jar кладётся в mods/, NeoForge сам переносит его в слой сервисов.
set -e
cd "$(dirname "$0")"
LIBS=${LIBS:-../../libs}
VERSION=${VERSION:-1.0.1}
CP=$(ls "$LIBS"/*.jar | tr '\n' ':')
rm -rf build && mkdir -p build/classes dist
javac -proc:none --release 21 -encoding UTF-8 -nowarn -cp "$CP" -d build/classes $(find src/main/java -name '*.java')
cp -r src/main/resources/. build/classes/
mkdir -p build/classes/META-INF
printf 'Manifest-Version: 1.0\nAutomatic-Module-Name: cld_earlywindow\nImplementation-Version: %s\n' "$VERSION" > build/manifest.txt
(cd build/classes && jar --create --file ../../dist/cld_earlywindow-1.21.1-$VERSION.jar --manifest ../manifest.txt .)
ls -la dist
