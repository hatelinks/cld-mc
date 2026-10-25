package dev.cld.tcw;

import com.mojang.serialization.Codec;
import dev.cld.tcw.gen.Macro;
import dev.cld.tcw.gen.Simplex;
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

/** Голые скалы с каменными слоями на крутых склонах и осыпи под ними (до растительности). */
public final class RockFeature extends Feature<NoneFeatureConfiguration> {
    private static final Simplex WOBBLE = new Simplex(7171);

    public RockFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL)
                || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.SANDSTONE)
                || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.CLAY) || s.is(Blocks.MUD) || s.is(Blocks.PACKED_ICE);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        Terrain t = Worlds.terrain(level);
        BlockPos o = ctx.origin();
        Macro.Sample ms = t.macro.sample(o.getX() + 8, o.getZ() + 8, new Macro.Sample());
        double snow = t.snowLine(o.getX() + 8, o.getZ() + 8, ms.temp);
        Surface sf = new Surface(level, o, Heightmap.Types.OCEAN_FLOOR_WG);
        long seed = t.seed();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int j = 0; j < 16; j++)
            for (int i = 0; i < 16; i++) {
                int x = o.getX() + i, z = o.getZ() + j, top = sf.at(i, j) - 1;
                if (top < 62) continue;
                int slope = sf.slope(i, j);
                boolean high = top > snow - 60;
                if (slope >= 3 || (slope >= 2 && high)) {
                    int style = Palette.pick(ms.style, seed, x, z);
                    double wob = 6 * WOBBLE.noise(x / 40.0, z / 40.0);
                    int bottom = Math.max(top - 40, Math.min(top - 4, sf.minNeighbor(i, j) - 2));
                    for (int y = top; y >= bottom; y--) {
                        p.set(x, y, z);
                        BlockState s = level.getBlockState(p);
                        if (!natural(s)) {
                            if (s.isAir()) break;
                            continue;
                        }
                        level.setBlock(p, Palette.rock(style, seed, x, y, z, wob), 2);
                    }
                } else if (slope >= 1 && sf.maxNeighbor(i, j) >= top + 4) {
                    double n = WOBBLE.noise(x / 9.0 + 50, z / 9.0);
                    if (n > 0.15) {
                        p.set(x, top, z);
                        BlockState s = level.getBlockState(p);
                        if (natural(s)) level.setBlock(p, n > 0.5 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState(), 2);
                    }
                }
            }
        return true;
    }
}
