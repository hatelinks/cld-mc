package dev.sableandroid;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

// Мод только подменяет нативку Rapier на Android и пишет свои логи.
// Сама подмена и прореживание тиков делаются coremod-скриптом sable_android_transformer.js.
@Mod(SableAndroid.MOD_ID)
public class SableAndroid {
    public static final String MOD_ID = "sable_android";
    public static final Logger LOGGER = LoggerFactory.getLogger("SableAndroid");
    public static final TickThrottleConfig THROTTLE_CONFIG = new TickThrottleConfig();

    public SableAndroid(IEventBus modBus) {
        SableBridgeLogger.init();
        THROTTLE_CONFIG.loadOrCreate();
        // строка версии осталась от 1.2.0, в jar 2.0.0 она такая же
        SableBridgeLogger.logBridge("Sable Android Support v1.2.0 (NeoForge) loaded");
        SableBridgeLogger.logBridge("Platform     : " + (isAndroid() ? "Android" : "PC/Desktop"));
        SableBridgeLogger.logBridge("OS Name      : " + System.getProperty("os.name", "unknown"));
        SableBridgeLogger.logBridge("OS Version   : " + System.getProperty("os.version", "unknown"));
        SableBridgeLogger.logBridge("OS Arch      : " + System.getProperty("os.arch", "unknown"));
        SableBridgeLogger.logBridge("Java Vendor  : " + System.getProperty("java.vendor", "unknown"));
        SableBridgeLogger.logBridge("Java Version : " + System.getProperty("java.version", "unknown"));
        SableBridgeLogger.logBridge("Throttle Config: " + THROTTLE_CONFIG.getAll().size() + " classes configured");

        if (!isAndroid()) {
            SableBridgeLogger.logBridge("Running on PC - native redirect inactive");
        }
    }

    public static void logNativeLoad(String originalPath, long sizeKb) {
        SableBridgeLogger.logSable("Native intercepted: " + originalPath);
        SableBridgeLogger.logSable("Extracted Android native (" + sizeKb + "KB) - loading...");
    }

    public static void logNativeSuccess() {
        SableBridgeLogger.logSable("Rapier native loaded successfully");
    }

    public static void logNativeError(String message) {
        SableBridgeLogger.logSableWarn("Native load error: " + message);
    }

    // GL11 через рефлексию, чтобы класс не грузился на сервере
    public static void logRendererInfo() {
        try {
            Class<?> gl = Class.forName("org.lwjgl.opengl.GL11");
            Method getString = gl.getMethod("glGetString", int.class);
            String version = (String) getString.invoke(null, 7938);
            String renderer = (String) getString.invoke(null, 7937);
            String vendor = (String) getString.invoke(null, 7936);
            SableBridgeLogger.logRender("GL Vendor   : " + vendor);
            SableBridgeLogger.logRender("GL Renderer : " + renderer);
            SableBridgeLogger.logRender("GL Version  : " + version);
        } catch (Exception e) {
            SableBridgeLogger.logRenderWarn("Could not query GL renderer info: " + e.getMessage());
        }
    }

    public static boolean isAndroid() {
        String osVersion = System.getProperty("os.version", "").toLowerCase();
        String vendor = System.getProperty("java.vendor", "").toLowerCase();
        String vm = System.getProperty("java.vm.name", "").toLowerCase();
        if (osVersion.contains("android")) return true;
        if (vendor.contains("android")) return true;
        if (vm.contains("android")) return true;
        try {
            Class.forName("android.os.Build");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
