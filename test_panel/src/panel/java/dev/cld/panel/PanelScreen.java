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
        g.drawString(font, "cld_test_panel  Y закрыть, U консоль" + (Panel.full() ? "" : "  (чужой сервер: только Baritone)"), 4, (int) (height / s) - 11, 0xA0FFFFFF, true);
        for (Window w : Panel.windows()) w.render(g, font, mx, my, false);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double s = s();
        int mx = (int) (mouseX / s), my = (int) (mouseY / s);
        List<Window> ws = Panel.windows();
        for (Window w : ws) for (Element e : w.rows) e.unfocus();
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
    public boolean charTyped(char c, int mods) {
        for (Window w : Panel.windows()) for (Element e : w.rows) if (e.chr(c)) return true;
        return super.charTyped(c, mods);
    }

    @Override
    public void removed() {
        PanelConfig.get().save();
    }
}
