# cld_menu: Главное меню сборки

Карта кода. Читать вместо разбора исходников; кто меняет мод, обновляет эту страницу в своём PR.

- modId `cld_menu`, папка `menu/`, версия **1.1.0** (`VERSION` в `menu/build.sh`). Только клиент.
- Живой пиксельный пейзаж вместо кубической панорамы (во всех меню, где она была), логотип «TelipailoCraft» шрифтом Minecraft, без кнопок Realms и «Моды», без строк версий внизу слева.
- С 1.1.0 пейзаж рисуется в память (`scene/`, без Minecraft) и одной текстурой выводится на экран. Погода выбирается при каждом запуске (7 видов, зимой снег 4 запуска из 5), время суток и фаза луны по часам компьютера, время года по календарю.

## Сборка
```sh
LIBS=/mnt/project-files/libs menu/build.sh   # menu/dist/cld_menu-neoforge-1.21.1-1.1.0.jar
```

## Файлы
| Файл | За что отвечает |
|---|---|
| `MenuMod` | Подписывает `TitleButtons.onInit` на `ScreenEvent.Init.Post`. |
| `Landscape` | Связка сцены с игрой: размер растра 1/270 высоты окна, перерисовка не чаще `fps`, загрузка в `DynamicTexture` (`cld_menu:landscape`) и один `blit`. Выбор погоды и сезона, подпись внизу слева при F9. |
| `scene/Scene` | Вся отрисовка: небо полосами с узором Байера, звёзды и падающие звёзды, квадратные солнце и луна с фазой, радуга, облака рядами, 4 слоя гор (снежные шапки, ели и лиственные деревья по сезону, цветы), туман полосами, дождь и снег в два слоя, брызги, метель, молнии рядом и вдалеке, виньетка. |
| `scene/Weather`, `WeatherKind` | Вид погоды (`pick` по сезону), силы облаков, дождя, снега, ветра, тумана во времени, удары молний (`Strike`: место, расстояние, вспышка; `listener` для звуков). |
| `scene/Sky`, `Season` | Солнце по часам (длина дня по сезону, широта около 55), луна, заря, фаза луны; сезон по месяцу (зима это декабрь, январь, февраль). |
| `scene/MenuConfig` | `config/cld_menu.properties`: `weather`, `season`, `time`, `fps`. |
| `scene/Raster`, `Col`, `Noise` | Картинка int[] RGB, цвета, хеш и шум. |
| `Logo` | Название из lang-ключа `cld_menu.title` (`assets/cld_menu/lang/en_us.json`): объёмные изумрудные бока, обводка, масштаб по ширине экрана. |
| `TitleButtons` | Убирает кнопки с ключами `menu.online` (Realms) и `fml.menu.mods`, сдвигает остальные вверх на освободившиеся ряды. |
| `mixin/PanoramaRendererMixin` | `PanoramaRenderer.render` HEAD, cancel: рисует `Landscape`. |
| `mixin/LogoRendererMixin` | `LogoRenderer.renderLogo` HEAD, cancel: рисует `Logo`. |
| `mixin/TitleScreenMixin` | `@Redirect` на `BrandingControl.forEachLine` в `render` (без строк версий); после `init` обнуляет `realmsNotificationsScreen` (значки Realms). |

## Настройки
`config/cld_menu.properties` (создаётся сам): `weather` (auto, clear, rain, thunder, snow, fog, blizzard, rainbow), `season` (auto, winter, spring, summer, autumn), `time` (auto или 21:30), `fps` (60). Название меняется в lang-файле.
В главном меню F9 следующая погода, Shift+F9 время года, Ctrl+F9 плюс два часа (для проверки, не сохраняется).

## Превью вне игры
Пакет `scene` не зависит от Minecraft: `menu/tools/shots.sh папка` собирает `tools/MenuPreview` и рисует сетки погоды в PNG (и пишет время кадра). Один вызов: `java -cp out MenuPreview out.png 480 270 thunder,snow winter 22:30 30`.

## Грабли
- В `NativeImage` порядок байт ABGR, растр хранит RGB: переставлять при загрузке (`Landscape.upload`).
- 1.0.1: не падать при возврате в меню из настроек.
- Кнопки ищутся по ключу перевода; если мод поменяет подпись кнопки, она не уберётся.
