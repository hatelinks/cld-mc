# CLAUDE.md — карта проекта cld-mc

Сборка «Create + MineColonies + Mystical Agriculture» для Minecraft **1.21.1, NeoForge 21.1.248** (версию NeoForge не менять).
Играют на ПК (Prism Launcher, Mac, Java 21) и на Android (Amethyst, Kopper Zink/Vulkan). Позже — сервер.
Язык общения с владельцем и игровых текстов — русский. Подробная история и грабли — в `HANDOFF.md` (читать перед работой над конкретной частью).

## Правила работы
- Работать в этом репозитории в облаке, а не на Маке владельца (там мало места). Мак (`~/Claude/`) — только чтобы прочитать jar и логи из игры.
- Каждый тред работает в своей ветке `claude/*` и отдаёт результат через PR в `main`. Отделы (треды) короткие, под задачу; результат перед выдачей владельцу проходит тестирование и проверку координатора.
- Комментарии в коде: только однострочные `//`, без `/** */` и Javadoc. Коротко, простыми словами, как пишет человек; без длинных тире, стрелок, кавычек-ёлочек и прочих спецсимволов (правило владельца).
- В коммитах и PR не добавлять строки `Claude-Session` и ссылки на сессии Claude (правило владельца).
- Перед коммитом проверять новые файлы на личные данные: ники, UUID, пути `/Users/…`, почту, токены, в том числе внутри jar/zip.
- Каждая новая версия любого нашего мода выходит на GitHub **только пререлизом** (prerelease, обычные релизы не делать — пока тестируем). Готовый jar публиковать `tools/publish_prerelease.sh <мод>-<версия> путь/к.jar` (тег вида `cld_far-0.2.0`; jar кладётся в `releases/<тег>/`, workflow `.github/workflows/prerelease.yml` при пуше создаёт тег и prerelease; push тегов из облака запрещён). Сборка в CI невозможна: libs NeoForge там нет. Ссылку на скачивание давать владельцу.
- Не коммитить `build/`, `dist/` у menu/earlywindow/tcw (см. `.gitignore`); готовые jar для владельца класть в `/mnt/project-files/` (папка проекта).
- Интернета для скачивания модов нет: моды скачивает владелец.
- Профилировать — Spark; оптимизировать только то, что видно в отчёте.
- Перед работой над модом читать его карту кода `docs/<мод>.md` (таблица ниже), а не разбирать исходники с нуля. Код открывать только в тех файлах, которые карта называет для задачи. Кто меняет мод, в том же PR обновляет его карту: новые классы и миксины, настройки, версию, грабли.

## Что где лежит

