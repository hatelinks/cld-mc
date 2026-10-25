package dev.cld.tcw;

import com.mojang.serialization.Codec;
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

/** Реки и озёра выше уровня моря: прорезает русло с точностью до блока и наливает воду. */
public final class WaterFeature extends Feature<NoneFeatureConfiguration> {
    private static final BlockState WATER = Blocks.WATER.defaultBlockState(), AIR = Blocks.AIR.defaultBlockState();

    public WaterFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        Terrain t = Worlds.terrain(level);
        BlockPos o = ctx.origin();
        double[] w = new double[2], w2 = new double[2];
        Surface sf = new Surface(level, o, Heightmap.Types.OCEAN_FLOOR_WG);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean any = false;
        for (int j = 0; j < 16; j++)
            for (int i = 0; i < 16; i++) {
                int x = o.getX() + i, z = o.getZ() + j;
                int kind = t.water(x, z, w);
                if (kind == Terrain.WATER_NONE) continue;
                int wl = (int) Math.floor(w[0]);
                if (wl < 62) continue;
                int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
                if (kind == Terrain.WATER_RIVER) {
                    int bottom = Math.min((int) Math.floor(w[1]), top);
                    if (top - wl > 8) continue; // под навесом или в столбе: не трогаем
                    // вода не выше соседей: иначе на крутых участках встают стены из блоков воды.
                    // Где уровень упирается в дно, кладём один слой, и вода сама стекает по уступам.
                    int lim = wl;
                    for (int d = 0; d < 4; d++) {
                        int dx = d == 0 ? 1 : d == 1 ? -1 : 0, dz = d == 2 ? 1 : d == 3 ? -1 : 0;
                        int nk = t.water(x + dx, z + dz, w2);
                        int nl = nk == Terrain.WATER_RIVER || nk == Terrain.WATER_LAKE ? (int) Math.floor(w2[0]) : sf.at(i + dx, j + dz) - 1;
                        lim = Math.min(lim, nl);
                    }
                    int carveTop = Math.max(top, wl);
                    wl = Math.max(bottom + 1, lim);
                    for (int y = carveTop; y > bottom; y--) {
                        p.set(x, y, z);
                        BlockState s = level.getBlockState(p);
                        if (s.isAir() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.getFluidState().isSource()) {
                            if (y <= wl && !s.getFluidState().isSource()) level.setBlock(p, WATER, 2);
                            continue;
                        }
                        level.setBlock(p, y <= wl ? WATER : AIR, 2);
                    }
                    p.set(x, bottom, z);
                    BlockState bed = level.getBlockState(p);
                    if (bed.is(BlockTags.DIRT) || bed.is(Blocks.GRASS_BLOCK)) level.setBlock(p, (x * 31 + z * 17 & 3) == 0 ? Blocks.SAND.defaultBlockState() : Blocks.GRAVEL.defaultBlockState(), 2);
                    for (int y = bottom + 1; y <= wl; y++) {
                        p.set(x, y, z);
                        if (!level.getBlockState(p).is(Blocks.WATER)) level.setBlock(p, WATER, 2);
                    }
                    p.set(x, wl, z);
                    level.scheduleTick(p, Fluids.WATER, 0);
                    any = true;
                } else if (top < wl) {
                    for (int y = top + 1; y <= wl; y++) {
                        p.set(x, y, z);
                        BlockState s = level.getBlockState(p);
                        if (s.isAir() || s.canBeReplaced()) level.setBlock(p, WATER, 2);
                    }
                    any = true;
                }
            }
        return any;
    }
}
