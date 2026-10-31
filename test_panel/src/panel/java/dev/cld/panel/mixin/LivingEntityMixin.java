package dev.cld.panel.mixin;

import dev.cld.panel.Cheats;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    // Spider: любая стена как лестница
    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void cld$climb(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (Cheats.spider() && self == Minecraft.getInstance().player && self.horizontalCollision) cir.setReturnValue(true);
    }
}
