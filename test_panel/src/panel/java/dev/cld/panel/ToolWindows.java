package dev.cld.panel;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.cache.IWaypoint;
import baritone.api.cache.IWaypointCollection;
import baritone.api.cache.Waypoint;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.SettingsUtil;
import dev.cld.panel.ui.Element.Button;
import dev.cld.panel.ui.Element.Input;
import dev.cld.panel.ui.Element.Label;
import dev.cld.panel.ui.Element.ListView;
import dev.cld.panel.ui.Element.ListView.Row;
import dev.cld.panel.ui.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;

// Окна второго этапа: все настройки Baritone, путевые точки, радар.
final class ToolWindows {
    private static final String[] RADAR_MODES = {"все", "враги", "игроки", "мирные"};
    private static int radarMode;
    // Краткие описания настроек Baritone по-русски: имя, табуляция, текст
    private static Map<String, String> descriptions;

    private ToolWindows() {}

    // Все настройки Baritone списком: поиск, ЛКМ переключить или править, ПКМ вернуть по умолчанию
    static Window settings(PanelConfig c) {
        Window w = new Window("settings", "Настройки Baritone", 210, c.win("settings", 442, 4, 3));
        Settings s = BaritoneAPI.getSettings();
        Input search = new Input("поиск по имени", true, t -> {});
        Input edit = new Input("имя значение, Enter: задать", false, t -> Panel.run("set " + t));
        w.add(search);
        w.add(new ListView(15, () -> {
            String q = search.value().trim().toLowerCase(Locale.ROOT);
            List<Row> rows = new ArrayList<>();
            List<Settings.Setting<?>> all = new ArrayList<>(s.allSettings);
            all.sort(Comparator.comparing(Settings.Setting::getName));
            for (Settings.Setting<?> st : all) {
                if (st.isJavaOnly() || (!q.isEmpty() && !st.getName().toLowerCase(Locale.ROOT).contains(q))) continue;
                rows.add(settingRow(s, st, edit));
            }
            return rows;
        }));
        w.add(edit);
        w.add(new Label(() -> "ЛКМ: вкл/выкл или править, ПКМ: по умолчанию"));
        return w;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Row settingRow(Settings s, Settings.Setting st, Input edit) {
        String val;
        try {
            val = SettingsUtil.settingValueToString(st);
        } catch (Exception e) {
            val = String.valueOf(st.value);
        }
        boolean bool = st.getValueClass() == Boolean.class;
        boolean changed = !java.util.Objects.equals(st.value, st.defaultValue);
        int color = bool ? (Boolean.TRUE.equals(st.value) ? 0xFF70E070 : 0xFFA0A0A0) : changed ? 0xFFFFD060 : 0xFFE0E0E0;
        String name = st.getName();
        String shown = val;
        // текст подсказки собирается только для строки под мышью
        java.util.function.Supplier<String> tip = () -> {
            String d = describe(name);
            return name + (d == null ? "" : ": " + d) + "\nПо умолчанию: " + defaultText(st);
        };
        return new Row(name + " = " + shown, color, b -> {
            if (b == 1) {
                st.reset();
                SettingsUtil.save(s);
                Panel.log(name + ": по умолчанию");
            } else if (bool) {
                st.value = !Boolean.TRUE.equals(st.value);
                SettingsUtil.save(s);
                Panel.log(name + " = " + st.value);
            } else {
                edit.edit(name + " " + shown);
            }
        }, tip);
    }

    private static String defaultText(Settings.Setting<?> st) {
        try {
            return SettingsUtil.settingDefaultToString(st);
        } catch (Exception e) {
            return String.valueOf(st.defaultValue);
        }
    }

    static String describe(String name) {
        if (descriptions == null) {
            Map<String, String> m = new HashMap<>();
            try (InputStream in = ToolWindows.class.getResourceAsStream("/assets/cld_test_panel/settings_ru.tsv")) {
                if (in != null) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    for (String line; (line = r.readLine()) != null; ) {
                        int t = line.indexOf('\t');
                        if (t > 0) m.put(line.substring(0, t).trim(), line.substring(t + 1).trim());
                    }
                }
            } catch (Exception e) {
                Panel.log("Нет описаний настроек: " + e);
            }
            descriptions = m;
        }
        return descriptions.get(name);
    }

