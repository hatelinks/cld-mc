# cld_structures: Структуры без наложений

Карта кода. Читать вместо разбора исходников; кто меняет мод, обновляет эту страницу в своём PR.

- modId `cld_structures`, папка `structures/`, версия **1.0.0** (`VERSION` в `structures/build.sh`).
- Структуры разных модов не появляются друг в друге: набор структур пропускает чанк, если рядом может встать структура более редкого набора того же этапа генерации с подходящим биомом.
- Решение зависит только от сида и координат, порядок генерации чанков не важен. Серверный по сути, ставится и на клиент, и на сервер.

## Сборка
```sh
LIBS=/mnt/project-files/libs structures/build.sh   # structures/dist/cld_structures-neoforge-1.21.1-1.0.0.jar
```

## Файлы
| Файл | За что отвечает |
|---|---|
| `StructuresMod` | Загружает конфиг при старте. |
| `Overlap` | Вся логика. `blocked(set, state, cx, cz)`: для каждого другого набора с большим приоритетом и общим этапом (`GenerationStep.Decoration`) ищет возможный старт в радиусе `min(max_radius, r1 + r2)` чанков. Приоритет = spacing у random spread, у колец (крепости) максимум, у прочих 32; при равенстве сравниваются id. Радиус структуры: у пазловых `maxDistanceFromCenter / 16 * size_factor` (1-6 чанков, через отражение), иначе 2. Биом проверяется на высотах Y -32, 32, 64, 128, 256. |
| `mixin/ChunkGeneratorMixin` | `@Inject` в HEAD лямбды `lambda$createStructures$14` у `ChunkGenerator`, отменяет набор, если `Overlap.blocked`. |
| `mixin/StructureStateAccessor` | Доступ к `biomeSource` у `ChunkGeneratorStructureState`. |

## Настройки
`config/cld_structures.properties` (создаётся сам):
- `max_radius_chunks` = 8: наибольшее расстояние проверки.
- `size_factor` = 0.6: какая доля `max_distance_from_center` пазловых структур считается радиусом.
- `ignored_sets`: наборы через запятую, которые не трогаем (например `minecraft:mineshafts`).

## Грабли
- Имя лямбды `lambda$createStructures$14` зависит от компилятора Minecraft: при смене версии игры или NeoForge проверить (иначе миксин упадёт, `defaultRequire 1`).
- В игре на новом мире ещё не проверен (`HANDOFF.md` §9).
- Кэш `INFO` по наборам живёт до выхода из игры; при смене датапаков без перезапуска может держать старые данные.
