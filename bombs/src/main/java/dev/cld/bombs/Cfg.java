package dev.cld.bombs;

import net.neoforged.neoforge.common.ModConfigSpec;

// Настройки. SERVER хранится в мире (serverconfig), на сервере меняет оператор. COMMON у каждого устройства свой: предел радиуса и скорость кратера.
public final class Cfg {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec COMMON_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue MAX_LEVEL;
    public static final ModConfigSpec.DoubleValue CURVE;
    public static final ModConfigSpec.DoubleValue NORMAL_MIN, NORMAL_MAX, BLUE_MIN, BLUE_MAX, BABY_MIN, BABY_MAX;
    public static final ModConfigSpec.IntValue OBSIDIAN_LEVEL, WATER_LEVEL, FLUID_LEVEL;
    public static final ModConfigSpec.DoubleValue DAMAGE_MIN, DAMAGE_MAX;
    public static final ModConfigSpec.DoubleValue DROP_CHANCE;
    public static final ModConfigSpec.IntValue MAX_DROP_STACKS;
    public static final ModConfigSpec.DoubleValue EDGE;
    public static final ModConfigSpec.BooleanValue PROTECT_COLONIES;
    public static final ModConfigSpec.IntValue POWDER_PER_LEVEL, TNT_POWDER;
    public static final ModConfigSpec.IntValue LOOT_MIN, LOOT_MAX;
    public static final ModConfigSpec.IntValue GLINT_LEVEL;

    public static final ModConfigSpec.DoubleValue RADIUS_CAP;
    public static final ModConfigSpec.IntValue BLOCKS_PER_TICK;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Включить уровни и кратеры. Выключено: бомбы работают как в самом Supplementaries").define("enabled", true);
        MAX_LEVEL = b.comment("Наибольший уровень бомбы").defineInRange("max_level", 10, 1, 10);
        CURVE = b.comment("Как быстро растёт радиус: 1 ровно, 2 медленно в начале и быстро в конце").defineInRange("radius_curve", 2.0, 0.5, 4.0);
        b.push("radius");
        NORMAL_MIN = b.comment("Бомба: радиус на 1 уровне").defineInRange("bomb_level1", 5.0, 0.5, 64.0);
        NORMAL_MAX = b.comment("Бомба: радиус на последнем уровне").defineInRange("bomb_max", 50.0, 0.5, 64.0);
        BLUE_MIN = b.comment("Синяя бомба: радиус на 1 уровне").defineInRange("blue_level1", 4.0, 0.5, 64.0);
        BLUE_MAX = b.comment("Синяя бомба: радиус на последнем уровне (она ещё разлетается на маленькие)").defineInRange("blue_max", 40.0, 0.5, 64.0);
        BABY_MIN = b.comment("Осколок синей бомбы: радиус на 1 уровне").defineInRange("baby_level1", 1.5, 0.5, 64.0);
        BABY_MAX = b.comment("Осколок синей бомбы: радиус на последнем уровне").defineInRange("baby_max", 12.0, 0.5, 64.0);
        b.pop();
        OBSIDIAN_LEVEL = b.comment("С какого уровня ломается обсидиан и блоки такой же прочности. Бедрок не ломается никогда").defineInRange("obsidian_level", 8, 1, 11);
        WATER_LEVEL = b.comment("С какого уровня взрыв убирает воду").defineInRange("water_level", 5, 1, 11);
        FLUID_LEVEL = b.comment("С какого уровня взрыв убирает лаву и жидкости модов").defineInRange("fluid_level", 8, 1, 11);
        DAMAGE_MIN = b.comment("Урон в центре на 1 уровне").defineInRange("damage_level1", 12.0, 0.0, 10000.0);
        DAMAGE_MAX = b.comment("Урон в центре на последнем уровне").defineInRange("damage_max", 400.0, 0.0, 10000.0);
        DROP_CHANCE = b.comment("Доля разрушенных блоков, которые выпадают предметом").defineInRange("drop_chance", 0.25, 0.0, 1.0);
        MAX_DROP_STACKS = b.comment("Сколько стопок предметов может выпасть из одного взрыва").defineInRange("max_drop_stacks", 200, 0, 5000);
        EDGE = b.comment("Неровность края кратера, блоков").defineInRange("edge_jitter", 1.5, 0.0, 6.0);
        PROTECT_COLONIES = b.comment("Взрыв не трогает блоки в колониях MineColonies").define("protect_colonies", true);
        POWDER_PER_LEVEL = b.comment("Сколько пороха стоит каждый уровень: уровень N стоит N раз по столько").defineInRange("powder_per_level", 1, 1, 8);
        TNT_POWDER = b.comment("Сколько пороха заменяет один TNT при прокачке").defineInRange("tnt_powder", 5, 1, 64);
        LOOT_MIN = b.comment("Уровень бомб в сундуках: от").defineInRange("loot_level_min", 1, 1, 10);
        LOOT_MAX = b.comment("Уровень бомб в сундуках: до").defineInRange("loot_level_max", 3, 1, 10);
        GLINT_LEVEL = b.comment("С какого уровня бомба блестит").defineInRange("glint_level", 4, 1, 11);
        SERVER_SPEC = b.build();

        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        RADIUS_CAP = c.comment("Предел радиуса на этом устройстве (на телефоне лучше 20-25)").defineInRange("radius_cap", 50.0, 1.0, 64.0);
        BLOCKS_PER_TICK = c.comment("Сколько блоков кратера проверять за тик: меньше, если взрыв подтормаживает").defineInRange("blocks_per_tick", 20000, 500, 500000);
        COMMON_SPEC = c.build();
    }

    private Cfg() {}

    // Значения SERVER нет, пока мир не открыт (например в подсказке в меню): тогда берётся значение по умолчанию.
    public static int i(ModConfigSpec.IntValue v) {
        try { return v.get(); } catch (RuntimeException e) { return v.getDefault(); }
    }

    public static double d(ModConfigSpec.DoubleValue v) {
        try { return v.get(); } catch (RuntimeException e) { return v.getDefault(); }
    }

    public static boolean b(ModConfigSpec.BooleanValue v) {
        try { return v.get(); } catch (RuntimeException e) { return v.getDefault(); }
    }
}
