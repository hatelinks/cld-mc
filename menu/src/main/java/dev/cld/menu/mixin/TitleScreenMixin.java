package dev.cld.menu.mixin;

import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Shadow private RealmsNotificationsScreen realmsNotificationsScreen;

    // без строк версий в левом нижнем углу
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/internal/BrandingControl;forEachLine(ZZLjava/util/function/BiConsumer;)V"))
    private void cld$noBranding(boolean includeMC, boolean reverse, BiConsumer<Integer, String> lineConsumer) {}

    // значки уведомлений Realms рисовались у убранной кнопки; обнуляем экран после init, все обращения к нему проверяют null
    @Inject(method = "init", at = @At("TAIL"))
    private void cld$noRealms(CallbackInfo ci) {
        realmsNotificationsScreen = null;
    }
}
