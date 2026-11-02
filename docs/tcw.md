# tcw: тип мира TelipailoCraft

Карта кода. Читать вместо разбора исходников; кто меняет мод, обновляет эту страницу в своём PR.

- modId `tcw`, папка `tcw/`, версия **0.8.0** (зашита в `tcw/build.sh`, переменная `VERSION`, без `${VERSION:-}`).
- Свой генератор рельефа поверх ванильного `NoiseBasedChunkGenerator`: континенты, хребты 8 стилей, реки до океана, озёра, снег, ледники, свои пещеры, горные биомы. Высота мира 768 (Y от -128 до 640), море 63.
- Тип мира по умолчанию. Ставится на клиент и на сервер. От него зависит cld_far (берёт тот же `Terrain` по сиду).
- Пререлизы: `releases/tcw-<версия>/`.

## Сборка и проверка

```sh
LIBS=/mnt/project-files/libs tcw/build.sh          # tcw/dist/tcw-neoforge-1.21.1-0.8.0.jar
```
`build.sh` сначала запускает `tools/build_data.py <client-*-extra.jar> build/classes`: все json мира (тип измерения, пресет, noise_settings, биомы, фичи, biome modifier) генерируются из ванильных файлов, в `src/main/resources/data` лежат только два biome modifier. Потом javac.

Вне игры, без libs (пакет `gen` не зависит от Minecraft):
```sh
javac -d /tmp/tcw tcw/src/main/java/dev/cld/tcw/gen/*.java tcw/tools/*.java
java -cp /tmp/tcw Preview <seed> <cx> <cz> <размер> <блоков на пиксель> out.png   # карта сверху
java -cp /tmp/tcw Bench      # время Macro.sample, Hydro, Terrain.column
java -cp /tmp/tcw Find       # координаты каждого стиля гор для сида 42
java -cp /tmp/tcw Dbg out.png  # маска суша/море/берег крупного рельефа
java -cp /tmp/tcw Sky <seed> <клеток> out.png  # список небесных островов, ближайшая группа сверху и в разрезе
java -cp /tmp/tcw Sea <seed> <cx> <cz> <размер> <блоков на пиксель> out.png  # мир End Sea: острова сверху и разрез
```
Скорость генерации: `qa/bench/tcw.sh` (только gen, контрольная сумма рельефа) и `qa/bench/server_pregen.sh` (настоящий сервер, JFR, `QAFEAT` по фичам), см. `docs/qa.md`.

## Как устроено

Цепочка: сид мира -> шум `tcw:seed` в `RandomState` -> `Worlds.seedFrom` -> `Terrain` (один на сид и настройки, кэш в `Worlds.last`) -> `Terrain.column(x, z)` (высота, вода, климат, склон) -> функции плотности `tcw:column` в noise_settings и наши фичи.

### Пакет `gen` (чистая Java, без Minecraft)
| Файл | За что отвечает |
|---|---|
| `Macro` | Крупный рельеф (детали крупнее ~64 блоков): ячейки континентов, океаны, хребты, 8 стилей гор (`Style`: ALPINE, PATAGONIA, DOLOMITE, GRANITE, VOLCANIC, MESA, PLATEAU, KARST), температура и влажность. `sample(x, z, Sample)`. |
| `Hydro` | Сток по одному континенту: priority flood от океана, накопление стока, русла и озёра. Каждая река доходит до океана. Считается раз на континент, кэш в `Terrain.hydros` (LRU с `CompletableFuture`). |
| `Terrain` | Итоговый столбец `Column`: макро + эрозия + долины рек + скалы, уровень воды, снеговая линия (`snowLine`), климат для биомов (`climate`, индексы `P_*`). |
| `Caves` | Свои пещеры: гигантские залы (`hall`), подземные реки (`nearGallery`, `riverLevel`), ледяные ходы (`glacial`, `iceLevel`), сеноты (`sink`). `density` < 0 внутри пещеры, `fluid`/`topFluidLevel` для воды и лавы. |
| `Rocks` | Арки и карнизы с навесами: `combined` (одно значение для final_density), `add`, `cut`, `top`. Карнизы пятнами: шум `pick`, порог `PICK`. |
| `Islands` | Небесные острова: клетки 1024x1024, в половине клеток группа (большой остров r 26-53 и до 4 маленьких). Верх над самым высоким рельефом под островом плюс глубина острова и зазор 40-80, не выше 600. `column` даёт низ и верх острова в точке, `bottom` для поправки карт высот, `at` для биома. 9 биомов по климату (`BIOMES`). |
| `Archipelago` | Мир TC Islands (в коде End Sea) (`Options.world = 1`): маска островов `edge` (три шума 1500/430/90 блоков, порог 0.16, на месте океанов tcw пустота) и низ острова `bottom` (у края 3-6 блоков, в середине больших до 190). `Terrain.isleBottom` кэширует низ в `Column.isle`. Около четверти площади острова. |
| `EndLands` | Край TelipailoCraft (оба типа мира): до `INNER` = 1000 блоков от центра ванильный Край с драконом, дальше после разгона `RAMP` = 300 свои острова (около трети площади): шумы 700/220/60 блоков, высота района 50-140 (`alt`, 1300), холмы, в части районов горы до 240 (`mount` 900, гребни `ridge` 180). `density` для шума, `erosion` для выбора ванильных биомов Края (0.4 нагорья с хорусом: хорусовые леса `forest` > 0.1 и вершины выше 150; -0.1 пустоши: край острова и голые плато `forest` < -0.3; 0 середина; -0.5 над пустотой), `darkBelt` толщина тёмного низа. `at(x,z)` кэш столбцов на поток. Превью вне игры `tools/EndView.java`. |
| `Options` | `world` (0 обычный, 1 End Sea, задаётся пресетом) и 6 настроек экрана «Настроить» (0, 1, 2; 1 по умолчанию): континенты, горы, высота, климат, снег, залы. Хранятся в level.dat внутри источника биомов. |
| `MapRender` | Цвет карты сверху с отмывкой (экран карты, `/tcwmap`, Preview). |
| `Simplex` | 2D симплекс-шум с производными. |

