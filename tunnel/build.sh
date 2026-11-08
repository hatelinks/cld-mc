#!/bin/sh
# Сборка мода без Gradle: javac против библиотек NeoForge 21.1.248 (LIBS=папка с jar).
set -e
cd "$(dirname "$0")"
LIBS=${LIBS:-../../libs}
VERSION=${VERSION:-0.1.0}
# neoforge-client первым: ls сортирует по алфавиту, поэтому отдельными вызовами
CP=$(ls "$LIBS"/neoforge-*-client.jar; ls "$LIBS"/client-*-srg.jar; ls "$LIBS"/*.jar | grep -v -e '-client.jar$' -e '-srg.jar$')
CP=$(echo "$CP" | tr '\n' ':')
rm -rf build/mod && mkdir -p build/mod dist
javac -proc:none --release 21 -encoding UTF-8 -nowarn -cp "$CP" -d build/mod $(find src/main/java -name '*.java')
cp -r src/main/resources/. build/mod/
sed -i.bak "s/\${version}/$VERSION/" build/mod/META-INF/neoforge.mods.toml && rm build/mod/META-INF/neoforge.mods.toml.bak
rm -f dist/cld_tunnel-neoforge-1.21.1-$VERSION.jar
(cd build/mod && zip -qr -X ../../dist/cld_tunnel-neoforge-1.21.1-$VERSION.jar .)
ls -la dist
