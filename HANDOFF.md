# Передача контекста: сборка «Create + MineColonies + Mystical Agriculture» (Minecraft 1.21.1, NeoForge 21.1.248)

Этот файл — сжатая память предыдущего диалога. Вставьте его в новый диалог (или приложите вместе с `quest_builder.zip`) и напишите, с чего продолжить.

## 1. Кто я и что за сборка
- Русскоязычный игрок, сборка ~96 модов, Prism Launcher на Mac (Java 21, 8 ГБ).
- Также играю на Android-телефоне, лаунчер **Amethyst**, рендерер **Kopper Zink (Vulkan)**, Java external 21.0.1. Для работы Sable на Android я написал свой мод **sable-android-support** (подменяет загрузку нативной библиотеки Rapier на Android-сборку + прореживает тики 16 блоков Simulated через coremod JS).
- Позже сборка пойдёт на сервер.
- Стабильно работает только **NeoForge 21.1.248** — менять его не хочу.
- Рабочая папка, подключаемая к Claude: `~/Claude/` (внутри `test-mc-pack/` — копия экземпляра, папка называется `minecraft — копия` с **неразрывным пробелом** перед тире; `new-mods/`; `sable-android-2.0/`).

## 2. Квесты FTB Quests (готово)
- 36 глав, 1309 квестов, рус. язык, группы: «Эра механизмов» (Create и аддоны, авиация), «Хозяйство и путешествия» (FD, Veggies, хранилища, Supplementaries, компасы, активности), «Мистическое земледелие» (MA + Agradditions), «Жизнь колонии» (MineColonies + WarNTaxes). Первая глава без группы — «Путь выжившего» (ванильные достижения, первый квест засчитывается входом в обычный мир).
- Все предметные задачи `consume_items: false` (автозасчёт, награду забираю вручную). Во всех главах `progression_mode: "flexible"` (тропы сохраняются, прогресс копится заранее).
- Вариантные квесты («любой цвет») засчитывают только базовый вариант — FTB Filter System в сборке нет.
- Награды: тематические ресурсы на ~каждом 4-м квесте, финал-шестерёнка в 28 главах (галочка + 200 XP + награда).
- Генератор квестов — Python-проект (архив `quest_builder.zip`): `make.py` (data_base → specs/*.py → data → build.py → `out/ftbquests_config.zip` + `checklist.md`), `tools/lex.py` (рус. названия, подсказки, рецепты из jar), `tools/gen.py` (добавление квестов, автозависимости), `tools/layout.py` (автосетка), `jars_new/*.json` (ID предметов, модели, рецепты, теги, достижения, структуры — извлечены из jar сборки). Важные правила сборщика: ID должен иметь модель предмета (`_valid.json`), lang-строки без переносов (иначе все названия «Безымянный»), ID без старшего бита.
- `checklist.md`: 12 квестов-галочек вместо несуществующих предметов, ~360 описаний «по общим знаниям» для проверки.

## 3. Исправления сборки (сделано)
- Из `config` убраны в `_to_delete` 24 лишних файла (Sodium/Iris/Xaero/ImmediatelyFast/Explosion Overhaul/Better Mineshafts, MineColonies Tweaks/Compatibility, *.bak).
- Датапаки в `moonlight-global-datapacks` (Moonlight подключает их ко всем мирам):
  - `pack_fixes.zip` — исправляет Tracks (ID `Tracks:` с заглавной → лут и добыча киркой; записи тегов `required:false`, иначе ломается весь `mineable/pickaxe`) и лут Veggies Delight (`lootTable` → `table`).
  - `colony_guard.zip` — Improved Mobs не трогает мобов MineColonies (`data/colony_guard/improvedmobs/entity_configs/minecolonies.json`, `features: []`) и не ворует из 58 хранилищ колонии (тег `improvedmobs:non_steal`). Проверено: в логе `Following entity overrides are loaded: [colony_guard:minecolonies]`.
- НЕ писать `"Feature Blacklist" = ["minecolonies"]` в improvedmobs/common.toml — это ломает загрузку (там имена функций, не модов).
- Вылет Veil «renderer is null» = в options.txt выключен `mod_resources` или сломался конфиг какого-то мода. Лечение: вернуть `mod_resources` / удалить строки resourcePacks.
- Sable 1.2.2 вылетает от огня на корабле → временно `/gamerule doFireTick false`.

## 4. Авиация и оптимизация (сделано)
- Авиация обновлена: Sable **2.0.6**, Create Aeronautics bundled **1.3.2**. Для Android — свой `sable-android-support 2.0.0` (пересобранная нативка Rapier под новый JNI Sable 2; скрипт сборки `~/Claude/sable-android-2.0/build_android_native.sh`). На Маке этот jar не нужен.
- На Маке стоят Sodium 0.8.13, FerriteCore 7.0.3, ModernFix 5.27.27, Lithium 0.15.4, Noisium(ed) 3.0.6. На Android Sodium не ставить (краши Aeronautics+Sodium на мобильных рендерерах).
- Java на Маке: **Azul Zulu 21.0.13**, аргументы `-XX:+UseZGC -XX:+ZGenerational`, память 6–8 ГБ. С Java от Mojang (Microsoft 21.0.7) + G1 было два крэша JVM (SIGSEGV в C2 `Chunk::chop`, порча `G1MonotonicArena`) сразу после старта рендера DH; после смены Java и GC крэшей нет.
- Профилировать — Spark; свои оптимизации писать только против конкретного тормоза из отчёта.

## 5. Рельеф JJThunder To The Max (jjtm, папка `jjtm/`)
- Порт датапака 1.20.1 → 1.21.1, мод lowcodefml `jjthunder_to_the_max`, сейчас **0.3.3**. Варианты высоты мира: h2096 (оригинал), h1024, h512 — рельеф выше Y 63 сжимается сплайном поверх суммы offset+factor+jagged, корка над пещерами не истончается. В игре стоит **h1024-fast**.
- `-fast`: `size_vertical 2` (ячейка шума 8 блоков по высоте, как в ванилле) — вдвое меньше расчётов; во всех вариантах `overworld_determiner` обёрнут в `flat_cache`, пещерные 3D-шумы — в `cache_once` (результат тот же). Генерация стала «на порядок» быстрее.
- Пещеры: толще корка (сплайн 0.045/0.09) и условный biome modifier убирает карвер `bettercaves:surface_cave` (главный источник дыр в поверхности).
- Структуры: теги `has_structure` (replace:false) добавляют 39 наземным биомам деревни, аванпосты, особняки и т.п. по климату.
- Облака Cirrus: `assets/minecraft/cirrus/cloud_dimensions/overworld.json` с `cloud_height` 1051/513/256 (пропорционально высоте мира); jjtm грузится после cirrus (optional dependency, ordering AFTER). Caelus — это API элитр, к облакам отношения не имеет.
- Сборка: `jjtm/build.sh` (port.py → tweaks.py → variant.py) → `jjtm/dist/`.

## 6. Свои моды
- **cld_pregen 1.2.0** (`pregen/`): кнопка «Предпросмотр» на вкладке «Мир» над «Тип мира». Создаёт мир, замораживает его и прегенерирует по спирали с живой картой; «Применить сид» пересоздаёт мир, «Оставить мир»/«Назад». На время карты: прорисовка 2, DH на паузе через его API (генерация и рендер выкл., 1 поток), всё возвращается при выходе. Важные грабли: в замороженном мире не снимаются тикеты `UNKNOWN` от `getChunkFuture` — мод снимает их сам (иначе утечка памяти и остановка генерации); перед `mc.disconnect()` нужен `level.disconnect()` (иначе зависание).
- **cld_structures 1.0.0** (`structures/`): mixin в лямбду `ChunkGenerator.createStructures` — набор структур пропускает чанк, если рядом может появиться структура более редкого набора (больше spacing) того же этапа генерации с подходящим биомом. Детерминированно (зависит только от сида и координат). Настройки: `config/cld_structures.properties`.
- Сборка обоих без Gradle: `LIBS=<jar NeoForge 21.1.248> <мод>/build.sh` (javac против `neoforge-*-client.jar` + `client-*-srg.jar`; у cld_structures `-proc:none`).

## 7. Дальняя прорисовка и конфиги
- Distant Horizons 3.3.3: дальность LOD 256 на Маке (на телефоне ~64), обычная прорисовка 32, автообновление DH выключено. Cirrus: совместимость с DH и «Sync Cloud Distance» включены.
- Предупреждение DH про WWOO: возможны линии сетки в LOD, которые DH генерирует сам; нормальные чанки рисуются без них.
- Логи: сообщения про `alexsmobs`, `iceandfire`, `ars_nouveau`, `quark`, `mowziesmobs` — из построек IDAS (мобы и предметы модов, которых нет), безвредны. Ошибки тегов IDAS bop/byg — нет BOP/BYG.

## 8. Репозиторий и сборка на GitHub
- `pack/` — конфиги экземпляра (Prism): `minecraft/config`, `defaultconfigs`, `options.txt`, `instance.cfg`, `mmc-pack.json`, `MODS.md` (список модов, версии, SHA-1). Полный архив с jar модов, ресурспаками и шейдером — в GitHub Releases (`cld-mc-pack.zip`, импорт в Prism).
- Перед заливкой сборки убирать: логи, крэши, hs_err/replay, сохранения, journeymap, baritone, blueprints, usercache/usernamecache, servers.dat, command_history, кэши, `.sable`, `pregen`, `config/sodium-fingerprint.json`, `*.bak`, отключённые jar; в options.txt — lastServer и размер окна; в instance.cfg — путь к Java. Затем искать ники, UUID, пути `/Users/…`, модель компьютера, почту, токены — в т.ч. внутри jar/zip.

## 9. Дальше
- Отдельный серверный набор (без клиентских модов).
- Пересобрать квесты после обновления авиации (ID предметов могли поменяться).
- Проверить в игре cld_structures и облака на новом мире.
- У Claude нет интернета для скачивания модов: моды скачиваю я сам и кладу в `~/Claude/new-mods` или `test-mc-pack/mods`; Claude читает jar и логи из подключённой папки.

## 10. Свой тип мира TelipailoCraft (папка `tcw/`)
- Анкета: https://claude.ai/artifact/137rSpH4xjY4DQUgefVCuh (ответы в db `answers/latest`).
- Кратко: свой генератор с нуля; добавляется рядом с «Горы до небес»; высота 768 (от -128 до 640); хребты с равнинами, горные системы 1–3 тыс. блоков; характер гор зависит от климата/биома; континенты с океанами; реки от гор до океана; снег по склонам, снеговая линия по климату; ледники с трещинами, ледяными пещерами, ледопадами; свои пещеры вместо Better Caves; биомы WWOO + свои горные; структуры только на ровном; DH генерирует; скорость на Android не важна.
- Сделано (tcw 0.4.1, `tcw/build.sh`, данные мира генерирует `tcw/tools/build_data.py`): тип мира по умолчанию; свой рельеф (`gen/Macro`, `gen/Terrain`, сток рек `gen/Hydro`); реки, озёра, водопады (`WaterFeature`), скалы и слои (`RockFeature`), снег и ледники (`SnowFeature`); свои пещеры: гигантские залы, подземные реки, ледяные ходы, сеноты (`gen/Caves`, `CaveFeature`), Better Caves в этом мире выключен; свой источник биомов `TcwBiomeSource`: ванильные биомы по климату + горные пояса (subalpine_forest, alpine_meadow, alpine_tundra, scree_slopes, glacier, volcanic_slopes) и пещерные биомы YUNG; спавн в долине (`Spawn`); `/locate tcw …` (`LocateCommand`); карта мира на экране создания мира и `/tcwmap` (`client/MapScreen`); «Настроить» с 6 настройками (`gen/Options`, хранятся в level.dat в источнике биомов); WWOO-«заплатки» из мёртвых кораллов, бетона и терракоты вне бэдлендса убраны во всех мирах.
- Проверка вне игры: генерация чанков без запуска игры (свой тест на классах Minecraft), превью карт `tcw/tools/Preview.java`.
- Ещё не сделано: «живые» горы (животные, туман, звуки), опасности (рыхлый снег, мобы), структуры только на ровном и свои горные структуры, нависания и арки.
- **На потом (просьба запомнить):** лавины, камнепады, разреженный воздух на пиках, холод на высоте.
