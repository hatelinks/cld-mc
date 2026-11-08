# Свои датапаки сборки

Каждая папка с `pack.mcmeta` (формат 48) собирается в zip: `datapacks/build.sh` -> `datapacks/dist/<папка>.zip`.
Ставить в `moonlight-global-datapacks` экземпляра: Moonlight подключает их ко всем мирам.

| Датапак | Что делает |
|---|---|
| `cld_hourglass` | Гравипесок Quark (`quark:gravisand`) кладётся в песочные часы Supplementaries и сыплется 140 тиков (7 секунд, обычный песок 70). Работает, только если стоят Quark и Supplementaries. |
| `cld_recipes` | Крафты для предметов без рецепта: декомпрессионная камера, якорь и весло Create Submarine. Каждый рецепт с условием на мод. В `optional/` лежат запасные рецепты (катушка кабеля и инструменты Propulsion, помпы Sophisticated Storage), в zip они не входят. |
| `cld_waystones` | Путевые камни Waystones (все 10 видов) собираются только на механическом крафтере Create: камень, камень перехода, андезитовые корпуса, шестерня, обсидиан. Рецепты верстака выключены (`neoforge:false`). Генерирует `tools/make_recipes.py`. |
| `cld_dupes` | Обмен одинаковых предметов разных модов в верстаке 1 к 1 (лук, томаты, рис MineColonies и Farmer's Delight, шоколад Create Food и Confectionery, верёвки, заполняемые блоки Copycats+ и Create Connected и т.д.). Рецепт включается, только если стоят оба мода. Список групп в `tools/make_recipes.py`. |
| `cld_start_book` | При первом заходе игрока в мир кладёт в инвентарь книгу квестов (`ftbquests:book`): скрытое достижение на `minecraft:tick` с наградой из таблицы лута. Выдаётся один раз на игрока в каждом мире. |
| `cld_airships` | Деревня воздушных кораблей Integrated Villages стоит на 100 блоков выше рельефа; в мире TelipailoCraft (верх 640) над высокими горами она уходила бы за потолок. Переопределение `airship_village` с `y_allowance.max_y_allowed = 590`. Нужен в мире до генерации (новые чанки). |

`pack_fixes.zip` и `colony_guard.zip` (`HANDOFF.md` §3) пока лежат только у владельца, в репозиторий не перенесены.
