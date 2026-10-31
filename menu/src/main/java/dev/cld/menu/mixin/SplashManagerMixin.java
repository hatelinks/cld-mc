package dev.cld.menu.mixin;

import dev.cld.menu.Splashes;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.resources.SplashManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// жёлтая надпись у логотипа: иногда своя по-русски, праздничные ванильные не трогаем
@Mixin(SplashManager.class)
public class SplashManagerMixin {
    @Inject(method = "getSplash", at = @At("RETURN"), cancellable = true)
    private void cld$splash(CallbackInfoReturnable<SplashRenderer> cir) {
        SplashRenderer own = Splashes.pick(cir.getReturnValue());
        if (own != null) cir.setReturnValue(own);
    }
}
