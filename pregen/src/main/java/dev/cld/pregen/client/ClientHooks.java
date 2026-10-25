package dev.cld.pregen.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
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
    private static Integer savedRenderDistance;

    /** На время предпросмотра сервер грузит вокруг игрока минимум чанков (дальность 2), чтобы все потоки шли на прегенерацию. */
    static void lowerRenderDistance() {
        var opt = Minecraft.getInstance().options.renderDistance();
        if (savedRenderDistance == null) savedRenderDistance = opt.get();
        opt.set(2);
        DhCompat.pause(); // с самого создания мира, а не только после открытия карты
    }

    static void restoreRenderDistance() {
        DhCompat.resume();
        if (savedRenderDistance == null) return;
        Minecraft.getInstance().options.renderDistance().set(savedRenderDistance);
        savedRenderDistance = null;
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ClientHooks::onScreenInit);
        NeoForge.EVENT_BUS.addListener(ClientHooks::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(ClientHooks::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientHooks::onScreenRender);
        NeoForge.EVENT_BUS.addListener(DhCompat::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(DhCompat::onLoggedOut);
    }

    static Button findCreateButton(CreateWorldScreen screen) {
        for (GuiEventListener l : screen.children()) {
            if (l instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents tc
                    && tc.getKey().equals("selectWorld.create")) return b;
        }
        return null;
    }

    private static Button previewButton;

    /** Кнопка «Тип мира» на вкладке «Мир»: CycleButton с подписью options.generic_value(selectWorld.mapType, …). */
    private static AbstractWidget findWorldTypeButton(CreateWorldScreen screen) {
        for (GuiEventListener l : screen.children()) {
            if (l instanceof CycleButton<?> b && b.getMessage().getContents() instanceof TranslatableContents tc
                    && tc.getKey().equals("options.generic_value") && tc.getArgs().length > 0
                    && tc.getArgs()[0] instanceof Component name && name.getContents() instanceof TranslatableContents nk
                    && nk.getKey().equals("selectWorld.mapType")) return b;
        }
        return null;
    }

    private static void onScreenInit(ScreenEvent.Init.Post e) {
        if (e.getScreen() instanceof CreateWorldScreen cws) {
            Button create = findCreateButton(cws);
            if (create == null) return;
            previewButton = Button.builder(Component.translatable("cld_pregen.button.preview"), b -> {
                origin = cws;
                pending = true;
                lowerRenderDistance();
                create.onPress();
            }).bounds(cws.width / 2 - 75, 0, 150, 20)
              .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("cld_pregen.button.preview.tip")))
              .build();
            previewButton.visible = false;
            e.addListener(previewButton);
        } else if (e.getScreen() instanceof TitleScreen || e.getScreen() instanceof SelectWorldScreen) {
            pending = false; // создание мира отменено
            if (session == null) restoreRenderDistance();
        }
    }

    /** Вкладки пересобирают виджеты без события Init, поэтому кнопку прячем и ставим на место каждый кадр. */
    private static void onScreenRender(ScreenEvent.Render.Pre e) {
        if (!(e.getScreen() instanceof CreateWorldScreen cws) || previewButton == null) return;
        AbstractWidget type = findWorldTypeButton(cws);
        previewButton.visible = type != null && type.visible;
        if (previewButton.visible) previewButton.setPosition(cws.width / 2 - previewButton.getWidth() / 2, type.getY() - 24);
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
