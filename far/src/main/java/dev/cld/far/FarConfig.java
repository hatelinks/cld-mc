package dev.cld.far;

import net.neoforged.neoforge.common.ModConfigSpec;

// Настройки клиента: config/cld_far-client.toml, в игре, клавиша F7. Разделы идут отдельными экранами.
public final class FarConfig {
    // На Android (Amethyst/Pojav) по умолчанию выключено.
    public static final boolean ANDROID = new java.io.File("/system/build.prop").exists();

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED, CLEAR_WATER, SCOPE, PILLARS, PEAKS, NEAR_BLOCKS, BLOCK_SIDES, MESA_BANDS, SKY_HAZE, SUNSET, WEATHER,
            TREES, ICE, SHORE, RIVERS, WAVES,
            DISK_CACHE, ADAPTIVE_UPLOADS, LOOK_AHEAD, PAUSE;
    public static final ModConfigSpec.IntValue DISTANCE, STEP, MOUNTAINS, THREADS, SCOPE_RANGE, PIXEL, BLOCK_SIZE, UPLOADS,
            SAMPLE_CACHE, DISK_CACHE_MB, MAX_VRAM;
    public static final ModConfigSpec.DoubleValue DETAIL, FOG, SMOOTH, SUN_SHADE, VALLEY_SHADE, COLOR_NOISE, BRIGHTNESS, SATURATION, HAZE,
            GLINT, REFLECT, CLOUD_SHADOWS, MIST, CURVATURE;

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
        BLOCK_SIDES = b.comment("Бока блоков как в игре: под травой земля, под снегом камень (в режиме блоков и в подзорной трубе)")
                .define("blockSides", true);
        MESA_BANDS = b.comment("Полосы терракоты на обрывах пустошей (мезы), разноцветными слоями по высоте").define("mesaBands", true);
        TREES = b.comment("Леса вдали: кроны бугром над землёй, гуще и выше в джунглях, с прогалинами рядом").define("trees", true);
        b.pop();

        b.comment("Вода: волны, блики, лёд, берега").push("water");
        CLEAR_WATER = b.comment("Прозрачная дальняя вода с дном, как в игре (без резкого среза на стыке с чанками). Выкл, дешевле: непрозрачная вода")
                .define("clearWater", !ANDROID);
        WAVES = b.comment("Волны: дальняя вода чуть рябит и переливается").define("waves", true);
        GLINT = b.comment("Блики солнца на воде (0, выкл)").defineInRange("sunGlint", 0.6, 0.0, 1.0);
        REFLECT = b.comment("Отражение неба: под острым углом вода светлеет цветом неба (0, выкл)").defineInRange("waterReflect", 0.5, 0.0, 1.0);
        ICE = b.comment("Лёд: вода в холодных биомах рисуется сплошным льдом").define("ice", true);
        SHORE = b.comment("Берега: светлая пена у берега, бирюзовое мелководье в тёплых морях").define("shore", true);
        RIVERS = b.comment("Узкие реки видны и вдали, где сетка грубее самой реки (иначе рвутся на куски)").define("rivers", true);
        b.pop();

        b.comment("Свет и цвет: солнце, тени, дымка, погода").push("light");
        SUN_SHADE = b.comment("Освещение от солнца: склоны к солнцу ярче, от него темнее, меняется в течение дня (0, выкл)")
                .defineInRange("sunShade", 0.35, 0.0, 1.0);
        VALLEY_SHADE = b.comment("Затенение впадин: дно долин и ущелий темнее, рельеф объёмнее (0, выкл)").defineInRange("valleyShade", 0.35, 0.0, 1.0);
        COLOR_NOISE = b.comment("Пестрота цвета: соседние клетки чуть разной яркости, как текстуры блоков (0, ровный цвет)")
                .defineInRange("colorNoise", 0.5, 0.0, 1.0);
        BRIGHTNESS = b.comment("Яркость дальнего рельефа (1, как у блоков)").defineInRange("brightness", 1.0, 0.5, 1.5);
        SATURATION = b.comment("Насыщенность цвета (1, как есть, 0, серый)").defineInRange("saturation", 1.0, 0.0, 2.0);
        HAZE = b.comment("Дымка вдали: насколько дальние горы светлеют к горизонту (0, выкл)").defineInRange("haze", 0.3, 0.0, 1.0);
        SKY_HAZE = b.comment("Цвет дымки от неба: днём голубая, на закате тёплая, ночью тёмная").define("skyHaze", true);
        SUNSET = b.comment("Тёплый свет на рассвете и закате").define("sunset", true);
        WEATHER = b.comment("Погода: в дождь и грозу рельеф темнее, а туман ближе").define("weather", true);
        b.pop();

