# Передача контекста: сборка «Create + MineColonies + Mystical Agriculture» (Minecraft 1.21.1, NeoForge 21.1.248)

Этот файл — история и подробные грабли сборки. Краткая карта проекта (что где лежит, версии, как собирать и тестировать) — в `CLAUDE.md`; при расхождении верить `CLAUDE.md` и `pack/MODS.md`.

## 1. Кто я и что за сборка
- Русскоязычный игрок, сборка 96 модов (список — `pack/MODS.md`), Prism Launcher на Mac (Java 21).
- Также играю на Android-телефоне, лаунчер **Amethyst**, рендерер **Kopper Zink (Vulkan)**, Java external 21.0.1. Для работы Sable на Android я написал свой мод **sable-android-support** (подменяет загрузку нативной библиотеки Rapier на Android-сборку + прореживает тики 16 блоков Simulated через coremod JS).
- Позже сборка пойдёт на сервер.
- Стабильно работает только **NeoForge 21.1.248** — менять его не хочу.
- Рабочая папка, подключаемая к Claude: `~/Claude/` (внутри `test-mc-pack/` — копия экземпляра, папка называется `minecraft — копия` с **неразрывным пробелом** перед тире; `new-mods/`; `sable-android-2.0/`).

## 2. Квесты FTB Quests (готово)
- 36 глав, 1309 квестов, рус. язык, группы: «Эра механизмов» (Create и аддоны, авиация), «Хозяйство и путешествия» (FD, Veggies, хранилища, Supplementaries, компасы, активности), «Мистическое земледелие» (MA + Agradditions), «Жизнь колонии» (MineColonies + WarNTaxes). Первая глава без группы — «Путь выжившего» (ванильные достижения, первый квест засчитывается входом в обычный мир).
- Все предметные задачи `consume_items: false` (автозасчёт, награду забираю вручную). Во всех главах `progression_mode: "flexible"` (тропы сохраняются, прогресс копится заранее).
- Вариантные квесты («любой цвет») засчитывают только базовый вариант — FTB Filter System в сборке нет.
- Награды: тематические ресурсы на ~каждом 4-м квесте, финал-шестерёнка в 28 главах (галочка + 200 XP + награда).
- Генератор квестов — Python-проект (папка `quest_builder/` в репозитории): `make.py` (data_base → specs/*.py → data → build.py → `out/ftbquests_config.zip` + `checklist.md`), `tools/lex.py` (рус. названия, подсказки, рецепты из jar), `tools/gen.py` (добавление квестов, автозависимости), `tools/layout.py` (автосетка), `jars_new/*.json` (ID предметов, модели, рецепты, теги, достижения, структуры — извлечены из jar сборки). Важные правила сборщика: ID должен иметь модель предмета (`_valid.json`), lang-строки без переносов (иначе все названия «Безымянный»), ID без старшего бита.
- `checklist.md`: 12 квестов-галочек вместо несуществующих предметов, ~360 описаний «по общим знаниям» для проверки.

## 3. Исправления сборки (сделано)
- Из `config` убраны в `_to_delete` 24 лишних файла (Sodium/Iris/Xaero/ImmediatelyFast/Explosion Overhaul/Better Mineshafts, MineColonies Tweaks/Compatibility, *.bak).
- Датапаки в `moonlight-global-datapacks` (Moonlight подключает их ко всем мирам; копии в репозитории: `pack/minecraft/moonlight-global-datapacks/`):
  - `pack_fixes.zip` — исправляет Tracks (ID `Tracks:` с заглавной → лут и добыча киркой; записи тегов `required:false`, иначе ломается весь `mineable/pickaxe`) и лут Veggies Delight (`lootTable` → `table`).
  - `colony_guard.zip` — Improved Mobs не трогает мобов MineColonies (`data/colony_guard/improvedmobs/entity_configs/minecolonies.json`, `features: []`) и не ворует из 58 хранилищ колонии (тег `improvedmobs:non_steal`). Проверено: в логе `Following entity overrides are loaded: [colony_guard:minecolonies]`.
- НЕ писать `"Feature Blacklist" = ["minecolonies"]` в improvedmobs/common.toml — это ломает загрузку (там имена функций, не модов).
- Вылет Veil «renderer is null» = в options.txt выключен `mod_resources` или сломался конфиг какого-то мода. Лечение: вернуть `mod_resources` / удалить строки resourcePacks.
- Sable 1.2.2 вылетает от огня на корабле → временно `/gamerule doFireTick false`.

## 4. Авиация и оптимизация (сделано)
- Авиация обновлена: Sable **2.0.6**, Create Aeronautics bundled **1.3.2**. Для Android — свой `sable-android-support 2.0.0` (пересобранная нативка Rapier под новый JNI Sable 2; скрипт сборки `~/Claude/sable-android-2.0/build_android_native.sh`). На Маке этот jar не нужен.
- На Маке стоят Sodium 0.8.13, FerriteCore 7.0.3, ModernFix 5.27.27, Lithium 0.15.4, Noisium(ed) 3.0.6. На Android Sodium не ставить (краши Aeronautics+Sodium на мобильных рендерерах).
- Java на Маке: **Azul Zulu 21.0.13**. С Java от Mojang (Microsoft 21.0.7) + G1 было два крэша JVM (SIGSEGV в C2 `Chunk::chop`, порча `G1MonotonicArena`) сразу после старта рендера DH. Потом на ZGC пошли крэши в самом ZGC (см. §7), поэтому итог: Zulu 21.0.13 (Java от Prism) + обычный G1 (аргументы Java пустые), память 2048–6144 МБ, PermGen 128. Так стоит в Prism (экземпляр «tc», сверено по скриншоту 29.10) и в `pack/instance.cfg`.
- Профилировать — Spark; свои оптимизации писать только против конкретного тормоза из отчёта.

## 5. Рельеф JJThunder To The Max (jjtm, папка `jjtm/`; в игре — gdn)
- Порт датапака 1.20.1 → 1.21.1, мод lowcodefml `jjthunder_to_the_max`, сейчас **0.3.3**. Варианты высоты мира: h2096 (оригинал), h1024, h512 — рельеф выше Y 63 сжимается сплайном поверх суммы offset+factor+jagged, корка над пещерами не истончается.
- Сейчас в игре стоит не jjtm, а **«Горы до небес» (gdn 1.0.0, папка `gdn/`)**: тот же рельеф jjtm как отдельные типы мира `gdn:h1024` и `gdn:h512` (fast-вариант), обычный мир остаётся ванильным, DH генерирует только в них. У gdn тот же modId `jjthunder_to_the_max` — jjtm вместе с ним не ставить.
- `-fast`: `size_vertical 2` (ячейка шума 8 блоков по высоте, как в ванилле) — вдвое меньше расчётов; во всех вариантах `overworld_determiner` обёрнут в `flat_cache`, пещерные 3D-шумы — в `cache_once` (результат тот же). Генерация стала «на порядок» быстрее.
- Пещеры: толще корка (сплайн 0.045/0.09) и условный biome modifier убирает карвер `bettercaves:surface_cave` (главный источник дыр в поверхности). Сейчас Better Caves в `pack/MODS.md` нет (в `config/` осталась только папка `bettercaves`).
- Структуры: теги `has_structure` (replace:false) добавляют 39 наземным биомам деревни, аванпосты, особняки и т.п. по климату.
- Облака Cirrus: `assets/minecraft/cirrus/cloud_dimensions/overworld.json` с `cloud_height` 1051/513/256 (пропорционально высоте мира); jjtm грузится после cirrus (optional dependency, ordering AFTER). Caelus — это API элитр, к облакам отношения не имеет.
- Сборка: `jjtm/build.sh` (port.py → tweaks.py → variant.py) → `jjtm/dist/`.

## 6. Свои моды
- **cld_pregen 1.3.2** (`pregen/`): кнопка «Предпросмотр» на вкладке «Мир» над «Тип мира». Создаёт мир, замораживает его и прегенерирует по спирали с живой картой; «Применить сид» пересоздаёт мир, «Оставить мир»/«Назад». На время карты: прорисовка 2, DH на паузе через его API (генерация и рендер выкл., 1 поток), всё возвращается при выходе. Важные грабли: в замороженном мире не снимаются тикеты `UNKNOWN` от `getChunkFuture` — мод снимает их сам (иначе утечка памяти и остановка генерации); перед `mc.disconnect()` нужен `level.disconnect()` (иначе зависание).
- **cld_structures 1.0.0** (`structures/`): mixin в лямбду `ChunkGenerator.createStructures` — набор структур пропускает чанк, если рядом может появиться структура более редкого набора (больше spacing) того же этапа генерации с подходящим биомом. Детерминированно (зависит только от сида и координат). Настройки: `config/cld_structures.properties`.
- **cld_menu 1.0.1** (`menu/`): своё главное меню (пиксельный пейзаж, логотип TelipailoCraft, без Realms и «Моды»). **cld_earlywindow 1.0.0** (`earlywindow/`): свой экран загрузки, включён на ПК (`earlyWindowProvider = "cldwindow"` в `fml.toml`); на Android пока стандартный, пока не проверен там.
- tcw и gdn — см. §5 и §10.
- Сборка без Gradle: `LIBS=<jar NeoForge 21.1.248> <мод>/build.sh` (javac против `neoforge-*-client.jar` + `client-*-srg.jar`; `-proc:none`). Подробно — `CLAUDE.md`.

## 7. Дальняя прорисовка и конфиги
- 29.10 (оптимизация, `pack/OPTIMIZATION.md`): DH решено заменить на cld_far, в копии на Маке DH, EMF и ETF уже выключены. В мирах gdn дальней прорисовки без DH не будет, cld_far работает только в tcw.
- Distant Horizons 3.3.3: дальность LOD 256 на Маке (на телефоне ~64), обычная прорисовка 16 (`options.txt`), автообновление DH выключено. `guiScale:6` выставлен специально — не менять. Cirrus: совместимость с DH и «Sync Cloud Distance» включены.
- Предупреждение DH про WWOO: возможны линии сетки в LOD, которые DH генерирует сам; нормальные чанки рисуются без них.
- Логи: сообщения про `alexsmobs`, `iceandfire`, `ars_nouveau`, `quark`, `mowziesmobs` — из построек IDAS (мобы и предметы модов, которых нет), безвредны. Ошибки тегов IDAS bop/byg — нет BOP/BYG.

- Краши Java на Mac (код 6, SIGSEGV в ZGC): причина была в ZGC и максимуме памяти 8 ГБ на 16 ГБ Mac. Помогло: убрать `-XX:+UseZGC -XX:+ZGenerational` (обычный G1), память 2048–6144 МБ. Sable и spark не виноваты.
- Спам «CSA config read» от Create Structures Arise в debug.log глушит tcw с 0.4.3.

## 8. Репозиторий и сборка на GitHub
- `pack/` — конфиги экземпляра (Prism): `minecraft/config`, `defaultconfigs`, `options.txt`, `instance.cfg`, `mmc-pack.json`, `MODS.md` (список модов, версии, SHA-1). Полный архив с jar модов, ресурспаками и шейдером — в GitHub Releases (`cld-mc-pack.zip`, импорт в Prism).
- Перед заливкой сборки убирать: логи, крэши, hs_err/replay, сохранения, journeymap, baritone, blueprints, usercache/usernamecache, servers.dat, command_history, кэши, `.sable`, `pregen`, `config/sodium-fingerprint.json`, `*.bak`, отключённые jar; в options.txt — lastServer и размер окна; в instance.cfg — путь к Java. Затем искать ники, UUID, пути `/Users/…`, модель компьютера, почту, токены — в т.ч. внутри jar/zip.
- После каждого готового изменения сразу заливать на GitHub, предварительно проверив новые файлы на личные данные. Сейчас: каждый тред работает в своей ветке `claude/*` и отдаёт результат через PR в `main`; в коммитах и PR не писать `Claude-Session` и ссылки на сессии Claude.

## 9. Дальше
- Отдельный серверный набор (без клиентских модов).
- Пересобрать квесты после обновления авиации (ID предметов могли поменяться).
- Проверить в игре cld_structures и облака на новом мире.
- У Claude нет интернета для скачивания модов: моды скачиваю я сам и кладу в `~/Claude/new-mods` или `test-mc-pack/mods`; Claude читает jar и логи из подключённой папки.

## 10. Свой тип мира TelipailoCraft (папка `tcw/`)
- Анкета: https://claude.ai/artifact/137rSpH4xjY4DQUgefVCuh (ответы в db `answers/latest`).
- Кратко: свой генератор с нуля; добавляется рядом с «Горы до небес»; высота 768 (от -128 до 640); хребты с равнинами, горные системы 1–3 тыс. блоков; характер гор зависит от климата/биома; континенты с океанами; реки от гор до океана; снег по склонам, снеговая линия по климату; ледники с трещинами, ледяными пещерами, ледопадами; свои пещеры вместо Better Caves; биомы WWOO + свои горные; структуры только на ровном; DH генерирует; скорость на Android не важна.
- Сделано (tcw 0.5.0, в игре пока 0.4.3; `tcw/build.sh`, данные мира генерирует `tcw/tools/build_data.py`): тип мира по умолчанию; свой рельеф (`gen/Macro`, `gen/Terrain`, сток рек `gen/Hydro`); реки, озёра, водопады (`WaterFeature`), скалы и слои (`RockFeature`), снег и ледники (`SnowFeature`); свои пещеры: гигантские залы, подземные реки, ледяные ходы, сеноты (`gen/Caves`, `CaveFeature`), Better Caves в этом мире выключен; свой источник биомов `TcwBiomeSource`: ванильные биомы по климату + горные пояса (subalpine_forest, alpine_meadow, alpine_tundra, scree_slopes, glacier, volcanic_slopes) и пещерные биомы YUNG; спавн в долине (`Spawn`); `/locate tcw …` (`LocateCommand`); карта мира на экране создания мира и `/tcwmap` (`client/MapScreen`); «Настроить» с 6 настройками (`gen/Options`, хранятся в level.dat в источнике биомов); WWOO-«заплатки» из мёртвых кораллов, бетона и терракоты вне бэдлендса убраны во всех мирах; горные озёра не стоят стеной над склоном (0.4.2); спам CSA в логе заглушён (0.4.3); каменные арки и карнизы с навесами, `/locate tcw arch` (0.5.0); карнизов в ~10 раз меньше, 1-2 на гору, и нет летающих кусков камня над тонкими пиками (0.5.2).
- Проверка вне игры: генерация чанков без запуска игры (свой тест на классах Minecraft), превью карт `tcw/tools/Preview.java` (работает в облаке без libs, см. `CLAUDE.md`), нагрузочный тест `qa/bench`.
- Ещё не сделано: «живые» горы (животные, туман, звуки), опасности (рыхлый снег, мобы), структуры только на ровном и свои горные структуры.
- **На потом (просьба запомнить):** лавины, камнепады, разреженный воздух на пиках, холод на высоте.
