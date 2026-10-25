#!/bin/sh
# Сборка без Gradle: javac против библиотек NeoForge 21.1.248 из Prism (LIBS=папка с jar).
set -e
cd "$(dirname "$0")"
LIBS=${LIBS:-../../libs}
VERSION=${VERSION:-1.0.1}
CP=$(ls "$LIBS"/neoforge-*-client.jar "$LIBS"/client-*-srg.jar; ls "$LIBS"/*.jar | grep -v -e '-client.jar$' -e '-srg.jar$')
CP=$(echo "$CP" | tr '\n' ':')
rm -rf build && mkdir -p build/classes dist
javac --release 21 -encoding UTF-8 -nowarn -cp "$CP" -d build/classes $(find src/main/java -name '*.java')
cp -r src/main/resources/. build/classes/
sed -i.bak "s/\${version}/$VERSION/" build/classes/META-INF/neoforge.mods.toml && rm build/classes/META-INF/neoforge.mods.toml.bak
(cd build/classes && zip -qr -X ../../dist/cld_pregen-neoforge-1.21.1-$VERSION.jar .)
ls -la dist
