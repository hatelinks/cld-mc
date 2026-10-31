package dev.cld.panel.ui;

import dev.cld.panel.Keys;
import dev.cld.panel.PanelConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

// Строка внутри окна: кнопка, надпись или поле ввода.
public abstract class Element {
    public static final int H = 12;
    // Для кнопок: под этим именем хранится бинд клавиши, ставит Window.add
    public String bindId;
    // Где мышь при отрисовке окна (в координатах панели), ставит Window.render
    static int mouseX = -1, mouseY = -1;
    // Подсказка строки под мышью, рисует PanelScreen поверх окон
    public static String tooltip;

    public int height() {
        return H;
    }

    public abstract void render(GuiGraphics g, Font font, int x, int y, int w, boolean hover);

    // button: 0 левая, 1 правая
    public boolean click(int mx, int my, int x, int y, int w, int button) {
        return false;
    }

    public boolean focused() {
        return false;
    }

    public void unfocus() {}

    public boolean key(int key) {
        return false;
    }

    // Колёсико мыши над строкой
    public boolean scroll(double amount) {
        return false;
    }

    public boolean chr(char c) {
        return false;
    }

    // Кнопка: действие или переключатель (подсвечена, когда on() истинно)
    public static class Button extends Element {
        final Supplier<String> label;
        final BooleanSupplier on;
        final Consumer<Integer> action;
        // Подсказка при наведении: что делает функция
        String tip;

        public Button tip(String t) {
            tip = t;
            return this;
        }

        public Button(Supplier<String> label, BooleanSupplier on, Consumer<Integer> action) {
            this.label = label;
            this.on = on;
            this.action = action;
        }

        public Button(String label, Runnable action) {
            this(() -> label, () -> false, b -> action.run());
        }

        @Override
        public void render(GuiGraphics g, Font font, int x, int y, int w, boolean hover) {
            boolean active = on.getAsBoolean();
            if (hover && tip != null) tooltip = tip;
            int bg = active ? Theme.accent(0xC0) : hover ? 0x40FFFFFF : 0x20FFFFFF;
            g.fill(x + 1, y + 1, x + w - 1, y + H - 1, bg);
            Integer key = PanelConfig.get().binds.get(bindId);
            String k = key == null ? null : "[" + Keys.name(key) + "]";
            // текст не вылезает за окно: обрезаем с учётом бинда справа
            int max = w - 8 - (k == null ? 0 : font.width(k) + 4);
            String s = label.get();
            if (font.width(s) > max) s = font.plainSubstrByWidth(s, max - font.width("..")) + "..";
            g.drawString(font, s, x + 4, y + 2, active ? 0xFFFFFFFF : 0xFFD0D0D0, true);
            if (k != null) g.drawString(font, k, x + w - 4 - font.width(k), y + 2, 0xFF909090, false);
        }

        @Override
        public boolean click(int mx, int my, int x, int y, int w, int button) {
            action.accept(button);
            return true;
        }
    }

    // Надпись, текст считается каждый кадр
    public static class Label extends Element {
        final Supplier<String> text;

        public Label(Supplier<String> text) {
            this.text = text;
        }

        @Override
        public void render(GuiGraphics g, Font font, int x, int y, int w, boolean hover) {
            String s = text.get();
            if (font.width(s) > w - 6) s = font.plainSubstrByWidth(s, w - 10) + "..";
            g.drawString(font, s, x + 4, y + 2, 0xFFE0E0E0, true);
        }
    }

    // Поле ввода: Enter отдаёт текст в onEnter
    public static class Input extends Element {
        final String hint;
        final Consumer<String> onEnter;
        final boolean keep;
        String text = "";
        boolean focus;

        public String value() {
            return text;
        }

        // Подставить текст и сразу дать печатать дальше
        public void edit(String t) {
            text = t;
            focus = true;
        }

        public Input(String hint, boolean keep, Consumer<String> onEnter) {
            this.hint = hint;
            this.keep = keep;
            this.onEnter = onEnter;
        }

