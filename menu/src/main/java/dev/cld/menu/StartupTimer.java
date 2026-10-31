package dev.cld.menu;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

// Замер запуска игры: сколько ушло до загрузки модов, до конца загрузки и до главного меню.
// Пишет строку в logs/cld_startup.log при каждом запуске, последние запуски видны в TC Модах.
public final class StartupTimer {
    private static final long JVM = ManagementFactory.getRuntimeMXBean().getStartTime();
    private static long constructed, setup, loaded, title;
    private static String summary;

    private StartupTimer() {}

    static void constructed() {
        constructed = System.currentTimeMillis();
    }

    static void setup() {
        setup = System.currentTimeMillis();
    }

    static void loaded() {
        loaded = System.currentTimeMillis();
    }

    // первое открытие главного меню
    static void title() {
        if (title != 0) return;
        title = System.currentTimeMillis();
        String when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM HH:mm"));
        summary = String.format("%.1f с: моды %.1f, настройка %.1f, ресурсы %.1f",
                sec(title - JVM), sec(setup - JVM), sec(loaded - setup), sec(title - loaded));
        String line = String.format("%s  %s  (модов %d, память %d МБ, ядер %d)", when, summary,
                ModList.get().size(), Runtime.getRuntime().maxMemory() >> 20, Runtime.getRuntime().availableProcessors());
        System.out.println("[cld_menu] запуск: " + line);
        try {
            Path f = FMLPaths.GAMEDIR.get().resolve("logs").resolve("cld_startup.log");
            Files.createDirectories(f.getParent());
            Files.writeString(f, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[cld_menu] startup log: " + e);
        }
    }

    private static double sec(long ms) {
        return Math.max(0, ms) / 1000.0;
    }

    static String summary() {
        return summary;
    }

    // последние запуски из файла, новые снизу
    static List<String> history(int max) {
        try {
            List<String> all = Files.readAllLines(FMLPaths.GAMEDIR.get().resolve("logs").resolve("cld_startup.log"), StandardCharsets.UTF_8);
            return new ArrayList<>(all.subList(Math.max(0, all.size() - max), all.size()));
        } catch (IOException e) {
            return List.of();
        }
    }
}
