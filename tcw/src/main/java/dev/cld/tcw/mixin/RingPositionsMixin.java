package dev.cld.tcw.mixin;

import dev.cld.tcw.RingCache;
import dev.cld.tcw.TcwBiomeSource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Кольца крепостей в мирах tcw: берём из кэша в папке мира, а посчитанные сохраняем туда (см. RingCache).
@Mixin(ChunkGeneratorStructureState.class)
public class RingPositionsMixin {
    @Shadow @Final private BiomeSource biomeSource;
    @Shadow @Final private long concentricRingsSeed;

    @Unique
    private String tcw$key(Holder<StructureSet> set, ConcentricRingsStructurePlacement p) {
        if (!(biomeSource instanceof TcwBiomeSource b)) return null;
        String id = set.unwrapKey().map(k -> k.location().toString()).orElse(null);
        if (id == null) return null;
        String biomes = p.preferredBiomes().unwrapKey().map(k -> k.location().toString()).orElse("list") + p.preferredBiomes().size();
        return RingCache.key(concentricRingsSeed, id, p.distance(), p.spread(), p.count(), biomes, b.options());
    }

    @Inject(method = "generateRingPositions", at = @At("HEAD"), cancellable = true)
    private void tcw$cached(Holder<StructureSet> set, ConcentricRingsStructurePlacement p, CallbackInfoReturnable<CompletableFuture<List<ChunkPos>>> cir) {
        String key = tcw$key(set, p);
        if (key == null) return;
        List<ChunkPos> l = RingCache.get(key);
        if (l != null && l.size() == p.count()) cir.setReturnValue(CompletableFuture.completedFuture(l));
    }

    @Inject(method = "generateRingPositions", at = @At("RETURN"), cancellable = true)
    private void tcw$store(Holder<StructureSet> set, ConcentricRingsStructurePlacement p, CallbackInfoReturnable<CompletableFuture<List<ChunkPos>>> cir) {
        String key = tcw$key(set, p);
        CompletableFuture<List<ChunkPos>> f = cir.getReturnValue();
        if (key == null || f == null || f.isDone()) return;
        cir.setReturnValue(f.thenApply(l -> {
            RingCache.put(key, l);
            return l;
        }));
    }
}
