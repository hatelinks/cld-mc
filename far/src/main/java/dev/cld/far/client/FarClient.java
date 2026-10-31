package dev.cld.far.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.cld.far.FarConfig;
import dev.cld.far.FarMod;
import dev.cld.far.SeedPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

// Клиент: шейдер, клавиша настроек, сид от сервера, рендер после неба и туман.
public final class FarClient {
    static ShaderInstance shader;
    private static ModContainer container;
    private static final KeyMapping SETTINGS = new KeyMapping("key.cld_far.settings", InputConstants.KEY_F7, "key.categories.cld_far");
    // быстро включить и выключить дальний рельеф (по умолчанию без клавиши, назначается в "Управлении")
    private static final KeyMapping TOGGLE = new KeyMapping("key.cld_far.toggle", InputConstants.UNKNOWN.getValue(), "key.categories.cld_far");
    // дальность больше и меньше (по умолчанию без клавиш)
    private static final KeyMapping FARTHER = new KeyMapping("key.cld_far.farther", InputConstants.UNKNOWN.getValue(), "key.categories.cld_far");
    private static final KeyMapping NEARER = new KeyMapping("key.cld_far.nearer", InputConstants.UNKNOWN.getValue(), "key.categories.cld_far");
    private static final FarRenderer RENDERER = new FarRenderer();
    private static FarConfig.Preset lastPreset;
    private static int joinTicks = -1;

    private FarClient() {}

