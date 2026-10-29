package dev.cld.far;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Настройки клиента: config/cld_far-client.toml, в игре — клавиша F7. */
public final class FarConfig {
    /** На Android (Amethyst/Pojav) по умолчанию выключено. */
    public static final boolean ANDROID = new java.io.File("/system/build.prop").exists();

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue DISTANCE, STEP, MOUNTAINS, THREADS;
    public static final ModConfigSpec.DoubleValue DETAIL, FOG;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Рисовать дальний рельеф").define("enabled", !ANDROID);
        DISTANCE = b.comment("Дальность, блоков").defineInRange("distance", ANDROID ? 2048 : 8192, 512, 32768);
        STEP = b.comment("Шаг сетки у ближнего края, блоков (меньше — подробнее и дольше считать)").defineInRange("step", 8, 2, 64);
        MOUNTAINS = b.comment("Во сколько раз мельче сетка у горных тайлов (1 — как у равнин)").defineInRange("mountains", ANDROID ? 1 : 2, 1, 3);
        DETAIL = b.comment("Детальность: тайл делится, пока до него ближе detail × его размер (больше — подробнее вдали)")
                .defineInRange("detail", 2.5, 1.0, 6.0);
        THREADS = b.comment("Фоновых потоков для расчёта рельефа").defineInRange("threads", ANDROID ? 1 : 2, 1, 8);
        FOG = b.comment("Начало тумана, доля дальности").defineInRange("fog", 0.55, 0.0, 0.95);
        SPEC = b.build();
    }

    private FarConfig() {}
}
