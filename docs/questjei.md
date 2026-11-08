# cld_questjei «Квесты и JEI»

Версия 1.0.0, только клиент. Нужны FTB Quests 2101.1 и JEI 19.

## Что делает
- Клик по предмету задачи в окне FTB Quests открывает рецепт в JEI, клик с Shift показывает, где предмет используется.
- Если у задачи несколько подходящих предметов (тег), FTB Quests открывает список, клик по предмету в нём тоже идёт в JEI.
- Клавиша закладки JEI (A) над предметом квеста добавляет или убирает закладку.
- Наведение на предмет и клавиши JEI R и U работали и без мода: это делает FTB Library (`JEIIntegration`, глобальный обработчик экранов).

## Как устроено
- `QuestJeiPlugin`: плагин JEI (`@JeiPlugin`). Когда JEI готов, ставит наш помощник через `FTBQuests.setRecipeModHelper`.
- `JeiQuestHelper`: реализация `dev.ftb.mods.ftbquests.integration.RecipeModHelper` (показ рецептов для предметов и жидкостей, закладки). Своих категорий квестов в JEI не добавляет.
- Без помощника FTB Quests использует заглушку `RecipeModHelper$NoOp`, поэтому клик ничего не делал. Обычно помощника ставит мод FTB XMod Compat, его в сборке нет.

## Грабли
- `setRecipeModHelper` второй раз кидает исключение. Если поставить FTB XMod Compat (modId `ftbxmodcompat`), наш мод сам отключается.

## Сборка
`LIBS=/mnt/project-files/libs MODS=/mnt/project-files/libs-mods questjei/build.sh`. В MODS нужны ftb-quests, ftb-library, jei и architectury (копии лежат в `/mnt/project-files/libs-mods/`).
