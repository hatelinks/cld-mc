package dev.cld.panel;

import baritone.api.BaritoneAPI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

// Консоль как в Nodus (U): команды Baritone без #, история стрелками, Tab дописывает слово.
public class ConsoleScreen extends Screen {
    private final Screen back;
    private EditBox box;
    private int hist = -1;

    public ConsoleScreen(Screen back) {
        super(Component.translatable("cld_test_panel.console"));
        this.back = back;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        box = new EditBox(font, 4, height - 16, width - 8, 12, Component.empty());
        box.setMaxLength(256);
        box.setBordered(true);
        addRenderableWidget(box);
        setInitialFocus(box);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height - 20, 0x90000000);
        List<String> lines;
        synchronized (Panel.LOG_LINES) {
            lines = new ArrayList<>(Panel.LOG_LINES);
        }
        int y = height - 32;
        for (int i = lines.size() - 1; i >= 0 && y > 2; i--, y -= 10) {
            String s = lines.get(i);
            g.drawString(font, s, 4, y, s.startsWith("> ") ? 0xFF80C0FF : 0xFFE0E0E0, true);
        }
        g.drawString(font, "Команды Baritone без #: goto x y z, mine diamond_ore, explore, stop, help", 4, 2, 0xFF909090, true);
        super.render(g, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        List<String> h = PanelConfig.get().history;
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            String cmd = box.getValue().trim();
            String prefix = BaritoneAPI.getSettings().prefix.value;
            if (cmd.startsWith(prefix)) cmd = cmd.substring(prefix.length()).trim();
            if (!cmd.isEmpty()) {
                h.remove(cmd);
                h.add(cmd);
                while (h.size() > 50) h.remove(0);
                Panel.run(cmd);
            }
            box.setValue("");
            hist = -1;
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            if (h.isEmpty()) return true;
            if (hist < 0) hist = h.size();
            hist += key == GLFW.GLFW_KEY_UP ? -1 : 1;
            hist = Math.max(0, Math.min(h.size(), hist));
            box.setValue(hist == h.size() ? "" : h.get(hist));
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            complete();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // Дописать последнее слово первым вариантом от Baritone
    private void complete() {
        if (minecraft.player == null) return;
        String v = box.getValue();
        try {
            String first = Panel.baritone().getCommandManager().tabComplete(v).findFirst().orElse(null);
            if (first == null) return;
            int sp = v.lastIndexOf(' ');
            box.setValue(v.substring(0, sp + 1) + first);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onClose() {
        PanelConfig.get().save();
        minecraft.setScreen(back);
    }
}
