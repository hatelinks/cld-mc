# cld_earlywindow: экран загрузки

Карта кода. Читать вместо разбора исходников; кто меняет мод, обновляет эту страницу в своём PR.

- Папка `earlywindow/`, версия **1.0.0** (`VERSION` в `earlywindow/build.sh`). Это не мод, а сервис FML (провайдер раннего окна `cldwindow`): нет `neoforge.mods.toml`, jar кладётся в `mods/`, NeoForge сам переносит его в слой сервисов.
- Тёмный фон, две пиксельные полоски (память и прогресс загрузки), вращающийся уроборос вместо лисы. Логотип Mojang, лог и версия убраны.
- Включается в `config/fml.toml`: `earlyWindowProvider = "cldwindow"`. В `pack/minecraft/config/fml.toml` (ПК) включён. На Android пока стандартный `fmlearlywindow`, пока не проверено там.

## Сборка
```sh
LIBS=/mnt/project-files/libs earlywindow/build.sh   # earlywindow/dist/cld_earlywindow-1.21.1-1.0.0.jar
```
Classpath все jar из LIBS подряд; манифест с `Automatic-Module-Name: cld_earlywindow`, собирается `jar`, а не `zip`.

## Файлы
| Файл | За что отвечает |
|---|---|
| `CldWindow` | Наследник `net.neoforged.fml.earlydisplay.DisplayWindow`. `name()` = `cldwindow`. `start`: тёмная схема `ColourScheme.BLACK` с фоном #121212, затем через 5 мс в планировщике рендера (`renderScheduler`) `swapElements` меняет список элементов на свои: `ouroboros`, `memoryBar`, `progressBars`. `addMojangTexture` пустой. `updateModuleReads` переопределён: родитель вызывает `addReads` от имени своего модуля, для подкласса это запрещено. |
| `META-INF/services/net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider` | Регистрация провайдера. |

Почти всё через отражение (`field`, `method`): поля `DisplayWindow` (`renderScheduler`, `font`, `elements`, `colourScheme`, `loadingOverlay`), методы `SimpleFont`, `PerformanceInfo`, конструктор `RenderElement`.

## Грабли
- Цвета в FML упакованы как ABGR (`EMERALD = 0xFF6ABF3F` это #3fbf6a).
- При любой ошибке в `swapElements` остаётся стандартный экран NeoForge (пишет `[cldwindow] keeping default screen` в stderr). Ошибка в `updateModuleReads` роняет запуск.
- Отражение завязано на внутренности FML 21.1.248: при смене NeoForge проверять первым делом.
- Виртуальный экран 854x480, кадры 20 в секунду (вращение считается по номеру кадра).
