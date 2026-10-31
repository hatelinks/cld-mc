package dev.cld.panel.mixin;

import dev.cld.panel.Cheats;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class PlayerMixin {
    // Phase: сразу после noPhysics = isSpectator() ставим своё, так игрока не выталкивает из стен весь тик.
    // На встроенном сервере значение держится и при разборе пакетов, иначе сервер вернёт игрока назад
    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z", opcode = org.objectweb.asm.Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void cld$tick(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!Cheats.phased(self)) return;
        self.noPhysics = true;
        // без столкновений игра не снимает "на земле", и LocalPlayer.aiStep выключает полёт: держим полёт сами
        if (self.level().isClientSide) {
            self.setOnGround(false);
            self.getAbilities().flying = true;
        }
    }
}
