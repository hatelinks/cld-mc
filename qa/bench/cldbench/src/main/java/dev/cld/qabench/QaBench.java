package dev.cld.qabench;

import java.lang.management.ManagementFactory;
import java.util.Comparator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Замер прегенерации на выделенном сервере: квадрат (2r+1)² чанков до FULL, не больше N в работе. Итог — строка QABENCH в лог. */
@Mod("cld_qabench")
public final class QaBench {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("cld_qabench", Comparator.comparingLong(ChunkPos::toLong));
    private static volatile boolean running;
    private static long tickNanos, ticks, maxTick, tickStart;

    public QaBench(IEventBus bus) {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> e.getDispatcher().register(Commands.literal("qabench")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("cx", IntegerArgumentType.integer()).then(Commands.argument("cz", IntegerArgumentType.integer())
                .then(Commands.argument("r", IntegerArgumentType.integer(0, 200))
                        .executes(c -> start(c.getSource().getLevel(), c, 64))
                        .then(Commands.argument("inflight", IntegerArgumentType.integer(1, 4096))
                                .executes(c -> start(c.getSource().getLevel(), c, IntegerArgumentType.getInteger(c, "inflight")))))))));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre e) -> tickStart = System.nanoTime());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (!running) return;
            long t = System.nanoTime() - tickStart;
            tickNanos += t; ticks++; maxTick = Math.max(maxTick, t);
        });
    }

    private static int start(ServerLevel level, com.mojang.brigadier.context.CommandContext<?> c, int inflight) {
        if (running) return 0;
        int cx = IntegerArgumentType.getInteger(c, "cx"), cz = IntegerArgumentType.getInteger(c, "cz"), r = IntegerArgumentType.getInteger(c, "r");
        running = true; tickNanos = 0; ticks = 0; maxTick = 0;
        Thread t = new Thread(() -> run(level, cx, cz, r, inflight), "qabench");
        t.setDaemon(true);
        t.start();
        return 1;
    }

    private static void run(ServerLevel level, int cx, int cz, int r, int inflight) {
        MinecraftServer server = level.getServer();
        ServerChunkCache cache = level.getChunkSource();
        Semaphore slots = new Semaphore(inflight);
        AtomicInteger done = new AtomicInteger(), failed = new AtomicInteger();
        int total = (2 * r + 1) * (2 * r + 1);
        long t0 = System.nanoTime();
        FeatureTimes.reset();
        FeatureTimes.on = true;
        System.out.println("QABENCH start epochMs=" + System.currentTimeMillis() + " r=" + r);
        try {
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    slots.acquire();
                    ChunkPos p = new ChunkPos(cx + dx, cz + dz);
                    server.execute(() -> cache.addRegionTicket(TICKET, p, 0, p));
                    cache.getChunkFuture(p.x, p.z, ChunkStatus.FULL, true).whenComplete((res, err) -> {
                        if (err != null || res == null || !res.isSuccess()) failed.incrementAndGet();
                        server.execute(() -> cache.removeRegionTicket(TICKET, p, 0, p));
                        int d = done.incrementAndGet();
                        if (d % 500 == 0) System.out.println("QABENCH progress " + d + "/" + total);
                        slots.release();
                    });
                }
            slots.acquire(inflight);
        } catch (InterruptedException e) {
            return;
        }
        double sec = (System.nanoTime() - t0) / 1e9;
        running = false;
        FeatureTimes.on = false;
        FeatureTimes.print(40);
        double mspt = ticks == 0 ? 0 : tickNanos / 1e6 / ticks;
        System.gc();
        long heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() >> 20;
        System.out.printf("QABENCH done epochMs=" + System.currentTimeMillis() + " chunks=%d failed=%d sec=%.1f cps=%.1f mspt=%.2f maxTick=%.1f ticks=%d tps=%.1f heapAfterGcMB=%d%n",
                total, failed.get(), sec, total / sec, mspt, maxTick / 1e6, ticks, Math.min(20.0, ticks / sec), heap);
    }
}
