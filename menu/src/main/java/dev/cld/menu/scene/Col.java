package dev.cld.menu.scene;

// Цвета в виде 0xRRGGBB, смешивание и яркость
public final class Col {
    private Col() {}

    public static int mix(int a, int b, float k) {
        if (k <= 0) return a;
        if (k >= 1) return b;
        int r = (int) ((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * k);
        int g = (int) ((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * k);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * k);
        return r << 16 | g << 8 | bl;
    }

    public static int scale(int c, float k) {
        int r = Math.min(255, Math.max(0, (int) ((c >> 16 & 255) * k)));
        int g = Math.min(255, Math.max(0, (int) ((c >> 8 & 255) * k)));
        int b = Math.min(255, Math.max(0, (int) ((c & 255) * k)));
        return r << 16 | g << 8 | b;
    }

    // умножение по каналам, например свет на цвет
    public static int mul(int c, int light) {
        int r = (c >> 16 & 255) * (light >> 16 & 255) / 255;
        int g = (c >> 8 & 255) * (light >> 8 & 255) / 255;
        int b = (c & 255) * (light & 255) / 255;
        return r << 16 | g << 8 | b;
    }

    public static int grey(int c, float k) {
        int l = luma(c);
        return mix(c, l << 16 | l << 8 | l, k);
    }

    public static int luma(int c) {
        return ((c >> 16 & 255) * 77 + (c >> 8 & 255) * 151 + (c & 255) * 28) >> 8;
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static float smooth(float x) {
        x = clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
