package dev.cld.tcw.client;

import dev.cld.tcw.TcwMod;
import dev.cld.tcw.Worlds;
import dev.cld.tcw.gen.Terrain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Клиент: тип мира по умолчанию, кнопка «Карта мира» при создании мира и команда /tcwmap. */
public final class ClientSetup {
    public static final ResourceKey<WorldPreset> PRESET =
            ResourceKey.create(Registries.WORLD_PRESET, ResourceLocation.fromNamespaceAndPath(TcwMod.NS, "telipailo"));
    static final ResourceKey<NoiseGeneratorSettings> SETTINGS =
            ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath(TcwMod.NS, "telipailo"));

    private ClientSetup() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ClientSetup::onScreenInit);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onClientCommands);
    }

    public static void selectTcw(WorldCreationUiState ui) {
        ui.getSettings().worldgenLoadContext().registryOrThrow(Registries.WORLD_PRESET).getHolder(PRESET)
                .ifPresent(h -> ui.setWorldType(new WorldCreationUiState.WorldTypeEntry(h)));
    }

    private static boolean isTcw(WorldCreationUiState ui) {
        var h = ui.getWorldType().preset();
        return h != null && h.is(PRESET);
    }

    private static void onScreenInit(ScreenEvent.Init.Post e) {
        if (!(e.getScreen() instanceof CreateWorldScreen s)) return;
        e.addListener(Button.builder(Component.literal("Карта мира"), b -> openPreview(s))
                .bounds(s.width - 92, 4, 88, 20).build());
    }

    private static void openPreview(CreateWorldScreen s) {
        WorldCreationUiState ui = s.getUiState();
        if (!isTcw(ui)) selectTcw(ui);
        long seed = WorldOptions.parseSeed(ui.getSeed()).orElseGet(() -> {
            long r = WorldOptions.randomSeed();
            ui.setSeed(Long.toString(r));
            return r;
        });
        var reg = ui.getSettings().worldgenLoadContext();
        var settings = reg.registryOrThrow(Registries.NOISE_SETTINGS).getOrThrow(SETTINGS);
        RandomState rs = RandomState.create(settings, reg.lookupOrThrow(Registries.NOISE), seed);
        Terrain t = Worlds.terrain(Worlds.seedFrom(rs.getOrCreateNoise(Worlds.SEED_NOISE)));
        Minecraft.getInstance().setScreen(new MapScreen(s, t, 0, 0, null, "Сид " + seed));
    }

    private static void onClientCommands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("tcwmap").executes(c -> {
            Minecraft mc = Minecraft.getInstance();
            var server = mc.getSingleplayerServer();
            if (server == null || mc.player == null) {
                c.getSource().sendFailure(Component.literal("Карта работает только в одиночной игре"));
                return 0;
            }
            ServerLevel level = server.overworld();
            if (!level.dimensionTypeRegistration().unwrapKey().map(k -> Worlds.isTcw(k.location())).orElse(false)) {
                c.getSource().sendFailure(Component.literal("Это не мир TelipailoCraft"));
                return 0;
            }
            Terrain t = Worlds.terrain(level);
            double px = mc.player.getX(), pz = mc.player.getZ();
            var sp = level.getSharedSpawnPos();
            mc.tell(() -> mc.setScreen(new MapScreen(null, t, px, pz, new double[] { px, pz, sp.getX(), sp.getZ() }, null)));
            return 1;
        }));
    }
}
