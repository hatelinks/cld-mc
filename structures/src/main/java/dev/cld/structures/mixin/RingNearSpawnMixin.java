package dev.cld.structures.mixin;

import dev.cld.structures.ring.RingCandidates;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Кольца крепостей. Ваниль на первом же чанке нового мира ждет, пока посчитаются все 128 крепостей
// (поиск биома вокруг каждой, в мире tcw это десятки секунд). Крепость может оказаться только в 7 чанках
// от своей начальной точки (поиск биома идет в 112 блоках), а сами точки считаются сразу.
// Поэтому чанк далеко от всех точек сразу получает "нет". Ответ тот же, что у ванили, только без ожидания.
@Mixin(ConcentricRingsStructurePlacement.class)
public abstract class RingNearSpawnMixin {
    @Inject(method = "isPlacementChunk", at = @At("HEAD"), cancellable = true)
    private void cld$farFromRings(ChunkGeneratorStructureState state, int x, int z, CallbackInfoReturnable<Boolean> cir) {
        ConcentricRingsStructurePlacement self = (ConcentricRingsStructurePlacement) (Object) this;
        if (self.count() <= 0 || !(state instanceof RingCandidates rc)) return;
        if (!RingCandidates.near(rc.cld$candidates(self), x, z, 0)) cir.setReturnValue(false);
    }
}
