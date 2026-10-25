package dev.cld.tcw.mixin.client;

import dev.cld.tcw.client.ClientSetup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Новый мир: тип TelipailoCraft выбран по умолчанию. */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
    @Inject(method = "openFresh", at = @At("TAIL"))
    private static void tcw$defaultType(Minecraft mc, Screen last, CallbackInfo ci) {
        if (mc.screen instanceof CreateWorldScreen s) ClientSetup.selectTcw(s.getUiState());
    }
}
