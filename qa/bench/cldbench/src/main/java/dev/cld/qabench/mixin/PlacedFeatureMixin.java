package dev.cld.qabench.mixin;

import dev.cld.qabench.FeatureTimes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlacedFeature.class)
public class PlacedFeatureMixin {
    @Unique private static final Map<PlacedFeature, String> QA_NAMES = new ConcurrentHashMap<>();
    @Unique private static final ThreadLocal<long[]> QA_T0 = ThreadLocal.withInitial(() -> new long[1]);

    @Inject(method = "placeWithBiomeCheck", at = @At("HEAD"))
    private void qa$start(WorldGenLevel level, ChunkGenerator gen, RandomSource rnd, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (FeatureTimes.on) QA_T0.get()[0] = System.nanoTime();
    }

    @Inject(method = "placeWithBiomeCheck", at = @At("RETURN"))
    private void qa$end(WorldGenLevel level, ChunkGenerator gen, RandomSource rnd, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!FeatureTimes.on) return;
        long ns = System.nanoTime() - QA_T0.get()[0];
        PlacedFeature self = (PlacedFeature) (Object) this;
        String id = QA_NAMES.computeIfAbsent(self, f -> level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE)
                .getResourceKey(f).map(k -> k.location().toString()).orElse("?" + f.feature().value().feature()));
        FeatureTimes.add(id, ns);
    }
}
