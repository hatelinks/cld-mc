# jjtm: порт JJThunder To The Max 1.20.1 на 1.21.1 (архив)

Карта кода. Читать вместо разбора исходников; кто меняет мод, обновляет эту страницу в своём PR.

- modId `jjthunder_to_the_max` (lowcodefml, без Java-кода), папка `jjtm/`, версия **0.3.3**. Личное использование, лицензия оригинала All Rights Reserved.
- Заменяет ванильный рельеф Верхнего мира рельефом JJThunder. В игре не стоит: его заменили gdn (те же данные как отдельные типы мира) и tcw. Архив, не развивается. Но `jjtm/src` это источник данных для gdn, поэтому папку не удалять.
- Вместе с gdn не ставить: одинаковый modId.

## Сборка (Python, без Java и без LIBS)
```sh
jjtm/build.sh    # original/*.jar -> src/ -> dist/
```
1. Распаковывает `original/JJThunder_To_The_Max_1.20.1_v0.2.0.jar`.
2. `tools/port.py <jar> vanilla-1.21.1 <out>`: перевод на 1.21.1 (`tags/blocks` -> `tags/block`, раскрытие старых обёрток `value` у height provider, спавны биомов берутся из ванили 1.21.1).
3. `tools/tweaks.py <pack>`: наши правки, общие для всех высот (см. ниже).
4. Результат копируется в `src/` (то есть `src/` перезаписывается сборкой, правки делать в tools, а не руками в src).
5. Для высот 2096 (оригинал), 1024, 512: `tools/variant.py src <out> <h>`, облака Cirrus `cloud_height`, вариант `-fast` (`size_vertical 2`). В `dist/` jar-моды и zip-датапаки, по 6 штук (dist в git).

## Файлы
| Файл | За что отвечает |
|---|---|
| `original/` | Оригинальный jar 1.20.1. |
| `vanilla-1.21.1/` | Ванильные json 1.21.1, с которыми сверяется и сливается порт. |
| `tools/port.py` | Перевод формата 1.20.1 в 1.21.1. |
| `tools/tweaks.py` | 1) Корка над пещерами толще (сплайн `overworld_combined` 0.025/0.05 -> 0.045/0.09) и biome modifier, убирающий карвер `bettercaves:surface_cave`. 2) Теги `has_structure/*` (replace false): 39 наземных биомов получают деревни, аванпосты, особняки по климату. 3) `flat_cache` на `overworld_determiner`, `cache_once` на 3D-шумы пещер (рельеф тот же, быстрее). |
| `tools/variant.py` | Другая высота мира: рельеф ниже Y 63 тот же, высота суши над морем сжимается линейно (сжимается offset+factor+jagged в `overworld_surface`, градиент плотности прежний, корка той же толщины). |
| `tools/cmp.py` | Отладка: сравнение json двух папок по списку `files.txt`. |
| `src/` | Готовый порт (h2096): `data/minecraft/{dimension, dimension_type, tags, worldgen}`, `data/jjthunder_to_the_max/neoforge/biome_modifier/`, `META-INF/neoforge.mods.toml`. |
| `dist/` | Собранные jar и датапаки всех вариантов. |

## Грабли
- Версия зашита строкой `0.3.3-1.21.1` в `src/META-INF/neoforge.mods.toml` и в sed внутри `build.sh`: менять в обоих местах.
- Облака Cirrus: jjtm грузится после cirrus (optional dependency, ordering AFTER), высота облаков 63 + 129 * (top - 63) / 257.
- Better Caves в сборке сейчас нет, правка карвера срабатывает только при нём.
