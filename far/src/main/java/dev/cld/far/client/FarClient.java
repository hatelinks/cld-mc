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

/** Клиент: шейдер, клавиша настроек, сид от сервера, рендер после неба и туман. */
public final class FarClient {
    static ShaderInstance shader;
    private static ModContainer container;
    private static final KeyMapping SETTINGS = new KeyMapping("key.cld_far.settings", InputConstants.KEY_F7, "key.categories.cld_far");
    private static final FarRenderer RENDERER = new FarRenderer();

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
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(SETTINGS));
        NeoForge.EVENT_BUS.addListener(FarClient::onStage);
        NeoForge.EVENT_BUS.addListener(FarClient::onFog);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            while (SETTINGS.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(new ConfigurationScreen(container, mc.screen));
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> RENDERER.setWorld(null));
    }

    public static void onSeed(SeedPayload p) {
        RENDERER.setWorld(p);
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

    /**
     * Под водой, в лаве, в порошковом снеге, со слепотой или темнотой игра сама сужает видимость своим туманом;
     * дальний рельеф там не нужен (снизу он виден как тёмные плиты поверхности моря), поэтому не рисуется.
     */
    private static boolean clearView(Minecraft mc, net.minecraft.client.Camera cam) {
        if (cam.getFluidInCamera() != FogType.NONE) return false;
        return !(cam.getEntity() instanceof net.minecraft.world.entity.LivingEntity le)
                || !le.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) && !le.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS);
    }

    /** Туман игры уносится к краю дальнего рельефа, иначе на границе прорисовки виден светлый обод. */
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
