package dev.cld.menu.scene;

// Картинка в памяти: пейзаж рисуется сюда, потом одной текстурой на экран
public final class Raster {
    public final int w, h;
    public final int[] px;

    public Raster(int w, int h) {
        this.w = w;
        this.h = h;
        this.px = new int[w * h];
    }

    public void fill(int x0, int y0, int x1, int y1, int rgb) {
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        x1 = Math.min(w, x1);
        y1 = Math.min(h, y1);
        for (int y = y0; y < y1; y++) {
            int o = y * w;
            java.util.Arrays.fill(px, o + x0, o + Math.max(x0, x1), rgb);
        }
    }

    // a от 0 до 255
    public void blend(int x0, int y0, int x1, int y1, int rgb, int a) {
        if (a <= 0) return;
        if (a >= 255) {
            fill(x0, y0, x1, y1, rgb);
            return;
        }
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        x1 = Math.min(w, x1);
        y1 = Math.min(h, y1);
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255, na = 255 - a;
        for (int y = y0; y < y1; y++) {
            int o = y * w;
            for (int x = x0; x < x1; x++) {
                int c = px[o + x];
                px[o + x] = ((c >> 16 & 255) * na + r * a) / 255 << 16
                        | ((c >> 8 & 255) * na + g * a) / 255 << 8
                        | ((c & 255) * na + b * a) / 255;
            }
        }
    }

    public void dot(int x, int y, int rgb, int a) {
        if (x < 0 || y < 0 || x >= w || y >= h || a <= 0) return;
        int i = y * w + x;
        if (a >= 255) {
            px[i] = rgb;
            return;
        }
        int c = px[i], na = 255 - a;
        px[i] = ((c >> 16 & 255) * na + (rgb >> 16 & 255) * a) / 255 << 16
                | ((c >> 8 & 255) * na + (rgb >> 8 & 255) * a) / 255 << 8
                | ((c & 255) * na + (rgb & 255) * a) / 255;
    }

    public int get(int x, int y) {
        return px[y * w + x];
    }
}
