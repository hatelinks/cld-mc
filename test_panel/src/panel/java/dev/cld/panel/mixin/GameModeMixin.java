package dev.cld.panel.mixin;

import dev.cld.panel.Cheats;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// FastBreak: без паузы между блоками, а пакет "блок сломан" уходит с 80% прогресса.
// Сервер засчитывает ломание с 70% (ServerPlayerGameMode), поэтому блок ломается по-настоящему.
@Mixin(MultiPlayerGameMode.class)
public class GameModeMixin {
    @Shadow
    private float destroyProgress;
    @Shadow
    private int destroyDelay;

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"))
    private void cld$fast(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        if (!Cheats.fastBreak()) return;
        destroyDelay = 0;
        if (destroyProgress >= 0.8f && destroyProgress < 1f) destroyProgress = 1f;
    }
}
