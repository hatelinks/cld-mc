# cld_compat: Совместимость модов сборки

Карта кода. Читать вместо разбора исходников; кто меняет мод, обновляет эту страницу в своём PR.

- modId `cld_compat`, папка `compat/`, версия **1.0.1** (`VERSION` в `compat/build.sh`). Клиент и сервер.
- Мелкие заплатки, чтобы чужие моды сборки уживались друг с другом без перепаковки их jar. Каждая заплатка включается только при том наборе модов, где она нужна (`CompatMixinPlugin`).
- Пререлизы: `releases/cld_compat-1.0.0/`, `releases/cld_compat-1.0.1/`.

## Сборка
```sh
LIBS=/mnt/project-files/libs compat/build.sh   # compat/dist/cld_compat-neoforge-1.21.1-1.0.1.jar
```

## Файлы
| Файл | За что отвечает |
|---|---|
| `CompatMod` | Пустой класс мода. |
| `mixin/CompatMixinPlugin` | Решает, какие миксины применять: смотрит, какие моды загружены (`LoadingModList`), и есть ли уже нужный метод в целевом классе (байткод через `MixinService`). В `preApply` правит байткод цели до всех миксинов. Пишет в лог строки `cld_compat: ...`. |
| `mixin/PropulsionThrusterStubMixin` | Gadgets & Gizmos 1.2.10 (`createthrusters`, внутри `gadgets-and-gizmos-bundled`) ставит `@Redirect` на вызов `getMultiblockOxidizerThrustMultiplier(I)F` внутри `ThrusterBlockEntity.addThrusterDetails`. В Create Propulsion 1.1.3 метод `addThrusterDetails` есть, а такого вызова и метода нет; плагин G&G проверяет только наличие мода, и `createpropulsion` падает при создании (краш 02.11, дальше «FTB Quests theme file is missing» и `NoSuchElementException` в `QuestShape.reload`). Миксин добавляет статический метод-заглушку (возвращает 1), а плагин в `preApply` вставляет в начало `addThrusterDetails` вызов `getMultiblockOxidizerThrustMultiplier(1)` с выбросом результата. Его и перехватывает G&G; работает только при показе подсказки в очках инженера, ничего не меняет. Включается, только если стоят оба мода и метода в классе ещё нет. |
| `mixin/WntNoSleepMixin` | War 'n Taxes 5.0.9 (`minecolonytax`): `OfficerColonyVisitTracker.onServerStarting` через `server.execute` вызывает `Thread.sleep(5000)`, а из потока сервера execute выполняется сразу, и сервер стоит 5 с при каждом входе в мир (в логе 5 с между `MineColonyTax server startup complete` и `Create: Food - Server Startup`). `@Redirect` убирает только сон, перенос колоний идет на том же месте. Включается, если стоит War 'n Taxes (1.0.1). |

## Грабли
- Добавить метод миксином мало: инжектор G&G ищет вызов внутри уже существующего `addThrusterDetails`, а новые методы миксинов в чужие методы не вставляются. Поэтому вызов вставляется в `preApply` (он идёт до всех миксинов цели). Первая версия с отдельным методом-заглушкой на сервере не помогла.
- Если инжектор чужого мода должен встать в метод, добавленный нашим миксином, наш приоритет должен быть ниже (`InjectionPoint.checkPriority`); у нас 500.
- Проверено 02.11 серверным тестом `qa/server.sh` с полным набором модов из `tc — копия` (без cld_test_panel, cld_perf, atmospherics): без cld_compat тот же `Failed to create mod instance: createpropulsion`, с ним сервер запустился, прогрузил чанки и остановился без ошибок миксинов.
- Перед `compat/build.sh` удалять старый jar той же версии из `dist/`: zip дописывает в существующий архив, и в jar остаются удаленные классы.
- Когда Create Propulsion обновят до версии с `getMultiblockOxidizerThrustMultiplier`, заглушка сама выключится; тогда её можно удалить.
- Миксины чужого мода можно проверить без игры: `python3 -I qa/mixcheck.py <jar с миксинами> <mods> [доп. jar]` ищет их цели в установленных jar (вложенные jarjar тоже) и печатает те, что не найдут, куда встать. Ваниль и NeoForge передавать доп. jar из libs.
