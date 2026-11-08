#!/bin/sh
# Проверка пилы Create на настоящем сервере: ставит деревья (/place feature), перед стволом пилу и творческий мотор,
# считает брёвна и листву до и после спила (clone filtered ничего не меняет в исходной области).
# Запуск: qa/saw_trees.sh <папка mods> [список деревьев через пробел]
# В mods: create, cld_lumber (или без него для сравнения), wwoo и cristellib для деревьев WWOO.
set -eu
NEO=21.1.248
WORK=${WORK:-$HOME/qa-server}
MODS=$(cd "$1" && pwd); shift
TREES=${*:-"minecraft:fancy_oak minecraft:dark_oak minecraft:mega_jungle_tree minecraft:mangrove minecraft:huge_red_mushroom wythers:vegetation/tree/baobab wythers:vegetation/tree/kapok wythers:vegetation/tree/ponderosa_pine wythers:vegetation/tree/banyan wythers:vegetation/tree/ancient_oak wythers:vegetation/tree/jungle_palm wythers:vegetation/tree/huge_sequoia/1"}
mkdir -p "$WORK/cache"
INST="$WORK/cache/neoforge-$NEO-installer.jar"
SRV="$WORK/cache/server-$NEO"
if [ ! -f "$SRV/run.sh" ]; then
  [ -f "$INST" ] || curl -sSfL -o "$INST" "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEO/neoforge-$NEO-installer.jar"
  mkdir -p "$SRV" && (cd "$SRV" && java -jar "$INST" --installServer . >install.log 2>&1)
fi
RUN="$WORK/saw"
rm -rf "$RUN" && cp -r "$SRV" "$RUN" && mkdir -p "$RUN/mods"
cp "$MODS"/*.jar "$RUN/mods/"
echo "eula=true" > "$RUN/eula.txt"
cat > "$RUN/server.properties" <<P
online-mode=false
server-port=${PORT:-25565}
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
view-distance=4
simulation-distance=4
P
echo "-Xms2G -Xmx6G -Dcld_lumber.debug=true" > "$RUN/user_jvm_args.txt"
cd "$RUN"
rm -f in && mkfifo in
sleep infinity > in &
HOLD=$!
./run.sh nogui < in > console.log 2>&1 &
PID=$!
t=0
while ! grep -q 'Done (' console.log 2>/dev/null; do
  kill -0 $PID 2>/dev/null || { echo "СЕРВЕР УПАЛ"; exit 1; }
  [ $t -ge 900 ] && { echo "ТАЙМАУТ"; kill $PID; exit 1; }
  sleep 5; t=$((t+5))
done
c() { echo "$1" > in; sleep "${2:-0.3}"; }
count() { # $1 x, $2 метка, $3 фильтр
  for y in 100 119 138 157 176; do c "say CNT $2 $1 $3"; c "clone $(($1-20)) $y -20 $(($1+20)) $((y+18)) 20 $(($1-20)) $y 40 filtered $3"; done
}
c "gamerule randomTickSpeed 0"
c "gamerule doTileDrops true"
i=0
for tr in $TREES; do
  x=$((i*64)); i=$((i+1))
  c "forceload add $((x-24)) -24 $((x+24)) 64" 3
done
sleep 20
i=0
for tr in $TREES; do
  x=$((i*64)); i=$((i+1))
  c "fill $((x-12)) 99 -12 $((x+12)) 99 12 minecraft:grass_block"
  c "say TREE $x $tr"
  c "place feature $tr $x 100 0" 1
  count $x before "#minecraft:logs"; count $x before "#minecraft:leaves"
  count $x before "minecraft:mushroom_stem"; count $x before "minecraft:red_mushroom_block"
done
i=0
for tr in $TREES; do
  x=$((i*64)); i=$((i+1))
  c "setblock $((x-1)) 100 0 create:mechanical_saw[facing=east,axis_along_first=false]"
  # у горизонтальной пилы вал вдоль её направления, мотор сзади
  c "setblock $((x-2)) 100 0 create:creative_motor[facing=east]{ScrollValue:256}"
done
sleep 60
i=0
for tr in $TREES; do
  x=$((i*64)); i=$((i+1))
  c "say SAW $x"; c "data get block $((x-1)) 100 0 Speed"
  count $x after "#minecraft:logs"; count $x after "#minecraft:leaves"
  count $x after "minecraft:mushroom_stem"; count $x after "minecraft:red_mushroom_block"
  # сколько брёвен осталось на уровне спила (пень и корни)
  c "say CNT ground $x #minecraft:logs"; c "clone $((x-20)) 100 -20 $((x+20)) 100 20 $((x-20)) 100 40 filtered #minecraft:logs"
done
c "kill @e[type=item]" 2
c "stop" 20
wait $PID || true
kill $HOLD 2>/dev/null || true
python3 -I - "$RUN/console.log" <<'PY'
import re, sys
lines = open(sys.argv[1], encoding='utf-8', errors='replace').read().splitlines()
cur = None; res = {}; names = {}; place = {}; speed = {}; last = None
for ln in lines:
    m = re.search(r'\[Server\] TREE (\S+) (\S+)', ln)
    if m: names[m.group(1)] = m.group(2); last = ('tree', m.group(1)); continue
    m = re.search(r'\[Server\] CNT (\w+) (\S+) (\S+)', ln)
    if m: cur = m.groups(); continue
    m = re.search(r'\[Server\] SAW (\S+)', ln)
    if m: last = ('saw', m.group(1)); continue
    if last and last[0] == 'tree' and ('Placed feature' in ln or 'Failed to place' in ln or 'Unknown' in ln or 'not found' in ln.lower()):
        place[last[1]] = 'ok' if 'Placed' in ln else ln.split(']: ')[-1]; last = None
    if last and last[0] == 'saw' and 'has the following block data' in ln:
        speed[last[1]] = ln.split(': ')[-1]; last = None
    if cur:
        m = re.search(r'Successfully cloned (\d+) block', ln) or re.search(r'cloned (\d+) block', ln)
        if m or 'No blocks' in ln or 'too many' in ln.lower():
            k = (cur[1], cur[2], cur[0]); res[k] = res.get(k, 0) + (int(m.group(1)) if m else 0); cur = None
for x, n in names.items():
    def g(f, w): return res.get((x, f, w), 0)
    print('%-40s %s скорость %s | брёвна %d -> %d (на уровне спила %d) | листва %d -> %d | грибы %d -> %d' % (n, place.get(x, '?'), speed.get(x, '?'),
        g('#minecraft:logs', 'before'), g('#minecraft:logs', 'after'), g('#minecraft:logs', 'ground'), g('#minecraft:leaves', 'before'), g('#minecraft:leaves', 'after'),
        g('minecraft:mushroom_stem', 'before') + g('minecraft:red_mushroom_block', 'before'), g('minecraft:mushroom_stem', 'after') + g('minecraft:red_mushroom_block', 'after')))
for ln in lines:
    if re.search(r'Killed \d+|cld_lumber|ERROR|Exception|mixin', ln, re.I) and 'say' not in ln: print(ln[:300])
PY
