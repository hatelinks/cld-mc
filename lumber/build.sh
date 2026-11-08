#!/bin/sh
# Сборка без Gradle: javac против библиотек NeoForge 21.1.248 (LIBS) и jar Create (MODS).
# neoforge-client.jar должен идти раньше client-srg.jar: в нём классы игры с патчами NeoForge (ls сортирует по имени).
# В MODS нужен create-1.21.1-6.0.10.jar (готовая копия: /mnt/project-files/libs-mods/).
set -e
cd "$(dirname "$0")"
LIBS=${LIBS:-../../libs}
MODS=${MODS:-../../libs-mods}
VERSION=${VERSION:-1.0.0}
CP=$(ls "$LIBS"/neoforge-*-client.jar; ls "$LIBS"/client-*-srg.jar; ls "$LIBS"/*.jar | grep -v -e '-client.jar$' -e '-srg.jar$'; ls "$MODS"/create-*.jar)
CP=$(echo "$CP" | tr '\n' ':')
rm -rf build && mkdir -p build/classes build/test dist
# сначала проверки поиска дерева вне игры
javac -proc:none --release 21 -encoding UTF-8 -nowarn -d build/test src/main/java/dev/cld/lumber/core/Fell.java tools/FellTest.java
java -Dstdout.encoding=UTF-8 -cp build/test FellTest
javac -proc:none --release 21 -encoding UTF-8 -nowarn -cp "$CP" -d build/classes $(find src/main/java -name '*.java')
cp -r src/main/resources/. build/classes/
sed -i.bak "s/\${version}/$VERSION/" build/classes/META-INF/neoforge.mods.toml && rm build/classes/META-INF/neoforge.mods.toml.bak
(cd build/classes && zip -qr -X ../../dist/cld_lumber-neoforge-1.21.1-$VERSION.jar .)
ls -la dist
