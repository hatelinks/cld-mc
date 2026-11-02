#!/bin/sh
# Тесты relay и туннеля без Minecraft (поддельный сервер мира и игроки на localhost).
set -e
cd "$(dirname "$0")"
rm -rf build/test && mkdir -p build/test
javac --release 21 -encoding UTF-8 -d build/test $(find src/main/java/dev/cld/tunnel/core src/relay/java test -name '*.java')
java -Dstdout.encoding=UTF-8 -cp build/test TunnelTest
