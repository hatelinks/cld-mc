package dev.cld.panel.mixin;

import dev.cld.panel.Cheats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
    // Свободная камера: мышь крутит камеру, а не игрока
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void cld$turn(double yaw, double pitch, CallbackInfo ci) {
        if (Cheats.turnCamera((Entity) (Object) this, yaw, pitch)) ci.cancel();
    }

    // Phase: игрок двигается без столкновений
    @Inject(method = "move", at = @At("HEAD"))
    private void cld$move(MoverType type, Vec3 delta, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (Cheats.phased(self)) self.noPhysics = true;
    }

    // Phase: внутри блоков не задыхаемся
    @Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
    private void cld$inWall(CallbackInfoReturnable<Boolean> cir) {
        if (Cheats.phased((Entity) (Object) this)) cir.setReturnValue(false);
    }
}
