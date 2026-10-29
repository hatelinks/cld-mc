# Сборка Minecraft 1.21.1 (NeoForge 21.1.248)

Рабочий репозиторий сборки «Create + MineColonies + Mystical Agriculture» для ПК (Prism Launcher) и Android (Amethyst).

- `CLAUDE.md` — карта проекта: версии, где что лежит, как собирать и тестировать.
- `HANDOFF.md` — история сборки и подробные грабли.
- `pack/` — конфиги экземпляра Prism и `MODS.md` (что стоит в игре, с SHA-1). Полная сборка с jar — архив в Releases.

## Свои моды и данные

| Папка | Что это |
|---|---|
| `tcw/` | Тип мира TelipailoCraft (tcw): свой рельеф, реки, ледники, пещеры, горные биомы, карта мира. Мир по умолчанию. |
| `far/` | Дальний рельеф (cld_far): лёгкая дальняя прорисовка для миров TelipailoCraft вместо Distant Horizons. |
| `pregen/` | Предпросмотр мира (cld_pregen): прегенерация с живой картой на экране создания мира. |
| `structures/` | Структуры без наложений (cld_structures): структуры разных модов не появляются друг в друге. |
| `gdn/` | «Горы до небес»: рельеф jjtm как отдельные типы мира. Стоит в игре вместо jjtm. |
| `jjtm/` | Неофициальный порт датапака JJThunder To The Max (1.20.1) на 1.21.1 для личного использования (лицензия оригинала: All Rights Reserved). Источник данных для gdn. |
| `menu/` | Главное меню сборки (cld_menu). |
| `earlywindow/` | Свой экран загрузки (cld_earlywindow, провайдер `cldwindow`). |
| `quest_builder/` | Генератор квестов FTB Quests: `python3 -I quest_builder/make.py` → `out/ftbquests_config.zip`. |
| `qa/` | Проверки перед выдачей: статическая проверка jar и датапаков, смоук-тест сервера, разбор логов, бенчмарк генератора. |
| `tools/` | `fetch_libs.sh` — собрать библиотеки NeoForge для сборки модов без Prism. |

Java-моды собираются без Gradle: `LIBS=<папка с jar NeoForge 21.1.248> <мод>/build.sh` → `<мод>/dist/`. Подробнее — в `CLAUDE.md`.

## Настройки Prism (Mac)
Java Azul Zulu 21.0.13, аргументы Java пустые (обычный G1), память 2048–6144 МБ. ZGC и 8 ГБ давали крэши.
