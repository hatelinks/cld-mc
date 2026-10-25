package dev.cld.menu.mixin;

import java.util.function.BiConsumer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.neoforge.internal.BrandingControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    /** Без строк версий в левом нижнем углу. */
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/internal/BrandingControl;forEachLine(ZZLjava/util/function/BiConsumer;)V"))
    private void cld$noBranding(boolean includeMC, boolean reverse, BiConsumer<Integer, String> lineConsumer) {}

    /** Значки уведомлений Realms рисовались у убранной кнопки Realms. */
    @Inject(method = "realmsNotificationsEnabled", at = @At("HEAD"), cancellable = true)
    private void cld$noRealms(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
