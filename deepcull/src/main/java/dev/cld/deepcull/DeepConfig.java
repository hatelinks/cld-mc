package dev.cld.deepcull;

import net.neoforged.neoforge.common.ModConfigSpec;

// Настройки клиента: config/cld_deepcull-client.toml.
public final class DeepConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue MARGIN, KEEP;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Не строить и не рисовать глубокие секции, пока игрок на поверхности (F8)").define("enabled", true);
        MARGIN = b.comment("На сколько блоков ниже самой низкой поверхности вокруг чанка начинается отсечка")
                .defineInRange("margin", 16, 0, 128);
        KEEP = b.comment("Сколько секций (по 16 блоков) под камерой всегда рисовать").defineInRange("keep", 2, 0, 16);
        SPEC = b.build();
    }

    private DeepConfig() {}
}