    public static void init(IEventBus modBus, ModContainer c) {
        container = c;
        c.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener((RegisterShadersEvent e) -> {
            try {
                e.registerShader(new ShaderInstance(e.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(FarMod.NS, "far_terrain"),
                        DefaultVertexFormat.POSITION_COLOR), s -> shader = s);
            } catch (java.io.IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(SETTINGS);
            e.register(TOGGLE);
            e.register(FARTHER);
            e.register(NEARER);
        });
        NeoForge.EVENT_BUS.addListener(FarClient::onStage);
        NeoForge.EVENT_BUS.addListener(FarClient::onFog);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent.DebugText e) -> RENDERER.debug(e.getRight()));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            Minecraft m = Minecraft.getInstance();
            FarRenderer.paused = FarConfig.PAUSE.get() && m.level != null && (m.isPaused() || !m.isWindowActive());
            // набор выбран в настройках: выставить его значения
            FarConfig.Preset p = FarConfig.PRESET.get();
            if (lastPreset != null && p != lastPreset) FarConfig.apply(p);
            lastPreset = p;
            hint(m);
            while (FARTHER.consumeClick()) distance(m, 1.25);
            while (NEARER.consumeClick()) distance(m, 0.8);
            while (SETTINGS.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(new ConfigurationScreen(container, mc.screen));
            }
            while (TOGGLE.consumeClick()) {
                boolean on = !FarConfig.ENABLED.get();
                FarConfig.ENABLED.set(on);
                FarConfig.ENABLED.save();
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null)
                    mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(on ? "cld_far.toggle.on" : "cld_far.toggle.off"), true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            RENDERER.setWorld(null);
            joinTicks = -1;
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> joinTicks = 0);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RegisterClientCommandsEvent e) -> commands(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(FarClient::onScreen);
    }

    // кнопка настроек: в Настройках справа от строки с полем зрения, в меню паузы над шестерёнкой у Открыть для сети,
    // в Настройках графики в правом верхнем углу
    private static void onScreen(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post e) {
        net.minecraft.client.gui.screens.Screen screen = e.getScreen();
        int x, y;
        if (screen instanceof net.minecraft.client.gui.screens.options.OptionsScreen) {
            String fov = net.minecraft.client.resources.language.I18n.get("options.fov");
            net.minecraft.client.gui.components.AbstractWidget row = null;
            for (var l : e.getListenersList())
                if (l instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getMessage().getString().startsWith(fov)) row = w;
            if (row == null) return;
            int right = row.getX() + row.getWidth();
            for (var l : e.getListenersList())
                if (l instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getY() == row.getY()) right = Math.max(right, w.getX() + w.getWidth());
            x = Math.min(right + 4, screen.width - 22);
            y = row.getY();
        } else if (screen instanceof net.minecraft.client.gui.screens.PauseScreen ps && ps.showsPauseMenu() && FarConfig.PAUSE_BUTTON.get()) {
            // над кнопкой справа от Открыть для сети (шестерёнка), чтобы не лезть на TC Моды в углу
            String lan = net.minecraft.client.resources.language.I18n.get("menu.shareToLan");
            net.minecraft.client.gui.components.AbstractWidget row = null;
            for (var l : e.getListenersList())
                if (l instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getMessage().getString().equals(lan)) row = w;
            if (row != null) {
                int right = row.getX() + row.getWidth();
                // ровно над ближайшей кнопкой справа в том же ряду, а если её нет, на её обычном месте
                x = Integer.MAX_VALUE;
                for (var l : e.getListenersList())
                    if (l instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getY() == row.getY() && w.getX() >= right) x = Math.min(x, w.getX());
                if (x == Integer.MAX_VALUE) x = right + 4;
                y = row.getY() - 24;
            } else {
                // по сети кнопки Открыть для сети нет: под TC Моды в правом углу
                x = screen.width - 26;
                y = 30;
            }
        } else if (screen instanceof net.minecraft.client.gui.screens.options.VideoSettingsScreen) {
            x = screen.width - 26;
            y = 6;
        } else return;
        net.minecraft.network.chat.Component name = net.minecraft.network.chat.Component.translatable("cld_far.button");
        e.addListener(net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("⛰"),
                        b -> Minecraft.getInstance().setScreen(new ConfigurationScreen(container, screen)))
                .bounds(x, y, 20, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(name)).build());
    }

    public static void onSeed(SeedPayload p) {
        RENDERER.setWorld(p);
    }

    // Через 10 секунд после входа без данных от сервера: подсказка в чате (один раз за вход).
    private static void hint(Minecraft m) {
        if (joinTicks < 0 || m.level == null || m.player == null) return;
        if (RENDERER.hasWorld() || !FarConfig.HINTS.get() || !FarConfig.ENABLED.get()) {
            joinTicks = -1;
            return;
        }
        if (++joinTicks < 200) return;
        joinTicks = -1;
        if (m.level.dimension() == Level.OVERWORLD) m.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("cld_far.hint.noseed"), false);
    }

    private static void distance(Minecraft m, double k) {
        int d = (int) Math.round(Math.max(512, Math.min(32768, FarConfig.DISTANCE.get() * k)) / 256) * 256;
        FarConfig.DISTANCE.set(d);
        FarConfig.DISTANCE.save();
        FarConfig.PRESET.set(FarConfig.Preset.CUSTOM);
        if (m.player != null) m.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("cld_far.distance", d), true);
    }

    // /cldfar reload: пересчитать всё; /cldfar stats: статистика; /cldfar cache clear: удалить кэш мира на диске.
    private static void commands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> d) {
        d.register(net.minecraft.commands.Commands.literal("cldfar")
                .then(net.minecraft.commands.Commands.literal("reload").executes(c -> {
                    RENDERER.reload(false);
                    c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable("cld_far.cmd.reload"), false);
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("stats").executes(c -> {
                    java.util.List<String> lines = new java.util.ArrayList<>();
                    RENDERER.debug(lines);
                    if (lines.isEmpty()) lines.add("cld_far: нет данных мира");
                    for (String l : lines) c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(l), false);
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("cache").then(net.minecraft.commands.Commands.literal("clear").executes(c -> {
                    RENDERER.reload(true);
                    c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable("cld_far.cmd.cache"), false);
                    return 1;
                }))));
    }

    private static void onStage(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        Minecraft mc = Minecraft.getInstance();
        if (shader == null || mc.level == null || mc.level.dimension() != Level.OVERWORLD) return;
        if (!clearView(mc, e.getCamera())) {
            RENDERER.idle();
            return;
        }
        RENDERER.render(mc, e);
    }

    // Под водой, в лаве, в порошковом снеге, со слепотой или темнотой игра сама сужает видимость своим туманом;
    // дальний рельеф там не нужен (снизу он виден как тёмные плиты поверхности моря), поэтому не рисуется.
    private static boolean clearView(Minecraft mc, net.minecraft.client.Camera cam) {
        if (cam.getFluidInCamera() != FogType.NONE) return false;
        // под землёй: неба над головой нет и камера заметно ниже поверхности
        if (FarConfig.HIDE_UNDERGROUND.get()) {
            net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(cam.getPosition());
            if (mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, bp) == 0
                    && bp.getY() < mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, bp.getX(), bp.getZ()) - 4) return false;
        }
        return !(cam.getEntity() instanceof net.minecraft.world.entity.LivingEntity le)
                || !le.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) && !le.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS);
    }

    // Туман игры уносится к краю дальнего рельефа, иначе на границе прорисовки виден светлый обод.
    private static void onFog(ViewportEvent.RenderFog e) {
        if (!RENDERER.active() || e.getMode() != FogRenderer.FogMode.FOG_TERRAIN || e.getType() != FogType.NONE) return;
        Minecraft mc = Minecraft.getInstance();
        if (e.getFarPlaneDistance() < mc.gameRenderer.getRenderDistance() - 1) return; // слепота, темнота и т. п.
        float dist = FarConfig.DISTANCE.get();
        e.setNearPlaneDistance((float) (dist * FarConfig.FOG.get()));
        e.setFarPlaneDistance(dist);
        e.setFogShape(com.mojang.blaze3d.shaders.FogShape.CYLINDER);
        e.setCanceled(true);
    }
}
