package dev.cld.tcw;

import dev.cld.tcw.gen.Macro;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Каменные слои на открытых склонах: свой набор пород у каждого стиля гор. */
final class Palette {
    private static final Block[][] ROCK = new Block[Macro.STYLES.length][];
    static {
        ROCK[Macro.Style.ALPINE.ordinal()] = new Block[] { Blocks.STONE, Blocks.STONE, Blocks.ANDESITE, Blocks.STONE, Blocks.TUFF, Blocks.COBBLESTONE };
        ROCK[Macro.Style.PATAGONIA.ordinal()] = new Block[] { Blocks.STONE, Blocks.DIORITE, Blocks.STONE, Blocks.ANDESITE, Blocks.SMOOTH_BASALT };
        ROCK[Macro.Style.DOLOMITE.ordinal()] = new Block[] { Blocks.CALCITE, Blocks.DIORITE, Blocks.CALCITE, Blocks.STONE, Blocks.SMOOTH_SANDSTONE };
        ROCK[Macro.Style.GRANITE.ordinal()] = new Block[] { Blocks.STONE, Blocks.DIORITE, Blocks.CALCITE, Blocks.STONE, Blocks.ANDESITE };
        ROCK[Macro.Style.VOLCANIC.ordinal()] = new Block[] { Blocks.BASALT, Blocks.BLACKSTONE, Blocks.TUFF, Blocks.SMOOTH_BASALT, Blocks.DEEPSLATE };
        ROCK[Macro.Style.MESA.ordinal()] = new Block[] { Blocks.TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.YELLOW_TERRACOTTA,
                Blocks.WHITE_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA };
        ROCK[Macro.Style.PLATEAU.ordinal()] = new Block[] { Blocks.STONE, Blocks.ANDESITE, Blocks.TUFF, Blocks.STONE };
        ROCK[Macro.Style.KARST.ordinal()] = new Block[] { Blocks.STONE, Blocks.ANDESITE, Blocks.CALCITE, Blocks.STONE, Blocks.MOSSY_COBBLESTONE };
    }

    private Palette() {}

    /** Стиль для столбца: случайно по весам, чтобы границы стилей были неровными. */
    static int pick(double[] weights, long seed, int x, int z) {
        double r = Macro.hashD(seed, x, z), acc = 0;
        for (int i = 0; i < weights.length; i++) {
            acc += weights[i];
            if (r <= acc) return i;
        }
        return 0;
    }

    static BlockState rock(int style, long seed, int x, int y, int z, double wobble) {
        Block[] p = ROCK[style];
        int band = (int) Math.floor((y + wobble) / 4.0);
        double r = Macro.hashD(seed ^ (style * 977L), band, style);
        int idx = r < 0.45 ? 0 : 1 + (int) ((r - 0.45) / 0.55 * (p.length - 1));
        return p[Math.min(idx, p.length - 1)].defaultBlockState();
    }
}
