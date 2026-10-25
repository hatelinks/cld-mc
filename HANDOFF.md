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

## 4. Текущая задача — оптимизация (шаг 7), в процессе
- Sodium у меня крашился, т.к. Sodium 0.8 несовместим с Sable 1.2 (нужен 0.6.13). Решили обновить авиацию:
  - Sable **2.0.6** и Create Aeronautics bundled **1.3.2** (внутри Simulated 1.3.2, Offroad 1.3.2) лежат в `~/Claude/new-mods`.
  - Sable 2.0.6 объявляет несовместимыми Sodium < 0.8.12-alpha.2 и Sable Companion > 1.6.0; требует NeoForge ≥ 21.1.228 (наш 248 подходит).
  - JNI-интерфейс Rapier в Sable 2 изменился (53 функции, сцена int→long) → нативку пересобрали на Маке скриптом `~/Claude/sable-android-2.0/build_android_native.sh` (rustup-цель aarch64-linux-android для закреплённого toolchain, cargo-ndk, NDK r27c). Исходники: коммит `ad8b4d3` (тег mc1.21.1-2.0.6). Проверка в скрипте через `strings` на Маке ложно ругается — сама библиотека верна (все 53 JNI-функции есть).
  - Готов `sable-android-support-neoforge-1.21.1-2.0.0.jar` (в `new-mods`): новая .so, версия 2.0.0, зависимость sable `[2.0.0,)`; coremod и классы прежние (loadLibrary по-прежнему через System.load, все 16 классов Simulated с tick() на месте).
- **Следующие шаги:**
  1. Положить в `new-mods` новые версии аддонов авиации: Create Propulsion (Simulated), Tracks, Compact Flap, Aeroworks, Climbable Ropes, Waystones Sable, Jade-VS — Claude проверит совместимость с Simulated 1.3.2 / Sable 2.
  2. Запуск на Маке без Sodium (убрать старые sable 1.2.2, aeronautics 1.2.1 и sable-android-support 1.2.0) → Claude смотрит лог.
  3. Телефон с sable-android-support 2.0.0.
  4. Sodium **0.8.13** на Маке (на Android Sodium не ставить — известны краши Aeronautics+Sodium на мобильных рендерерах).
  5. Затем по одному: FerriteCore 7.0.3, ModernFix 5.27.26, Entity Culling 1.11.3 (только клиент), Lithium 0.15.4. Память на Маке: мин = макс 6–8 ГБ.
  6. Отдельный серверный набор (без клиентских модов).
  7. Пересобрать квесты после обновления авиации (ID предметов могли поменяться) — нужен `quest_builder.zip` и новые jar.
- У Claude нет интернета для скачивания модов: моды скачиваю я сам и кладу в `~/Claude/new-mods` или `test-mc-pack/mods`; Claude читает jar и логи из подключённой папки.
