# gdn: Горы до небес

Карта кода. Читать вместо разбора исходников; кто меняет мод, обновляет эту страницу в своём PR.

- modId `jjthunder_to_the_max` (тот же, что у jjtm, вместе не ставить), папка `gdn/`, версия **1.0.0** (`VERSION` в `gdn/build.sh`). Пространство имён данных `gdn`.
- Рельеф jjtm как отдельные типы мира `gdn:h1024` и `gdn:h512` (оба с ячейкой шума 8 блоков). Обычный мир остаётся ванильным. Стоит в игре; тип мира по умолчанию теперь tcw.

## Сборка
```sh
LIBS=/mnt/project-files/libs gdn/build.sh   # gdn/dist/gdn-neoforge-1.21.1-1.0.0.jar
```
Для 1024 и 512: `../jjtm/tools/variant.py ../jjtm/src` -> `size_vertical 2` -> `tools/build_data.py <variant> ../jjtm/vanilla-1.21.1 build/classes h<высота> "Горы до небес <высота>"`. Затем javac. Данные берутся из `jjtm/src`, поэтому правки рельефа делаются в jjtm/tools.

## Файлы
| Файл | За что отвечает |
|---|---|
| `tools/build_data.py` | Переносит вариант jjtm из `data/minecraft` в `gdn:<tag>/...`: density_function, noise, noise_settings, placed/configured_feature, configured_carver. Пишет dimension_type и world_preset `gdn:<tag>`. Изменения биомов jjtm превращает в biome modifier `gdn:patch`, срабатывающие только в своём типе мира. Теги `has_structure` переносит в `gdn:has_structure/*`. |
| `GdnMod` | Регистрирует сериализатор `gdn:patch`, события сервера для `WorldType`. |
| `PatchModifier` | Biome modifier `gdn:patch`: при совпадении типа измерения Верхнего мира меняет фичи (по шагу), карверы (по стадии), климат и цвета биома. |
| `WorldType` | Тип мира сервера по типу измерения Верхнего мира. При старте сервера в мире gdn собирает карту структура -> биомы с добавками `gdn:has_structure/*`. |
| `mixin/StructureMixin` | `Structure.biomes` RETURN: в мирах gdn отдаёт расширенный набор биомов. |
| `mixin/WorldCreationUiStateMixin` (клиент) | При создании мира выбран `gdn:h1024`, если стоял обычный. tcw перебивает это своим миксином позже. |
| `mixin/cirrus/CirrusCloudHeightMixin` (клиент, необязательный) | Облака Cirrus выше пропорционально высоте мира, только в gdn. |
| `src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json` | Пресеты gdn в списке типов мира. |

## Грабли
- cld_far в мирах gdn не работает (только tcw), DH из сборки убран: в gdn нет дальней прорисовки.
- cld_pregen (`DhCompat`) считает мир «горным» по пространству имён `gdn` или `tcw`.
