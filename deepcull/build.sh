#!/bin/sh
# Эксперимент: не строить и не рисовать секции глубоко под поверхностью, пока игрок наверху.
# Сборка без Gradle: javac против NeoForge 21.1.248 (LIBS) и Sodium (SODIUM, только для компиляции).
set -e
cd "$(dirname "$0")"
LIBS=${LIBS:-../../libs}
SODIUM=${SODIUM:-$LIBS/../libs-extra/sodium-neoforge-0.8.13+mc1.21.1-mod.jar}
VERSION=0.1.0
# neoforge-client первым: ls сортирует по алфавиту, поэтому отдельными вызовами
CP=$(ls "$LIBS"/neoforge-*-client.jar; ls "$LIBS"/client-*-srg.jar; ls "$LIBS"/*.jar | grep -v -e '-client.jar$' -e '-srg.jar$'; echo "$SODIUM")
CP=$(echo "$CP" | tr '\n' ':')
rm -rf build && mkdir -p build/classes dist
javac -proc:none --release 21 -encoding UTF-8 -nowarn -cp "$CP" -d build/classes $(find src/main/java -name '*.java')
cp -r src/main/resources/. build/classes/
sed -i.bak "s/\${version}/$VERSION/" build/classes/META-INF/neoforge.mods.toml && rm build/classes/META-INF/neoforge.mods.toml.bak
(cd build/classes && zip -qr -X ../../dist/cld_deepcull-exwip-neoforge-1.21.1-$VERSION.jar .)
ls -la dist
