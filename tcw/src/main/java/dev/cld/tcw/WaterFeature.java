package dev.cld.tcw;

import com.mojang.serialization.Codec;
import dev.cld.tcw.gen.RiverWater;
import dev.cld.tcw.gen.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

// Реки и озёра выше уровня моря. Реки: глиняное дно, вода не выше перелива через берег (RiverWater).
public final class WaterFeature extends Feature<NoneFeatureConfiguration> {
    private static final BlockState WATER = Blocks.WATER.defaultBlockState(), AIR = Blocks.AIR.defaultBlockState(),
            CLAY = Blocks.CLAY.defaultBlockState(), DIRT = Blocks.DIRT.defaultBlockState();
    // поле вокруг чанка, по которому считается перелив
    private static final int M = 8, N = 16 + 2 * M;

    public WaterFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        Terrain t = Worlds.terrain(level);
        BlockPos o = ctx.origin();
        boolean any = rivers(level, t, o);
        double[] w = new double[2];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int[] lakes = new int[256];
        if (!t.lakeLevels(o.getX(), o.getZ(), lakes)) return any;
        for (int j = 0; j < 16; j++)
            for (int i = 0; i < 16; i++) {
                int x = o.getX() + i, z = o.getZ() + j;
                if (lakes[j * 16 + i] == Integer.MIN_VALUE || t.water(x, z, w) != Terrain.WATER_LAKE) continue;
                int wl = Math.min((int) Math.floor(w[0]), lakes[j * 16 + i]);
                if (wl < 62) continue;
                int top = Surface.height(level, t.islands, Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
                for (int y = top + 1; y <= wl; y++) {
                    p.set(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || s.canBeReplaced()) level.setBlock(p, WATER, 2);
                }
                if (top < wl) any = true;
            }
        return any;
    }

    private boolean rivers(WorldGenLevel level, Terrain t, BlockPos o) {
        int x0 = o.getX() - M, z0 = o.getZ() - M;
        double[] w = new double[2];
        int[] r = new int[2];
        int[] lvl = new int[N * N], bed = new int[N * N], top = new int[N * N], water = new int[N * N], bank = new int[N * N];
        boolean inside = false;
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                int id = j * N + i;
                if (t.riverBlocks(x0 + i, z0 + j, w, r)) {
                    lvl[id] = r[0];
                    bed[id] = r[1];
                    if (i >= M && j >= M && i < M + 16 && j < M + 16) inside = true;
                } else lvl[id] = bed[id] = RiverWater.NONE;
            }
        if (!inside) return false;
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++)
                top[j * N + i] = Surface.height(level, t.islands, Heightmap.Types.OCEAN_FLOOR_WG, x0 + i, z0 + j) - 1;
        RiverWater.solve(N, lvl, bed, top, water, bank);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean any = false;
        for (int j = M; j < M + 16; j++)
            for (int i = M; i < M + 16; i++) {
                int id = j * N + i, x = x0 + i, z = z0 + j;
                if (bank[id] != RiverWater.NONE) raiseBank(level, p, x, z, top[id], bank[id]);
                if (water[id] == RiverWater.NONE) continue;
                int wl = water[id], b = bed[id];
                for (int y = Math.max(top[id], wl); y > b; y--) {
                    p.set(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (y <= wl) {
                        if (!s.getFluidState().isSource() || !s.is(Blocks.WATER)) level.setBlock(p, WATER, 2);
                    } else if (!s.isAir() && !s.is(BlockTags.LOGS) && !s.is(BlockTags.LEAVES)) level.setBlock(p, AIR, 2);
                }
                // общее дно из глины, мелкую яму под ним тоже засыпаем глиной
                level.setBlock(p.set(x, b, z), CLAY, 2);
                for (int y = b - 1; y > b - 4; y--) {
                    BlockState s = level.getBlockState(p.set(x, y, z));
                    if (!s.isAir() && s.getFluidState().isEmpty()) break;
                    level.setBlock(p, CLAY, 2);
                }
                level.scheduleTick(p.set(x, wl, z), Fluids.WATER, 0);
                any = true;
            }
        return any;
    }

    // досыпает берег до уровня воды рядом, сверху тот же блок, что был
    private static void raiseBank(WorldGenLevel level, BlockPos.MutableBlockPos p, int x, int z, int top, int to) {
        BlockState surf = level.getBlockState(p.set(x, top, z));
        if (surf.isAir() || !surf.getFluidState().isEmpty() || surf.is(BlockTags.LEAVES)) surf = DIRT;
        BlockState fill = surf.is(Blocks.GRASS_BLOCK) || surf.is(Blocks.PODZOL) || surf.is(Blocks.MYCELIUM) ? DIRT : surf;
        if (fill != surf) level.setBlock(p, fill, 2);
        for (int y = top + 1; y <= to; y++) {
            p.set(x, y, z);
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && !s.canBeReplaced()) continue;
            level.setBlock(p, y == to ? surf : fill, 2);
        }
    }
}
