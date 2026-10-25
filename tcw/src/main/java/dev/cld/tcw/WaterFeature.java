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
        double[] w = new double[2];
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
                    for (int y = Math.max(top, wl); y > bottom; y--) {
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
