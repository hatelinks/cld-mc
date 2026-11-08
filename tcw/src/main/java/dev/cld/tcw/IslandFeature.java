package dev.cld.tcw;

import com.mojang.serialization.Codec;
import dev.cld.tcw.gen.Islands;
import dev.cld.tcw.gen.Simplex;
import dev.cld.tcw.gen.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// Небесные острова (gen/Islands): ставит блоки своей части островов в каждом чанке.
// Шаг raw_generation, до деревьев: деревья и трава встают на остров сами по карте высот и биому острова.
public final class IslandFeature extends Feature<NoneFeatureConfiguration> {
    private static final Simplex ROCK = new Simplex(9191);
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState(), DIRT = Blocks.DIRT.defaultBlockState(),
            STONE = Blocks.STONE.defaultBlockState(), ANDESITE = Blocks.ANDESITE.defaultBlockState(), TUFF = Blocks.TUFF.defaultBlockState(),
            COAL = Blocks.COAL_ORE.defaultBlockState(), IRON = Blocks.IRON_ORE.defaultBlockState(), COPPER = Blocks.COPPER_ORE.defaultBlockState(),
            GOLD = Blocks.GOLD_ORE.defaultBlockState(), MOSS = Blocks.MOSS_BLOCK.defaultBlockState(), COARSE = Blocks.COARSE_DIRT.defaultBlockState();

    public IslandFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        Terrain t = Worlds.terrain(level);
        BlockPos o = ctx.origin();
        int x0 = o.getX(), z0 = o.getZ();
        Islands.Island[] all = t.islands.near(x0 + 8, z0 + 8);
        if (all.length == 0) return false;
        long seed = t.seed();
        int[] c = new int[2];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean any = false;
        for (Islands.Island is : all) {
            int reach = is.reach();
            if (is.x + reach < x0 || is.x - reach > x0 + 15 || is.z + reach < z0 || is.z - reach > z0 + 15) continue;
            for (int j = 0; j < 16; j++)
                for (int i = 0; i < 16; i++) {
                    int x = x0 + i, z = z0 + j;
                    if (!t.islands.column(is, x, z, c)) continue;
                    int bottom = c[0], top = c[1];
                    double rock = ROCK.noise(x / 14.0, z / 14.0);
                    for (int y = bottom; y <= top; y++) {
                        p.set(x, y, z);
                        if (!level.getBlockState(p).isAir()) continue;
                        level.setBlock(p, block(seed, x, y, z, bottom, top, rock), 2);
                    }
                    any = true;
                }
        }
        return any;
    }

    private static BlockState block(long seed, int x, int y, int z, int bottom, int top, double rock) {
        long h = seed ^ x * 341873128712L ^ y * 132897987541L ^ z * 7919L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        int r = (int) ((h >>> 1) % 1000);
        if (y == top) return rock > 0.55 && r < 400 ? COARSE : GRASS;
        if (y >= top - 3) return DIRT;
        // снизу местами мох, руды внутри: награда за первый полёт
        if (y == bottom && r < 250) return MOSS;
        if (r < 14) return COAL;
        if (r < 23) return IRON;
        if (r < 30) return COPPER;
        if (r < 33 && y < top - 10) return GOLD;
        if (rock > 0.4) return ANDESITE;
        if (rock < -0.6) return TUFF;
        return STONE;
    }
}
