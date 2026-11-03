package dev.cld.tcw;

import dev.cld.tcw.gen.EndLands;
import java.util.List;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

// Структуры нашего Края: первая часть (основание города Края и т.п.) должна целиком стоять на острове,
// не у самого края и не над пустотой. Остальное (башни, мосты, корабль) висит в воздухе, как в ванили.
// Ванильный центр с драконом не трогаем.
public final class EndStructures {
    private EndStructures() {}

    static final int STEP = 4;
    static final double EDGE = 0.02;

    public static boolean ours(ChunkGenerator gen) {
        return gen instanceof NoiseBasedChunkGenerator g && g.generatorSettings().is(EndFeature.SETTINGS);
    }

    public static boolean check(EndLands e, StructureStart s) {
        List<StructurePiece> pieces = s.getPieces();
        if (pieces.isEmpty()) return true;
        BoundingBox box = pieces.get(0).getBoundingBox();
        if (EndLands.vanilla(box.getCenter().getX(), box.getCenter().getZ())) return true;
        for (int x = box.minX(); ; x = Math.min(x + STEP, box.maxX())) {
            for (int z = box.minZ(); ; z = Math.min(z + STEP, box.maxZ())) {
                EndLands.Col c = e.at(x, z);
                // под основанием остров не тоньше пары блоков, и верх острова не сильно ниже основания
                if (c.e <= EDGE || c.bottom > box.minY() - 2 || c.top < box.minY() - 4) return false;
                if (z == box.maxZ()) break;
            }
            if (x == box.maxX()) break;
        }
        return true;
    }
}
