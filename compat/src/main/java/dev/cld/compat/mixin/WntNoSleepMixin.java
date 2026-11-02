package dev.cld.compat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// War 'n Taxes 5.0.9: при старте мира OfficerColonyVisitTracker через server.execute запускает
// Thread.sleep(5000) прямо в потоке сервера (execute из того же потока выполняется сразу).
// Сервер стоит 5 секунд, а сон ничего не дает: пока он идет, колонии все равно не грузятся.
// Убираем только сон, перенос колоний идет как раньше и в том же месте.
@Mixin(targets = "net.machiavelli.minecolonytax.event.OfficerColonyVisitTracker", remap = false)
public abstract class WntNoSleepMixin {
    @Redirect(method = "lambda$onServerStarting$0", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;sleep(J)V"), remap = false)
    private static void cld$noSleep(long ms) {
    }
}