| Папка | Что это | Версия | Сборка |
|---|---|---|---|
| `tcw/` | Свой тип мира **TelipailoCraft** (modId `tcw`): рельеф, реки, ледники, пещеры, горные биомы, `/locate tcw`, `/tcwmap`, карта и настройки на экране создания мира. Тип мира по умолчанию, высота 768 (−128…640). | 0.5.8 | `tcw/build.sh` (версия зашита в скрипте). Данные мира генерирует `tools/build_data.py` из `client-*-extra.jar`. |
| `pregen/` | **cld_pregen** «Предпросмотр мира»: прегенерация с живой картой на экране создания мира. | 1.3.2 | `pregen/build.sh` |
| `structures/` | **cld_structures** «Структуры без наложений»: структуры разных модов не появляются друг в друге. Конфиг `config/cld_structures.properties`. | 1.0.0 | `structures/build.sh` |
| `menu/` | **cld_menu** «Главное меню сборки»: пейзаж из блоков Minecraft вместо панорамы (погода при каждом запуске со звуками, время по часам, сезоны), логотип TelipailoCraft, без Realms и «Моды»; экран «TC Моды» (настройки всех наших модов) в настройках и меню паузы; замер запуска в `logs/cld_startup.log`. | 1.5.0 | `menu/build.sh` |
| `earlywindow/` | **cld_earlywindow**: свой экран загрузки (провайдер `cldwindow`). Не мод, а сервис FML; jar кладётся в `mods/`. Включается в `config/fml.toml`: `earlyWindowProvider = "cldwindow"` — в `pack/` (экземпляр ПК) включён. На Android пока оставить `fmlearlywindow`, пока не проверено там отдельно. | 1.0.1 | `earlywindow/build.sh` |
| `far/` | **cld_far** «Дальний рельеф»: лёгкая замена Distant Horizons для миров TelipailoCraft. Рельеф за прорисовкой считается по сиду генератора tcw (без чанков), тайлы LOD, цвета по биомам, настройки — F7. Нужен tcw; на сервере тоже ставить (передаёт сид). Вне игры: `far/tools/FarPreview`. С 0.6.0 стык с чанками по маске (с учётом Sodium), каменные столбы tcw рисуются отдельно, настройки по разделам; 0.7.0 свет и цвет (солнце, впадины, бока блоков, слои мезы, погода); 0.8.0 вода, леса, небо (волны, блики, лёд, кроны, тени облаков, туман в низинах); 0.9.0 скорость (выборка и сетка раздельно, кэш рельефа в памяти и на диске `cld_far_cache/`, лимит видеопамяти, статистика в F3); 1.0.0 пресеты, команды `/cldfar`, клавиши дальности, без рельефа в пещерах; 1.0.1 без светлой сетки по стыкам тайлов на воде, льду и лесах. Карта мода для работы: `docs/cld_far.md`. | 1.0.1 | `far/build.sh` (компилирует и `../tcw`) |
| `perf/` | **cld_perf** «Ускорения клиента сборки»: ровный ограничитель кадров (ванильный на Mac терял 12-15% кадров). Место для мелких клиентских правок по замерам. | 0.1.0 | `perf/build.sh` |
| `test_panel/` | **cld_test_panel** «Панель тестирования»: полный форк Baritone 1.11.3 (LGPL-3.0, `test_panel/UPSTREAM.md`), дальше в нём интерфейс в стиле Nodus по клавише. Только клиент; одиночная игра и свой сервер из списка в настройках, без боевых функций. Вместе с обычным Baritone не ставить. 0.2.0: окна Baritone, Инфо, Панель (таскать, свернуть, закрепить на HUD), клавиши Y и U, кнопка в Esc; 0.3.0 размер вместе с «Размером интерфейса» игры, бинды клавиш как в Nodus; 0.3.1 без кнопки в меню Esc (только клавиша Y); 0.4.0 окна всех настроек Baritone, путевых точек и радара; 0.5.0 подсказки к настройкам при наведении, яркость, xray и поиск блоков рамками, полёт, свободная камера; 0.6.0 NoFall и FastBreak через пакеты, Step, Jesus, Spider, Phase, Timer, AutoTool, AutoFish, ESP, Tracers; 0.6.1 без надписей о включении функций на экране; 0.6.2 названия функций по-английски как в Nodus с подсказками, исправлен Freecam. | 0.6.2 | `test_panel/build.sh` (javac; Gradle Baritone в облаке не работает) |
| `autotest/` | **cld_autotest**, служебный, в игру не ставить: с `config/cld_autotest.properties` сам заходит в мир, меряет FPS, CPU, память, пишет JFR и выходит. Свойства: `world`, `warmup`, `measure`, `pause`, `yaw`, `fly`, `maxfps`, `jfr`, `spark`, `setup`, `quit`. | 0.1.3 | `autotest/build.sh` |
| `sable_android/` | **sable-android-support** (modId `sable_android`): нативка Rapier для Sable на Android + прореживание тиков блоков Simulated (coremod JS). Только для Android, на Маке не нужен. Исходники восстановлены из jar. | 2.0.0 | `sable_android/build.sh`; нативку пересобирать `sable_android/native/build_android_native.sh` (на Маке) |
| `bombs/` | **cld_bombs** «Бомбы с уровнями»: дополнение к бомбам Supplementaries, уровни 1-10, прокачка порохом, кратер за несколько тиков, уровни в сундуках. Нужен Supplementaries 4.0. | 1.0.0 | `MODS=/mnt/project-files/libs-mods bombs/build.sh` |
| `datapacks/` | Свои датапаки сборки (сейчас `cld_hourglass`: гравипесок Quark в песочных часах). | — | `datapacks/build.sh` → `datapacks/dist/*.zip` |
| `quest_builder/` | Генератор квестов FTB Quests (36 глав, ~1300 квестов). | — | `python3 -I quest_builder/make.py` → `out/ftbquests_config.zip` + `out/checklist.md` |
| `pack/` | Конфиги экземпляра Prism (`minecraft/config`, `defaultconfigs`, `options.txt`, `instance.cfg`, `mmc-pack.json`) и `MODS.md` — список модов с SHA-1. Полный архив с jar — в Releases (`cld-mc-pack.zip`). | — | — |

