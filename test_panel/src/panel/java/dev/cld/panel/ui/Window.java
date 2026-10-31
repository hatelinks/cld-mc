package dev.cld.panel.ui;

import dev.cld.panel.PanelConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

// Окно как в Nodus: заголовок (тащить), кнопки закрепить и свернуть, ниже строки.
public class Window {
    public static final int TITLE = 13;

    public final String id;
    public final String title;
    public final int width;
    public final PanelConfig.Win state;
    public final List<Element> rows = new ArrayList<>();

    public Window(String id, String title, int width, PanelConfig.Win state) {
        this.id = id;
        this.title = title;
        this.width = width;
        this.state = state;
    }

    public Window add(Element e) {
        if (e instanceof Element.Button) e.bindId = id + "." + rows.size();
        rows.add(e);
        return this;
    }

    public int height() {
        if (state.collapsed) return TITLE;
        int h = TITLE + 2;
        for (Element e : rows) h += e.height();
        return h;
    }

    public boolean inside(int mx, int my) {
        return mx >= state.x && mx < state.x + width && my >= state.y && my < state.y + height();
    }

    public boolean onTitle(int mx, int my) {
        return mx >= state.x && mx < state.x + width && my >= state.y && my < state.y + TITLE;
    }

    // hud: окно закреплено и рисуется поверх игры, без наведения
    public void render(GuiGraphics g, Font font, int mx, int my, boolean hud) {
        int x = state.x, y = state.y, w = width;
        int color = Theme.color(state.color);
        Theme.current = color;
        int h = height();
        // на экране панели фон плотный, чтобы окно сверху не просвечивало; на HUD полупрозрачный
        g.fill(x, y, x + w, y + h, hud ? Theme.BODY : 0xFF141414);
        g.fill(x, y, x + w, y + TITLE, 0xE0000000 | color);
        g.drawString(font, title, x + 4, y + 3, 0xFFFFFFFF, true);
        if (!hud) {
            // справа в заголовке: цвет, закрепить, свернуть
            box(g, font, x + w - 36, y, "c", false);
            box(g, font, x + w - 24, y, "p", state.pinned);
            box(g, font, x + w - 12, y, state.collapsed ? "+" : "-", false);
        } else if (state.pinned) {
            g.fill(x + w - 4, y + 2, x + w - 2, y + TITLE - 2, 0xFFFFFFFF);
        }
        g.renderOutline(x, y, w, h, 0x80000000);
        if (state.collapsed) return;
        int ry = y + TITLE + 1;
        Element.mouseX = mx;
        Element.mouseY = my;
        for (Element e : rows) {
            boolean hover = !hud && mx >= x && mx < x + w && my >= ry && my < ry + e.height();
            e.render(g, font, x, ry, w, hover);
            ry += e.height();
        }
    }

    private static void box(GuiGraphics g, Font font, int x, int y, String s, boolean on) {
        g.fill(x + 1, y + 2, x + 11, y + TITLE - 2, on ? 0xFFFFFFFF : 0x50000000);
        g.drawString(font, s, x + 4, y + 2, on ? 0xFF000000 : 0xFFFFFFFF, false);
    }

    // Клик по заголовку: true если попали в одну из кнопок
    public boolean clickTitle(int mx, int button) {
        int rx = mx - state.x;
        if (rx >= width - 12) {
            state.collapsed = !state.collapsed;
            return true;
        }
        if (rx >= width - 24) {
            state.pinned = !state.pinned;
            return true;
        }
        if (rx >= width - 36) {
            state.color += button == 1 ? -1 : 1;
            return true;
        }
        return false;
    }

    public Element at(int mx, int my) {
        if (state.collapsed || !inside(mx, my)) return null;
        int ry = state.y + TITLE + 1;
        for (Element e : rows) {
            if (my >= ry && my < ry + e.height()) return e;
            ry += e.height();
        }
        return null;
    }

    public boolean clickBody(int mx, int my, int button) {
        if (state.collapsed) return false;
        int ry = state.y + TITLE + 1;
        for (Element e : rows) {
            if (my >= ry && my < ry + e.height()) return e.click(mx, my, state.x, ry, width, button);
            ry += e.height();
        }
        return false;
    }
}
