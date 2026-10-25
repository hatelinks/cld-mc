package dev.cld.pregen.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientHooks {
    static CreateWorldScreen origin;
    static boolean pending;
    static PreviewSession session;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ClientHooks::onScreenInit);
        NeoForge.EVENT_BUS.addListener(ClientHooks::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(ClientHooks::onClientTick);
    }

    static Button findCreateButton(CreateWorldScreen screen) {
        for (GuiEventListener l : screen.children()) {
            if (l instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents tc
                    && tc.getKey().equals("selectWorld.create")) return b;
        }
        return null;
    }

    private static void onScreenInit(ScreenEvent.Init.Post e) {
        if (e.getScreen() instanceof CreateWorldScreen cws) {
            Button create = findCreateButton(cws);
            if (create == null) return;
            e.addListener(Button.builder(Component.translatable("cld_pregen.button.preview"), b -> {
                origin = cws;
                pending = true;
                create.onPress();
            }).bounds(8, cws.height - 28, 110, 20)
              .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("cld_pregen.button.preview.tip")))
              .build());
        } else if (e.getScreen() instanceof TitleScreen || e.getScreen() instanceof SelectWorldScreen) {
            pending = false; // создание мира отменено
        }
    }

    private static void onScreenOpening(ScreenEvent.Opening e) {
        // пока идёт предпросмотр, меню паузы (например, при потере фокуса) не должно его закрывать
        if (session != null && session.active && e.getNewScreen() instanceof PauseScreen) e.setCanceled(true);
    }

    private static void onClientTick(ClientTickEvent.Post e) {
        if (!pending) return;
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null || mc.screen instanceof ReceivingLevelScreen) return;
        pending = false;
        session = new PreviewSession(server, origin);
        mc.setScreen(new PreviewScreen(session));
    }

    private ClientHooks() {}
}
