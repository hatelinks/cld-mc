package dev.cld.panel.mixin;

import dev.cld.panel.Cheats;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// NoFall: в пакетах движения при падении пишем "на земле", сервер не копит высоту падения
@Mixin(ServerboundMovePlayerPacket.class)
public class MovePacketMixin {
    @Shadow
    @Final
    @Mutable
    protected boolean onGround;

    @Inject(method = "<init>(DDDFFZZZ)V", at = @At("RETURN"))
    private void cld$init(CallbackInfo ci) {
        if (Cheats.noFallNow()) onGround = true;
    }
}
