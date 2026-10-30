package dev.cld.deepcull.mixin.sodium;

import dev.cld.deepcull.DeepCull;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Sodium: спрятанная секция не проходит проверку видимости, поэтому её не ставят в очередь на сборку,
// не рисуют и не идут через неё дальше.
@Mixin(value = OcclusionCuller.class, remap = false)
public abstract class OcclusionCullerMixin {
    @Inject(method = "isSectionVisible", at = @At("HEAD"), cancellable = true)
    private static void deepcull$hide(RenderSection s, Viewport v, float dist, CallbackInfoReturnable<Boolean> cir) {
        if (DeepCull.hide(s.getChunkX(), s.getChunkY(), s.getChunkZ())) cir.setReturnValue(false);
    }
}
