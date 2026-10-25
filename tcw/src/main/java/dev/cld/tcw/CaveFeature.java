package dev.cld.tcw;

import com.mojang.serialization.Codec;
import dev.cld.tcw.gen.Caves;
import dev.cld.tcw.gen.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Озёра в гигантских залах (глубоко — лава), вода подземных рек и сенотов. */
public final class CaveFeature extends Feature<NoneFeatureConfiguration> {
    private static final BlockState WATER = Blocks.WATER.defaultBlockState(), LAVA = Blocks.LAVA.defaultBlockState();

    public CaveFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        Terrain t = Worlds.terrain(level);
        BlockPos o = ctx.origin();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int minY = level.getMinBuildHeight();
        boolean any = false;
        for (int j = 0; j < 16; j++)
            for (int i = 0; i < 16; i++) {
                int x = o.getX() + i, z = o.getZ() + j;
                Terrain.Column c = Worlds.column(t, x, z);
                int top = t.caves.topFluidLevel(x, z, c);
                if (top < minY) continue;
                for (int y = top; y > Math.max(minY, top - 64); y--) {
                    p.set(x, y, z);
                    if (!level.getBlockState(p).isAir()) continue;
                    int f = t.caves.fluid(x, y, z, c);
                    if (f == Caves.NONE) continue;
                    level.setBlock(p, f == Caves.LAVA ? LAVA : WATER, 2);
                    any = true;
                }
            }
        return any;
    }
}
