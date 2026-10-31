package dev.cld.deepcull;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

@Mod(value = DeepCullMod.NS, dist = Dist.CLIENT)
public final class DeepCullMod {
    public static final String NS = "cld_deepcull";
    private static final KeyMapping TOGGLE = new KeyMapping("key.cld_deepcull.toggle", InputConstants.KEY_F8, "key.categories.cld_deepcull");

    public DeepCullMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, DeepConfig.SPEC);
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(TOGGLE));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            while (TOGGLE.consumeClick()) {
                DeepConfig.ENABLED.set(!DeepConfig.ENABLED.get());
                if (mc.player != null)
                    mc.player.displayClientMessage(Component.translatable(DeepConfig.ENABLED.get() ? "cld_deepcull.on" : "cld_deepcull.off"), true);
                DeepCull.graphChanged();
            }
            DeepCull.tick(mc);
        });
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load e) -> {
            if (e.getLevel().isClientSide()) DeepCull.forget(e.getChunk().getPos().x, e.getChunk().getPos().z);
        });
        NeoForge.EVENT_BUS.addListener((CustomizeGuiOverlayEvent.DebugText e) -> {
            if (Minecraft.getInstance().getDebugOverlay().showDebugScreen()) e.getLeft().add(DeepCull.debug());
        });
    }
}
