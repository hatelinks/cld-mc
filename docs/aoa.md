# Разбор чужой сборки All of Create: Aeronautics (aoa)

Справка для тредов, которые берут идеи из этой сборки. Сама сборка лежит у владельца в `~/Claude/handoff/aoa` (экземпляр Prism, 496 МБ), её не менять и в репозиторий не класть: моды и датапаки чужие.

- CurseForge: All of Create - Aeronautics v2.9 (id 1518930), автор quickqwek. Minecraft 1.21.1, NeoForge 21.1.264 (у нас 21.1.248).
- 226 jar в `mods`, 264 позиции в манифесте (часть это ресурспаки и шейдеры).
- Свой мод `Aoc_Core_1.21_v2.jar` (modId `aoc`) без кода: только картинки для квестов (`allofcreate:textures/logo.png`, `qb_image1.png`, `qb_aeroclaims1.png`).
- Глобальные датапаки через Paxi (`config/paxi/datapacks`), настройки по умолчанию через Configured Defaults, меню FancyMenu, экран загрузки Drippy.

## Квесты (FTB Quests 2101.1.37)
`config/ftbquests/quests`: 28 глав, 577 квестов, тексты только в `lang/en_us.snbt` (и pt_br).

### Вкладки
Две главы без группы сверху, потом 5 групп:

| Группа | Главы (квестов) |
|---|---|
| без группы | Tutorial (6), To Know (3) |
| Create | Getting Started (3), Andesite Age (63), Copper Age (16), Brass Age (30), Locomotive Age (12) |
| Aeronautics | Simulated (27), Aeronautics (14), Off-Road (9), Propulsion (15) |
| Engineer's Paradise | Aquatic Ambitions (23), BB Fuels (17), Connected (32), Deep Dark (11), Enchantment Industry (11), Fun Additions (16), Optical (24), Petrochem (21), Pump Dat Oil (14), New Age (36), Electro Energetics (51), Gadgets & Gizmos (19) |
| Transportation | Trains (23), Aircrafts (22), Minecarts (14) |
| Farming and Food | Create Kitchen (18), Confectionery (27) |

### Оформление
- Названия групп цветные, жирные и подчёркнутые: `&e&l&nCreate`, `&b&l&nAeronautics`, `&6&l&nTransportation`.
- Названия глав с цветом по смыслу и пробелом в начале: `&8Andesite Age` (серый), `&cCopper Age` (красный), `&eBrass Age` (жёлтый), `&8Locomotive Age`.
- В заголовках квестов цветом выделен предмет: `Craft a &6Mechanical Press&r`, `&7Andesite&r, the Foundation`.
- В описаниях цвета по смыслу: `&6` предметы и механики, `&b` клавиши и интерфейс, `&9` вода, `&a` полезное, `&d&l` JEI и названия модов, `&6&l` Create.
- Картинки в описаниях: `{image:allofcreate:textures/qb_image1.png width:397 height:140 align:center fit:true}` (скриншот подсказки «Hold [W] to Ponder», схема Aero Claims).
- Картинки на поле главы (`images`): логотип сборки в Tutorial и To Know, иконки по теме (торт, трезубец, курица) рядом с квестами.
- `data.snbt`: форма по умолчанию `circle`, пресеты `normal` квадрат 1.0, `info` шестерня 1.0, `goal` шестиугольник 2.0. На деле: вехи шестерня 1.5, приветствие шестиугольник или квадрат 2.0, редкие сердце, пятиугольник.
- В каждой главе аддона первый квест это «Welcome to X» (галочка, большая форма, рассказ о моде).
- Иконка книги квестов заменена ресурспаками `Create_FTBQuests.zip` и `AOC Resources.zip` (16x16 и 18x18 в стиле Create).
- `ftbquests-client.snbt`: список глав всегда открыт (`chapter_panel_pinned: true`), закреплённые квесты справа, масштаб 0.75.
- Тексты короткие: у 435 квестов заголовок, у 368 подзаголовок в одну фразу, полное описание только у 52 (приветствия и сложные механики). Остальное отдано Ponder и JEI.

### Обучение
Глава Tutorial: один большой квест-галочка (награда хлеб), после него по одному появляются квесты про инвентарь и FTB Teams, книгу квестов, управление (R, U, C, M), JEI (поиск по `@мод`), конец обучения со ссылкой на Discord. Скрытие сделано через `hide_until_deps_visible: true` и `invisible_until_tasks: 1` (квест виден только после первой задачи, задача-галочка плюс задача «хлеб», который нельзя съесть до конца обучения). Глава To Know: три квеста про особенности сборки (Aero Claims, резина вместо сушёной ламинарии в рецептах, Stock Market).

