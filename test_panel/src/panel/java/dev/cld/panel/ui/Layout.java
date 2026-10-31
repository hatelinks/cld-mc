package dev.cld.panel.ui;

import java.util.ArrayList;
import java.util.List;

// Раскладка окон без наложений: колонками слева направо, а на маленьком экране ряды заголовков как в Nodus.
// Нужна при крупном размере интерфейса (guiScale 6), где экран в координатах GUI маленький.
public final class Layout {
    private static final int GAP = 2;

    private Layout() {}

    // bottom: нижняя граница, под ней строка подсказки панели
    public static void arrange(List<Window> windows, int sw, int bottom) {
        if (allOpen(windows, sw, bottom)) return;
        // Все раскрытыми не влезают (маленький экран в координатах GUI): как в Nodus, сверху ряды
        // заголовков свёрнутых окон, под ними раскрытые. Раскрываем по порядку те, кому хватает места
        List<Window> open = new ArrayList<>();
        for (Window w : windows) {
            List<Window> trial = new ArrayList<>(open);
            trial.add(w);
            if (place(windows, trial, sw, bottom)) open = trial;
        }
        place(windows, open, sw, bottom);
    }

    // Ряды заголовков для свёрнутых, свободные места для раскрытых; false, если раскрытым места не хватило
    private static boolean place(List<Window> windows, List<Window> open, int sw, int bottom) {
        List<int[]> placed = new ArrayList<>();
        int x = GAP, y = GAP;
        for (Window w : windows) {
            if (open.contains(w)) continue;
            w.state.collapsed = true;
            if (x + w.width > sw - GAP && x > GAP) {
                x = GAP;
                y += Window.TITLE + GAP;
            }
            w.state.x = x;
            w.state.y = y;
            placed.add(new int[]{x, y, w.width, Window.TITLE});
            x += w.width + GAP;
        }
        for (Window w : open) {
            w.state.collapsed = false;
            int[] at = find(placed, w.width, w.height(), sw, bottom);
            if (at == null) return false;
            w.state.x = at[0];
            w.state.y = at[1];
            placed.add(new int[]{at[0], at[1], w.width, w.height()});
        }
        return true;
    }

    // Пробуем разложить всё раскрытым; если хоть одно не влезло, false
    private static boolean allOpen(List<Window> windows, int sw, int bottom) {
        List<int[]> placed = new ArrayList<>();
        for (Window w : windows) {
            w.state.collapsed = false;
            int[] at = find(placed, w.width, w.height(), sw, bottom);
            if (at == null) return false;
            w.state.x = at[0];
            w.state.y = at[1];
            placed.add(new int[]{at[0], at[1], w.width, w.height()});
        }
        return true;
    }

    // Окно только что раскрыли: если оно налезает на другие, переносим на свободное место, если такое есть
    public static void fitOpened(Window w, List<Window> all, int sw, int bottom) {
        List<int[]> others = new ArrayList<>();
        for (Window o : all) if (o != w) others.add(new int[]{o.state.x, o.state.y, o.width, o.height()});
        if (w.state.y + w.height() <= bottom && free(others, w.state.x, w.state.y, w.width, w.height())) return;
        int[] at = find(others, w.width, w.height(), sw, bottom);
        if (at == null) return;
        w.state.x = at[0];
        w.state.y = at[1];
    }

    // Первое свободное место: сначала вниз по колонке, потом следующая колонка
    private static int[] find(List<int[]> placed, int w, int h, int sw, int bottom) {
        for (int x = GAP; x + w <= sw - GAP; x += GAP) {
            for (int y = GAP; y + h <= bottom; y += GAP) {
                if (free(placed, x, y, w, h)) return new int[]{x, y};
            }
        }
        return null;
    }

    private static boolean free(List<int[]> placed, int x, int y, int w, int h) {
        for (int[] r : placed) {
            if (x < r[0] + r[2] + GAP && x + w + GAP > r[0] && y < r[1] + r[3] + GAP && y + h + GAP > r[1]) return false;
        }
        return true;
    }
}
