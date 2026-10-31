package dev.cld.panel;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import com.mojang.blaze3d.platform.InputConstants;
import dev.cld.panel.ui.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

// Панель тестирования поверх Baritone: клавиши, кнопка в Esc, закреплённые окна на HUD.
public final class Panel {
    public static final Logger LOG = LoggerFactory.getLogger("cld_test_panel");
    static final KeyMapping OPEN = new KeyMapping("key.cld_test_panel.open", InputConstants.KEY_Y, "key.categories.cld_test_panel");
    static final KeyMapping CONSOLE = new KeyMapping("key.cld_test_panel.console", InputConstants.KEY_U, "key.categories.cld_test_panel");

    // Последние сообщения Baritone для консоли
    static final Deque<String> LOG_LINES = new ArrayDeque<>();
    private static boolean hooked;
    private static List<Window> windows;

    private Panel() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(OPEN);
            e.register(CONSOLE);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener(Panel::onScreen);
        NeoForge.EVENT_BUS.addListener(Panel::onHud);
        NeoForge.EVENT_BUS.addListener(Keys::onKey);
    }

    public static List<Window> windows() {
        if (windows == null) windows = Windows.build(PanelConfig.get());
        return windows;
    }

    static void resetWindows() {
        windows = null;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!hooked && mc.player != null) hookLogger();
        while (OPEN.consumeClick()) if (mc.screen == null) mc.setScreen(new PanelScreen());
        while (CONSOLE.consumeClick()) if (mc.screen == null) mc.setScreen(new ConsoleScreen(null));
    }

    // Сообщения Baritone идут и в чат, и в консоль панели
    private static void hookLogger() {
        hooked = true;
        Consumer<Component> old = BaritoneAPI.getSettings().logger.value;
        BaritoneAPI.getSettings().logger.value = old.andThen(Panel::log);
    }

    static void log(Component msg) {
        log(msg.getString());
    }

    static void log(String s) {
        synchronized (LOG_LINES) {
            for (String line : s.split("\n")) LOG_LINES.addLast(line);
            while (LOG_LINES.size() > 200) LOG_LINES.removeFirst();
        }
    }

    // Кнопка "Панель" в левом верхнем углу меню Esc (справа сверху стоит кнопка cld_far)
    private static void onScreen(ScreenEvent.Init.Post e) {
        if (!(e.getScreen() instanceof PauseScreen ps) || !ps.showsPauseMenu()) return;
        e.addListener(Button.builder(Component.translatable("cld_test_panel.button"), b -> Minecraft.getInstance().setScreen(new PanelScreen()))
                .bounds(6, 6, 60, 20).build());
    }

    private static void onHud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.screen instanceof PanelScreen) return;
        boolean any = false;
        for (Window w : windows()) any |= w.state.pinned;
        if (!any) return;
        GuiGraphics g = e.getGuiGraphics();
        double s = scale();
        g.pose().pushPose();
        g.pose().scale((float) s, (float) s, 1);
        for (Window w : windows()) if (w.state.pinned) w.render(g, mc.font, -1, -1, true);
        g.pose().popPose();
    }

    // Панель меняется вместе с "Размером интерфейса" игры, size только подстраивает
    static double scale() {
        return PanelConfig.get().size;
    }

    // Всё разрешено в одиночной игре и на своих серверах из настроек, на чужих только Baritone и инфо
    public static boolean full() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer()) return true;
        ServerData sd = mc.getCurrentServer();
        return sd != null && PanelConfig.get().servers.contains(sd.ip.trim().toLowerCase());
    }

    public static String serverAddress() {
        ServerData sd = Minecraft.getInstance().getCurrentServer();
        return sd == null ? null : sd.ip.trim().toLowerCase();
    }

    public static IBaritone baritone() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    // Команда Baritone без префикса, как в чате после #
    public static void run(String cmd) {
        if (Minecraft.getInstance().player == null) return;
        log("> " + cmd);
        try {
            baritone().getCommandManager().execute(cmd);
        } catch (Exception ex) {
            log("Ошибка: " + ex.getMessage());
        }
    }
}
