package dev.sableandroid;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

// Сюда coremod перенаправляет System.load из Rapier3D.loadLibrary()
public class SableAndroidLoader {
    private static final String ANDROID_NATIVE = "/natives/sable_rapier/sable_rapier_aarch64_android.so";

    public static void load(String originalPath) {
        if (!SableAndroid.isAndroid()) {
            SableBridgeLogger.logSable("PC environment - using original native: " + originalPath);
            System.load(originalPath);
            return;
        }

        SableBridgeLogger.logSable("Android environment - intercepting native load");
        SableBridgeLogger.logSable("Original path: " + originalPath);

        try (InputStream in = SableAndroidLoader.class.getResourceAsStream(ANDROID_NATIVE)) {
            if (in == null) {
                SableBridgeLogger.logSableWarn("Android native not found in JAR: " + ANDROID_NATIVE);
                SableBridgeLogger.logSableWarn("Falling back to original path");
                System.load(originalPath);
                return;
            }
            // нативку из jar грузить нельзя, копируем во временный файл
            Path tmp = Files.createTempFile("sable_rapier_android", ".so");
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            long sizeKb = Files.size(tmp) / 1024L;
            SableAndroid.logNativeLoad(originalPath, sizeKb);
            System.load(tmp.toAbsolutePath().toString());
            SableAndroid.logNativeSuccess();
            SableAndroid.logRendererInfo();
            SableBridgeLogger.flush();
        } catch (IOException e) {
            SableAndroid.logNativeError(e.getMessage());
            SableBridgeLogger.flush();
            throw new RuntimeException("[SableAndroid] Failed to load Android Rapier native", e);
        }
    }
}
