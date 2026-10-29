package dev.cld.tcw;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

// Позиции колец крепостей. Ваниль считает их заново при каждом запуске мира (сотни тысяч запросов биома,
// у нас это десятки секунд процессора), поэтому сохраняем их в папке мира: data/tcw_rings.txt.
// Ключ включает сид, набор структур, параметры колец, версию tcw и настройки мира.
public final class RingCache {
    private static final String FILE = "tcw_rings.txt";
    private static Path loadedFrom;
    private static final Map<String, List<ChunkPos>> MAP = new HashMap<>();

    private RingCache() {}

    public static String key(long seed, String set, int distance, int spread, int count, String biomes, Object options) {
        String ver = ModList.get().getModContainerById(TcwMod.NS).map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        return (seed + "|" + set + "|" + distance + "|" + spread + "|" + count + "|" + biomes + "|" + ver + "|" + options).replace(' ', '_');
    }

    private static Path file() {
        MinecraftServer server;
        try {
            server = ServerLifecycleHooks.getCurrentServer();
        } catch (LinkageError e) {
            return null;
        }
        return server == null ? null : server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE);
    }

    public static synchronized List<ChunkPos> get(String key) {
        Path f = file();
        if (f == null) return null;
        if (!f.equals(loadedFrom)) {
            MAP.clear();
            loadedFrom = f;
            if (Files.isRegularFile(f)) {
                try {
                    for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                        String[] kv = line.split(" ");
                        if (kv.length < 2) continue;
                        List<ChunkPos> l = new ArrayList<>();
                        for (int i = 1; i + 1 < kv.length; i += 2) l.add(new ChunkPos(Integer.parseInt(kv[i]), Integer.parseInt(kv[i + 1])));
                        MAP.put(kv[0], l);
                    }
                } catch (IOException | RuntimeException e) {
                    MAP.clear();
                }
            }
        }
        return MAP.get(key);
    }

    public static synchronized void put(String key, List<ChunkPos> list) {
        Path f = file();
        if (f == null || list == null || list.isEmpty()) return;
        get(key);
        MAP.put(key, List.copyOf(list));
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<ChunkPos>> e : MAP.entrySet()) {
            sb.append(e.getKey());
            for (ChunkPos p : e.getValue()) sb.append(' ').append(p.x).append(' ').append(p.z);
            sb.append('\n');
        }
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(FILE + ".tmp");
            Files.writeString(tmp, sb, StandardCharsets.UTF_8);
            Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // не записали, посчитаем в следующий раз
        }
    }
}
