#!/bin/bash
# Сборка Android-нативки Sable Rapier для Sable 2.0.6. Результат кладётся рядом,
# в sable_rapier_aarch64_android.so, дальше jar собирает ../build.sh.
# Запускать на Маке: bash build_android_native.sh
# Нужно: git, rustup, интернет. Скрипт сам поставит rustup-цель, cargo-ndk и при необходимости скачает Android NDK.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$HERE/work"
mkdir -p "$WORK"

echo "== 1. Инструменты"
command -v rustup >/dev/null || { echo "Нет rustup. Установите: curl https://sh.rustup.rs -sSf | sh"; exit 1; }
rustup target add aarch64-linux-android
command -v cargo-ndk >/dev/null || cargo install cargo-ndk
find_ndk() {
  for d in "${ANDROID_NDK_HOME:-}" "$HOME/Library/Android/ndk/current" $(ls -d "$HOME"/Library/Android/sdk/ndk/* 2>/dev/null | sort -V -r) $(ls -d "$HOME"/android-ndk-* 2>/dev/null) /opt/homebrew/share/android-ndk; do
    [ -n "$d" ] && [ -f "$d/source.properties" ] && { echo "$d"; return; }
  done
}
NDK="$(find_ndk || true)"
if [ -z "$NDK" ]; then
  echo "NDK не найден, скачиваю Android NDK r27c с dl.google.com (~700 МБ)..."
  DMG="$WORK/android-ndk-r27c-darwin.dmg"
  [ -f "$DMG" ] || curl -L --fail -o "$DMG" https://dl.google.com/android/repository/android-ndk-r27c-darwin.dmg
  MNT="$(hdiutil attach -nobrowse -readonly "$DMG" | awk -F'\t' '/\/Volumes\//{print $NF}' | tail -1)"
  SRC="$(dirname "$(find "$MNT" -maxdepth 5 -name source.properties | head -1)")"
  mkdir -p "$HOME/Library/Android/ndk"
  echo "Копирую NDK из $SRC ..."
  rm -rf "$HOME/Library/Android/ndk/current"
  cp -R "$SRC" "$HOME/Library/Android/ndk/current"
  hdiutil detach "$MNT" >/dev/null || true
  NDK="$HOME/Library/Android/ndk/current"
fi
export ANDROID_NDK_HOME="$NDK"
echo "NDK: $ANDROID_NDK_HOME"

echo "== 2. Исходники Sable"
if [ ! -d "$WORK/sable" ]; then git clone https://github.com/ryanhcode/sable "$WORK/sable"; fi
cd "$WORK/sable"
git fetch --tags
# ищем коммит версии 2.0.6: тег или коммит, где в gradle.properties стоит 2.0.6
REV=$(git tag -l | grep -E '(^|[^0-9])2\.0\.6' | head -1 || true)
if [ -z "$REV" ]; then REV=$(git log --format=%H -S'2.0.6' -- gradle.properties | tail -1 || true); fi
if [ -z "$REV" ]; then echo "Не нашёл версию 2.0.6 в истории, соберу из main (проверьте, что main == 2.0.6)"; REV=main; fi
echo "Версия исходников: $REV"; git checkout -q "$REV"
git submodule update --init --recursive

echo "== 3. Сборка нативки для arm64-v8a"
CRATE=$(dirname "$(find "$WORK/sable/sable_rapier" -name Cargo.toml -not -path '*/target/*' | head -1)")
echo "Rust-проект: $CRATE"
cd "$CRATE"
# Sable закрепляет свою версию Rust (rust-toolchain), Android-цель нужно добавить именно в неё
TC="$(rustup show active-toolchain | awk '{print $1}')"
echo "Toolchain проекта: $TC"
rustup target add --toolchain "$TC" aarch64-linux-android
# без этого в .so попадают пути вида /Users/<имя>/.cargo
export RUSTFLAGS="--remap-path-prefix=$HOME=/home/user"
cargo ndk -t arm64-v8a --platform 26 build --release
SO=$(find "$CRATE/target/aarch64-linux-android/release" -maxdepth 1 -name '*.so' | head -1)
[ -n "$SO" ] || { echo "Не найден .so после сборки"; exit 1; }
echo "Собрано: $SO"

echo "== 4. Проверка JNI-функций (должны совпасть с Sable 2.0.6)"
MISS=0
while read -r line; do
  name=$(echo "$line" | sed -E 's/.* ([A-Za-z0-9_]+)\(.*/\1/')
  if ! strings "$SO" | grep -q "Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_${name}$"; then echo "  нет: $name"; MISS=1; fi
done < "$HERE/jni_sable_2.0.6.txt"
[ $MISS -eq 0 ] && echo "  все 53 функции на месте" || { echo "Нативка не совпадает с Sable 2.0.6, проверьте версию исходников"; exit 1; }

echo "== 5. Копирую нативку"
cp "$SO" "$HERE/sable_rapier_aarch64_android.so"
echo "Готово: $HERE/sable_rapier_aarch64_android.so, теперь собрать jar: ../build.sh"
