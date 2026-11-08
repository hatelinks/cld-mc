#!/bin/sh
# Сборка relay: обычная Java 21, библиотеки не нужны. Итог: dist/cld_tunnel-relay-<версия>.jar
set -e
cd "$(dirname "$0")"
VERSION=${VERSION:-0.1.1}
rm -rf build/relay && mkdir -p build/relay dist
javac --release 21 -encoding UTF-8 -d build/relay $(find src/main/java/dev/cld/tunnel/core src/relay/java -name '*.java')
printf 'Main-Class: dev.cld.tunnel.relay.RelayMain\nImplementation-Version: %s\n' "$VERSION" > build/manifest.txt
rm -f dist/cld_tunnel-relay-$VERSION.jar
(cd build/relay && jar --create --file ../../dist/cld_tunnel-relay-$VERSION.jar --manifest ../manifest.txt .)
ls -la dist
