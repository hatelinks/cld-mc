#!/bin/sh
# Дальний рельеф (cld_far): сборка без Gradle, javac против libs NeoForge 21.1.248 и классов tcw (исходники ../tcw).
set -e
cd "$(dirname "$0")"
LIBS=${LIBS:-../../libs}
VERSION=${VERSION:-1.0.3}
# neoforge-client первым: в нём пропатченные классы игры (ls сортирует по алфавиту, поэтому отдельными вызовами)
CP=$(ls "$LIBS"/neoforge-*-client.jar; ls "$LIBS"/client-*-srg.jar; ls "$LIBS"/*.jar | grep -v -e '-client.jar$' -e '-srg.jar$')
CP=$(echo "$CP" | tr '\n' ':')
rm -rf build && mkdir -p build/classes build/tcw dist
# tcw нужен только для компиляции, в jar не попадает (в игре он отдельным модом)
javac -proc:none --release 21 -encoding UTF-8 -nowarn -cp "$CP" -d build/tcw $(find ../tcw/src/main/java -name '*.java')
javac -proc:none --release 21 -encoding UTF-8 -nowarn -cp "$CP:build/tcw" -d build/classes $(find src/main/java -name '*.java')
cp -r src/main/resources/. build/classes/
sed -i.bak "s/\${version}/$VERSION/" build/classes/META-INF/neoforge.mods.toml && rm build/classes/META-INF/neoforge.mods.toml.bak
(cd build/classes && zip -qr -X ../../dist/cld_far-neoforge-1.21.1-$VERSION.jar .)
ls -la dist
