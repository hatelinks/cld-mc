package dev.cld.menu.mixin;

import dev.cld.menu.MenuSounds;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// в грозу музыка меню тише
@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void cld$musicDuck(SoundInstance s, CallbackInfoReturnable<Float> cir) {
        if (s.getSource() == SoundSource.MUSIC && MenuSounds.musicFactor() < 1f) cir.setReturnValue(cir.getReturnValue() * MenuSounds.musicFactor());
    }
}
