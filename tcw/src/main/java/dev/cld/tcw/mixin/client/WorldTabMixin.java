package dev.cld.tcw.mixin.client;

import dev.cld.tcw.client.ClientSetup;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Вкладка «Мир»: широкая кнопка «Карта мира» сразу под строкой «Тип мира / Настроить». */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
public abstract class WorldTabMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;I)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
    private LayoutElement tcw$mapButton(GridLayout.RowHelper rows, LayoutElement seed, int span) {
        rows.addChild(ClientSetup.mapButton(), 2);
        return rows.addChild(seed, span);
    }
}