### Связь с Minecraft (`dev.cld.tcw`)
| Файл | За что отвечает |
|---|---|
| `TcwMod` | Регистрация: источник биомов `tcw:telipailo`, функция плотности `tcw:column`, фичи `tcw:water/rock/caves/snow`, biome modifier `tcw:world`, события спавна и команд. |
| `Worlds` | Генератор по сиду, настройки текущего сервера (`currentOptions`), кэш столбцов на поток (`column`, размер `-Dtcw.columnCache`), `isTcw` по типу измерения. |
| `ColumnDensity` | Функция плотности `tcw:column`, значения `Value`: surface, continents, erosion, ridges, temperature, vegetation, depth, caves, rock, peak, sky. `peak` защищает верх тонких пиков от ванильных пещер, `sky` отсекает небо. |
| `TcwBiomeSource` | Биомы: на небесных островах свой биом группы (`Islands.at`, от низа острова до 24 блоков над верхом); ванильные по климату, в горах пояса от снеговой линии (subalpine_forest, alpine_meadow, alpine_tundra, scree_slopes, glacier, volcanic_slopes), под землёй пещерные биомы YUNG, если мод стоит. |
| `WaterFeature` | Реки и озёра выше моря: русло с точностью до блока и вода. Шаг `lakes`. |
| `CaveFeature` | Озёра в залах (глубоко лава), вода подземных рек и сенотов. Столбец берёт лениво (`Supplier`, с 0.5.8). Шаг `lakes`. |
| `IslandFeature` | Блоки небесных островов (трава, земля, камень с рудами, мох снизу), шаг `raw_generation`, до деревьев: деревья и трава встают на остров сами по биому острова. |
| `RockFeature` + `Palette` | Голые скалы с каменными слоями на крутых склонах (набор пород по стилю гор) и осыпи. Шаг `local_modifications`. |
| `SnowFeature` + `Surface` | Снег выше снеговой линии на пологих местах, сугробы, ледники с трещинами, ледяные пещеры. `Surface` это сетка высот 18x18 и крутизна; `Surface.height` берёт землю под небесным островом, а не его верх (так же в `WaterFeature`). Шаг `top_layer_modification`. |
| `WorldModifier` | Biome modifier `tcw:world`: добавляет наши фичи и убирает карверы Better Caves только в мире tcw. |
| `Spawn` | Точка появления: зелёная долина у воды в радиусе 6000 блоков. |
| `LocateCommand` | `/locate tcw <стиль|sky_island|peak|volcano|glacier|fjord|ocean|river|waterfall|lake|cave_hall|cenote|arch|underground_river|ice_cave>` по модели рельефа, без генерации чанков. |
| `RingCache` | Кольца крепостей в `world/data/tcw_rings.txt` (ключ: сид, набор, параметры, версия tcw, настройки). |
| `SkyBound`, `BeardRange`, `DeepFluidPicker`, `Precheck`, `PrecheckSlot`, `OreSkip` | Помощники ускорений, см. миксины ниже. |
| `QuietLogs` | Глушит спам «CSA config read» от Create Structures Arise. |
| `EndSeaStructures` + `mixin/StructureMixin`, `mixin/NearestStructureMixin` | End Sea: старт структуры только на целом острове (вся площадь с запасом 4 блока дальше 0.02 от края, низ структуры выше дна острова); подземная структура переносится вверх в толщу острова или отменяется; структуры только морских биомов и старты над пустотой отбрасываются до подсчёта биомов; поиск ближайшей структуры (/locate, карты, глаз Края) не дальше 8000 блоков, иначе поиск того, чего нет, вешает сервер. |
| `EndFeature` (`tcw:end`, vegetal_decoration) | Только в Крае с настройками `tcw:end` и вне ванильного центра: нижний пояс островов чернокамень, у дна обсидиан (местами плачущий), в хорусовых лесах 3-16 хорусов на чанк (`ChorusFlowerBlock.generatePlant`), на вершинах от 135 с шансом 1/3 на чанк пурпурный шпиль 9-20 блоков со стержнем Края. Подключается biome modifier `end_telipailo.json`, `end_islands.json` (`#minecraft:is_end`, по типу мира). |
| `EndStructures` + `mixin/StructureMixin` | Наш Край: старт структуры над пустотой (середина чанка) отбрасывается сразу; после генерации первая часть (основание города Края) должна целиком стоять на острове (каждые 4 блока край дальше 0.02, дно острова ниже основания на 2+, верх не ниже основания больше чем на 4). Башни, мосты и корабль висят в воздухе, как в ванили. |
| `client/ClientSetup` | Редактор пресета (кнопка «Настроить»), кнопка «Карта мира», клиентская команда `/tcwmap` (только одиночная игра). |
| `client/MapScreen`, `client/OptionsScreen` | Экран карты мира (колесо, перетаскивание) и экран 6 настроек. |

