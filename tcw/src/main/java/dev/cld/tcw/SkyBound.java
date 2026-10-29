package dev.cld.tcw;

import dev.cld.tcw.gen.Rocks;
import dev.cld.tcw.gen.Terrain;
import dev.cld.tcw.mixin.NoiseChunkAccessor;
import net.minecraft.world.level.levelgen.NoiseChunk;

// Сколько ячеек шума снизу надо заполнять в чанке мира tcw. Выше этой высоты гарантированно воздух:
// плотность рельефа отрицательна во всех углах ячеек и в каждом столбце (поверхность и пики), нет камня
// (арки, карнизы), подсыпки под структуры и воды водоносных слоев. Ванильный doFill всё равно считает
// каждый блок до верха мира (640), а это больше половины работы.
public final class SkyBound {
    private SkyBound() {}

    public static int cells(Terrain t, NoiseChunk nc, int x0, int z0, int minY, int cellH, int sea, int cellCountY) {
        Rocks.Columns cols = (x, z) -> Worlds.column(t, x, z);
        // y <= bound может быть не воздухом
        double bound = sea - 1;
        for (int dx = 0; dx <= 16; dx++)
            for (int dz = 0; dz <= 16; dz++) {
                int x = x0 + dx, z = z0 + dz;
                bound = Math.max(bound, Math.ceil(Worlds.column(t, x, z).height));
                if (dx < 16 && dz < 16) bound = Math.max(bound, Math.ceil(t.rocks.top(x, z, cols)));
            }
        if (((NoiseChunkAccessor) (Object) nc).tcw$beardifier() instanceof BeardRange b) bound = Math.max(bound, b.tcw$hi());
        // водоносные слои: воздух гарантирован там, где все центры кандидатов выше своей поверхности (см. AquiferMixin)
        long surf = Long.MIN_VALUE;
        for (int x = x0 - 16; x <= x0 + 24; x += 4)
            for (int z = z0 - 16; z <= z0 + 24; z += 4) surf = Math.max(surf, nc.preliminarySurfaceLevel(x, z));
        if (surf == Integer.MAX_VALUE) return cellCountY;
        // наименьший y, при котором (floorDiv(y + 1, 12) - 1) * 12 - 20 > surf
        long y = surf + 20 + 1;
        long g = Math.floorDiv(y + 11, 12); // наименьший кратный 12 центр не ниже y
        long yAq = (g + 1) * 12 - 1;        // floorDiv(yAq + 1, 12) - 1 == g
        bound = Math.max(bound, yAq - 1);
        long k = Math.floorDiv((long) bound - minY, cellH) + 1;
        return (int) Math.max(1, Math.min(cellCountY, k));
    }
}
