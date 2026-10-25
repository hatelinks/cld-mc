package dev.cld.pregen;

import dev.cld.pregen.client.ClientHooks;
import dev.cld.pregen.server.PregenTask;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(PregenMod.MODID)
public final class PregenMod {
    public static final String MODID = "cld_pregen";

    public PregenMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> PregenTask.tickActive());
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> PregenTask.stopActive());
        if (FMLEnvironment.dist == Dist.CLIENT) ClientHooks.init();
    }
}
