package dev.cld.panel.ui;

// Цвета в духе Nodus: тёмные полупрозрачные окна, цветной заголовок.
public final class Theme {
    // Цвета заголовков: левый клик по цвету берёт следующий, правый предыдущий
    public static final int[] COLORS = {0x2E6DB4, 0x2E8B57, 0xB22222, 0x7B3FBF, 0xD08A10, 0x158C8C, 0x555555};
    public static final int BODY = 0xB0101010;
    static int current = COLORS[0];

    private Theme() {}

    public static int accent(int alpha) {
        return (alpha << 24) | (current & 0xFFFFFF);
    }

    public static int color(int index) {
        return COLORS[Math.floorMod(index, COLORS.length)];
    }
}
