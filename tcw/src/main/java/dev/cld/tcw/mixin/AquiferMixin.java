package dev.cld.tcw.mixin;

import dev.cld.tcw.DeepFluidPicker;
import net.minecraft.world.level.block.Blocks;
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

// Воздух высоко над поверхностью: ванильный водоносный слой для каждого такого блока ищет 12 ближайших
// центров и всё равно отдаёт воздух. Считаем это сразу, если все центры заведомо выше своей поверхности
// (тогда у них глобальная жидкость, а над морем это воздух). Результат тот же, что у ванили.
@Mixin(Aquifer.NoiseBasedAquifer.class)
public class AquiferMixin {
    @Shadow @Final private NoiseChunk noiseChunk;
    @Shadow @Final private Aquifer.FluidPicker globalFluidPicker;
    @Shadow private boolean shouldScheduleFluidUpdate;

    @Unique private static final BlockState TCW_AIR = Blocks.AIR.defaultBlockState();
    // максимум предварительной поверхности по клеткам сетки (gx, gz), 4 последних
    @Unique private final long[] tcw$keys = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
    @Unique private final long[] tcw$max = new long[4];

    @Inject(method = "computeSubstance", at = @At("HEAD"), cancellable = true)
    private void tcw$skyAir(DensityFunction.FunctionContext ctx, double substance, CallbackInfoReturnable<BlockState> cir) {
        if (substance > 0 || !(globalFluidPicker instanceof DeepFluidPicker p)) return;
        int y = ctx.blockY();
        if (y < p.sea) return;
        // самый низкий возможный центр среди кандидатов (сетка 12 по Y, сдвиг центра 0..8)
        int cy = (Math.floorDiv(y + 1, 12) - 1) * 12;
        if (cy < p.limit) return;
        int gx = Math.floorDiv(ctx.blockX() - 5, 16), gz = Math.floorDiv(ctx.blockZ() - 5, 16);
        // у центра своя жидкость, только если cy - 12 <= поверхность + 8
        if (cy - 20 <= tcw$maxSurface(gx, gz)) return;
        shouldScheduleFluidUpdate = false;
        cir.setReturnValue(TCW_AIR);
    }

    // центры кандидатов лежат в x от gx*16 до gx*16+25 (две клетки по 16, сдвиг 0..9), так же по z;
    // поверхность считается по четвертям блока, поэтому хватает 7x7 точек
    @Unique
    private long tcw$maxSurface(int gx, int gz) {
        long key = ((long) gx << 32) ^ (gz & 0xffffffffL);
        int slot = (gx & 1) | (gz & 1) << 1;
        if (tcw$keys[slot] == key) return tcw$max[slot];
        long m = Long.MIN_VALUE;
        for (int i = 0; i <= 24; i += 4)
            for (int j = 0; j <= 24; j += 4)
                m = Math.max(m, noiseChunk.preliminarySurfaceLevel(gx * 16 + i, gz * 16 + j));
        tcw$keys[slot] = key;
        tcw$max[slot] = m;
        return m;
    }
}
