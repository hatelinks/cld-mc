package dev.cld.tcw;

import com.mojang.serialization.Codec;
import dev.cld.tcw.gen.EndLands;
import dev.cld.tcw.gen.Simplex;
import dev.cld.tcw.gen.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// Край TelipailoCraft: пояса и районы на наших островах (gen/EndLands).
// Низ острова из чернокамня с обсидианом, в хорусовых лесах густой хорус, на высоких вершинах пурпурные шпили.
// Работает только в Крае с настройками tcw:end и не трогает ванильный центр с драконом.
public final class EndFeature extends Feature<NoneFeatureConfiguration> {
    public static final ResourceKey<NoiseGeneratorSettings> SETTINGS =
            ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath(TcwMod.NS, "end"));
    private static final Simplex SPOT = new Simplex(7311);
    private static final BlockState END_STONE = Blocks.END_STONE.defaultBlockState(), BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState(),
            OBSIDIAN = Blocks.OBSIDIAN.defaultBlockState(), CRYING = Blocks.CRYING_OBSIDIAN.defaultBlockState(),
            PURPUR = Blocks.PURPUR_BLOCK.defaultBlockState(), PILLAR = Blocks.PURPUR_PILLAR.defaultBlockState(),
            ROD = Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.UP);
    // шпили только на вершинах не ниже этой высоты
    static final int SPIRE_MIN = 135;

    public EndFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    public static boolean ours(WorldGenLevel level) {
        return level.getLevel().getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator g && g.generatorSettings().is(SETTINGS);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos o = ctx.origin();
        int x0 = o.getX() & ~15, z0 = o.getZ() & ~15;
        if (EndLands.vanilla(x0 + 8, z0 + 8) || !ours(level)) return false;
        Terrain t = Worlds.terrain(level);
        EndLands e = t.end;
        RandomSource rnd = ctx.random();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean any = false;
        int bestX = 0, bestZ = 0;
        double best = -1;
        for (int j = 0; j < 16; j++)
            for (int i = 0; i < 16; i++) {
                int x = x0 + i, z = z0 + j;
                EndLands.Col c = e.at(x, z);
                if (!c.island()) continue;
                any = true;
                if (c.top > best && c.e > 0.05) { best = c.top; bestX = x; bestZ = z; }
                belt(level, e, c, x, z, p);
            }
        if (!any) return false;
        double f = e.forest(x0 + 8, z0 + 8);
        if (f > 0.1) chorus(level, rnd, x0, z0, f, p);
        if (best >= SPIRE_MIN && rnd.nextInt(3) == 0) spire(level, rnd, bestX, bestZ, p);
        return true;
    }

    // нижний тёмный пояс: чернокамень, у самого дна обсидиан, местами плачущий
    private static void belt(WorldGenLevel level, EndLands e, EndLands.Col c, int x, int z, BlockPos.MutableBlockPos p) {
        int bottom = (int) Math.ceil(c.bottom - 0.5), top = (int) Math.floor(c.top + 0.5);
        int belt = Math.min(top - 3, bottom + (int) e.darkBelt(x, z, c));
        double s = SPOT.noise(x / 9.0, z / 9.0);
        int obs = bottom + 1 + (int) (2 * s);
        for (int y = Math.max(level.getMinBuildHeight(), bottom - 1); y <= belt; y++) {
            p.set(x, y, z);
            if (level.getBlockState(p) != END_STONE) continue;
            BlockState b = y <= obs ? (s > 0.55 && ((x ^ z ^ y) & 7) == 0 ? CRYING : OBSIDIAN) : BLACKSTONE;
            level.setBlock(p, b, 2);
        }
    }

    // густой хорус в лесных районах: чем глубже в районе, тем больше растений
    private static void chorus(WorldGenLevel level, RandomSource rnd, int x0, int z0, double f, BlockPos.MutableBlockPos p) {
        int n = 2 + (int) (f * 14);
        for (int k = 0; k < n; k++) {
            int x = x0 + rnd.nextInt(16), z = z0 + rnd.nextInt(16);
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            p.set(x, y - 1, z);
            if (level.getBlockState(p) != END_STONE) continue;
            p.set(x, y, z);
            if (!level.isEmptyBlock(p)) continue;
            ChorusFlowerBlock.generatePlant(level, p.immutable(), rnd, 8);
        }
    }

    // пурпурный шпиль на вершине: опора в землю, сужается кверху, наверху стержень Края
    private static void spire(WorldGenLevel level, RandomSource rnd, int x, int z, BlockPos.MutableBlockPos p) {
        int y0 = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        p.set(x, y0 - 1, z);
        if (level.getBlockState(p) != END_STONE) return;
        int h = 9 + rnd.nextInt(12);
        if (y0 + h + 1 >= level.getMaxBuildHeight()) return;
        for (int dy = -3; dy < h; dy++) {
            double q = Math.max(0, dy) / (double) h;
            int r = q < 0.3 ? 2 : q < 0.7 ? 1 : 0;
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (r == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    p.set(x + dx, y0 + dy, z + dz);
                    if (dy < 0 && level.isEmptyBlock(p)) continue;
                    level.setBlock(p, dx == 0 && dz == 0 ? PILLAR : PURPUR, 2);
                }
        }
        p.set(x, y0 + h, z);
        level.setBlock(p, ROD, 2);
    }
}
