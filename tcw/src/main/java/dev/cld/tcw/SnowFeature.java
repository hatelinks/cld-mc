package dev.cld.tcw;

import com.mojang.serialization.Codec;
import dev.cld.tcw.gen.Macro;
import dev.cld.tcw.gen.Simplex;
import dev.cld.tcw.gen.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Снег выше снеговой линии: лежит только на пологих местах, толще в ложбинах (сугробы), скалы голые.
 * В холодных горах на пологих высоких местах — ледники с трещинами.
 */
public final class SnowFeature extends Feature<NoneFeatureConfiguration> {
    private static final Simplex N1 = new Simplex(9001), N2 = new Simplex(9002);
    private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState(), PACKED = Blocks.PACKED_ICE.defaultBlockState(),
            BLUE = Blocks.BLUE_ICE.defaultBlockState(), AIR = Blocks.AIR.defaultBlockState();

    public SnowFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        Terrain t = Worlds.terrain(level);
        BlockPos o = ctx.origin();
        Macro.Sample ms = t.macro.sample(o.getX() + 8, o.getZ() + 8, new Macro.Sample());
        double snowLine = t.snowLine(o.getX() + 8, o.getZ() + 8, ms.temp);
        Surface ground = new Surface(level, o, Heightmap.Types.OCEAN_FLOOR_WG);
        boolean cold = ms.temp < -0.2 && ms.mountain > 0.2;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int j = 0; j < 16; j++)
            for (int i = 0; i < 16; i++) {
                int x = o.getX() + i, z = o.getZ() + j;
                int g = ground.at(i, j) - 1;
                double a = (g - snowLine) / 40.0 + 0.4 * N1.noise(x / 30.0, z / 30.0);
                if (a < -0.6) continue;
                int slope = ground.slope(i, j);
                if (slope >= 3) continue;
                p.set(x, g, z);
                BlockState gs = level.getBlockState(p);
                if (!gs.getFluidState().isEmpty() || !RockFeature.natural(gs)) continue;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                int hollow = Math.max(0, ground.maxNeighbor(i, j) - g);
                if (cold && slope <= 1 && g > snowLine + 20 && a > 0.6) {
                    // ледник
                    int thick = 4 + (int) Math.min(12, (g - snowLine - 20) / 8.0);
                    double cr = N2.noise(x / 26.0, z / 26.0);
                    boolean crevasse = Math.abs(cr) < 0.035 && N1.noise(x / 70.0 + 30, z / 70.0) > -0.1;
                    for (int k = 0; k < thick; k++) {
                        p.set(x, g - k, z);
                        BlockState s = level.getBlockState(p);
                        if (s.isAir()) continue; // ледяная пещера под ледником
                        if (!RockFeature.natural(s)) break;
                        BlockState ice = k == 0 ? SNOW_BLOCK : k > thick - 4 ? BLUE : PACKED;
                        level.setBlock(p, crevasse && k < thick - 2 ? AIR : ice, 2);
                    }
                    continue;
                }
                if (a > 0) {
                    int depth = Math.min(3, 1 + (int) (a * 1.5));
                    for (int k = 0; k < depth; k++) {
                        p.set(x, g - k, z);
                        if (RockFeature.natural(level.getBlockState(p))) level.setBlock(p, SNOW_BLOCK, 2);
                    }
                }
                if (top == g + 1 && slope <= 2) {
                    int layers = 1 + Math.min(5, hollow / 2 + (a > 0 ? 1 : 0) + (N2.noise(x / 12.0, z / 12.0) > 0.3 ? 1 : 0));
                    if (a <= 0 && N1.noise(x / 9.0, z / 9.0) < 0.1) continue;
                    p.set(x, g + 1, z);
                    if (level.getBlockState(p).isAir())
                        level.setBlock(p, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers), 2);
                }
            }
        iceCaves(level, t, o, ground);
        return true;
    }

    private static final net.minecraft.core.Direction[] DIRS = net.minecraft.core.Direction.values();

    /** Под ледниками стены и пол пещер становятся льдом. */
    private static void iceCaves(WorldGenLevel level, Terrain t, BlockPos o, Surface ground) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(), q = new BlockPos.MutableBlockPos();
        for (int j = 0; j < 16; j++)
            for (int i = 0; i < 16; i++) {
                int x = o.getX() + i, z = o.getZ() + j;
                if (!t.caves.glacial(Worlds.column(t, x, z), x, z)) continue;
                int g = ground.at(i, j);
                for (int y = g; y > g - 32; y--) {
                    p.set(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || !RockFeature.natural(s) || s.is(Blocks.SNOW_BLOCK)) continue;
                    boolean open = false;
                    for (net.minecraft.core.Direction d : DIRS) {
                        q.setWithOffset(p, d);
                        if (level.getBlockState(q).isAir() && q.getY() < g - 1) { open = true; break; }
                    }
                    if (!open) continue;
                    long h = (x * 341873128712L) ^ (y * 132897987541L) ^ (z * 7919L);
                    level.setBlock(p, (h >>> 7 & 3) == 0 ? BLUE : PACKED, 2);
                }
            }
    }
}
