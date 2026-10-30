package dev.cld.tcw;

import dev.cld.tcw.mixin.RuleTestAccessors;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockStateMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;

// Руда или замена камня (wythers ставит их по 100 штук размером 64 на чанк) проверяет каждый блок своего облака.
// Если ни в одной секции вокруг облака нет блока, который она может заменить, ничего не поставится,
// и случайные числа после этого места тоже не тратятся, поэтому такую руду можно не считать.
public final class OreSkip {
    private static final Predicate<BlockState> NONE = s -> true;
    private static final Map<OreConfiguration, Predicate<BlockState>> CACHE = new ConcurrentHashMap<>();

    public static boolean nothingToReplace(WorldGenLevel level, OreConfiguration cfg,
                                           double x1, double x2, double z1, double z2, double y1, double y2) {
        Predicate<BlockState> p = CACHE.computeIfAbsent(cfg, OreSkip::of);
        if (p == NONE) return false;
        double r = cfg.size / 16.0 + 1.5;
        int minX = (int) Math.floor(Math.min(x1, x2) - r), maxX = (int) Math.floor(Math.max(x1, x2) + r);
        int minY = (int) Math.floor(Math.min(y1, y2) - r), maxY = (int) Math.floor(Math.max(y1, y2) + r);
        int minZ = (int) Math.floor(Math.min(z1, z2) - r), maxZ = (int) Math.floor(Math.max(z1, z2) + r);
        minY = Math.max(minY, level.getMinBuildHeight());
        maxY = Math.min(maxY, level.getMaxBuildHeight() - 1);
        if (minY > maxY) return true;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++)
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) return false;
                ChunkAccess c = level.getChunk(cx, cz);
                for (int sy = SectionPos.blockToSectionCoord(minY); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
                    LevelChunkSection s = c.getSection(c.getSectionIndexFromSectionY(sy));
                    if (!s.hasOnlyAir() && s.maybeHas(p)) return false;
                    if (s.hasOnlyAir() && p.test(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())) return false;
                }
            }
        return true;
    }

    private static Predicate<BlockState> of(OreConfiguration cfg) {
        try {
            List<Predicate<BlockState>> parts = new ArrayList<>();
            for (OreConfiguration.TargetBlockState t : cfg.targetStates) {
                RuleTest rt = t.target;
                if (rt.getClass() == TagMatchTest.class) {
                    var tag = ((RuleTestAccessors.Tag) rt).tcw$tag();
                    parts.add(s -> s.is(tag));
                } else if (rt.getClass() == BlockMatchTest.class) {
                    var b = ((RuleTestAccessors.Block) rt).tcw$block();
                    parts.add(s -> s.is(b));
                } else if (rt.getClass() == BlockStateMatchTest.class) {
                    var st = ((RuleTestAccessors.State) rt).tcw$state();
                    parts.add(s -> s == st);
                } else {
                    return NONE;
                }
            }
            if (parts.isEmpty()) return NONE;
            return s -> {
                for (Predicate<BlockState> q : parts) if (q.test(s)) return true;
                return false;
            };
        } catch (Throwable e) {
            return NONE;
        }
    }
}