### Прогрессия
- `progression_mode: "linear"` во всей книге (квест нельзя сдать, пока не сданы зависимости). Внутри глав почти цепочки: 514 зависимостей на 577 квестов.
- Между главами зависимостей почти нет (две ссылки на главу Simulated). Хребет это эпохи Create: андезит, медь, латунь, поезда. Аддоны открыты сразу, их порядок задают только вкладки.
- Задачи: 727 на предметы, 24 галочки, 12 достижений (в основном поезда), по одной на структуру и наблюдение.

### Награды
- 187 раз опыт (25-50, вехи 50), 162 случайные награды из таблиц (`random`), 81 `loot`, 5 `all_table`, 16 предметов, 1 выбор.
- Таблицы в `reward_tables`: `create` (андезитовый сплав, шестерни, валы, листы, водяное колесо, трубы, ремни, корпуса), `ingots` (железо, медь, цинк, латунь), `xp` (самородки опыта, 100 и 500 опыта, 5 уровней). У случайных наград `exclude_from_claim_all: true`, чтобы их открывали по одной.

### Сравнение с нашими квестами (quest_builder)
| | aoa | у нас |
|---|---|---|
| Квестов, глав | 577, 28 | 1304, 36 |
| Язык | английский | русский |
| Описания | короткие, полные у 9% | у всех, автоописание (как получить, зачем нужен) |
| Цвета в названиях глав и групп | есть | нет |
| Обучение и «полезно знать» | есть | нет (первая глава «Путь выжившего» по достижениям) |
| Картинки | логотип, иконки, скриншоты в тексте | нет |
| Награды | опыт плюс случайные из 3 таблиц | опыт у всех плюс предметы у 28% |
| Прогрессия | linear | flexible в каждой главе |
| Формы | круги, вехи шестерни | круги, rsquare, hexagon, gear |

## Генерация мира и «летающие острова»
- Обычный мир ванильный. Своих генераторов рельефа нет, TerraBlender и Lithostitched стоят как библиотеки.
- Летающие корабли над землёй это **Integrated Villages** (CraisinLord): деревня `airship_village` из кораблей на 100 блоков выше поверхности (`project_start_to_heightmap WORLD_SURFACE_WG` плюс `start_height absolute 100`, `terrain_adaptation none`), набор `air_villages`, spacing 115 и separation 90 чанков, в любом биоме обычного мира. Остальные 10 деревень (таверна, пиратская, средиземноморская, кутча, оазис, мшистые холмы, хижины, Quark минка, рыночная, clockwork, затонувшая) в наборе `regular_villages` (spacing 26). В конфиге `Disable Vanilla Villages = true` и `Activate Create Contraptions = true` (в деревнях работающие механизмы Create).
- Для tcw: над горами выше 540 корабли ушли бы за потолок мира (640). Датапак `datapacks/cld_airships` ограничивает `y_allowance.max_y_allowed = 590` (поле Integrated API, `min_y_allowed` и `max_y_allowed`). С cld_structures ещё проверить в игре.
- Пресеты Simulated (есть и у нас, мод в Create Aeronautics bundled): `simulated:airship_ready` (суперплоский на y=63 для проверки кораблей) и `simulated:end_sea` (пустой Энд как «море», игрок появляется на одном физическом блоке камня, `sable:floating_materials/end_stone` даёт энд-камню подъёмную силу).
- Create Submarine (Deep Seas) добавляет измерение `create_abyss:abyss` (море до y=700) и углубление океанов через Lithostitched, в aoa оба выключены (`enableDeeperOceans = false`).

## Структуры
- Integrated Dungeons and Structures 1.13.7 (у нас та же), Integrated Villages 1.3.3, Integrated Stronghold 1.1.4, Integrated API.
- Structure Essentials: быстрый поиск структур, радиусы locate 110 и карт 40; минимальное расстояние между структурами выключено.
- Loot Integrations: подмешивает лут модов в сундуки структур.
- YUNG's API без структурных модов YUNG.