Свои датапаки сборки (`pack_fixes.zip`, `colony_guard.zip`) лежат у владельца в `moonlight-global-datapacks`, описаны в `HANDOFF.md` §3.

`pack/OPTIMIZATION.md` — разбор производительности клиента (29.10): какие конфиги ускорены (lambdynlights fast, Flywheel TRI_LINEAR, без проверки обновлений, JourneyMap без топо-слоя) и какие моды сменены: DH, EMF, ETF убраны, вместо DH cld_far (нужен tcw 0.5.x). Dynamic FPS, Entity Culling, ImmediatelyFast владелец качает сам: modrinth и github из облака закрыты (403).

Замеры 30.10 (`pack/OPTIMIZATION.md`, третий заход): в копии добавлены Dynamic FPS, Entity Culling, ImmediatelyFast, BadOptimizations, Clumps, Packet Fixer, cld_far 0.5.2, cld_perf. Видеокарта не узкое место; при полёте нагрузка в генерации и в растекании воды новых чанков океана (передано треду генерации).

`pack/MODS.md` — что реально стоит в игре (обновлять по jar из папки `mods` владельца). В репозитории tcw 0.5.8 (быстрее генерация, нет лага при загрузке океанов). В копии на Маке после оптимизации tcw 0.5.2 и cld_far 0.5.2 без DH; в экземпляре Prism пока tcw 0.4.3 и DH, пока владелец не перенесёт.

## Карты кода модов
Одна страница на мод: назначение, какой файл за что отвечает, миксины и хуки, настройки, сборка, грабли, версии.

| Мод | Карта |
|---|---|
| tcw (TelipailoCraft) | `docs/tcw.md` |
| cld_far | `docs/cld_far.md` |
| cld_pregen | `docs/pregen.md` |
| cld_structures | `docs/structures.md` |
| cld_menu | `docs/menu.md` |
| cld_earlywindow | `docs/earlywindow.md` |
| cld_perf | `docs/perf.md` |
| cld_test_panel (форк Baritone) | `docs/test_panel.md` |
| cld_autotest | `docs/autotest.md` |
| sable-android-support | `docs/sable_android.md` |
| cld_bombs | `docs/bombs.md` |
| quest_builder | `docs/quest_builder.md` |
| qa и cld_qabench | `docs/qa.md` |

Исходники всех наших модов есть в репозитории и собираются (проверено 29.10: размеры jar совпадают с выданными). Порт JJThunder To The Max (jjtm) и «Горы до небес» (gdn) удалены 30.10: чужой датапак (All Rights Reserved) не распространяем, вместо них tcw. 31.10 история git переписана без них (git filter-repo, force push всех веток), теги пререлизов переносит владелец скриптом с Мака: push тегов из облака запрещён. Чужие моды и датапаки в репозиторий и релизы не класть.

## Как собирать Java-моды
Gradle не используется. Каждый `build.sh` вызывает `javac --release 21` против библиотек NeoForge 21.1.248 из папки `LIBS` (по умолчанию `../../libs`, то есть рядом с клоном репозитория):

```sh
LIBS=/путь/к/libs tcw/build.sh      # → tcw/dist/tcw-neoforge-1.21.1-0.5.8.jar
VERSION=1.3.3 LIBS=... pregen/build.sh
```

Собрать `LIBS` можно и в облаке, если открыта сеть: `tools/fetch_libs.sh [папка]` (установщик NeoForge + библиотеки ванили); готовая копия лежит в `/mnt/project-files/libs/`.
В `LIBS` нужны `neoforge-21.1.248-client.jar`, `client-1.21.1-*-srg.jar`, `client-1.21.1-*-extra.jar` и остальные jar из `libraries` Prism (готовая копия — `/mnt/project-files/libs/`). Если её нет, а сеть до maven.neoforged.net и mojang.com закрыта, попросить владельца положить libs туда.

Версию мода поднимать в `build.sh` (переменная `VERSION`) при каждом выпуске; в `neoforge.mods.toml` стоит `${version}`.

