package dev.cld.menu;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = "cld_menu", dist = Dist.CLIENT)
public final class MenuMod {
    public MenuMod(IEventBus bus) {
        bus.addListener(RegisterClientReloadListenersEvent.class,
                e -> e.registerReloadListener((ResourceManagerReloadListener) rm -> Landscape.texturesChanged()));
        NeoForge.EVENT_BUS.addListener(TitleButtons::onInit);
        NeoForge.EVENT_BUS.addListener(MenuMod::onKey);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> MenuSounds.tick());
        NeoForge.EVENT_BUS.addListener(SoundSlider::onInit);
    }

    // в главном меню F9 переключает погоду, чтобы посмотреть все виды
    private static void onKey(ScreenEvent.KeyPressed.Pre e) {
        if (e.getScreen() instanceof TitleScreen && e.getKeyCode() == GLFW.GLFW_KEY_F9) {
            Landscape.cycle(Screen.hasShiftDown(), Screen.hasControlDown());
            e.setCanceled(true);
        }
    }
}