## Моды и фишки
Всего около 40 модов совпадают с нашими (Create 6.0.10, Aeronautics 1.3.2, Sable 2.0.6, Quark, Supplementaries, FD, IDAS, Jade, JEI). Чего у нас нет:
- Аддоны Create: New Age, Diesel Generators, Petrochem, Electro Energetics, Power Grid, Optical, Deep Dark, Blaze Burner Fuels, Hypertubes, Kart, Train Parts, Railways Navigator, Bells & Whistles, Escalated, Copycats+, Framed, Design n Decor, Dreams n Desires, Gears n Kinetics, Bits n Bobs, Factory, Encased, Fantasizing, Garnished, Central Kitchen, Food, Confectionery, Cafe, Winery, Chocolate Fountain, Slice & Dice, Mechanical Botany, Integrated Farming, Metalwork, Prism, Vibrant Vaults, Mobile Packages, Package Couriers, Pattern Schematics, Fast Schematic Cannon, Stock Market, Trading Floor, Tradeworks, Mob Spawners, Easy Stone Generators, Transmission, Springs, Tweaked Controllers, Rolling Tones, Pipe Organs, Rubberworks, Submarine.
- Аддоны авиации: Gadgets & Gizmos (Create Thrusters), Aerocopycats, Throwable Rope Connector, Transmission & Linkage, Toolgun, Aero Claims, Drive-By-Wire, Coasters Engineered и Simulated, Struts, Shtreimel, Immersive Aircraft с рецептами Create.
- Удобства: Polymorph, Controlling, Searchables, Mouse Tweaks, Corpse, Enchantment Descriptions, Just Enough Professions и Breeding, Pick Up Notifier, Toast Control, Easy Anvils, Easy Magic, Visual Workbench, Cut Through, Highlighter, NetherPortalFix, Trading Post, Chat Heads, Backpacked.
- Вид: Continuity, Skin Layers 3D, Not Enough Animations, Personality, WATUT, Iris и Complementary, ресурспаки Create UI, GUI Retextures, Stay True, Create Touch-Ups, 3D гаечный ключ, воронка в стиле Create.
- Сервер: Open Parties and Claims, Aero Claims (приват кораблей через OPAC), e4mc (игра с друзьями без сервера), Server Browser, Crash Assistant, Neruina, Smooth Chunk Save.
- Датапаки: `create waystones` (путевые камни крафтятся на механическом крафтере Create, ванильные рецепты пустые), `no_dnd_diamond` (без рецептов алмазов из Dreams n Desires), `charcoal_to_coal`, `No_Obsidian_Gen`, `aoc_data` (переводы одинаковых предметов разных модов друг в друга, например цинковые корпуса двух модов).

## Грабли aoa (не повторять)
- `ExtraCreateRecipes.zip` с pack_format 9 и папкой `recipes/`: в 1.21 рецепты лежат в `recipe/`, этот датапак не загружается.
- Sinytra Connector и Forgified Fabric API ради фабрик-модов, Iris с шейдерами, EMF и ETF: тяжело, на Android не годится.
- Конфиги от модов, которых нет в сборке (waystones, biomeswevegone, createbigcannons), и десятки `.bak`: мусор после обновлений.

## Что перенесли к себе (анкета владельца, 02.11)
- Квесты: цвета групп и глав, цвета в текстах, приветствия, формы, логотип и картинки (ресурспак `resourcepack/`), иконка книги, закреплённая панель глав, «Обучение» и «Полезно знать», группы и эпохи, linear, таблицы случайных наград и награды на выбор. Подробно в `docs/quest_builder.md`.
- Моды (jar взяты из `aoa/minecraft/mods`, копия для владельца в `/mnt/project-files/aoa-mods/`, список в `pack/MODS.md`): Polymorph, Controlling и Searchables, Corpse, Enchantment Descriptions (с Bookshelf и Prickle), Mouse Tweaks, JEP и Just Enough Breeding, Copycats+, Train Parts, Railways Navigator (с DragonLib), Bells & Whistles, Create Food, Confectionery, Winery, Cafe, Stock Market, Trading Floor, Tradeworks, Gadgets & Gizmos, Create Submarine, Neruina (с Configurable), Integrated Villages и Stronghold, Loot Integrations (с Cupboard).
- Датапаки: `cld_waystones` (путевые камни на механическом крафтере), `cld_dupes` (обмен одинаковых предметов), `cld_airships` (корабли не выше 590). Свои, написаны заново.
- Небесные острова в tcw и мир End Sea ведёт отдельный тред.