## Как тестировать
- tcw вне игры, **без libs NeoForge**: пакет `dev.cld.tcw.gen` не зависит от Minecraft, поэтому генератор рельефа и инструменты из `tcw/tools/` (`Preview`, `Bench`, `Find`, `Dbg`) собираются и запускаются в облаке как есть:
  ```sh
  javac -d /tmp/tcw tcw/src/main/java/dev/cld/tcw/gen/*.java tcw/tools/*.java
  java -cp /tmp/tcw Preview <seed> <cx> <cz> <размер блоков> <блоков на пиксель> out.png   # карта сверху с отмывкой
  ```
  Остальное (фичи, биомы, миксины) проверяется только сборкой против libs и в игре.
- Логи и крэши из игры читаются из папки владельца на Маке; безвредный шум в логах описан в `HANDOFF.md` §7.
- `qa/` (см. `qa/README.md`): статическая проверка jar и датапаков, смоук-тест сервера, разбор логов, нагрузочный тест генератора tcw (`qa/bench`).
- В игре владелец проверяет только на новых мирах/чанках: изменения генерации не трогают уже созданные чанки.

## Грабли, которые легко повторить
- В `build.sh` `ls a b` сортирует по алфавиту, и `client-*-srg.jar` (ваниль) оказывается в classpath раньше `neoforge-*-client.jar` (пропатченные классы): методы из патчей NeoForge не находятся. Перечислять отдельными `ls`, как в `far/build.sh`.
- `guiScale:6` в `pack/minecraft/options.txt` выставлен владельцем специально — не менять.
- Java на Маке: Zulu 21.0.13, обычный G1 без аргументов, память 2048–6144 МБ (`pack/instance.cfg`). ZGC и 8 ГБ давали крэши — не возвращать.
- Android: Sodium не ставить; для Sable нужен свой `sable-android-support` (на Маке не нужен).
- improvedmobs: не писать `"Feature Blacklist" = ["minecolonies"]` — ломает загрузку.
- Вылет Veil «renderer is null» — выключен `mod_resources` в options.txt.
- cld_pregen: в замороженном мире самому снимать тикеты `UNKNOWN`; перед `mc.disconnect()` вызывать `level.disconnect()`.
- Нативки Rust (sable_android) по умолчанию содержат пути `/Users/<имя>/.cargo`: собирать с `--remap-path-prefix`, перед коммитом проверять `strings`.
- tcw: карнизы (`gen/Rocks`) появляются не в каждой полосе склона, а пятнами (шум `pick`, порог `PICK`): владелец хочет 1-2 на гору. Верх тонких пиков защищён от ванильных пещер (`ColumnDensity.peak`, `max` в final_density), иначе entrances/noodle отрезают куски и они висят в воздухе (0.5.2).
- Запуск игры на Маке из облака: Remote Control, экземпляр Prism `cld-test` (симлинк на копию), `prismlauncher --launch cld-test -o tester` (офлайн). В имени папки копии `tc — копия` перед тире неразрывный пробел. Мак качает пререлизы только из открытого репозитория.
- Prism держит `instance.cfg` в памяти и перезаписывает его при выходе; `JvmArgs` с запятыми без кавычек теряется молча. Править так: закрыть Prism, записать `JvmArgs="..."` в кавычках, потом запускать.
- JFR: `jfr print` режет стек до 5 кадров, нужен `--stack-depth 64`. `ServerChunkCache.getChunk` в потоке рендера из `ClientLevel.animateTick` это ошибка атрибуции JFR, не настоящий заход в серверный кеш. Сравнивать FPS только без лимита: ванильный лимит на Mac сам срезает кадры (с cld_perf уже нет).
- Скорость генерации tcw (0.5.3): мерить `qa/bench/server_pregen.sh` (JFR) и разбирать по профилю. Почти всё время уходит на заполнение шумом (doFill) на всю высоту 768, свет и фичи мелочь. Пустое небо над рельефом не заполняется (`SkyBound`, `NoiseFillMixin`, выключить `-Dtcw.fullFill=true`), водоносный слой и подсыпка структур над поверхностью отдают ответ сразу (`AquiferMixin`, `BeardifierMixin`), макро-образцы рельефа кэшируются (`Terrain.sampleC`), кольца крепостей хранятся в `world/data/tcw_rings.txt`. Всё это точно, рельеф не меняется; новые функции плотности, которые считаются на каждый блок (как `peak`), сразу видны в профиле, их проверять замером. Noisiumed в сборке переписывает лямбду fillFromNoise и setBlockState в doFill, с нашей вставкой в начало doFill совместим.
- Полная сборка генерирует в 2-3 раза медленнее, чем один tcw (5.5-6 чанков/с против 15): почти половина времени у фич других модов. Дороже всего wythers stone_cliffs (две фичи по 12%), yungscavebiomes lost_caves_surface_replace (2 по 8%), прочая покраска wythers, Quark DeferredFeature (большие каменные кластеры, корунд), в океане ещё wythers replace_dirt_to_sand и supplementaries shore_barnacles. Время по каждой фиче печатает `qabench` (строки `QAFEAT` в конце прогона); размер замера `SIZE=` у `server_pregen.sh`. Полная сборка на сервере: `BASE=~/qa-server/full`. С 0.5.5 `DiskFeatureMixin` + `Precheck` сначала проверяют условие диска по одному блоку в точке (точно, мир тот же): stone_cliffs вдвое дешевле, вся полная сборка 7.1-7.3 стала 7.9 чанка/с. В 0.5.6 `OreFeatureMixin` (`OreSkip`) не считает облако руды, если в секциях вокруг него нет заменяемых блоков (после формы облака случайные числа больше не тратятся, поэтому точно), а `ReplaceBlobsMixin` при поиске цели сверху вниз пропускает секции без нужного блока в палитре: 8.6 чанка/с. Сверять миры полной сборки только с повтором той же версии: сама сборка от прогона к прогону даёт десятки тысяч отличий (песок и трава у берегов, капельник, руды, кусты Quark). Кэш чанков в `WorldGenRegion` пробовали: выигрыш около 2%, не оставили. В 0.5.7 по решению владельца убраны (biome modifier в `tcw/src/main/resources/data/tcw/neoforge/biome_modifier/`, во всех мирах, с условием на мод) обрывы wythers `stone_cliffs*` и поверхностная замена YUNG `lost_caves_surface_replace(_2)`: полная сборка 11.6 стала 15.7 чанка/с (машина в облаке в разное время даёт разную скорость, сравнивать только прогоны подряд). В 0.5.8 `CaveFeature` берёт столбец рельефа лениво (`Caves.gallery` с Supplier, проверка высоты после шума, сверено со старой версией на миллионе столбцов): tcw:caves 10.7 с стало 2.1 с, 17.2 чанка/с. По решению владельца в `pack/minecraft/config/quark-common.toml` выключены большие каменные кластеры Quark (`"Big Stone Clusters" = false`, около 8%). Дальше по времени фич: wythers base_savanna, scree и grass_spread (владелец пока оставил). Поиск биома у Biolith (внутри Quark) около 5% и не наш.
- Лаг при загрузке океана (0.5.4): ванильный водоносный слой 1.21.1 помечает на дообработку почти всю воду, и при загрузке чанка она тикает на главном потоке (в глубоком океане tcw ~15 тысяч блоков на чанк). `FluidPostProcessMixin` пропускает тик источника, вокруг которого снизу и по бокам только такие же источники или сплошные блоки. Зависание при загрузке 256 океанских чанков стало 2.5 с вместо 10.9 с.
- JEI 19.70 и All The Leaks 1.1.12 несовместимы: миксин ATL `SmithingCategoryExtensionMixin` (фикс FixCopyItemStack) считает, что в слоте 0 лежит `this`, а JEI 19.70 собран оптимизатором и переиспользует слот 0. Итог: `VerifyError: Bad type on operand stack` в `SmithingCategoryExtension.setOutput` при входе в мир (30.10). ATL убран из `mods` копии (лежит в `pack-update-backup-2026-10-30/mods`); вернуть только с версией ATL, где это исправлено, или с JEI 19.27.
- cld_test_panel это полный форк Baritone (пакеты `baritone.*`): вместе с обычным `baritone-api-neoforge` не ставить, иначе игра не запускается (`ResolutionException: Modules baritoe and cld_test_panel export package baritone.api.schematic`, 31.10). Обычный Baritone убран из `mods` копии в `pack-update-backup-2026-10-30/mods`.
- Квесты: ID предмета должен иметь модель (`_valid.json`), lang-строки без переносов.
