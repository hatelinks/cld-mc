# Сборка Minecraft 1.21.1 (NeoForge 21.1.248)

Рабочий репозиторий сборки «Create + MineColonies + Mystical Agriculture» для Minecraft 1.21.1 на NeoForge 21.1.248.
Играем на ПК (Prism Launcher, Mac, Java 21) и на Android (Amethyst, Kopper Zink/Vulkan), позже будет сервер.
Здесь лежат исходники наших модов, генератор квестов, конфиги экземпляра и инструменты проверки.

- [`CLAUDE.md`](CLAUDE.md): карта проекта, правила работы, версии, грабли.
- [`HANDOFF.md`](HANDOFF.md): история сборки и подробные грабли.
- [`pack/`](pack/): конфиги экземпляра Prism и [`MODS.md`](pack/MODS.md) (что стоит в игре, с SHA-1). Разбор производительности клиента: [`pack/OPTIMIZATION.md`](pack/OPTIMIZATION.md).

## Наши моды

| Мод | Папка | Что делает | Версия | Карта кода |
|---|---|---|---|---|
| TelipailoCraft (`tcw`) | [`tcw/`](tcw/) | Свой тип мира по умолчанию: рельеф, реки, ледники, пещеры, горные биомы, `/locate tcw`, `/tcwmap`, карта и настройки на экране создания мира. Высота 768 (от -128 до 640). | 0.5.8 | [docs/tcw.md](docs/tcw.md) |
| Дальний рельеф (`cld_far`) | [`far/`](far/) | Лёгкая замена Distant Horizons для миров TelipailoCraft: рельеф за прорисовкой считается по сиду, без чанков. Свет, вода, леса, пресеты, настройки на F7, команды `/cldfar`. Нужен tcw, на сервере тоже ставить. | 1.0.0 | [docs/cld_far.md](docs/cld_far.md) |
| Предпросмотр мира (`cld_pregen`) | [`pregen/`](pregen/) | Прегенерация с живой картой на экране создания мира. | 1.3.2 | [docs/pregen.md](docs/pregen.md) |
| Структуры без наложений (`cld_structures`) | [`structures/`](structures/) | Структуры разных модов не появляются друг в друге. Конфиг `config/cld_structures.properties`. | 1.0.0 | [docs/structures.md](docs/structures.md) |
| Горы до небес (`gdn`) | [`gdn/`](gdn/) | Рельеф jjtm как отдельные типы мира `gdn:h1024` и `gdn:h512`. modId тот же, что у jjtm, вместе не ставить. | 1.0.0 | [docs/gdn.md](docs/gdn.md) |
| Главное меню (`cld_menu`) | [`menu/`](menu/) | Пиксельный пейзаж вместо панорамы, логотип TelipailoCraft, без Realms и «Моды». | 1.0.1 | [docs/menu.md](docs/menu.md) |
| Экран загрузки (`cld_earlywindow`) | [`earlywindow/`](earlywindow/) | Свой экран загрузки, провайдер `cldwindow` в `config/fml.toml`. Сервис FML, jar кладётся в `mods/`. | 1.0.0 | [docs/earlywindow.md](docs/earlywindow.md) |
| Ускорения клиента (`cld_perf`) | [`perf/`](perf/) | Ровный ограничитель кадров и мелкие клиентские правки по замерам. | 0.1.0 | [docs/perf.md](docs/perf.md) |
| Sable для Android (`sable_android`) | [`sable_android/`](sable_android/) | Нативка Rapier для Sable на Android и прореживание тиков блоков Simulated. Только для Android. | 2.0.0 | [docs/sable_android.md](docs/sable_android.md) |
| Автотест (`cld_autotest`) | [`autotest/`](autotest/) | Служебный, в игру не ставить: сам заходит в мир, меряет FPS, CPU, память, пишет JFR и выходит. | 0.1.3 | [docs/autotest.md](docs/autotest.md) |
| JJThunder To The Max (порт) | [`jjtm/`](jjtm/) | Архив: неофициальный порт датапака 1.20.1 на 1.21.1 для личного использования (лицензия оригинала All Rights Reserved). Источник данных для gdn, не развивается. | 0.3.3 | [docs/jjtm.md](docs/jjtm.md) |

## Остальное

| Папка | Что это | Описание |
|---|---|---|
| [`quest_builder/`](quest_builder/) | Генератор квестов FTB Quests (36 глав, около 1300 квестов): `python3 -I quest_builder/make.py`, результат в `out/ftbquests_config.zip` и `out/checklist.md`. | [docs/quest_builder.md](docs/quest_builder.md) |
| [`qa/`](qa/) | Проверки перед выдачей: статическая проверка jar и датапаков, смоук-тест сервера, разбор логов и JFR, нагрузочный тест генератора tcw. | [docs/qa.md](docs/qa.md), [qa/README.md](qa/README.md) |
| [`tools/`](tools/) | `fetch_libs.sh` собирает библиотеки NeoForge для сборки модов, `publish_prerelease.sh` готовит пререлиз. | |

## Где скачать

Готовые jar наших модов выходят только пререлизами: [Releases](https://github.com/hatelinks/cld-mc/releases).
Тег имеет вид `<мод>-<версия>`, например `cld_far-1.0.0`. Полный архив сборки с jar (`cld-mc-pack.zip`) тоже лежит в Releases.

## Как собирать

Gradle не используется. Каждый `build.sh` вызывает `javac --release 21` против библиотек NeoForge 21.1.248 из папки `LIBS` (по умолчанию `../../libs`, рядом с клоном):

```sh
LIBS=/путь/к/libs tcw/build.sh            # tcw/dist/tcw-neoforge-1.21.1-0.5.8.jar
VERSION=1.3.3 LIBS=/путь/к/libs pregen/build.sh
```

Папку `LIBS` можно собрать скриптом `tools/fetch_libs.sh [папка]`, если есть сеть до maven.neoforged.net и mojang.com. В CI собрать нельзя: библиотек NeoForge там нет.
Версия мода поднимается в его `build.sh` (переменная `VERSION`).

Выпуск пререлиза:

```sh
tools/publish_prerelease.sh cld_far-1.0.0 far/dist/cld_far-neoforge-1.21.1-1.0.0.jar
```

Скрипт кладёт jar в `releases/<тег>/`, при пуше workflow [`prerelease.yml`](.github/workflows/prerelease.yml) создаёт тег и пререлиз.

Генератор рельефа tcw можно проверять без библиотек NeoForge: пакет `dev.cld.tcw.gen` от Minecraft не зависит.

```sh
javac -d /tmp/tcw tcw/src/main/java/dev/cld/tcw/gen/*.java tcw/tools/*.java
java -cp /tmp/tcw Preview <seed> <cx> <cz> <размер> <блоков на пиксель> out.png
```

## Настройки Prism (Mac)

Java Azul Zulu 21.0.13, аргументы Java пустые (обычный G1), память 2048-6144 МБ. ZGC и 8 ГБ давали крэши.
