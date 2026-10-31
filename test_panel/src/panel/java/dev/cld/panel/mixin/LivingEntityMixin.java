package dev.cld.panel.mixin;

import dev.cld.panel.Cheats;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    // Jesus: стоять на воде и лаве как страйдер, присед опускает в воду
    @Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true)
    private void cld$standOnFluid(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (Cheats.jesus() && self == Minecraft.getInstance().player && !self.isShiftKeyDown()) cir.setReturnValue(true);
    }

    // Spider: любая стена как лестница
    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void cld$climb(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (Cheats.spider() && self == Minecraft.getInstance().player && self.horizontalCollision) cir.setReturnValue(true);
    }
}
