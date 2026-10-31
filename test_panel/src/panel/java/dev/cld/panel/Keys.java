package dev.cld.panel;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cld.panel.ui.Element;
import dev.cld.panel.ui.Window;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

// Бинды клавиш как в Nodus: кнопка панели или команда Baritone срабатывают по клавише в игре.
public final class Keys {
    private Keys() {}

    public static String name(int key) {
        return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
    }

    // Клавиша по имени для .bind: буква, цифра, f1..f12 или код
    public static int parse(String s) {
        s = s.trim().toLowerCase();
        if (s.length() == 1) {
            char c = Character.toUpperCase(s.charAt(0));
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) return c;
        }
        if (s.matches("f([1-9]|1[0-2])")) return GLFW.GLFW_KEY_F1 + Integer.parseInt(s.substring(1)) - 1;
        try {
            return InputConstants.getKey("key.keyboard." + s).getValue();
        } catch (Exception e) {
            return -1;
        }
    }

    static void onKey(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getAction() != GLFW.GLFW_PRESS || mc.screen != null || mc.player == null) return;
        int key = e.getKey();
        PanelConfig c = PanelConfig.get();
        for (Window w : Panel.windows())
            for (Element el : w.rows) {
                Integer k = c.binds.get(el.bindId);
                if (k != null && k == key) el.click(0, 0, 0, 0, w.width, 0);
            }
        for (Map.Entry<String, Integer> b : c.commandBinds.entrySet())
            if (b.getValue() == key) Panel.run(b.getKey());
    }

    // Команды консоли с точкой, как в Nodus: .bind клавиша команда, .unbind клавиша, .binds
    static boolean dotCommand(String line) {
        if (!line.startsWith(".")) return false;
        String[] p = line.substring(1).trim().split("\\s+", 3);
        PanelConfig c = PanelConfig.get();
        switch (p[0].toLowerCase()) {
            case "bind" -> {
                if (p.length < 3 || parse(p[1]) < 0) {
                    Panel.log("Так: .bind клавиша команда, например .bind g goto 0 100 0");
                    return true;
                }
                c.commandBinds.put(p[2], parse(p[1]));
                Panel.log("Клавиша " + name(parse(p[1])) + ": " + p[2]);
            }
            case "unbind" -> {
                int k = p.length > 1 ? parse(p[1]) : -1;
                c.commandBinds.values().removeIf(v -> v == k);
                c.binds.values().removeIf(v -> v == k);
                Panel.log(k < 0 ? "Так: .unbind клавиша" : "Клавиша " + name(k) + " свободна");
            }
            case "binds" -> {
                if (c.binds.isEmpty() && c.commandBinds.isEmpty()) Panel.log("Биндов нет");
                c.binds.forEach((id, k) -> Panel.log(name(k) + ": кнопка " + id));
                c.commandBinds.forEach((cmd, k) -> Panel.log(name(k) + ": " + cmd));
            }
            default -> Panel.log("Есть .bind, .unbind, .binds");
        }
        c.save();
        return true;
    }
}
