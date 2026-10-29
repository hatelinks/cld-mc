package dev.cld.perf.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Ограничитель кадров. В игре следующий кадр отсчитывается от момента, когда поток проснулся,
// а glfwWaitEventsTimeout на Mac просыпается с опозданием, и опоздание копится: при лимите 90 выходило 79 кадров.
// Здесь отсчёт идёт от расписания, поэтому в среднем ровно столько кадров, сколько задано.
@Mixin(RenderSystem.class)
public class RenderSystemMixin {
    @Unique
    private static double cld$next;

    @Inject(method = "limitDisplayFPS", at = @At("HEAD"), cancellable = true)
    private static void cld$limit(int fps, CallbackInfo ci) {
        ci.cancel();
        double frame = 1.0 / fps, now = GLFW.glfwGetTime();
        double target = cld$next + frame;
        // сильно отстали (тяжёлый кадр, пауза) или лимит сменился: начинаем расписание заново
        if (target < now - frame || target > now + frame) target = now + frame;
        while (now < target) {
            GLFW.glfwWaitEventsTimeout(target - now);
            now = GLFW.glfwGetTime();
        }
        cld$next = target;
    }
}
