package dev.cld.deepcull;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.ModList;

// Решает, прятать ли секцию. Пока камера не ниже поверхности своего чанка, секции, которые целиком
// ниже самой низкой поверхности в чанке и его соседях (минус margin), для графа видимости не существуют:
// их не строят и не рисуют, и обход через них не идёт. Спустился ниже поверхности, отсечка выключается,
// и Sodium или ванила сами достроят то, что стало видно.
public final class DeepCull {
    private static final int NONE = Integer.MIN_VALUE;

    private static volatile boolean active;
    private static volatile int camSecY;
    private static volatile ClientLevel level;
    private static final boolean sodium = ModList.get().isLoaded("sodium");
    // самая низкая поверхность в чанке, по ключу ChunkPos; читается и из потока графа ванилы
    private static final Long2IntOpenHashMap floors = new Long2IntOpenHashMap();
    private static final AtomicLong checks = new AtomicLong(), hidden = new AtomicLong();
    private static long shownChecks, shownHidden;
    private static int ticks;

    static { floors.defaultReturnValue(NONE); }

    private DeepCull() {}

    public static boolean hide(int secX, int secY, int secZ) {
        if (!active) return false;
        checks.incrementAndGet();
        if (secY >= camSecY - DeepConfig.KEEP.get()) return false;
        int f = NONE;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                int v = floor(secX + dx, secZ + dz);
                if (v == NONE) return false;
                f = f == NONE ? v : Math.min(f, v);
            }
        if ((secY << 4) + 16 > f - DeepConfig.MARGIN.get()) return false;
        hidden.incrementAndGet();
        return true;
    }

    static void tick(Minecraft mc) {
        ClientLevel lvl = mc.level;
        if (lvl != level) {
            synchronized (floors) { floors.clear(); }
            level = lvl;
        }
        boolean now = false;
        Camera cam = mc.gameRenderer.getMainCamera();
        if (lvl != null && DeepConfig.ENABLED.get() && cam.isInitialized()) {
            double y = cam.getPosition().y;
            int cx = Mth.floor(cam.getPosition().x) >> 4, cz = Mth.floor(cam.getPosition().z) >> 4;
            int f = floor(cx, cz);
            // гистерезис 2 блока, чтобы не дёргать граф на краю
            if (f != NONE) now = active ? y >= f - 2 : y >= f;
            camSecY = Mth.floor(y) >> 4;
        }
        if (now != active) {
            active = now;
            graphChanged();
        }
        // поверхность меняется (стройка, взрывы): раз в 5 секунд считаем заново
        if (++ticks % 100 == 0) {
            synchronized (floors) { floors.clear(); }
            shownChecks = checks.getAndSet(0) / 5;
            shownHidden = hidden.getAndSet(0) / 5;
        }
    }

    static void forget(int cx, int cz) {
        synchronized (floors) { floors.remove(ChunkPos.asLong(cx, cz)); }
    }

    static void graphChanged() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer != null) mc.levelRenderer.needsUpdate();
        if (sodium) SodiumHook.scheduleUpdate();
    }

    static String debug() {
        return "cld_deepcull: " + (!DeepConfig.ENABLED.get() ? "выкл" : active ? "работает" : "под землёй, не режет")
                + ", скрыто " + shownHidden + " из " + shownChecks + " проверок/с";
    }

    private static int floor(int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        int v;
        synchronized (floors) { v = floors.get(key); }
        if (v != NONE) return v;
        ClientLevel lvl = level;
        if (lvl == null) return NONE;
        if (!(lvl.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk ch) || ch.isEmpty()) return NONE;
        int min = Integer.MAX_VALUE;
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) min = Math.min(min, ch.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
        synchronized (floors) { floors.put(key, min); }
        return min;
    }

}
