#!/bin/sh
# Публикация готового jar как ПРЕРЕЛИЗА на GitHub (обычные релизы не делаем).
# Использование: tools/publish_prerelease.sh <мод>-<версия> путь/к.jar [заметки]
# Пример:        tools/publish_prerelease.sh cld_far-0.2.0 far/dist/cld_far-neoforge-1.21.1-0.2.0.jar
# Кладёт jar в releases/<тег>/, коммитит и пушит тег; дальше workflow prerelease.yml создаёт prerelease.
set -e
TAG="$1"; JAR="$2"; NOTES="${3:-Тестовая сборка $1.}"
[ -n "$TAG" ] && [ -f "$JAR" ] || { echo "usage: $0 <tag> <jar> [notes]"; exit 1; }
cd "$(dirname "$0")/.."
mkdir -p "releases/$TAG"
cp "$JAR" "releases/$TAG/"
printf '%s\n' "$NOTES" > "releases/$TAG/NOTES.md"
git add "releases/$TAG"
git commit -m "Пререлиз $TAG"
git tag "$TAG"
git push origin HEAD "$TAG"
echo "https://github.com/hatelinks/cld-mc/releases/download/$TAG/$(basename "$JAR")"
