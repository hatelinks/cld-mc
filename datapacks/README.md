# Свои датапаки сборки

Каждая папка с `pack.mcmeta` (формат 48) собирается в zip: `datapacks/build.sh` -> `datapacks/dist/<папка>.zip`.
Ставить в `moonlight-global-datapacks` экземпляра: Moonlight подключает их ко всем мирам.

| Датапак | Что делает |
|---|---|
| `cld_hourglass` | Гравипесок Quark (`quark:gravisand`) кладётся в песочные часы Supplementaries и сыплется 140 тиков (7 секунд, обычный песок 70). Работает, только если стоят Quark и Supplementaries. |
| `cld_recipes` | Крафты для предметов без рецепта: декомпрессионная камера, якорь и весло Create Submarine. Каждый рецепт с условием на мод. В `optional/` лежат запасные рецепты (катушка кабеля и инструменты Propulsion, помпы Sophisticated Storage), в zip они не входят. |

`pack_fixes.zip` и `colony_guard.zip` (`HANDOFF.md` §3) пока лежат только у владельца, в репозиторий не перенесены.
