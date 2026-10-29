package dev.sableandroid;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// config/sable-android-patches.toml: раздел [tick_throttle], строки "класс = раз в сколько тиков"
// Сейчас файл только читается и пишется: интервалы в игре берутся из coremod-скрипта,
// updateIntervals при загрузке не вызывается (так было и в 1.2.0).
public class TickThrottleConfig {
    private static final String CONFIG_FILE = "config/sable-android-patches.toml";
    private static final Map<String, Integer> DEFAULTS = new LinkedHashMap<>();
    private final Map<String, Integer> throttleMap = new LinkedHashMap<>();

    static {
        // те же числа, что в sable_android_transformer.js
        DEFAULTS.put("dev/simulated_team/simulated/content/blocks/gimbal_sensor/GimbalSensorBlockEntity", 4);
        DEFAULTS.put("dev/simulated_team/simulated/content/blocks/velocity_sensor/VelocitySensorBlockEntity", 3);
        DEFAULTS.put("dev/simulated_team/simulated/content/blocks/altitude_sensor/AltitudeSensorBlockEntity", 3);
        DEFAULTS.put("dev/simulated_team/simulated/content/blocks/spring/SpringBlockEntity", 3);
        DEFAULTS.put("dev/simulated_team/simulated/content/blocks/nav_table/NavTableBlockEntity", 4);
        DEFAULTS.put("dev/simulated_team/simulated/content/blocks/lasers/optical_sensor/OpticalSensorBlockEntity", 2);
        DEFAULTS.put("dev/simulated_team/simulated/content/blocks/redstone_magnet/RedstoneMagnetBlockEntity", 3);
        DEFAULTS.put("dev/simulated_team/simulated/content/blocks/docking_connector/DockingConnectorBlockEntity", 3);
        DEFAULTS.put("dev/simulated_team/simulated/content/blocks/nameplate/NameplateBlockEntity", 4);
        DEFAULTS.put("dev/simulated_team/simulated/content/blocks/rope/rope_winch/RopeWinchBlockEntity", 2);
        DEFAULTS.put("dev/simulated_team/simulated/content/blocks/swivel_bearing/SwivelBearingBlockEntity", 2);
        DEFAULTS.put("dev/simulated_team/simulated/content/blocks/physics_assembler/PhysicsAssemblerBlockEntity", 2);
        DEFAULTS.put("dev/simulated_team/simulated/content/blocks/redstone/AbstractLinkedReceiverBlockEntity", 2);
        DEFAULTS.put("dev/simulated_team/simulated/content/blocks/redstone/redstone_inductor/RedstoneInductorBlockEntity", 2);
        DEFAULTS.put("dev/simulated_team/simulated/content/blocks/throttle_lever/ThrottleLeverBlockEntity", 2);
        DEFAULTS.put("dev/simulated_team/simulated/content/blocks/merging_glue/MergingGlueBlockEntity", 3);
    }

    public void loadOrCreate() {
        Path path = Paths.get(CONFIG_FILE);
        throttleMap.putAll(DEFAULTS);
        if (!Files.exists(path)) {
            writeToFile(path);
            SableAndroid.LOGGER.info("[SableAndroid] Created default patch config at {}", CONFIG_FILE);
            return;
        }
        try {
            List<String> lines = Files.readAllLines(path);
            boolean inSection = false;
            for (String line : lines) {
                line = line.trim();
                if (line.equals("[tick_throttle]")) {
                    inSection = true;
                    continue;
                }
                if (line.startsWith("[")) {
                    inSection = false;
                    continue;
                }
                if (!inSection || line.isEmpty()) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String cls = line.substring(0, eq).trim().replace('.', '/');
                String value = line.substring(eq + 1).trim();
                try {
                    int interval = Integer.parseInt(value);
                    if (interval >= 0) throttleMap.put(cls, interval);
                } catch (NumberFormatException ignored) {
                }
            }
            SableAndroid.LOGGER.info("[SableAndroid] Loaded patch config from {}", CONFIG_FILE);
        } catch (IOException e) {
            SableAndroid.LOGGER.error("[SableAndroid] Failed to read config: {}", e.getMessage());
        }
    }

    public void applyAndSave(Map<String, Integer> values) {
        throttleMap.clear();
        throttleMap.putAll(values);
        TickThrottleManager.updateIntervals(throttleMap);
        writeToFile(Paths.get(CONFIG_FILE));
    }

    private void writeToFile(Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(path))) {
                out.println("[tick_throttle]");
                for (Map.Entry<String, Integer> e : throttleMap.entrySet()) {
                    out.println(e.getKey().replace('/', '.') + " = " + e.getValue());
                }
            }
        } catch (IOException e) {
            SableAndroid.LOGGER.error("[SableAndroid] Failed to write config: {}", e.getMessage());
        }
    }

    public int getThrottle(String className) {
        return throttleMap.getOrDefault(className, 1);
    }

    public Map<String, Integer> getAll() {
        return Collections.unmodifiableMap(throttleMap);
    }
}
