package dev.cld.panel.ui;

import java.util.ArrayList;
import java.util.List;

// Раскладка окон без наложений: колонками слева направо, что не влезает, сворачивается до заголовка.
// Нужна при крупном размере интерфейса (guiScale 6), где экран в координатах GUI маленький.
public final class Layout {
    private static final int GAP = 2;

    private Layout() {}

    // bottom: нижняя граница, под ней строка подсказки панели
    public static void arrange(List<Window> windows, int sw, int bottom) {
        List<int[]> placed = new ArrayList<>();
        for (Window w : windows) {
            w.state.collapsed = false;
            int[] at = find(placed, w.width, w.height(), sw, bottom);
            if (at == null) {
                w.state.collapsed = true;
                at = find(placed, w.width, w.height(), sw, bottom);
            }
            if (at == null) at = new int[]{GAP, GAP};
            w.state.x = at[0];
            w.state.y = at[1];
            placed.add(new int[]{at[0], at[1], w.width, w.height()});
        }
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
