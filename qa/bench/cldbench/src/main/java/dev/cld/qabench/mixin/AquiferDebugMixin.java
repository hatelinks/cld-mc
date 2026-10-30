package dev.cld.qabench.mixin;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Отладка: где водоносный слой просит обновить жидкость (включается -Dqabench.aquiferLog=N)
@Mixin(Aquifer.NoiseBasedAquifer.class)
public class AquiferDebugMixin {
    @Shadow @Final private NoiseChunk noiseChunk;
    @Shadow private boolean shouldScheduleFluidUpdate;
    @Unique private static final int QA_N = Integer.getInteger("qabench.aquiferLog", 0);
    @Unique private static final AtomicInteger QA_C = new AtomicInteger();

    @Inject(method = "computeSubstance", at = @At("RETURN"))
    private void qa$log(DensityFunction.FunctionContext ctx, double substance, CallbackInfoReturnable<BlockState> cir) {
        if (QA_N == 0 || !shouldScheduleFluidUpdate || cir.getReturnValue() == null || cir.getReturnValue().getFluidState().isEmpty()) return;
        int c = QA_C.incrementAndGet();
        if (c > QA_N || c % 97 != 0) return;
        int x = ctx.blockX(), y = ctx.blockY(), z = ctx.blockZ();
        System.out.println("QAAQ " + x + " " + y + " " + z + " surf=" + noiseChunk.preliminarySurfaceLevel(x, z) + " sub=" + substance);
    }
}