        b.comment("Небо и воздух: тени облаков, туман в долинах, горизонт").push("atmosphere");
        CLOUD_SHADOWS = b.comment("Тени облаков: по дальнему рельефу плывут тёмные пятна (0, выкл)").defineInRange("cloudShadows", 0.3, 0.0, 1.0);
        MIST = b.comment("Туман в низинах: над водой и в долинах вдали белая дымка, гуще утром (0, выкл)").defineInRange("valleyMist", 0.4, 0.0, 1.0);
        CURVATURE = b.comment("Кривизна земли: дальний край чуть уходит вниз за горизонт (0, плоско)").defineInRange("curvature", 0.25, 0.0, 1.0);
        b.pop();

        b.comment("Стык с чанками и блочный рельеф рядом").push("blocks");
        NEAR_BLOCKS = b.comment("Высокие скалы рядом: то, что игра не рисует выше и ниже прорисовки, достраивается блоками, а не грубой сеткой")
                .define("nearBlocks", !ANDROID);
        SCOPE = b.comment("Подзорная труба: в поле зрения рельеф блочный, как обычные чанки").define("spyglass", !ANDROID);
        SCOPE_RANGE = b.comment("Подзорная труба: до какой дальности, блоков").defineInRange("spyglassRange", 1024, 256, 2048);
        BLOCK_SIZE = b.comment("Размер блока у блочного рельефа: 1, как в игре, 2, вдвое крупнее и вчетверо дешевле").defineInRange("blockSize", 1, 1, 2);
        b.pop();

        b.comment("Производительность").push("performance");
        THREADS = b.comment("Фоновых потоков для расчёта рельефа (0, по числу ядер)").defineInRange("threads", ANDROID ? 1 : 0, 0, 8);
        PAUSE = b.comment("На паузе и в свёрнутом окне расчёт рельефа ждёт").define("pauseInMenu", true);
        LOOK_AHEAD = b.comment("Сначала считать то, что впереди по взгляду, потом за спиной").define("lookAhead", true);
        ADAPTIVE_UPLOADS = b.comment("При просадке кадров загружать в видеокарту по одному тайлу за кадр").define("adaptiveUploads", true);
        MAX_VRAM = b.comment("Лимит видеопамяти под дальний рельеф, МБ: сверх него выгружается то, что давно не видно")
                .defineInRange("maxVramMb", ANDROID ? 256 : 1024, 64, 8192);
        SAMPLE_CACHE = b.comment("Кэш рельефа в памяти, МБ: с ним смена вида и возврат на место не считают рельеф заново")
                .defineInRange("sampleCacheMb", ANDROID ? 64 : 256, 0, 4096);
        DISK_CACHE = b.comment("Кэш рельефа на диске (папка cld_far_cache): при повторном входе в мир рельеф появляется сразу")
                .define("diskCache", !ANDROID);
        DISK_CACHE_MB = b.comment("Лимит кэша на диске для одного мира, МБ").defineInRange("diskCacheMb", 1024, 64, 65536);
        UPLOADS = b.comment("Сколько готовых тайлов загружать в видеокарту за кадр (больше, быстрее появляется, но возможны рывки)")
                .defineInRange("uploadsPerFrame", 4, 1, 32);
        b.pop();
        SPEC = b.build();
    }

    private FarConfig() {}
}
