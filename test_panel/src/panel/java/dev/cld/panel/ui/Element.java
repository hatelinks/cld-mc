package dev.cld.panel.ui;

import dev.cld.panel.Keys;
import dev.cld.panel.PanelConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

// Строка внутри окна: кнопка, надпись или поле ввода.
public abstract class Element {
    public static final int H = 12;
    // Для кнопок: под этим именем хранится бинд клавиши, ставит Window.add
    public String bindId;

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

    public boolean chr(char c) {
        return false;
    }

    // Кнопка: действие или переключатель (подсвечена, когда on() истинно)
    public static class Button extends Element {
        final Supplier<String> label;
        final BooleanSupplier on;
        final Consumer<Integer> action;

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
            int bg = active ? Theme.accent(0xC0) : hover ? 0x40FFFFFF : 0x20FFFFFF;
            g.fill(x + 1, y + 1, x + w - 1, y + H - 1, bg);
            g.drawString(font, label.get(), x + 4, y + 2, active ? 0xFFFFFFFF : 0xFFD0D0D0, true);
            Integer key = PanelConfig.get().binds.get(bindId);
            if (key != null) {
                String k = "[" + Keys.name(key) + "]";
                g.drawString(font, k, x + w - 4 - font.width(k), y + 2, 0xFF909090, false);
            }
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
}
