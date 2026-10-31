# Откуда взят Baritone

- Репозиторий: https://github.com/cabaletta/baritone
- Ветка `1.21.1`, коммит `11ea66cd11c3bdf2eb7a8dfdbdec97b964fda6c7`, версия 1.11.3.
- Лицензия LGPL-3.0 (`LICENSE`), авторы Baritone: leijurv, Brady. Наши правки тоже под LGPL-3.0, исходники открыты в этом репозитории.

Раскладка: `src/api`, `src/main`, `src/launch`, `src/schematica_api` как в оригинале, `src/neoforge` это `neoforge/src/main` оригинала.

Первый коммит папки (`test_panel: исходники Baritone 1.11.3 ...`) это оригинал без изменений, всё после него наши правки:
`git diff <тот коммит> -- test_panel` показывает их целиком.

Чтобы взять новые исправления Baritone, сравнить ветку `1.21.1` оригинала с коммитом выше и перенести diff в `src/`.
