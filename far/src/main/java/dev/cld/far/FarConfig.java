package dev.cld.far;

import net.neoforged.neoforge.common.ModConfigSpec;

// Настройки клиента: config/cld_far-client.toml, в игре, клавиша F7. Разделы идут отдельными экранами.
public final class FarConfig {
    // На Android (Amethyst/Pojav) по умолчанию выключено.
    public static final boolean ANDROID = new java.io.File("/system/build.prop").exists();

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED, CLEAR_WATER, SCOPE, PILLARS, PEAKS, NEAR_BLOCKS;
    public static final ModConfigSpec.IntValue DISTANCE, STEP, MOUNTAINS, THREADS, SCOPE_RANGE, PIXEL, BLOCK_SIZE, UPLOADS;
    public static final ModConfigSpec.DoubleValue DETAIL, FOG, SMOOTH;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Основное: включение, дальность, туман").push("general");
        ENABLED = b.comment("Рисовать дальний рельеф").define("enabled", !ANDROID);
        DISTANCE = b.comment("Дальность, блоков").defineInRange("distance", ANDROID ? 2048 : 8192, 512, 32768);
        FOG = b.comment("Начало тумана, доля дальности").defineInRange("fog", 0.55, 0.0, 0.95);
        b.pop();

        b.comment("Качество рельефа: насколько мелкая сетка").push("quality");
        STEP = b.comment("Шаг сетки у ближнего края, блоков (меньше, подробнее и дольше считать)").defineInRange("step", 8, 2, 64);
        MOUNTAINS = b.comment("Во сколько раз мельче сетка у горных тайлов (1, как у равнин)").defineInRange("mountains", ANDROID ? 1 : 2, 1, 3);
        DETAIL = b.comment("Детальность: тайл делится, пока до него ближе detail x его размер (больше, подробнее вдали)")
                .defineInRange("detail", 2.5, 1.0, 6.0);
        b.pop();

        b.comment("Вид рельефа: форма склонов, скалы, вода").push("look");
        PIXEL = b.comment("Пикселизация: 0, гладкие склоны, 1, плоские грани, 2, ступеньки-блоки").defineInRange("pixel", 2, 0, 2);
        SMOOTH = b.comment("Сглаживание рельефа вдали: 0, как есть (острые пики), 1, мягко и мыльно").defineInRange("smooth", 0.15, 0.0, 1.0);
        PILLARS = b.comment("Каменные столбы и шпили генератора рисуются отдельно, круглыми и на своём месте, а не коробками по сетке")
                .define("pillars", true);
        PEAKS = b.comment("Острые пики: одиночная вершина в режиме блоков сужается ступенями кверху, а не стоит широкой коробкой")
                .define("peaks", true);
        CLEAR_WATER = b.comment("Прозрачная дальняя вода с дном, как в игре (без резкого среза на стыке с чанками). Выкл, дешевле: непрозрачная вода")
                .define("clearWater", !ANDROID);
        b.pop();

        b.comment("Стык с чанками и блочный рельеф рядом").push("blocks");
        NEAR_BLOCKS = b.comment("Высокие скалы рядом: то, что игра не рисует выше и ниже прорисовки, достраивается блоками, а не грубой сеткой")
                .define("nearBlocks", !ANDROID);
        SCOPE = b.comment("Подзорная труба: в поле зрения рельеф блочный, как обычные чанки").define("spyglass", !ANDROID);
        SCOPE_RANGE = b.comment("Подзорная труба: до какой дальности, блоков").defineInRange("spyglassRange", 1024, 256, 2048);
        BLOCK_SIZE = b.comment("Размер блока у блочного рельефа: 1, как в игре, 2, вдвое крупнее и вчетверо дешевле").defineInRange("blockSize", 1, 1, 2);
        b.pop();

        b.comment("Производительность").push("performance");
        THREADS = b.comment("Фоновых потоков для расчёта рельефа").defineInRange("threads", ANDROID ? 1 : 2, 1, 8);
        UPLOADS = b.comment("Сколько готовых тайлов загружать в видеокарту за кадр (больше, быстрее появляется, но возможны рывки)")
                .defineInRange("uploadsPerFrame", 4, 1, 32);
        b.pop();
        SPEC = b.build();
    }

    private FarConfig() {}
}
