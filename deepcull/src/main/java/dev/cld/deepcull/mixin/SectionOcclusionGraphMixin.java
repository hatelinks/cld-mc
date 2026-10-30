package dev.cld.deepcull.mixin;

import dev.cld.deepcull.DeepCull;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Ванила (без Sodium, например на Android): спрятанный сосед для обхода видимости как будто за краем мира.
@Mixin(SectionOcclusionGraph.class)
public abstract class SectionOcclusionGraphMixin {
    @Inject(method = "getRelativeFrom", at = @At("RETURN"), cancellable = true)
    private void deepcull$hide(BlockPos cam, SectionRenderDispatcher.RenderSection from, Direction dir,
                               CallbackInfoReturnable<SectionRenderDispatcher.RenderSection> cir) {
        SectionRenderDispatcher.RenderSection s = cir.getReturnValue();
        if (s == null) return;
        BlockPos o = s.getOrigin();
        if (DeepCull.hide(o.getX() >> 4, o.getY() >> 4, o.getZ() >> 4)) cir.setReturnValue(null);
    }
}
