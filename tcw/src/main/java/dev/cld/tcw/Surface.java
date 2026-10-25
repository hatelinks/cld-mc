package dev.cld.tcw;

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
        for (int j = 0; j < 18; j++)
            for (int i = 0; i < 18; i++)
                h[j * 18 + i] = level.getHeight(type, x0 + i - 1, z0 + j - 1);
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
