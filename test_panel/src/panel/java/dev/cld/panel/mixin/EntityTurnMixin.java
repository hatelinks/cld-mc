package dev.cld.panel.mixin;

import dev.cld.panel.Cheats;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Свободная камера: мышь крутит камеру, а не игрока
@Mixin(Entity.class)
public class EntityTurnMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void cld$turn(double yaw, double pitch, CallbackInfo ci) {
        if (Cheats.turnCamera((Entity) (Object) this, yaw, pitch)) ci.cancel();
    }
}
