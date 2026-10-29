#!/bin/sh
# Нагрузочный тест генератора tcw без игры. qa/bench/tcw.sh [сид] [чанков] [потоков]; JFR=1 — записать профиль.
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=${OUT:-$ROOT/qa/bench/build}
rm -rf "$OUT" && mkdir -p "$OUT"
javac -nowarn -d "$OUT" "$ROOT"/tcw/src/main/java/dev/cld/tcw/gen/*.java "$ROOT"/qa/bench/TcwChunkBench.java
J=""
[ -n "${JFR:-}" ] && J="-XX:StartFlightRecording=filename=$OUT/tcw.jfr,settings=profile"
java -Dstdout.encoding=UTF-8 $J -cp "$OUT" TcwChunkBench "$@"
