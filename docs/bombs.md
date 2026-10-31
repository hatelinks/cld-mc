# cld_bombs: Бомбы с уровнями

Карта кода. Читать вместо разбора исходников; кто меняет мод, обновляет эту страницу в своём PR.

- modId `cld_bombs`, папка `bombs/`, версия **1.0.0** (`VERSION` в `bombs/build.sh`). Ставится и на клиент, и на сервер.
- Дополнение к бомбам Supplementaries (проверено против 4.0.2). Сам Supplementaries не меняется, всё через миксины в его классы.
- Уровни 1-10. Радиус растёт по кривой от радиуса 1 уровня до радиуса последнего: бомба 5 -> 50, синяя 4 -> 40, осколок синей 1.5 -> 12. Обсидиан и блоки прочностью до 1200 ломаются с 8 уровня, бедрок и тег `cld_bombs:immune` никогда.
- Кратер вырезается от центра наружу за несколько тиков (`blocks_per_tick`), урон и отбрасывание сразу. Часть блоков выпадает (`drop_chance`), добыча собирается в стопки и выпадает в конце. Колонии MineColonies не трогаются.
- Прокачка на верстаке: бомба уровня N и ровно N+1 пороха дают уровень N+1. TNT считается за 5 пороха, иначе уровни 9 и 10 не помещаются в сетку 3x3.
- Бомбы в сундуках и прочей добыче получают случайный уровень 1-3.
- Название «Бомба, уровень 5», цвет названия ярче с уровнем, блеск с 4 уровня. В подсказке радиус и цена следующего уровня.

## Сборка
```sh
LIBS=/mnt/project-files/libs MODS=/mnt/project-files/libs-mods bombs/build.sh   # bombs/dist/cld_bombs-neoforge-1.21.1-1.0.0.jar
```
В `MODS` jar Supplementaries, Moonlight, MineColonies и `mixinextras-neoforge` (из jarjar NeoForge). В репозиторий их не класть.

## Файлы
| Файл | За что отвечает |
|---|---|
| `BombsMod` | Регистрация: компонент предмета `cld_bombs:level`, вложение сущности `cld_bombs:level`, рецепт, модификатор добычи, конфиги, тик кратеров. |
| `Cfg` | Настройки. `Cfg.i/d/b` отдают значение по умолчанию, если конфиг мира ещё не загружен. |
| `Levels` | Уровень у предмета и сущности (уровень 1 = нет компонента), радиус, урон, цвет, цена. |
| `Blast` | Свой взрыв: эффекты, урон по сущностям, кратер по заранее отсортированным смещениям шара (до радиуса 63), неровный край, обновление соседей только на краю. |
| `BombUpgradeRecipe` | Рецепт прокачки `cld_bombs:bomb_upgrade` (`data/cld_bombs/recipe/bomb_upgrade.json`). В JEI не виден. |
| `LootLevels` | Глобальный модификатор добычи `cld_bombs:bomb_levels`. |
| `Colonies` | Проверка колонии через API MineColonies, грузится только если мод есть. |
| `client/BombsClient` | Экран настроек NeoForge (через `IConfigScreenFactory`) и подсказка. |
| `mixin/BombItemMixin` | Название с уровнем, блеск, уровень в брошенную бомбу (`use`) и из раздатчика (`asProjectile`). |
| `mixin/BombEntityMixin` | Подмена `BombExplosion.createExplosion` в `createExplosion`, уровень осколкам (`spawnCluster`), уровень у подобранной бомбы (`playerTouch`). |

## Настройки
- `serverconfig/cld_bombs-server.toml` в папке мира (на сервере меняет оператор): `enabled`, `max_level`, `radius_curve`, радиусы в разделе `radius`, `obsidian_level`, урон, `drop_chance`, `max_drop_stacks`, `edge_jitter`, `protect_colonies`, `powder_per_level`, `tnt_powder`, уровни в сундуках, `glint_level`.
- `config/cld_bombs-common.toml` у каждого устройства: `radius_cap` (на Android 20-25), `blocks_per_tick`.
- В игре: экран настроек NeoForge этого мода. Общий экран «Наши моды» (отдел меню) должен открывать его же.

## Грабли
- `build.sh` ставит `neoforge-*-client.jar` в classpath раньше `client-*-srg.jar`: иначе javac берёт классы игры без патчей NeoForge (нет `Entity.getData`).
- Миксины завязаны на имена методов Supplementaries 4.0.2 (`createExplosion`, `spawnCluster`, `playerTouch`, `use`, `asProjectile`, `isFoil`). При обновлении Supplementaries проверить, `defaultRequire 1` уронит запуск при промахе.
- Недорезанный кратер при выходе из мира не сохраняется.
- Событие `ExplosionEvent` не вызывается: моды приватов, кроме Flan (его проверяет сам Supplementaries), взрыв не увидят.
- В игре ещё не проверен.
