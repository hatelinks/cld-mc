#!/bin/sh
# Скриншоты пейзажа для отчёта: tools/shots.sh папка
set -e
cd "$(dirname "$0")/.."
OUT=${1:-/tmp/menu-shots}
mkdir -p "$OUT" /tmp/menu-preview
javac -d /tmp/menu-preview src/main/java/dev/cld/menu/scene/*.java tools/MenuPreview.java
P="java -cp /tmp/menu-preview MenuPreview"
$P "$OUT/day.png" 480 270 clear,rain,thunder,fog autumn 14:00 30
$P "$OUT/day2.png" 480 270 snow,blizzard,rainbow,clear summer 11:00 30
$P "$OUT/winter-night.png" 480 270 clear,snow,blizzard,fog winter 22:30 30
$P "$OUT/evening.png" 480 270 clear,rainbow,thunder,rain summer 20:50 30
