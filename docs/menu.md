# cld_menu: Главное меню сборки

Карта кода. Читать вместо разбора исходников; кто меняет мод, обновляет эту страницу в своём PR.

- modId `cld_menu`, папка `menu/`, версия **1.0.1** (`VERSION` в `menu/build.sh`). Только клиент.
- Живой пиксельный пейзаж вместо кубической панорамы (во всех меню, где она была), логотип «TelipailoCraft» шрифтом Minecraft, без кнопок Realms и «Моды», без строк версий внизу слева.

## Сборка
```sh
LIBS=/mnt/project-files/libs menu/build.sh   # menu/dist/cld_menu-neoforge-1.21.1-1.0.1.jar
```

## Файлы
| Файл | За что отвечает |
|---|---|
| `MenuMod` | Подписывает `TitleButtons.onInit` на `ScreenEvent.Init.Post`. |
| `Landscape` | Весь пейзаж: 4 слоя гор с параллаксом и ёлками, смена суток (240 с), туман и гроза с дождём и молниями (цикл 150 с), звёзды, квадратные солнце и луна, облака. Только `GuiGraphics.fill`, без текстур. Настройки цвета и скорости в константах в начале класса. |
| `Logo` | Название из lang-ключа `cld_menu.title` (`assets/cld_menu/lang/en_us.json`): объёмные изумрудные бока, обводка, масштаб по ширине экрана. |
| `TitleButtons` | Убирает кнопки с ключами `menu.online` (Realms) и `fml.menu.mods`, сдвигает остальные вверх на освободившиеся ряды. |
| `mixin/PanoramaRendererMixin` | `PanoramaRenderer.render` HEAD, cancel: рисует `Landscape`. |
| `mixin/LogoRendererMixin` | `LogoRenderer.renderLogo` HEAD, cancel: рисует `Logo`. |
| `mixin/TitleScreenMixin` | `@Redirect` на `BrandingControl.forEachLine` в `render` (без строк версий); после `init` обнуляет `realmsNotificationsScreen` (значки Realms). |

## Настройки
Нет. Название меняется в lang-файле.

## Грабли
- 1.0.1: не падать при возврате в меню из настроек.
- Кнопки ищутся по ключу перевода; если мод поменяет подпись кнопки, она не уберётся.
