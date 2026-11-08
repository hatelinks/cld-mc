package dev.cld.structures.mixin;

import dev.cld.structures.ring.RingCandidates;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

// Повторяет начало ванильного generateRingPositions: те же вызовы случайных чисел, но без поиска биома.
// Получаются точки, вокруг которых ваниль потом ищет биом в радиусе 112 блоков.
@Mixin(ChunkGeneratorStructureState.class)
public abstract class RingCandidatesMixin implements RingCandidates {
    @Shadow @Final private long concentricRingsSeed;

    @Unique private final Map<ConcentricRingsStructurePlacement, int[]> cld$points = new ConcurrentHashMap<>();

    @Override
    public int[] cld$candidates(ConcentricRingsStructurePlacement p) {
        return cld$points.computeIfAbsent(p, this::cld$compute);
    }

    @Unique
    private int[] cld$compute(ConcentricRingsStructurePlacement p) {
        int dist = p.distance();
        int count = p.count();
        int spread = p.spread();
        int[] out = new int[Math.max(count, 0) * 2];
        RandomSource random = RandomSource.create();
        random.setSeed(concentricRingsSeed);
        double angle = random.nextDouble() * Math.PI * 2.0;
        int inRing = 0;
        int ring = 0;
        for (int i = 0; i < count; i++) {
            double r = (double) (4 * dist + dist * ring * 6) + (random.nextDouble() - 0.5) * (double) dist * 2.5;
            out[i * 2] = (int) Math.round(Math.cos(angle) * r);
            out[i * 2 + 1] = (int) Math.round(Math.sin(angle) * r);
            // ваниль здесь берет fork для поиска биома, он тоже тратит случайные числа
            random.fork();
            angle += (Math.PI * 2) / (double) spread;
            if (++inRing == spread) {
                ring++;
                inRing = 0;
                spread += 2 * spread / (ring + 1);
                spread = Math.min(spread, count - i);
                angle += random.nextDouble() * Math.PI * 2.0;
            }
        }
        return out;
    }
}
