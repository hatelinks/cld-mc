package dev.cld.pregen.client;

import dev.cld.pregen.server.MapData;
import dev.cld.pregen.server.PregenTask;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Один сеанс предпросмотра: свежесозданный мир, замороженный на время генерации. */
final class PreviewSession {
    private static final Logger LOG = LogUtils.getLogger();

    final IntegratedServer server;
    final CreateWorldScreen origin;
    final String levelId;
    final long seed;
    final ChunkPos center;
    final int minY, maxY, sea;
    boolean active = true;

    PreviewSession(IntegratedServer server, CreateWorldScreen origin) {
        this.server = server;
        this.origin = origin;
        this.levelId = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
        ServerLevel ow = server.overworld();
        this.seed = ow.getSeed();
        this.center = new ChunkPos(ow.getSharedSpawnPos());
        this.minY = ow.getMinBuildHeight();
        this.maxY = ow.getMaxBuildHeight() - 1;
        this.sea = ow.getSeaLevel();
        MapData.reset();
        server.execute(() -> server.tickRateManager().setFrozen(true));
    }

    void start(int radius, PregenTask.Shape shape, int maxInFlight, int msPerTick) {
        server.execute(() -> PregenTask.start(new PregenTask(server.overworld(), center, radius, shape, maxInFlight, msPerTick)));
    }

    void stop() { server.execute(PregenTask::stopActive); }

    /** Оставить мир: снять заморозку и пустить игрока. */
    void keep() {
        active = false;
        server.execute(() -> {
            PregenTask.stopActive();
            server.tickRateManager().setFrozen(false);
        });
        ClientHooks.session = null;
        ClientHooks.restoreRenderDistance();
        Minecraft.getInstance().setScreen(null);
    }

    /** Удалить мир и вернуться к экрану создания; если seed != null — сразу создать заново с этим сидом. */
    void discard(String newSeed) {
        active = false;
        Minecraft mc = Minecraft.getInstance();
        server.execute(PregenTask::stopActive);
        ClientHooks.session = null;
        // Как в PauseScreen: сначала закрыть соединение, иначе сервер не узнает, что игрок вышел,
        // не остановится, и mc.disconnect() будет вечно ждать его выключения.
        if (mc.level != null) mc.level.disconnect();
        mc.disconnect(new GenericMessageScreen(Component.translatable("cld_pregen.deleting")));
        try (var access = mc.getLevelSource().createAccess(levelId)) {
            access.deleteLevel();
        } catch (Exception e) {
            LOG.warn("Не удалось удалить мир предпросмотра {}", levelId, e);
        }
        MapData.reset();
        if (newSeed == null) ClientHooks.restoreRenderDistance();
        if (origin == null) {
            mc.setScreen(new SelectWorldScreen(new TitleScreen()));
            return;
        }
        mc.setScreen(origin);
        if (newSeed != null) {
            origin.getUiState().setSeed(newSeed);
            Button create = ClientHooks.findCreateButton(origin);
            if (create != null) {
                ClientHooks.pending = true;
                ClientHooks.lowerRenderDistance();
                create.onPress();
            }
        }
    }
}
