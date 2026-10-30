#!/bin/sh
# Тип мира TelipailoCraft: свой генератор рельефа (dev.cld.tcw.gen) поверх ванильного NoiseBasedChunkGenerator.
set -e
cd "$(dirname "$0")"
LIBS=${LIBS:-../../libs}
VERSION=0.5.5
rm -rf build/classes && mkdir -p build/classes dist
cp -r src/main/resources/. build/classes/
python3 -I tools/build_data.py "$(ls "$LIBS"/client-*-extra.jar)" build/classes
CP=$(ls "$LIBS"/neoforge-*-client.jar "$LIBS"/client-*-srg.jar; ls "$LIBS"/*.jar | grep -v -e '-client.jar$' -e '-srg.jar$')
CP=$(echo "$CP" | tr '\n' ':')
javac -proc:none --release 21 -encoding UTF-8 -nowarn -cp "$CP" -d build/classes $(find src/main/java -name '*.java')
sed -i.bak "s/\${version}/$VERSION/" build/classes/META-INF/neoforge.mods.toml && rm build/classes/META-INF/neoforge.mods.toml.bak
(cd build/classes && zip -qr -X ../../dist/tcw-neoforge-1.21.1-$VERSION.jar .)
ls -la dist
