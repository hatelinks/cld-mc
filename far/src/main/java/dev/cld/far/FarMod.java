package dev.cld.far;

import dev.cld.tcw.Worlds;
import dev.cld.tcw.gen.Options;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Дальняя прорисовка рельефа TelipailoCraft: сервер сообщает клиенту сид генератора, клиент сам считает дальний рельеф. */
@Mod(FarMod.NS)
public final class FarMod {
    public static final String NS = "cld_far";

    public FarMod(IEventBus modBus, ModContainer container) {
        modBus.addListener(FarMod::onPayloads);
        NeoForge.EVENT_BUS.addListener(FarMod::onLogin);
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            container.registerConfig(ModConfig.Type.CLIENT, FarConfig.SPEC);
            dev.cld.far.client.FarClient.init(modBus, container);
        }
    }

    private static void onPayloads(RegisterPayloadHandlersEvent e) {
        e.registrar("1").optional().playToClient(SeedPayload.TYPE, SeedPayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> dev.cld.far.client.FarClient.onSeed(p)));
    }

    /** Сид генератора (не сид мира: он выводится из сида через шум tcw:seed) и настройки мира. */
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        var server = sp.server;
        if (!Worlds.overworldType(server.registryAccess()).map(Worlds::isTcw).orElse(false)) return;
        ServerLevel ow = server.overworld();
        long seed = Worlds.seedFrom(ow.getChunkSource().randomState().getOrCreateNoise(Worlds.SEED_NOISE));
        Options o = Worlds.currentOptions();
        PacketDistributor.sendToPlayer(sp, new SeedPayload(seed, o.continents(), o.mountains(), o.height(), o.climate(), o.snow(), o.halls()));
    }
}
