# sable-android-support

Карта кода. Читать вместо разбора исходников; кто меняет мод, обновляет эту страницу в своём PR. Подробности сборки нативки: `sable_android/README.md`.

- modId `sable_android`, папка `sable_android/`, версия **2.0.0** (`VERSION` в `sable_android/build.sh`). Зависит от `sable` (Sable 2.0.6).
- Только для Android (Amethyst, Kopper Zink): Sable грузит нативку Rapier только для ПК, мод подменяет её на сборку под arm64 Android и прореживает тики 16 блоков Simulated. На Маке не нужен.
- Исходники восстановлены из jar 2.0.0 вручную (javap), поведение как в jar.

## Сборка
```sh
LIBS=/mnt/project-files/libs sable_android/build.sh   # dist/sable-android-support-neoforge-1.21.1-2.0.0.jar
```
В jar кладутся: скрипт coremod в корень и копией в `META-INF/coremods/`, нативка в `natives/sable_rapier/`, манифест с `MixinConfigs`. Нативку пересобирать только на Маке: `native/build_android_native.sh` (rustup, cargo-ndk, NDK), когда Sable меняет JNI (список функций `native/jni_sable_2.0.6.txt`).

## Файлы
| Файл | За что отвечает |
|---|---|
| `src/main/resources/sable_android_transformer.js` | Coremod (`META-INF/coremods.json`). 1) `Rapier3D.loadLibrary()`: `System.load` заменяется на `SableAndroidLoader.load`. 2) В начало `tick()` 16 классов Simulated вставляется `if (!TickThrottleManager.shouldTick(this, N)) return;`, N от 2 до 4 (таблица `TICK_THROTTLE_CLASSES`). |
| `SableAndroidLoader` | Не Android: обычный `System.load`. Android: копирует `.so` из jar во временный файл и грузит его. |
| `TickThrottleManager` | Счётчик тиков на блок (`WeakHashMap`), true раз в N тиков. `LIVE_INTERVALS` может переопределить N, но сейчас не заполняется. |
| `TickThrottleConfig` | `config/sable-android-patches.toml`, раздел `[tick_throttle]`: создаётся и читается, но в игре не применяется (интервалы из скрипта, так было и в 1.2.0). |
| `SableAndroid` | Класс мода: логи о платформе, `isAndroid()` (os.version, java.vendor, java.vm.name, наличие `android.os.Build`), версия GL через отражение. |
| `SableBridgeLogger` | Свои логи в `SableBridge_Logs/` рядом с игрой (`*_Latest.log`, старые переименовываются). |
| `sable_android.mixins.json` | Пустой, необязательный. |

## Грабли
- Прореживание тиков вставляется coremod на любой платформе, не только на Android: если поставить мод на ПК, блоки Simulated тоже будут тикать реже.
- Строка версии в логе осталась «v1.2.0» (так в оригинальном jar).
- Нативка Rust по умолчанию содержит пути `/Users/<имя>/.cargo`: собирать с `--remap-path-prefix`, перед коммитом проверять `strings`.
- Sodium на Android не ставить (краши Aeronautics).
