#!/bin/sh
# Служебный мод отдела тестирования для замера прегенерации на сервере (не для игроков).
set -e
cd "$(dirname "$0")"
LIBS=${LIBS:-../../../../libs}
CP=$(ls "$LIBS"/neoforge-*-client.jar "$LIBS"/client-*-srg.jar; ls "$LIBS"/*.jar | grep -v -e '-client.jar$' -e '-srg.jar$')
CP=$(echo "$CP" | tr '\n' ':')
rm -rf build && mkdir -p build/classes dist
javac -proc:none --release 21 -encoding UTF-8 -nowarn -cp "$CP" -d build/classes $(find src/main/java -name '*.java')
cp -r src/main/resources/. build/classes/
(cd build/classes && zip -qr -X ../../dist/cld_qabench-1.0.0.jar .)
ls -la dist
