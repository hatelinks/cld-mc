package dev.cld.panel.mixin;

import dev.cld.panel.Cheats;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Яркость: вместо гаммы из настроек подставляем 16, темнота пропадает
@Mixin(LightTexture.class)
public class LightTextureMixin {
    // в updateLightTexture два вызова OptionInstance.get: 0 это эффект темноты, 1 это гамма
    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 1), require = 0)
    private Object cld$gamma(OptionInstance<?> option) {
        return Cheats.bright() ? (Object) Double.valueOf(16.0) : option.get();
    }
}
