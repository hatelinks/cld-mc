package dev.cld.pregen.server;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Генерация чанков по спирали от центра. Все методы, кроме счётчиков, вызываются в потоке сервера. */
public final class PregenTask {
    public enum Shape { SQUARE, CIRCLE }

    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("cld_pregen", Comparator.comparingLong(ChunkPos::toLong));
    private static volatile PregenTask active;

    public final ServerLevel level;
    public final ChunkPos center;
    public final int radius;
    public final Shape shape;
    public final int total;
    public volatile int maxInFlight;
    public volatile int msPerTick;
    private final AtomicInteger done = new AtomicInteger();
    private final AtomicInteger inFlight = new AtomicInteger();
    private volatile boolean running;
    private final long startedAt = System.currentTimeMillis();
    private int ring, side, step; // курсор спирали

    public PregenTask(ServerLevel level, ChunkPos center, int radius, Shape shape, int maxInFlight, int msPerTick) {
        this.level = level; this.center = center; this.radius = radius; this.shape = shape;
        this.maxInFlight = maxInFlight; this.msPerTick = msPerTick;
        int t = 0;
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++)
                if (inside(dx, dz)) t++;
        this.total = t;
    }

    public static PregenTask active() { return active; }

    public static void start(PregenTask task) {
        stopActive();
        task.running = true;
        active = task;
    }

    public static void stopActive() {
        PregenTask t = active;
        if (t != null) t.running = false;
    }

    public static void tickActive() {
        PregenTask t = active;
        if (t != null && t.running) t.tick();
    }

    public boolean running() { return running; }
    public int done() { return done.get(); }
    public boolean finished() { return done.get() >= total; }
    public long elapsedMs() { return System.currentTimeMillis() - startedAt; }

    private boolean inside(int dx, int dz) {
        return shape == Shape.SQUARE || dx * dx + dz * dz <= radius * radius;
    }

    /** Следующая позиция спирали внутри формы или null, если обход закончен. */
    private ChunkPos next() {
        while (ring <= radius) {
            if (ring == 0) {
                ring = 1; side = 0; step = 0;
                return center;
            }
            int len = ring * 2;
            if (side < 4) {
                int dx, dz;
                switch (side) {
                    case 0 -> { dx = -ring + step; dz = -ring; }
                    case 1 -> { dx = ring; dz = -ring + step; }
                    case 2 -> { dx = ring - step; dz = ring; }
                    default -> { dx = -ring; dz = ring - step; }
                }
                if (++step >= len) { step = 0; side++; }
                if (inside(dx, dz)) return new ChunkPos(center.x + dx, center.z + dz);
            } else {
                ring++; side = 0; step = 0;
            }
        }
        return null;
    }

    private void tick() { fill(System.nanoTime() + msPerTick * 1_000_000L); }

    /** Ставит в очередь новые чанки, пока есть место в maxInFlight и не вышло время. */
    private void fill(long end) {
        ServerChunkCache cache = level.getChunkSource();
        MapData map = MapData.get();
        while (running && inFlight.get() < maxInFlight && System.nanoTime() < end) {
            ChunkPos p = next();
            if (p == null) { running = !finished(); return; }
            if (map.at(p.x, p.z) != null) { done.incrementAndGet(); continue; }
            inFlight.incrementAndGet();
            cache.addRegionTicket(TICKET, p, 0, p);
            cache.getChunkFuture(p.x, p.z, ChunkStatus.FULL, true).whenCompleteAsync((res, err) -> {
                try {
                    if (err == null && res != null && res.isSuccess() && res.orElse(null) instanceof LevelChunk c)
                        map.put(ChunkSample.of(level, c));
                } finally {
                    cache.removeRegionTicket(TICKET, p, 0, p);
                    inFlight.decrementAndGet();
                    done.incrementAndGet();
                }
                // сразу занять освободившееся место, не дожидаясь следующего тика
                if (running && active == this) fill(System.nanoTime() + 1_000_000L);
            }, level.getServer());
        }
    }
}
