package dev.cld.tcw;

import dev.cld.tcw.gen.Terrain;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;

// Структуры в мире End Sea: только там, где под ними целый остров.
// Наземная структура должна целиком стоять на острове, не ближе нескольких блоков к краю, и не уходить ниже его дна.
// Подземная (целиком ниже поверхности) переносится по высоте в толщу острова, если помещается, иначе не появляется.
public final class EndSeaStructures {
    private EndSeaStructures() {}

    static final int STEP = 8, PAD = 4;
    // радиус поиска ближайшей структуры в блоках (см. NearestStructureMixin)
    public static final int SEARCH = 8000;

    private static final Map<Structure, Boolean> OCEAN = Collections.synchronizedMap(new WeakHashMap<>());

    public static boolean oceanOnly(Structure s) {
        return OCEAN.computeIfAbsent(s, k -> {
            HolderSet<Biome> set = k.biomes();
            if (set.size() == 0) return false;
            for (Holder<Biome> h : set) if (!h.is(BiomeTags.IS_OCEAN)) return false;
            return true;
        });
    }
    static final double EDGE = 0.02;

    public static StructureStart check(Terrain t, StructureStart s) {
        BoundingBox box = s.getBoundingBox();
        int x0 = box.minX() - PAD, x1 = box.maxX() + PAD, z0 = box.minZ() - PAD, z1 = box.maxZ() + PAD;
        double lowTop = Double.MAX_VALUE, highBottom = -Double.MAX_VALUE;
        Terrain.Column c = new Terrain.Column();
        for (int x = x0; ; x = Math.min(x + STEP, x1)) {
            for (int z = z0; ; z = Math.min(z + STEP, z1)) {
                t.column(x, z, c);
                if (t.arch.edge(x, z, c.height) <= EDGE) return StructureStart.INVALID_START;
                lowTop = Math.min(lowTop, c.height);
                highBottom = Math.max(highBottom, t.isleBottom(c, x, z));
                if (z == z1) break;
            }
            if (x == x1) break;
        }
        int lo = (int) Math.ceil(highBottom) + 3;
        if (box.maxY() < lowTop - 2) {
            // подземная: двигаем вверх в толщу, если не помещается, её нет
            int hi = (int) Math.floor(lowTop) - 3;
            if (box.getYSpan() > hi - lo) return StructureStart.INVALID_START;
            if (box.minY() >= lo) return s;
            int dy = lo - box.minY();
            for (StructurePiece p : s.getPieces()) p.move(0, dy, 0);
            return new StructureStart(s.getStructure(), s.getChunkPos(), s.getReferences(), new PiecesContainer(s.getPieces()));
        }
        return box.minY() >= lo ? s : StructureStart.INVALID_START;
    }
}
