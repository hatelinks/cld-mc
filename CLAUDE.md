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
- Не коммитить `build/`, `dist/` у menu/earlywindow/gdn/tcw (см. `.gitignore`); готовые jar для владельца класть в `/mnt/project-files/` (папка проекта).
- Интернета для скачивания модов нет: моды скачивает владелец.
- Профилировать — Spark; оптимизировать только то, что видно в отчёте.

## Что где лежит

| Папка | Что это | Версия | Сборка |
|---|---|---|---|
| `tcw/` | Свой тип мира **TelipailoCraft** (modId `tcw`): рельеф, реки, ледники, пещеры, горные биомы, `/locate tcw`, `/tcwmap`, карта и настройки на экране создания мира. Тип мира по умолчанию, высота 768 (−128…640). | 0.5.3 | `tcw/build.sh` (версия зашита в скрипте). Данные мира генерирует `tools/build_data.py` из `client-*-extra.jar`. |
| `pregen/` | **cld_pregen** «Предпросмотр мира»: прегенерация с живой картой на экране создания мира. | 1.3.2 | `pregen/build.sh` |
| `structures/` | **cld_structures** «Структуры без наложений»: структуры разных модов не появляются друг в друге. Конфиг `config/cld_structures.properties`. | 1.0.0 | `structures/build.sh` |
| `jjtm/` | Порт датапака JJThunder To The Max 1.20.1 → 1.21.1 (modId `jjthunder_to_the_max`, личное использование). Варианты высоты h2096/h1024/h512, `-fast` = ячейка шума 8 блоков. Сейчас в игре вместо него стоит gdn. | 0.3.3 | `jjtm/build.sh` (Python, без Java): `original/` → `src/` → `dist/` |
| `gdn/` | «Горы до небес»: тот же рельеф jjtm как отдельные типы мира `gdn:h1024`, `gdn:h512`. **Стоит в игре.** Тот же modId `jjthunder_to_the_max`, что у jjtm, — вместе с jjtm не ставить. | 1.0.0 | `gdn/build.sh` (берёт данные из `../jjtm/src`) |
| `menu/` | **cld_menu** «Главное меню сборки»: пиксельный пейзаж вместо панорамы, логотип TelipailoCraft, без Realms и «Моды». | 1.0.1 | `menu/build.sh` |
| `earlywindow/` | **cld_earlywindow**: свой экран загрузки (провайдер `cldwindow`). Не мод, а сервис FML; jar кладётся в `mods/`. Включается в `config/fml.toml`: `earlyWindowProvider = "cldwindow"` — в `pack/` (экземпляр ПК) включён. На Android пока оставить `fmlearlywindow`, пока не проверено там отдельно. | 1.0.0 | `earlywindow/build.sh` |
| `far/` | **cld_far** «Дальний рельеф»: лёгкая замена Distant Horizons для миров TelipailoCraft. Рельеф за прорисовкой считается по сиду генератора tcw (без чанков), тайлы LOD, цвета по биомам, настройки — F7. Нужен tcw; на сервере тоже ставить (передаёт сид). Вне игры: `far/tools/FarPreview`. С 0.5.1 тайлы вне поля зрения не рисуются, с 0.5.2 тайлы в fastutil-картах (в `HashMap<Long>` ключи сталкивались). | 0.5.2 | `far/build.sh` (компилирует и `../tcw`) |
| `perf/` | **cld_perf** «Ускорения клиента сборки»: ровный ограничитель кадров (ванильный на Mac терял 12-15% кадров). Место для мелких клиентских правок по замерам. | 0.1.0 | `perf/build.sh` |
| `autotest/` | **cld_autotest**, служебный, в игру не ставить: с `config/cld_autotest.properties` сам заходит в мир, меряет FPS, CPU, память, пишет JFR и выходит. Свойства: `world`, `warmup`, `measure`, `pause`, `yaw`, `fly`, `maxfps`, `jfr`, `spark`, `setup`, `quit`. | 0.1.3 | `autotest/build.sh` |
| `sable_android/` | **sable-android-support** (modId `sable_android`): нативка Rapier для Sable на Android + прореживание тиков блоков Simulated (coremod JS). Только для Android, на Маке не нужен. Исходники восстановлены из jar. | 2.0.0 | `sable_android/build.sh`; нативку пересобирать `sable_android/native/build_android_native.sh` (на Маке) |
| `quest_builder/` | Генератор квестов FTB Quests (36 глав, ~1300 квестов). | — | `python3 -I quest_builder/make.py` → `out/ftbquests_config.zip` + `out/checklist.md` |
| `pack/` | Конфиги экземпляра Prism (`minecraft/config`, `defaultconfigs`, `options.txt`, `instance.cfg`, `mmc-pack.json`) и `MODS.md` — список модов с SHA-1. Полный архив с jar — в Releases (`cld-mc-pack.zip`). | — | — |

