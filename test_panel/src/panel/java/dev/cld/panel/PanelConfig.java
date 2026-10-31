package dev.cld.panel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Настройки панели в config/cld_test_panel.json: раскладка окон, масштаб, свои серверы.
public final class PanelConfig {
    public static final class Win {
        public int x, y;
        public boolean collapsed, pinned;
        public int color;
    }

    // Размер панели относительно "Размера интерфейса" игры: 1 значит как у остального меню
    public double size = 1.0;
    // Свои серверы (адрес как в списке серверов), на них работает всё
    public List<String> servers = new ArrayList<>();
    public Map<String, Win> windows = new LinkedHashMap<>();
    public List<String> history = new ArrayList<>();
    // Бинды как в Nodus: кнопка окна (id окна.номер строки) или команда Baritone на клавишу
    public Map<String, Integer> binds = new LinkedHashMap<>();
    public Map<String, Integer> commandBinds = new LinkedHashMap<>();
    // Функции: скорость полёта (во сколько раз быстрее обычного), скорость свободной камеры (блоков за тик)
    public int flySpeed = 2;
    public double camSpeed = 1.0;
    // Xray и поиск блоков: радиус в блоках и запросы поиска
    public int espRadius = 64;
    public List<String> search = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PanelConfig instance;

    public static PanelConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("cld_test_panel.json");
    }

    private static PanelConfig load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                PanelConfig c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), PanelConfig.class);
                if (c != null) {
                    if (c.servers == null) c.servers = new ArrayList<>();
                    if (c.windows == null) c.windows = new LinkedHashMap<>();
                    if (c.history == null) c.history = new ArrayList<>();
                    if (c.binds == null) c.binds = new LinkedHashMap<>();
                    if (c.commandBinds == null) c.commandBinds = new LinkedHashMap<>();
                    if (c.search == null) c.search = new ArrayList<>();
                    if (c.flySpeed < 1 || c.flySpeed > 10) c.flySpeed = 2;
                    if (c.camSpeed < 0.25 || c.camSpeed > 5) c.camSpeed = 1.0;
                    if (c.espRadius < 16 || c.espRadius > 160) c.espRadius = 64;
                    if (c.size < 0.5 || c.size > 2) c.size = 1.0;
                    return c;
                }
            }
        } catch (Exception e) {
            Panel.LOG.warn("cld_test_panel: не прочитался конфиг, беру по умолчанию", e);
        }
        return new PanelConfig();
    }

    public void save() {
        try {
            Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Panel.LOG.warn("cld_test_panel: не записался конфиг", e);
        }
    }

    public Win win(String id, int x, int y, int color) {
        return windows.computeIfAbsent(id, k -> {
            Win w = new Win();
            w.x = x;
            w.y = y;
            w.color = color;
            return w;
        });
    }
}
