package dev.cld.panel;

import dev.cld.panel.ui.Element;
import dev.cld.panel.ui.Window;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

// Экран панели: все окна, перетаскивание за заголовок. Игру не ставит на паузу.
public class PanelScreen extends Screen {
    private Window drag;
    // Кнопка, которой назначаем клавишу (средний клик по кнопке)
    private Element binding;
    private int dx, dy;

    public PanelScreen() {
        super(Component.translatable("cld_test_panel.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // без затемнения и размытия, мир видно как в Nodus
    }

    private double s() {
        return Panel.scale();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        double s = s();
        int mx = (int) (mouseX / s), my = (int) (mouseY / s);
        g.pose().pushPose();
        g.pose().scale((float) s, (float) s, 1);
        if (binding != null) {
            String t = "Нажми клавишу для кнопки, Esc или Backspace убрать бинд";
            int tw = font.width(t);
            int cx = (int) (width / s) / 2;
            g.fill(cx - tw / 2 - 6, 20, cx + tw / 2 + 6, 36, 0xE0000000);
            g.drawString(font, t, cx - tw / 2, 24, 0xFFFFFF60, true);
        }
        g.drawString(font, "cld_test_panel  Y закрыть, U консоль, средний клик по кнопке: бинд" + (Panel.full() ? "" : "  (чужой сервер: только Baritone)"), 4, (int) (height / s) - 11, 0xA0FFFFFF, true);
        int sw = (int) (width / s), sh = (int) (height / s);
        for (Window w : Panel.windows()) {
            // после смены размера интерфейса окно не должно уехать за край
            w.state.x = Math.max(0, Math.min(sw - w.width, w.state.x));
            w.state.y = Math.max(0, Math.min(sh - Window.TITLE, w.state.y));
            w.render(g, font, mx, my, false);
        }
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double s = s();
        int mx = (int) (mouseX / s), my = (int) (mouseY / s);
        List<Window> ws = Panel.windows();
        for (Window w : ws) for (Element e : w.rows) e.unfocus();
        binding = null;
        for (int i = ws.size() - 1; i >= 0; i--) {
            Window w = ws.get(i);
            if (!w.inside(mx, my)) continue;
            // окно, по которому кликнули, поверх остальных
            ws.remove(i);
            ws.add(w);
            if (w.onTitle(mx, my)) {
                if (!w.clickTitle(mx, button) && button == 0) {
                    drag = w;
                    dx = mx - w.state.x;
                    dy = my - w.state.y;
                }
            } else if (button == 2) {
                Element e = w.at(mx, my);
                if (e != null && e.bindId != null) binding = e;
            } else {
                w.clickBody(mx, my, button);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double ddx, double ddy) {
        if (drag != null) {
            double s = s();
            int maxX = (int) (width / s) - drag.width, maxY = (int) (height / s) - Window.TITLE;
            drag.state.x = Math.max(0, Math.min(maxX, (int) (mouseX / s) - dx));
            drag.state.y = Math.max(0, Math.min(maxY, (int) (mouseY / s) - dy));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, ddx, ddy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        drag = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (binding != null) {
            PanelConfig c = PanelConfig.get();
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE) c.binds.remove(binding.bindId);
            else c.binds.put(binding.bindId, key);
            c.save();
            binding = null;
            return true;
        }
        for (Window w : Panel.windows()) for (Element e : w.rows) if (e.focused()) return e.key(key);
        if (Panel.OPEN.matches(key, scan)) {
            onClose();
            return true;
        }
        if (Panel.CONSOLE.matches(key, scan)) {
            minecraft.setScreen(new ConsoleScreen(this));
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
        double s = s();
        int mx = (int) (mouseX / s), my = (int) (mouseY / s);
        List<Window> ws = Panel.windows();
        for (int i = ws.size() - 1; i >= 0; i--) {
            Element e = ws.get(i).at(mx, my);
            if (e != null && e.scroll(sy)) return true;
            if (ws.get(i).inside(mx, my)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, sx, sy);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        for (Window w : Panel.windows()) for (Element e : w.rows) if (e.chr(c)) return true;
        return super.charTyped(c, mods);
    }

    @Override
    public void removed() {
        PanelConfig.get().save();
    }
}
