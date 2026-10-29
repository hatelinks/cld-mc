# sable-android-support

Мод для Android (Amethyst, Kopper Zink): Sable грузит свою нативку Rapier только для ПК, этот мод подменяет её на сборку под arm64 Android. На Маке не нужен (на ПК он ничего не делает, только пишет логи).

- `src/main/resources/sable_android_transformer.js` — coremod: в `Rapier3D.loadLibrary()` меняет `System.load` на `SableAndroidLoader.load`, а в `tick()` 16 блоков Simulated вставляет прореживание (раз в 2-4 тика).
- `SableAndroidLoader` на Android достаёт `natives/sable_rapier/sable_rapier_aarch64_android.so` из jar во временный файл и грузит его.
- `SableBridgeLogger` пишет логи в папку `SableBridge_Logs` рядом с игрой.
- `config/sable-android-patches.toml` создаётся и читается, но интервалы в игре берутся из скрипта (так было и в 1.2.0).

Исходники восстановлены из jar 2.0.0 (javap, вручную), в репозитории их до этого не было.

## Сборка

```sh
LIBS=/путь/к/libs sable_android/build.sh   # dist/sable-android-support-neoforge-1.21.1-2.0.0.jar
```

Нативка готовая лежит в `native/sable_rapier_aarch64_android.so` (Sable 2.0.6, JNI из `native/jni_sable_2.0.6.txt`). Если Sable обновится и поменяет JNI, нативку пересобрать на Маке: `native/build_android_native.sh` (rustup, cargo-ndk, Android NDK; клонирует Sable в `native/work/`).
В готовой нативке пути `/Users/<имя>/.cargo/...` из сообщений паники заменены на `/Users/user/...` той же длины; скрипт теперь сам убирает их через `--remap-path-prefix`.
