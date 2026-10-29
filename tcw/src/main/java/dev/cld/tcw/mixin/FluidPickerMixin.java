package dev.cld.tcw.mixin;

import dev.cld.tcw.DeepFluidPicker;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ванильный «лавовый уровень» пещер стоит на Y -54 жёстко. В мирах глубже -64 переносим его
 * на 10 блоков над дном мира, иначе все пещеры ниже -54 будут залиты лавой. Ванильные миры не меняются.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public class FluidPickerMixin {
    @Inject(method = "createFluidPicker", at = @At("HEAD"), cancellable = true)
    private static void tcw$deepLava(NoiseGeneratorSettings settings, CallbackInfoReturnable<Aquifer.FluidPicker> cir) {
        int minY = settings.noiseSettings().minY();
        if (minY >= -64) return;
        cir.setReturnValue(new DeepFluidPicker(minY + 10, settings.seaLevel(), settings.defaultFluid()));
    }
}
