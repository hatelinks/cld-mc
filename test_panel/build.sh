#!/bin/sh
# Панель тестирования (cld_test_panel), форк Baritone 1.11.3: сборка без Gradle, javac против libs NeoForge 21.1.248.
# Gradle у Baritone (unimined) в облаке не работает: его maven закрыты.
set -e
cd "$(dirname "$0")"
LIBS=${LIBS:-../../libs}
VERSION=${VERSION:-0.6.2}
# nether-pathfinder (поиск пути на элитрах, с нативками) берём из официального jar Baritone без ProGuard
NP_JAR=${NP_JAR:-$LIBS/baritone-unoptimized-neoforge-1.11.3.jar}
if [ ! -f "$NP_JAR" ]; then
  NP_JAR=build/baritone-unoptimized-neoforge-1.11.3.jar
  mkdir -p build
  [ -f "$NP_JAR" ] || curl -sSfL -o "$NP_JAR" https://github.com/cabaletta/baritone/releases/download/v1.11.3/baritone-unoptimized-neoforge-1.11.3.jar
fi
# neoforge-client первым: в нём пропатченные классы игры (ls сортирует по алфавиту, поэтому отдельными вызовами)
CP=$(ls "$LIBS"/neoforge-*-client.jar; ls "$LIBS"/client-*-srg.jar; ls "$LIBS"/*.jar | grep -v -e '-client.jar$' -e '-srg.jar$' -e 'baritone')
CP=$(echo "$CP" | tr '\n' ':')
rm -rf build/classes build/sch build/np && mkdir -p build/classes build/sch build/np dist
NP_JAR=$(cd "$(dirname "$NP_JAR")" && pwd)/$(basename "$NP_JAR")
(cd build/np && unzip -q -o "$NP_JAR" 'dev/*' natives.zip.xz)
# заглушки Schematica нужны только для компиляции
javac -proc:none --release 21 -encoding UTF-8 -nowarn -cp "$CP" -d build/sch $(find src/schematica_api -name '*.java')
javac -proc:none --release 21 -encoding UTF-8 -nowarn -cp "$CP:build/sch:build/np" -d build/classes \
  $(find src/api src/main src/launch src/neoforge src/panel -name '*.java')
cp -r src/launch/resources/. src/neoforge/resources/. src/panel/resources/. build/classes/
cp -r build/np/. build/classes/
cp LICENSE build/classes/LICENSE-baritone
sed -i.bak "s/\${version}/$VERSION/" build/classes/META-INF/neoforge.mods.toml && rm build/classes/META-INF/neoforge.mods.toml.bak
cat > build/MANIFEST.MF <<EOF
Manifest-Version: 1.0
MixinConfigs: mixins.baritone.json,mixins.cld_test_panel.json
MixinConnector: baritone.launch.BaritoneMixinConnector
Implementation-Title: cld_test_panel
Implementation-Version: $VERSION
EOF
rm -f dist/cld_test_panel-neoforge-1.21.1-$VERSION.jar
(cd build/classes && zip -qr -X ../../dist/cld_test_panel-neoforge-1.21.1-$VERSION.jar .)
(cd build && mkdir -p mf/META-INF && cp MANIFEST.MF mf/META-INF/ && cd mf && zip -q -X ../../dist/cld_test_panel-neoforge-1.21.1-$VERSION.jar META-INF/MANIFEST.MF)
ls -la dist
