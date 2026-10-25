package dev.cld.tcw.mixin.cirrus;

import dev.cld.tcw.Worlds;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Облака Cirrus в мире TelipailoCraft поднимаются пропорционально высоте мира
 * (ванильные 192 при верхе 320 → 513 при верхе 960, 256 при верхе 448). В стандартном мире не трогаем.
 */
@Pseudo
@Mixin(targets = "com.jvn.cirrus.client.CirrusCloudDimensions", remap = false)
public class CirrusCloudHeightMixin {
    @Inject(method = "cloudHeight", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void tcw$height(ClientLevel level, CallbackInfoReturnable<Float> cir) {
        if (level == null || !Float.isFinite(cir.getReturnValueF())) return;
        boolean tcw = level.dimensionTypeRegistration().unwrapKey().map(ResourceKey::location).map(Worlds::isTcw).orElse(false);
        if (!tcw) return;
        int top = level.getMaxBuildHeight();
        cir.setReturnValue(63f + 129f * (top - 63) / 257f);
    }
}