    // Путевые точки Baritone для текущего мира
    static Window waypoints(PanelConfig c) {
        Window w = new Window("waypoints", "Путевые точки", 170, c.win("waypoints", 140, 110, 4));
        w.add(new Input("имя, Enter: точка здесь", false, name -> {
            IWaypointCollection wc = collection();
            Player p = Minecraft.getInstance().player;
            if (wc == null || p == null) return;
            wc.addWaypoint(new Waypoint(name, IWaypoint.Tag.USER, new BetterBlockPos(p.blockPosition())));
            Panel.log("Точка " + name + " сохранена");
        }));
        w.add(new ListView(8, () -> {
            IWaypointCollection wc = collection();
            Player p = Minecraft.getInstance().player;
            List<Row> rows = new ArrayList<>();
            if (wc == null || p == null) return rows;
            List<IWaypoint> list = new ArrayList<>(wc.getAllWaypoints());
            list.sort(Comparator.comparingDouble(wp -> wp.getLocation().distSqr(p.blockPosition())));
            for (IWaypoint wp : list) {
                BetterBlockPos l = wp.getLocation();
                String name = wp.getName().isEmpty() ? wp.getTag().getName() : wp.getName();
                int dist = (int) Math.sqrt(l.distSqr(p.blockPosition()));
                rows.add(new Row(name + "  " + l.x + " " + l.y + " " + l.z + "  " + dist + "м", 0xFFE0E0E0, b -> {
                    if (b == 0) {
                        Panel.log("Иду к " + name);
                        Panel.baritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(l));
                    } else if (Screen.hasShiftDown()) {
                        wc.removeWaypoint(wp);
                        Panel.log("Точка " + name + " удалена");
                    } else {
                        teleport(l.x + 0.5, l.y, l.z + 0.5);
                    }
                }));
            }
            return rows;
        }));
        w.add(new Label(() -> "ЛКМ идти, ПКМ телепорт, Shift+ПКМ удалить"));
        return w;
    }

    private static IWaypointCollection collection() {
        if (Minecraft.getInstance().player == null) return null;
        try {
            var world = Panel.baritone().getWorldProvider().getCurrentWorld();
            return world == null ? null : world.getWaypoints();
        } catch (Exception e) {
            return null;
        }
    }

    // Телепорт через команду /tp: только в одиночной игре и на своём сервере
    static void teleport(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!Panel.full()) {
            Panel.log("Телепорт только в одиночной игре и на своём сервере");
            return;
        }
        mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f", x, y, z));
    }

    // Радар как Text Radar в Nodus: существа рядом, ближние сверху
    static Window radar(PanelConfig c) {
        Window w = new Window("radar", "Радар", 170, c.win("radar", 296, 90, 2));
        w.add(new Button(() -> "Показывать: " + RADAR_MODES[radarMode], () -> false, b -> radarMode = Math.floorMod(radarMode + (b == 1 ? -1 : 1), RADAR_MODES.length)));
        w.add(new ListView(10, () -> {
            Minecraft mc = Minecraft.getInstance();
            List<Row> rows = new ArrayList<>();
            if (mc.player == null || mc.level == null) return rows;
            List<LivingEntity> near = new ArrayList<>();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof LivingEntity le) || e == mc.player || !e.isAlive()) continue;
                if (e.distanceToSqr(mc.player) > 128 * 128) continue;
                boolean enemy = e instanceof Enemy, player = e instanceof Player;
                if (radarMode == 1 && !enemy || radarMode == 2 && !player || radarMode == 3 && (enemy || player)) continue;
                near.add(le);
            }
            near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));
            for (LivingEntity e : near) {
                int dist = (int) Math.sqrt(e.distanceToSqr(mc.player));
                int color = e instanceof Player ? 0xFF60A0FF : e instanceof Enemy ? 0xFFFF6060 : e instanceof Animal ? 0xFF80E080 : 0xFFE0E0A0;
                String text = e.getName().getString() + "  " + dist + "м " + compass(e.getX() - mc.player.getX(), e.getZ() - mc.player.getZ());
                rows.add(new Row(text, color, b -> {
                    if (b == 0) Panel.baritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(e.blockPosition()));
                    else teleport(e.getX(), e.getY(), e.getZ());
                }));
            }
            return rows;
        }));
        w.add(new Label(() -> "ЛКМ идти к нему, ПКМ телепорт"));
        return w;
    }

    // Сторона света по смещению: в Minecraft север это -Z
    private static String compass(double dx, double dz) {
        String[] n = {"Ю", "ЮЗ", "З", "СЗ", "С", "СВ", "В", "ЮВ"};
        double a = Math.toDegrees(Math.atan2(-dx, dz));
        return n[Math.floorMod((int) Math.round(a / 45), 8)];
    }
}
