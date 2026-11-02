package dev.cld.structures.ring;

import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;

// Точки колец до поиска биома: x0, z0, x1, z1, ... в чанках
public interface RingCandidates {
    int[] cld$candidates(ConcentricRingsStructurePlacement p);

    // Крепость встает не дальше 7 чанков от своей точки (поиск биома в 112 блоках), берем 8 с запасом
    int REACH = 8;

    static boolean near(int[] c, int x, int z, int r) {
        int d = r + REACH;
        for (int i = 0; i + 1 < c.length; i += 2) {
            if (Math.abs(x - c[i]) <= d && Math.abs(z - c[i + 1]) <= d) return true;
        }
        return false;
    }
}
