package dev.cld.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ScreenEvent;

// Ползунок Звуки меню в конце настроек звука
final class SoundSlider {
    private SoundSlider() {}

    static OptionInstance<Double> option() {
        return new OptionInstance<>("cld_menu.options.sounds", OptionInstance.cachedConstantTooltip(Component.translatable("cld_menu.options.sounds.tooltip")),
                (caption, v) -> v == 0 ? Component.translatable("options.generic_value", caption, Component.translatable("options.off"))
                        : Component.translatable("options.percent_value", caption, (int) Math.round(v * 100)),
                OptionInstance.UnitDouble.INSTANCE, (double) Landscape.config().sounds, v -> {
                    Landscape.config().sounds = v.floatValue();
                    Landscape.config().save(FMLPaths.CONFIGDIR.get().resolve("cld_menu.properties"));
                    Minecraft.getInstance().getSoundManager().updateSourceVolume(SoundSource.MASTER, Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER));
                });
    }

    static void onInit(ScreenEvent.Init.Post e) {
        if (!(e.getScreen() instanceof SoundOptionsScreen)) return;
        for (GuiEventListener l : e.getListenersList()) {
            if (l instanceof OptionsList list) {
                list.addBig(option());
                return;
            }
        }
    }
}
