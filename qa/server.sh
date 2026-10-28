#!/bin/sh
# Смоук-тест выделенного сервера NeoForge 21.1.248: ставит сервер, кладёт моды (без клиентских) и датапаки,
# запускает, ждёт «Done», выполняет команды проверки, останавливает и разбирает лог.
# Запуск: qa/server.sh <папка mods> [датапак.zip ...]
# Нужен доступ в сеть к maven.neoforged.net, libraries.minecraft.net, piston-meta/piston-data.mojang.com.
set -eu
QA=$(cd "$(dirname "$0")" && pwd)
NEO=21.1.248
WORK=${WORK:-$HOME/qa-server}
TIMEOUT=${TIMEOUT:-900}
MODS=$(cd "$1" && pwd); shift
mkdir -p "$WORK/cache"
INST="$WORK/cache/neoforge-$NEO-installer.jar"
SRV="$WORK/cache/server-$NEO"
if [ ! -f "$SRV/run.sh" ]; then
  [ -f "$INST" ] || curl -sSfL -o "$INST" "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEO/neoforge-$NEO-installer.jar"
  mkdir -p "$SRV" && (cd "$SRV" && java -jar "$INST" --installServer . >install.log 2>&1)
fi
RUN="$WORK/run"
rm -rf "$RUN" && cp -r "$SRV" "$RUN" && mkdir -p "$RUN/mods" "$RUN/world/datapacks"
CLIENT=$(python3 -I "$QA/check_mods.py" "$MODS" --list-client-only)
for j in "$MODS"/*.jar; do
  b=$(basename "$j")
  echo "$CLIENT" | grep -qxF "$b" && { echo "пропуск клиентского: $b"; continue; }
  cp "$j" "$RUN/mods/"
done
for d in "$@"; do cp -r "$d" "$RUN/world/datapacks/"; done
[ -d "$MODS/../config" ] && cp -r "$MODS/../config" "$RUN/" || true
[ -d "$MODS/../defaultconfigs" ] && cp -r "$MODS/../defaultconfigs" "$RUN/" || true
echo "eula=true" > "$RUN/eula.txt"
cat > "$RUN/server.properties" <<P
online-mode=false
view-distance=4
simulation-distance=4
level-seed=${SEED:-12345}
P
echo "-Xms2G -Xmx${XMX:-6G}" > "$RUN/user_jvm_args.txt"
cd "$RUN"
rm -f in && mkfifo in
(sleep infinity > in &)          # держим fifo открытым
./run.sh nogui < in > console.log 2>&1 &
PID=$!
t=0
while ! grep -q 'Done (' console.log 2>/dev/null; do
  if ! kill -0 $PID 2>/dev/null; then echo "СЕРВЕР УПАЛ при запуске"; break; fi
  [ $t -ge "$TIMEOUT" ] && { echo "ТАЙМАУТ запуска ${TIMEOUT}с"; kill $PID; break; }
  sleep 5; t=$((t+5))
done
if kill -0 $PID 2>/dev/null; then
  for c in "datapack list" "reload" "forceload add 0 0 160 160" "tps" "stop"; do echo "$c" > in; sleep 20; done
  wait $PID || true
fi
pkill -f "sleep infinity" 2>/dev/null || true
python3 -I "$QA/log_report.py" "$RUN/logs/latest.log" "$RUN/crash-reports" | tee "$WORK/report.txt"
