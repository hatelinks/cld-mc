package dev.sableandroid;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// Свои логи в папке SableBridge_Logs: на телефоне их проще найти, чем latest.log
public class SableBridgeLogger {
    private static final String LOG_DIR = "SableBridge_Logs";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static PrintWriter bridgeWriter;
    private static PrintWriter sableWriter;
    private static PrintWriter renderWriter;

    public static void init() {
        try {
            Path dir = Paths.get(LOG_DIR);
            Files.createDirectories(dir);
            String session = LocalDateTime.now().format(FILE_TIMESTAMP);
            // прошлые логи переименовываем, новые всегда *_Latest.log
            archiveOldLog(dir, "SableBridge_Latest.log", "SableBridge_" + session + ".log");
            archiveOldLog(dir, "Sable_Latest.log", "Sable_" + session + ".log");
            archiveOldLog(dir, "Render_Latest.log", "Render_" + session + ".log");
            bridgeWriter = openWriter(dir.resolve("SableBridge_Latest.log"));
            sableWriter = openWriter(dir.resolve("Sable_Latest.log"));
            renderWriter = openWriter(dir.resolve("Render_Latest.log"));
            logBridge("SableBridge Logger initialized - Session: " + session);
            logBridge("Log directory: " + dir.toAbsolutePath());
        } catch (IOException e) {
            SableAndroid.LOGGER.error("[SableAndroid] Failed to initialize SableBridge log directory: {}", e.getMessage());
        }
    }

    private static void archiveOldLog(Path dir, String latest, String archived) {
        try {
            Path file = dir.resolve(latest);
            if (Files.exists(file) && Files.size(file) > 0) {
                Files.move(file, dir.resolve(archived), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        }
    }

    private static PrintWriter openWriter(Path path) throws IOException {
        return new PrintWriter(new BufferedWriter(new FileWriter(path.toFile(), false)), true);
    }

    private static String timestamp() {
        return "[" + LocalDateTime.now().format(TIMESTAMP) + "]";
    }

    public static void logBridge(String msg) {
        String line = timestamp() + "[SableBridge] " + msg;
        SableAndroid.LOGGER.info("[SableAndroid] {}", msg);
        if (bridgeWriter != null) bridgeWriter.println(line);
    }

    public static void logBridgeWarn(String msg) {
        String line = timestamp() + "[SableBridge/WARN] " + msg;
        SableAndroid.LOGGER.warn("[SableAndroid] {}", msg);
        if (bridgeWriter != null) bridgeWriter.println(line);
    }

    public static void logBridgeError(String msg) {
        String line = timestamp() + "[SableBridge/ERROR] " + msg;
        SableAndroid.LOGGER.error("[SableAndroid] {}", msg);
        if (bridgeWriter != null) bridgeWriter.println(line);
    }

    public static void logSable(String msg) {
        String line = timestamp() + "[Sable] " + msg;
        SableAndroid.LOGGER.info("[Sable] {}", msg);
        if (sableWriter != null) sableWriter.println(line);
    }

    public static void logSableWarn(String msg) {
        String line = timestamp() + "[Sable/WARN] " + msg;
        SableAndroid.LOGGER.warn("[Sable] {}", msg);
        if (sableWriter != null) sableWriter.println(line);
    }

    public static void logRender(String msg) {
        String line = timestamp() + "[Render] " + msg;
        SableAndroid.LOGGER.info("[Render] {}", msg);
        if (renderWriter != null) renderWriter.println(line);
    }

    public static void logRenderWarn(String msg) {
        String line = timestamp() + "[Render/WARN] " + msg;
        SableAndroid.LOGGER.warn("[Render] {}", msg);
        if (renderWriter != null) renderWriter.println(line);
    }

    public static void flush() {
        if (bridgeWriter != null) bridgeWriter.flush();
        if (sableWriter != null) sableWriter.flush();
        if (renderWriter != null) renderWriter.flush();
    }

    public static void close() {
        flush();
        if (bridgeWriter != null) bridgeWriter.close();
        if (sableWriter != null) sableWriter.close();
        if (renderWriter != null) renderWriter.close();
    }
}
