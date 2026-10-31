package dev.cld.bombs;

import net.mehvahdjukaar.supplementaries.common.entities.BombEntity;
import net.mehvahdjukaar.supplementaries.common.items.BombItem;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

// Уровень бомбы: у предмета компонент cld_bombs:level, у летящей бомбы вложение cld_bombs:level. Нет уровня = 1.
public final class Levels {
    private Levels() {}

    public static int max() {
        return Cfg.i(Cfg.MAX_LEVEL);
    }

    public static int of(ItemStack s) {
        Integer l = s.get(BombsMod.LEVEL.get());
        return clamp(l == null ? 1 : l);
    }

    // уровень 1 хранится как отсутствие компонента, чтобы такие бомбы складывались с только что скрафченными
    public static void set(ItemStack s, int level) {
        if (clamp(level) <= 1) s.remove(BombsMod.LEVEL.get());
        else s.set(BombsMod.LEVEL.get(), clamp(level));
    }

    public static int of(Entity e) {
        return e.hasData(BombsMod.ENTITY_LEVEL.get()) ? clamp(e.getData(BombsMod.ENTITY_LEVEL.get())) : 1;
    }

    public static void set(Entity e, int level) {
        e.setData(BombsMod.ENTITY_LEVEL.get(), clamp(level));
    }

    public static int clamp(int l) {
        return Math.max(1, Math.min(max(), l));
    }

    public static boolean isBomb(ItemStack s) {
        return s.getItem() instanceof BombItem;
    }

    // Радиус: от радиуса 1 уровня до радиуса последнего, по кривой (медленно в начале, быстро в конце). Сверху предел устройства.
    public static double radius(BombEntity.BombType type, int level) {
        double lo, hi;
        switch (type) {
            case BLUE -> { lo = Cfg.d(Cfg.BLUE_MIN); hi = Cfg.d(Cfg.BLUE_MAX); }
            case BLUE_BABY -> { lo = Cfg.d(Cfg.BABY_MIN); hi = Cfg.d(Cfg.BABY_MAX); }
            default -> { lo = Cfg.d(Cfg.NORMAL_MIN); hi = Cfg.d(Cfg.NORMAL_MAX); }
        }
        double r = lo + (hi - lo) * t(level);
        return Math.min(r, Cfg.d(Cfg.RADIUS_CAP));
    }

    public static double damage(int level) {
        double lo = Cfg.d(Cfg.DAMAGE_MIN), hi = Cfg.d(Cfg.DAMAGE_MAX);
        return lo + (hi - lo) * t(level);
    }

    // 0 на 1 уровне, 1 на последнем
    private static double t(int level) {
        int m = max();
        if (m <= 1) return 1;
        double x = (clamp(level) - 1) / (double) (m - 1);
        return Math.pow(x, Cfg.d(Cfg.CURVE));
    }

    public static boolean breaksObsidian(int level) {
        return level >= Cfg.i(Cfg.OBSIDIAN_LEVEL);
    }

    // Цвет названия ярче с уровнем
    public static ChatFormatting color(int level) {
        int m = max();
        double x = m <= 1 ? 1 : (level - 1) / (double) (m - 1);
        if (x < 0.25) return ChatFormatting.WHITE;
        if (x < 0.5) return ChatFormatting.YELLOW;
        if (x < 0.75) return ChatFormatting.GOLD;
        if (x < 1) return ChatFormatting.RED;
        return ChatFormatting.LIGHT_PURPLE;
    }

    // Сколько пороха стоит поднять бомбу до уровня level
    public static int cost(int level) {
        return level * Cfg.i(Cfg.POWDER_PER_LEVEL);
    }
}
