# cld_test_panel: Панель тестирования (форк Baritone)

Карта кода. Читать вместо разбора исходников; кто меняет мод, обновляет эту страницу в своём PR.

- modId `cld_test_panel`, папка `test_panel/`, версия **0.1.0** (`VERSION` в `test_panel/build.sh`). Только клиент.
- Полный форк Baritone 1.11.3 (ветка `1.21.1`), откуда и что взято: `test_panel/UPSTREAM.md`. Лицензия LGPL-3.0, наши правки под ней же.
- Цель: Baritone с интерфейсом в стиле старого клиента Nodus (окна по категориям, перетаскивание, закрепление на HUD, консоль), открывается клавишей и кнопкой в меню Esc. Только для тестирования сборки в одиночной игре: боевых функций и обходов для серверов нет и не будет.
- Вместе с обычным Baritone не ставить: те же пакеты `baritone.*` и тот же `mixins.baritone.json`.
- План по этапам: Claude Doc «План: Baritone с панелью в стиле Nodus».

## Сборка
```sh
LIBS=/mnt/project-files/libs test_panel/build.sh   # test_panel/dist/cld_test_panel-neoforge-1.21.1-0.1.0.jar
```
Gradle у Baritone (unimined) в облаке не работает, его maven отвечают 403, поэтому `javac` как у остальных модов. Миксинам refmap не нужен: NeoForge 1.21.1 в игре на mojmap.
nether-pathfinder (элитры в Незере, нативки внутри) достаётся из официального `baritone-unoptimized-neoforge-1.11.3.jar`: лежит в `/mnt/project-files/libs/`, если нет, скрипт скачивает его с GitHub. Брать именно unoptimized: в обычных jar он прогнан ProGuard и методы переименованы.
Набор файлов в jar 0.1.0 совпадает с официальным unoptimized jar (плюс `LICENSE-baritone`).

## Файлы
| Путь | Что там |
|---|---|
| `src/api` | API Baritone (`BaritoneAPI`, `Settings` на 249 настроек, команды, процессы, путевые точки). |
| `src/main` | Сам Baritone: поиск пути, процессы, команды (`command/defaults`), рендер пути. |
| `src/launch` | Миксины (`mixins.baritone.json`) и `BaritoneMixinConnector`. |
| `src/neoforge` | Класс мода `BaritoneForgeModXD` (`@Mod("cld_test_panel")`) и `neoforge.mods.toml`. |
| `src/schematica_api` | Заглушки Schematica, только для компиляции, в jar не идут. |

## Наши правки поверх Baritone
- modId `baritoe` стал `cld_test_panel`, версия Minecraft только 1.21.1, сторона CLIENT.
- `PathRenderer`: убрана аннотация `@Nullable` из `org.jetbrains.annotations` (её нет в libs).

## Грабли
- Миксины подключаются через манифест (`MixinConfigs`, `MixinConnector`), как у оригинала; манифест пишет `build.sh`.