### Данные (`tools/build_data.py`)
- `tcw:islands` (тип мира «TC Islands», до 0.7.1 назывался End Sea, `tcw:end_sea`): тип измерения как у Верхнего мира (обычное небо, день и ночь), noise_settings как у telipailo, но final_density = min(..., `tcw:column isle`), без водоносных слоёв, жидкость по умолчанию воздух (FluidPickerMixin отдаёт воздух везде, ни моря, ни лавы у дна), уровень моря 63 оставлен для глубины крепостей и шахт. Пресет `tcw:islands` в теге `minecraft:normal`, название «TC Islands». Biome modifier `islands.json` с теми же фичами, кроме небесных островов.
- `tcw:end` (с 0.8.0, Край в обоих пресетах): копия ванильного `end.json`, высота шума 256, `legacy_random_source` false (иначе шум `tcw:seed` другой и общий генератор `Worlds.terrain` пересоздаётся при каждом переходе между измерениями). final_density, initial_density и erosion: `range_choice` по `tcw:column end_zone` (0 внутри `EndLands.INNER`, 1 снаружи), внутри ванильные, снаружи `end` и `end_erosion`. Источник биомов ванильный `minecraft:the_end`, он сам берёт erosion.
- `tcw:telipailo`: dimension_type (min_y -128, height 768), world_preset (добавлен в тег `minecraft:normal`), noise_settings (`size_vertical 2`, роутер климата из `tcw:column`, final_density = ванильный с нашей поверхностью, пещеры `caves`, `peak`, `rock`, `sky_cut`).
- Ванильные пещерные функции копируются в `tcw:caves/*` с высотами под 768.
- Свои биомы копируются с ванильных (taiga, meadow, stony_peaks, frozen_peaks), теги `is_mountain`, `is_taiga`, `is_forest`.
- Biome modifier `tcw:world` (фичи и карверы), WWOO-заплатки (кораллы, бетон, терракота вне бэдлендса) убираются во всех мирах при `wwoo`.
- В `src/main/resources/data/tcw/neoforge/biome_modifier/`: `no_wythers_cliffs.json` и `no_lost_caves_surface.json` (с 0.5.7, во всех мирах, с условием на мод).

