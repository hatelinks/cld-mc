package dev.cld.menu.mixin;

import dev.cld.menu.Landscape;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PanoramaRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Вместо кубической панорамы рисуем живой пейзаж (во всех меню, где она была). */
@Mixin(PanoramaRenderer.class)
public class PanoramaRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void cld$landscape(GuiGraphics g, int width, int height, float fade, float partialTick, CallbackInfo ci) {
        Landscape.render(g, width, height, fade);
        ci.cancel();
    }
}
