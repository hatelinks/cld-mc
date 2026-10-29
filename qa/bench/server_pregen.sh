#!/bin/bash
# Замер прегенерации tcw на выделенном сервере NeoForge 21.1.248 (только tcw + служебный cld_qabench).
# qa/bench/server_pregen.sh <имя прогона> <tcw.jar> ["доп. ключи JVM"]
# Сервер должен быть установлен в $BASE (java -jar neoforge-21.1.248-installer.jar --installServer $BASE).
# Мир tcw:telipailo, сид 12345: разогрев 21×21 чанк у -200 -200, затем замер 65×65 чанков у 2048 5968 (чанк 128 373).
# Итог — строка «QABENCH done …» (сек, чанки/с, MSPT, TPS, heap после GC); JFR-профиль в <прогон>/b.jfr.
# Сверка блоков двух миров: python3 -I qa/bench/cmpregion.py <прогон1>/world <прогон2>/world 128 373 32
set -u
QA=$(cd "$(dirname "$0")" && pwd)
BASE=${BASE:-$HOME/qa-server/base}
NAME=$1; JAR=$(realpath "$2"); EXTRA=${3:-}
R=${WORK:-$HOME/qa-bench}/$NAME; rm -rf "$R"; mkdir -p "$(dirname "$R")"; cp -r "$BASE" "$R"; cd "$R"
mkdir -p mods; cp "$JAR" mods/; cp "$QA"/cldbench/dist/cld_qabench-1.0.0.jar mods/
echo eula=true > eula.txt
printf 'online-mode=false\nview-distance=4\nsimulation-distance=4\nlevel-seed=12345\nlevel-type=tcw\\:telipailo\n' > server.properties
echo "-Xms6G -Xmx6G -XX:StartFlightRecording=filename=$R/b.jfr,settings=profile $EXTRA" > user_jvm_args.txt
rm -f in; mkfifo in; sleep 100000 > in & SL=$!
./run.sh nogui < in > console.log 2>&1 & SRV=$!
until grep -q 'Done (' console.log; do sleep 5; kill -0 $SRV 2>/dev/null || { echo "СЕРВЕР УПАЛ"; exit 1; }; done
echo "qabench -200 -200 10 64" > in
until grep -q 'QABENCH done' console.log; do sleep 5; done
echo "qabench 128 373 32 64" > in
until [ "$(grep -c 'QABENCH done' console.log)" -ge 2 ]; do sleep 5; done
echo "save-all flush" > in; sleep 10; echo stop > in
wait $SRV; kill $SL 2>/dev/null
grep 'QABENCH done' console.log | tail -1
