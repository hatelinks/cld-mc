package dev.cld.tcw;

import dev.cld.tcw.gen.Islands;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** Сетка высот 18×18 вокруг чанка (с соседями) и крутизна склона. */
final class Surface {
    final int x0, z0;
    final int[] h = new int[18 * 18];

    Surface(WorldGenLevel level, BlockPos origin, Heightmap.Types type) {
        x0 = origin.getX();
        z0 = origin.getZ();
        Islands is = Worlds.terrain(level).islands;
        for (int j = 0; j < 18; j++)
            for (int i = 0; i < 18; i++)
                h[j * 18 + i] = height(level, is, type, x0 + i - 1, z0 + j - 1);
    }

    // высота по карте высот, но под небесным островом: земля под ним, а не верх острова
    static int height(WorldGenLevel level, Heightmap.Types type, int x, int z) {
        return height(level, Worlds.terrain(level).islands, type, x, z);
    }

    static int height(WorldGenLevel level, Islands is, Heightmap.Types type, int x, int z) {
        int h = level.getHeight(type, x, z);
        int b = is.bottom(x, z);
        if (b == Integer.MIN_VALUE || h <= b) return h;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = b - 1; y >= level.getMinBuildHeight(); y--)
            if (type.isOpaque().test(level.getBlockState(p.set(x, y, z)))) return y + 1;
        return level.getMinBuildHeight();
    }

    /** Высота первого свободного блока над поверхностью. */
    int at(int i, int j) { return h[(j + 1) * 18 + i + 1]; }

    /** Наибольший перепад до соседей (блоков на блок). */
    int slope(int i, int j) {
        int c = at(i, j);
        return Math.max(Math.max(Math.abs(c - at(i - 1, j)), Math.abs(c - at(i + 1, j))),
                Math.max(Math.abs(c - at(i, j - 1)), Math.abs(c - at(i, j + 1))));
    }

    int minNeighbor(int i, int j) {
        return Math.min(Math.min(at(i - 1, j), at(i + 1, j)), Math.min(at(i, j - 1), at(i, j + 1)));
    }

    int maxNeighbor(int i, int j) {
        return Math.max(Math.max(at(i - 1, j), at(i + 1, j)), Math.max(at(i, j - 1), at(i, j + 1)));
    }
}
