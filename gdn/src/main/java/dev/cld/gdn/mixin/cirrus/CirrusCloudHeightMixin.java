package dev.cld.gdn.mixin.cirrus;

import dev.cld.gdn.WorldType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Облака Cirrus в мирах «Горы до небес» поднимаются пропорционально высоте мира
 * (ванильные 192 при верхе 320 → 513 при верхе 960, 256 при верхе 448). В стандартном мире не трогаем.
 */
@Pseudo
@Mixin(targets = "com.jvn.cirrus.client.CirrusCloudDimensions", remap = false)
public class CirrusCloudHeightMixin {
    @Inject(method = "cloudHeight", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void gdn$height(ClientLevel level, CallbackInfoReturnable<Float> cir) {
        if (level == null || !Float.isFinite(cir.getReturnValueF())) return;
        boolean gdn = level.dimensionTypeRegistration().unwrapKey().map(ResourceKey::location).map(WorldType::isGdn).orElse(false);
        if (!gdn) return;
        int top = level.getMaxBuildHeight();
        cir.setReturnValue(63f + 129f * (top - 63) / 257f);
    }
}
