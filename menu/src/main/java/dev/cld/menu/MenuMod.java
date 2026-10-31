package dev.cld.menu;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = "cld_menu", dist = Dist.CLIENT)
public final class MenuMod {
    public MenuMod(IEventBus bus, ModContainer mod) {
        StartupTimer.constructed();
        bus.addListener(FMLClientSetupEvent.class, e -> StartupTimer.setup());
        bus.addListener(FMLLoadCompleteEvent.class, e -> StartupTimer.loaded());
        mod.registerExtensionPoint(IConfigScreenFactory.class, (c, parent) -> new OurModsScreen(parent));
        // последним, чтобы видеть кнопки других модов и не лечь на них
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, false, ScreenEvent.Init.Post.class, MenuMod::ourModsButton);
        bus.addListener(RegisterClientReloadListenersEvent.class,
                e -> e.registerReloadListener((ResourceManagerReloadListener) rm -> Landscape.texturesChanged()));
        NeoForge.EVENT_BUS.addListener(TitleButtons::onInit);
        NeoForge.EVENT_BUS.addListener(MenuMod::onKey);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> MenuSounds.tick());
        NeoForge.EVENT_BUS.addListener(SoundSlider::onInit);
    }

    // кнопка TC Моды в правом верхнем углу настроек и меню паузы
    private static void ourModsButton(ScreenEvent.Init.Post e) {
        Screen s = e.getScreen();
        if (!(s instanceof OptionsScreen) && !(s instanceof PauseScreen p && p.showsPauseMenu())) return;
        int w = 100, y = 6, x = s.width - w - 8;
        // если в углу уже есть чужая кнопка, сдвигаемся левее неё с зазором
        for (boolean moved = true; moved; ) {
            moved = false;
            for (var l : e.getListenersList())
                if (l instanceof net.minecraft.client.gui.components.AbstractWidget o && o.visible
                        && o.getX() < x + w + 2 && o.getX() + o.getWidth() + 2 > x && o.getY() < y + 20 && o.getY() + o.getHeight() > y) {
                    x = o.getX() - w - 4;
                    moved = true;
                }
        }
        e.addListener(Button.builder(Component.translatable("cld_menu.ourmods.title"), b -> OurModsScreen.open())
                .bounds(x, y, w, 20).build());
    }

    // в главном меню F9 переключает погоду, чтобы посмотреть все виды
    private static void onKey(ScreenEvent.KeyPressed.Pre e) {
        if (e.getScreen() instanceof TitleScreen && e.getKeyCode() == GLFW.GLFW_KEY_F9) {
            Landscape.cycle(Screen.hasShiftDown(), Screen.hasControlDown());
            e.setCanceled(true);
        }
    }
}