## Миксины (`tcw.mixins.json`, `tcw.cirrus.mixins.json`)
Все ускорения дают тот же мир, что без них (сверено).
| Миксин | Куда | Что делает |
|---|---|---|
| `StructureMixin` | `Structure.findValidGenerationPoint` HEAD, `Structure.generate` RETURN | Только TC Islands и наш Край, см. `EndSeaStructures`, `EndStructures`. |
| `NearestStructureMixin` | `ChunkGenerator.findNearestMapStructure` HEAD | Только End Sea: радиус поиска не больше 8000 блоков по шагу размещения. |
| `NoiseFillMixin` + `NoiseChunkAccessor` | `NoiseBasedChunkGenerator.doFill` HEAD | Заполняет шумом только ячейки до верха рельефа (`SkyBound`). Выключить: `-Dtcw.fullFill=true`. Совместим с Noisiumed. |
| `AquiferMixin` | `Aquifer.NoiseBasedAquifer.computeSubstance` | Высоко над поверхностью сразу воздух, без поиска 12 центров. |
| `BeardifierMixin` | `Beardifier` init/compute | Вне диапазона Y частей структур подсыпка сразу 0. Все миры. |
| `FluidPickerMixin` | `createFluidPicker` | Лавовый уровень на 10 блоков над дном мира (а не Y -54) в мирах глубже -64; `DeepFluidPicker`. Если жидкость по умолчанию воздух (End Sea), везде воздух. |
| `FluidPostProcessMixin` | `LevelChunk.postProcessGeneration` | Не тикает стоячие источники воды при загрузке чанка (лаг океанов, 0.5.4). Все миры. |
| `RingPositionsMixin` | `ChunkGeneratorStructureState.generateRingPositions` | Кэш колец крепостей (`RingCache`), только tcw. |
| `DiskFeatureMixin` + `PrecheckSlotMixin`, `StateTestingAccessor`, `CombiningAccessor`, `NotAccessor` | `DiskFeature.placeColumn` | Сначала проверка условия по одному блоку (`Precheck`). Удешевило wythers stone_cliffs. |
| `OreFeatureMixin` + `RuleTestAccessors` | `OreFeature.doPlace` | Не проверяет блоки облака руды, если рядом нечего заменять (`OreSkip`). |
| `ReplaceBlobsMixin` | `ReplaceBlobsFeature.findTarget` | Пропускает секции без нужного блока в палитре. |
| `client/CreateWorldScreenMixin` | `CreateWorldScreen.openFresh` TAIL | Тип мира tcw выбран по умолчанию (срабатывает в TAIL, позже других модов с тем же хуком, поэтому выигрывает). |
| `client/WorldTabMixin` | `CreateWorldScreen$WorldTab` init | Широкая кнопка «Карта мира» под «Тип мира». |
| `cirrus/CirrusCloudHeightMixin` | Cirrus `cloudHeight` (необязательный) | Облака выше пропорционально высоте мира. |

## Настройки
- Экран «Настроить» (`Options`), сохраняются в мире. Файла конфига нет.
- Ключи JVM: `-Dtcw.fullFill=true` (без отсечения неба), `-Dtcw.columnCache=N` (кэш столбцов на поток, по умолчанию 4096).

## Грабли
- Изменения генерации видны только в новых чанках. Сверять полную сборку только с повтором той же версии (руды, капельник, песок у берегов разные от прогона к прогону).
- Новая функция плотности, считаемая на каждый блок (как `peak`), сразу видна в профиле: проверять замером.
- `cache_once` на `rock` портит разрешение (ячейки 4x8), поэтому `rock` без кэша.
- Карнизы должны быть пятнами, 1-2 на гору (решение владельца).
- `build_data.py` проверяет, что в роутере не осталось ссылок `overworld/`: при обновлении ванильных json падает assert.
- Тяжёлые фичи других модов в полной сборке и что уже выключено по решению владельца: см. `CLAUDE.md`, раздел «Грабли».
- Небесные острова ставятся фичей, а не функцией плотности: старт структур, `SkyBound` и водоносные слои их не видят, структуры под островом не прыгают на него. Под островом нет деревьев (карта высот смотрит на остров). cld_far острова пока не рисует.
- Сид `Terrain` в игре это не сид мира, а `Worlds.seedFrom(шум)`: координаты из `Sky`/`Preview` с сидом мира не совпадают с игрой, в игре искать `/locate tcw sky_island`.
- End Sea: в мире нет морских структур (монументы, затонувшие корабли, руины), шахты и древние города редки (нужна толща острова). cld_far рисует End Sea как обычный мир, без пустоты (пока не доделан). Спавн в середине крупного острова (`Spawn`, `edge >= 0.15`). Реки у края острова стекают водопадом в пустоту.
- Не реализовано (на потом): живые горы, опасности, структуры только на ровном, лавины, камнепады, холод на высоте (`HANDOFF.md` §10).

## Версии (коротко)
0.1 свой тип мира; 0.2 `/locate tcw`; 0.3 свои пещеры; 0.4 тип по умолчанию, горные пояса, спавн в долине, карта, настройки; 0.4.3 тишина CSA; 0.5.0 арки и карнизы; 0.5.2 карнизов меньше, нет летающих кусков; 0.5.3 генерация почти вдвое быстрее; 0.5.4 нет лага океанов; 0.5.5 Precheck дисков; 0.5.6 OreSkip, ReplaceBlobs; 0.5.7 убраны обрывы wythers и Lost Caves surface; 0.5.8 ленивый столбец в CaveFeature; 0.6.0 небесные острова; 0.7.0 мир End Sea; 0.7.1 он переименован в TC Islands (`tcw:islands`), обычное небо; 0.8.0 свой Край в обоих типах мира (крупные острова за кольцом пустоты, пояса, хорусовые леса, шпили, структуры только на целом острове).
