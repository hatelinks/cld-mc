#!/bin/sh
# Сборка без Gradle: javac против библиотек NeoForge 21.1.248 из Prism (LIBS=папка с jar).
# Нативка Rapier для Android лежит готовая в native/, пересобирать её native/build_android_native.sh (на Маке).
set -e
cd "$(dirname "$0")"
LIBS=${LIBS:-../../libs}
VERSION=${VERSION:-2.0.0}
CP=$(ls "$LIBS"/neoforge-*-client.jar "$LIBS"/client-*-srg.jar; ls "$LIBS"/*.jar | grep -v -e '-client.jar$' -e '-srg.jar$')
CP=$(echo "$CP" | tr '\n' ':')
rm -rf build && mkdir -p build/classes dist
javac -proc:none --release 21 -encoding UTF-8 -nowarn -cp "$CP" -d build/classes $(find src/main/java -name '*.java')
cp -r src/main/resources/. build/classes/
sed -i.bak "s/\${version}/$VERSION/" build/classes/META-INF/neoforge.mods.toml && rm build/classes/META-INF/neoforge.mods.toml.bak
# coremods.json ссылается на скрипт в корне jar, копия в META-INF/coremods была и в 1.2.0
mkdir -p build/classes/META-INF/coremods build/classes/natives/sable_rapier
cp src/main/resources/sable_android_transformer.js build/classes/META-INF/coremods/
cp native/sable_rapier_aarch64_android.so build/classes/natives/sable_rapier/
printf 'Manifest-Version: 1.0\nMixinConfigs: sable_android.mixins.json\n\n' > build/manifest.txt
(cd build/classes && jar --create --file ../../dist/sable-android-support-neoforge-1.21.1-$VERSION.jar --manifest ../manifest.txt .)
ls -la dist
