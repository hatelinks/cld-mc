# cld_autotest: автозамер в игре (служебный)

Карта кода. Читать вместо разбора исходников; кто меняет мод, обновляет эту страницу в своём PR.

- modId `cld_autotest`, папка `autotest/`, версия **0.1.3** (`VERSION` в `autotest/build.sh`). Только клиент. В обычную игру не ставить.
- Без файла `config/cld_autotest.properties` ничего не делает. С ним: ждёт главное меню, открывает мир, ставит одинаковые условия, крутит камеру или летит, меряет FPS, CPU, память, GC, по желанию пишет JFR и Spark, делает скриншот и выходит из игры.
- Пререлизы: `releases/cld_autotest-<версия>/`.

## Сборка
```sh
LIBS=/mnt/project-files/libs autotest/build.sh   # autotest/dist/cld_autotest-neoforge-1.21.1-0.1.3.jar
```

## Файлы
Один класс `AutoTest`, машина состояний по `ClientTickEvent.Post`: MENU, LOADING, WARMUP, MEASURE, PAUSE, DONE. Время кадров берётся из `RenderFrameEvent.Post`.
- MENU: пишет время запуска и память, отключает паузу при потере фокуса, ставит лимит FPS только на этот запуск, открывает мир.
- WARMUP: команды `setup` (по умолчанию: мирно, без смены дня и погоды, без мобов, время 6000, наблюдатель, tp на 40 вверх), разогрев.
- MEASURE: кадры, сэмпл раз в секунду (fps, heap, чанки, сущности), скриншот на середине, итог (fps avg и 1% low, p50, p99, max, cpu_load, GC, секции).
- PAUSE: кадры на экране паузы.
- DONE: результат в `cld_autotest/result-<время>.txt` (и строки `AUTOTEST` в логе), выход через `System.exit` в отдельном потоке; сторож через 120 с пишет стеки потоков и делает `halt`.

## Настройки (`config/cld_autotest.properties`)
| Ключ | По умолчанию | Что |
|---|---|---|
| `enabled` | true | выключить, не удаляя файл |
| `world` | | папка мира в saves (без неё сразу выход) |
| `warmup` / `measure` / `pause` | 40 / 60 / 15 | секунды фаз |
| `yaw` | 0.5 | поворот камеры за тик |
| `fly` | 0 | полёт на север, блоков за тик (вместо поворота) |
| `maxfps` | 0 | лимит FPS на этот запуск |
| `jfr` | false | JFR на время замера, `cld_autotest/rec-*.jfr` |
| `spark` | true | `spark profiler start` на время замера |
| `setup` | см. выше | команды через `;` |
| `quit` | true | выйти из игры после замера |

## Как пользоваться
Запуск на Маке через Remote Control: экземпляр Prism `cld-test`, `prismlauncher --launch cld-test -o tester`. Разбор JFR: `qa/jfr_top.py`, `jfr print --stack-depth 64`.

## Грабли
- Обычный выход из мира зависал в ожидании встроенного сервера, поэтому `System.exit` (мир тестовый, не сохраняется).
- `ServerChunkCache.getChunk` в потоке рендера из `ClientLevel.animateTick` в JFR это ошибка атрибуции.
- Лимит `maxfps` не сохраняется в options.txt, пока не открыть настройки.
