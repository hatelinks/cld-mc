package dev.cld.autotest;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Автотест: ждёт главное меню, открывает мир из настроек, крутит камеру, меряет кадры, память и CPU,
// по желанию запускает Spark, потом выходит из игры. Работает только при наличии config/cld_autotest.properties.
@Mod(value = "cld_autotest", dist = Dist.CLIENT)
public final class AutoTest {
    private static final Logger LOG = LoggerFactory.getLogger("cld_autotest");

    private enum Phase { MENU, LOADING, WARMUP, MEASURE, PAUSE, DONE }

    private final Properties cfg = new Properties();
    private Phase phase = Phase.MENU;
    private long phaseStart, openAt;
    private long lastFrame;
    private final List<Long> frames = new ArrayList<>(), pauseFrames = new ArrayList<>();
    private final List<String> samples = new ArrayList<>();
    private final StringBuilder out = new StringBuilder();
    private long gcCount0, gcTime0, cpu0, wall0;
    private int ticks;

    public AutoTest() {
        Path p = FMLPaths.CONFIGDIR.get().resolve("cld_autotest.properties");
        if (!Files.exists(p)) return;
        try (InputStream in = Files.newInputStream(p)) {
            cfg.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.error("не прочитать {}", p, e);
            return;
        }
        if (!Boolean.parseBoolean(cfg.getProperty("enabled", "true"))) return;
        LOG.info("AUTOTEST включён: {}", cfg);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::frame);
    }

    private int num(String k, int def) { return Integer.parseInt(cfg.getProperty(k, String.valueOf(def)).trim()); }

    private void line(String s) {
        LOG.info("AUTOTEST {}", s);
        out.append(s).append('\n');
    }

    private void frame(RenderFrameEvent.Post e) {
        long now = System.nanoTime();
        if (lastFrame != 0) {
            if (phase == Phase.MEASURE) frames.add(now - lastFrame);
            else if (phase == Phase.PAUSE) pauseFrames.add(now - lastFrame);
        }
        lastFrame = now;
    }

    private static double sec(long ns) { return ns / 1e9; }

    private void enter(Phase p) {
        phase = p;
        phaseStart = System.nanoTime();
    }

    private void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        ticks++;
        switch (phase) {
            case MENU -> {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                line("startup_ms=" + ManagementFactory.getRuntimeMXBean().getUptime());
                line("heap_menu_mb=" + usedMb());
                // окно может быть не в фокусе, пусть игра не уходит в паузу сама
                mc.options.pauseOnLostFocus = false;
                String world = cfg.getProperty("world", "").trim();
                if (world.isEmpty() || !mc.getLevelSource().levelExists(world)) {
                    line("world_missing=" + world);
                    finish(mc);
                    return;
                }
                openAt = now;
                enter(Phase.LOADING);
                mc.createWorldOpenFlows().openWorld(world, () -> line("world_open_cancelled"));
            }
            case LOADING -> {
                if (mc.level == null || mc.player == null || mc.screen != null) {
                    if (sec(now - openAt) > 600) {
                        line("world_load_timeout");
                        finish(mc);
                    }
                    return;
                }
                line("world_load_ms=" + (now - openAt) / 1_000_000);
                enter(Phase.WARMUP);
            }
            case WARMUP -> {
                spin(mc);
                if (sec(now - phaseStart) < num("warmup", 40)) return;
                gcCount0 = gcCount();
                gcTime0 = gcTime();
                cpu0 = cpuNs();
                wall0 = now;
                if (Boolean.parseBoolean(cfg.getProperty("spark", "true"))) spark(mc, "spark profiler start --thread * --timeout " + num("measure", 60));
                enter(Phase.MEASURE);
            }
            case MEASURE -> {
                spin(mc);
                if (ticks % 20 == 0) samples.add(String.format("t=%.0f fps=%d heap=%d chunks=%s entities=%d active=%b",
                        sec(now - phaseStart), mc.getFps(), usedMb(), mc.level.getChunkSource().getLoadedChunksCount(),
                        mc.level.getEntityCount(), mc.isWindowActive()));
                if (sec(now - phaseStart) < num("measure", 60)) return;
                long wall = now - wall0;
                line("window_active=" + mc.isWindowActive());
                line("render_distance=" + mc.options.getEffectiveRenderDistance());
                stats("world", frames);
                line(String.format("cpu_load=%.2f (ядер в среднем)", (double) (cpuNs() - cpu0) / wall));
                line("gc_count=" + (gcCount() - gcCount0) + " gc_ms=" + (gcTime() - gcTime0));
                line("heap_world_mb=" + usedMb() + " heap_max_mb=" + Runtime.getRuntime().maxMemory() / 1048576);
                line("sections " + mc.levelRenderer.getSectionStatistics());
                line("entities " + mc.levelRenderer.getEntityStatistics());
                int pause = num("pause", 15);
                if (pause > 0) {
                    mc.pauseGame(false);
                    enter(Phase.PAUSE);
                } else finish(mc);
            }
            case PAUSE -> {
                if (sec(now - phaseStart) < num("pause", 15)) return;
                stats("pause", pauseFrames);
                finish(mc);
            }
            case DONE -> {}
        }
    }

    // медленный поворот камеры, чтобы в кадр попадало всё вокруг
    private void spin(Minecraft mc) {
        double yaw = Double.parseDouble(cfg.getProperty("yaw", "0.5"));
        if (mc.player != null && yaw != 0) mc.player.setYRot(mc.player.getYRot() + (float) yaw);
    }

    private void stats(String name, List<Long> f) {
        if (f.isEmpty()) {
            line(name + "_frames=0");
            return;
        }
        long[] a = f.stream().mapToLong(Long::longValue).sorted().toArray();
        long sum = Arrays.stream(a).sum();
        double avg = a.length / sec(sum);
        // 1% low: средний FPS по худшему проценту кадров
        int n = Math.max(1, a.length / 100);
        long worst = 0;
        for (int i = a.length - n; i < a.length; i++) worst += a[i];
        line(String.format("%s_frames=%d fps_avg=%.1f fps_1low=%.1f ms_p50=%.2f ms_p99=%.2f ms_max=%.1f", name, a.length, avg, n / sec(worst),
                a[a.length / 2] / 1e6, a[(int) (a.length * 0.99)] / 1e6, a[a.length - 1] / 1e6));
    }

    private void spark(Minecraft mc, String cmd) {
        MinecraftServer s = mc.getSingleplayerServer();
        if (s == null) return;
        line("spark: " + cmd);
        s.execute(() -> s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), cmd));
    }

    private void finish(Minecraft mc) {
        enter(Phase.DONE);
        for (String s : samples) out.append("  ").append(s).append('\n');
        Path p = FMLPaths.GAMEDIR.get().resolve("cld_autotest").resolve("result-" + System.currentTimeMillis() + ".txt");
        try {
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                w.write(out.toString());
            }
            LOG.info("AUTOTEST результат: {}", p);
        } catch (IOException ex) {
            LOG.error("не записать результат", ex);
        }
        if (!Boolean.parseBoolean(cfg.getProperty("quit", "true"))) return;
        // Spark сам выгружает отчёт по таймауту, даём ему время
        int wait = Boolean.parseBoolean(cfg.getProperty("spark", "true")) ? 15 : 0;
        new Thread(() -> {
            try {
                Thread.sleep(wait * 1000L);
            } catch (InterruptedException ignored) {}
            mc.execute(() -> {
                if (mc.level != null) {
                    mc.level.disconnect();
                    mc.disconnect();
                }
                mc.stop();
            });
        }, "cld_autotest-quit").start();
    }

    private static long usedMb() {
        Runtime r = Runtime.getRuntime();
        return (r.totalMemory() - r.freeMemory()) / 1048576;
    }

    private static long gcCount() {
        long n = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) n += Math.max(0, b.getCollectionCount());
        return n;
    }

    private static long gcTime() {
        long n = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) n += Math.max(0, b.getCollectionTime());
        return n;
    }

    private static long cpuNs() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) return os.getProcessCpuTime();
        return 0;
    }
}
