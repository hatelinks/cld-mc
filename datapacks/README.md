# Свои датапаки сборки

Каждая папка с `pack.mcmeta` (формат 48) собирается в zip: `datapacks/build.sh` -> `datapacks/dist/<папка>.zip`.
Ставить в `moonlight-global-datapacks` экземпляра: Moonlight подключает их ко всем мирам.

| Датапак | Что делает |
|---|---|
| `cld_hourglass` | Гравипесок Quark (`quark:gravisand`) кладётся в песочные часы Supplementaries и сыплется 140 тиков (7 секунд, обычный песок 70). Работает, только если стоят Quark и Supplementaries. |

`pack_fixes.zip` и `colony_guard.zip` (`HANDOFF.md` §3) пока лежат только у владельца, в репозиторий не перенесены.