Свои датапаки сборки (`pack_fixes.zip`, `colony_guard.zip`) лежат у владельца в `moonlight-global-datapacks`, описаны в `HANDOFF.md` §3.

`pack/OPTIMIZATION.md` — разбор производительности клиента (29.10): какие конфиги ускорены (lambdynlights fast, Flywheel TRI_LINEAR, без проверки обновлений, JourneyMap без топо-слоя) и какие моды сменены: DH, EMF, ETF убраны, вместо DH cld_far (нужен tcw 0.5.x). Dynamic FPS, Entity Culling, ImmediatelyFast владелец качает сам: modrinth и github из облака закрыты (403).

Замеры 30.10 (`pack/OPTIMIZATION.md`, третий заход): в копии добавлены Dynamic FPS, Entity Culling, ImmediatelyFast, BadOptimizations, Clumps, Packet Fixer, cld_far 0.5.2, cld_perf. Видеокарта не узкое место; при полёте нагрузка в генерации и в растекании воды новых чанков океана (передано треду генерации).

`pack/MODS.md` — что реально стоит в игре (обновлять по jar из папки `mods` владельца). В репозитории tcw 0.5.3 (быстрее генерация). В копии на Маке после оптимизации tcw 0.5.2 и cld_far 0.5.2 без DH; в экземпляре Prism пока tcw 0.4.3 и DH, пока владелец не перенесёт.

Исходники всех наших модов есть в репозитории и собираются (проверено 29.10: размеры jar совпадают с выданными). jjtm оставлен как архив и не развивается: его заменили gdn и tcw.

## Как собирать Java-моды
Gradle не используется. Каждый `build.sh` вызывает `javac --release 21` против библиотек NeoForge 21.1.248 из папки `LIBS` (по умолчанию `../../libs`, то есть рядом с клоном репозитория):

```sh
LIBS=/путь/к/libs tcw/build.sh      # → tcw/dist/tcw-neoforge-1.21.1-0.5.3.jar
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
- JFR: `jfr print` режет стек до 5 кадров, нужен `--stack-depth 64`. `ServerChunkCache.getChunk` в потоке рендера из `ClientLevel.animateTick` это ошибка атрибуции JFR, не настоящий заход в серверный кеш. Сравнивать FPS только без лимита: ванильный лимит на Mac сам срезает кадры (с cld_perf уже нет).
- Скорость генерации tcw (0.5.3): мерить `qa/bench/server_pregen.sh` (JFR) и разбирать по профилю. Почти всё время уходит на заполнение шумом (doFill) на всю высоту 768, свет и фичи мелочь. Пустое небо над рельефом не заполняется (`SkyBound`, `NoiseFillMixin`, выключить `-Dtcw.fullFill=true`), водоносный слой и подсыпка структур над поверхностью отдают ответ сразу (`AquiferMixin`, `BeardifierMixin`), макро-образцы рельефа кэшируются (`Terrain.sampleC`), кольца крепостей хранятся в `world/data/tcw_rings.txt`. Всё это точно, рельеф не меняется; новые функции плотности, которые считаются на каждый блок (как `peak`), сразу видны в профиле, их проверять замером. Noisiumed в сборке переписывает лямбду fillFromNoise и setBlockState в doFill, с нашей вставкой в начало doFill совместим.
- Квесты: ID предмета должен иметь модель (`_valid.json`), lang-строки без переносов.