        @Override
        public void render(GuiGraphics g, Font font, int x, int y, int w, boolean hover) {
            g.fill(x + 1, y + 1, x + w - 1, y + H - 1, focus ? 0x60000000 : 0x40000000);
            if (focus) g.renderOutline(x + 1, y + 1, w - 2, H - 2, Theme.accent(0xFF));
            String s = text.isEmpty() && !focus ? hint : text;
            int max = w - 10;
            // длинный текст показываем с конца, чтобы было видно, что печатаешь
            while (s.length() > 1 && font.width(s) > max) s = s.substring(1);
            boolean blink = focus && (System.currentTimeMillis() / 500) % 2 == 0;
            g.drawString(font, s + (blink ? "_" : ""), x + 4, y + 2, text.isEmpty() && !focus ? 0xFF808080 : 0xFFFFFFFF, true);
        }

        @Override
        public boolean click(int mx, int my, int x, int y, int w, int button) {
            if (button == 1) text = "";
            focus = true;
            return true;
        }

        @Override
        public boolean focused() {
            return focus;
        }

        @Override
        public void unfocus() {
            focus = false;
        }

        @Override
        public boolean key(int key) {
            if (!focus) return false;
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!text.isEmpty()) text = text.substring(0, text.length() - 1);
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                String t = text.trim();
                if (!keep) text = "";
                focus = false;
                if (!t.isEmpty()) onEnter.accept(t);
            } else if (key == GLFW.GLFW_KEY_ESCAPE) {
                focus = false;
            }
            return true;
        }

        @Override
        public boolean chr(char c) {
            if (!focus || c < 32) return false;
            if (text.length() < 200) text += c;
            return true;
        }
    }

    // Список со своей прокруткой: настройки, путевые точки, радар
    public static class ListView extends Element {
        public record Row(String text, int color, Consumer<Integer> click, Supplier<String> tip) {
            public Row(String text, int color, Consumer<Integer> click) {
                this(text, color, click, null);
            }
        }

        final Supplier<List<Row>> rows;
        final int visible;
        int offset;

        public ListView(int visible, Supplier<List<Row>> rows) {
            this.visible = visible;
            this.rows = rows;
        }

        @Override
        public int height() {
            return visible * H;
        }

        @Override
        public void render(GuiGraphics g, Font font, int x, int y, int w, boolean hover) {
            List<Row> list = rows.get();
            offset = Math.max(0, Math.min(offset, list.size() - visible));
            boolean bar = list.size() > visible;
            int tw = w - (bar ? 4 : 0);
            if (list.isEmpty()) g.drawString(font, "пусто", x + 4, y + 2, 0xFF808080, true);
            for (int i = 0; i < visible && offset + i < list.size(); i++) {
                Row r = list.get(offset + i);
                int ry = y + i * H;
                if (hover && mouseY >= ry && mouseY < ry + H && mouseX < x + tw) {
                    g.fill(x + 1, ry, x + tw - 1, ry + H, 0x30FFFFFF);
                    if (r.tip() != null) tooltip = r.tip().get();
                }
                String s = r.text();
                if (font.width(s) > tw - 6) s = font.plainSubstrByWidth(s, tw - 10) + "..";
                g.drawString(font, s, x + 4, ry + 2, r.color(), true);
            }
            if (bar) {
                int h = visible * H;
                int th = Math.max(6, h * visible / list.size());
                int ty = y + (h - th) * offset / Math.max(1, list.size() - visible);
                g.fill(x + w - 3, y, x + w - 1, y + h, 0x40FFFFFF);
                g.fill(x + w - 3, ty, x + w - 1, ty + th, 0xC0FFFFFF);
            }
        }

        @Override
        public boolean click(int mx, int my, int x, int y, int w, int button) {
            List<Row> list = rows.get();
            int i = offset + (my - y) / H;
            if (i >= 0 && i < list.size() && list.get(i).click() != null) list.get(i).click().accept(button);
            return true;
        }

        @Override
        public boolean scroll(double amount) {
            offset -= (int) Math.signum(amount) * 3;
            if (offset < 0) offset = 0;
            return true;
        }
    }
}
