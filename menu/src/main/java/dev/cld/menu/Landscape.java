package dev.cld.menu;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Пиксельный пейзаж в духе Террарии: слои гор с параллаксом, смена суток,
 * волны тумана и грозы с дождём и молниями. Рельеф медленно едет и меняет форму.
 */
public final class Landscape {
    private static final double DAY_SECONDS = 240;     // полные сутки
    private static final double WEATHER_SECONDS = 150; // цикл туман → гроза → затишье
    private static final double START_PHASE = 0.62;    // начинаем ближе к вечеру
    private static final int PX = 2;                   // размер «пикселя» пейзажа
    private static final long START = Util.getMillis();

    // фазы суток: полночь, рассвет, полдень, закат
    private static final int[] SKY_TOP = {0x050813, 0x2B2F5E, 0x4F86C6, 0x3B2A5A};
    private static final int[] SKY_LOW = {0x1A2440, 0xE0855A, 0xA9CDEA, 0xF08A4B};
    // слои гор, от дальнего к ближнему
    private static final int[] HILL = {0x4A6478, 0x355364, 0x26443F, 0x172B22};
    private static final float[] BASE = {0.50f, 0.58f, 0.67f, 0.79f};
    private static final float[] AMP = {0.26f, 0.20f, 0.13f, 0.07f};
    private static final float[] SPEED = {1.5f, 4f, 9f, 16f};
    private static final float[] FREQ = {0.006f, 0.009f, 0.014f, 0.022f};

    private Landscape() {}

    public static void render(GuiGraphics g, int w, int h, float fade) {
        double t = (Util.getMillis() - START) / 1000.0;
        double phase = (t / DAY_SECONDS + START_PHASE) % 1.0;
        float day = (float) Math.max(0, Math.sin((phase - 0.25) * Math.PI * 2) * 0.5 + 0.5); // 0 ночь, 1 полдень
        float light = 0.34f + 0.66f * smooth(day);
        float storm = clamp((float) (0.45 + 0.75 * Math.sin(t / WEATHER_SECONDS * Math.PI * 2)), 0, 1);
        float fog = clamp(0.3f + 0.6f * storm, 0, 1);
        float flash = lightning(t, storm);

        int top = sky(SKY_TOP, phase), low = sky(SKY_LOW, phase);
        top = overcast(top, storm * 0.8f);
        low = overcast(low, storm * 0.7f);
        top = mix(top, 0xFFFFFF, flash * 0.5f);
        low = mix(low, 0xFFFFFF, flash * 0.5f);
        int horizon = (int) (h * 0.62f);
        g.fillGradient(0, 0, w, horizon, 0xFF000000 | top, 0xFF000000 | low);
        g.fill(0, horizon, w, h, 0xFF000000 | low);

        stars(g, w, horizon, t, (1 - smooth(day) * 1.6f) * (1 - storm));
        celestial(g, w, horizon, phase, storm);
        clouds(g, w, h, t, storm, light, low);
        if (flash > 0.05f) bolt(g, w, horizon, t, flash);

        for (int k = 0; k < HILL.length; k++) {
            float depthFog = (1f - k / (float) HILL.length) * 0.65f + fog * 0.25f;
            int c = mix(HILL[k], low, clamp(depthFog, 0, 0.9f));
            c = scale(c, Math.min(1f, light + flash * 0.6f));
            hills(g, w, h, t, k, c);
            if (k == 1 || k == 2) {
                int a = (int) (fog * 120);
                int y0 = (int) (h * BASE[k + 1]) - (int) (h * 0.08f);
                g.fillGradient(0, y0, w, h, low & 0xFFFFFF, (a << 24) | (low & 0xFFFFFF));
            }
        }
        rain(g, w, h, t, storm);
        if (flash > 0.05f) g.fill(0, 0, w, h, ((int) (flash * 70) << 24) | 0xFFFFFF);
        if (fade < 1f) g.fill(0, 0, w, h, ((int) ((1 - fade) * 255) << 24));
    }

    // ---------------------------------------------------------------- слои

    private static void hills(GuiGraphics g, int w, int h, double t, int k, int colour) {
        double scroll = t * SPEED[k];
        int grass = scale(colour, 1.25f);
        int dark = scale(colour, 0.8f);
        int c = 0xFF000000 | colour;
        for (int x = 0; x < w; x += PX) {
            double wx = x + scroll + k * 1000;
            int y = height(wx, t, k, h);
            g.fill(x, y, x + PX, h, c);
            if (k >= 2) {
                g.fill(x, y, x + PX, y + PX, 0xFF000000 | grass);
                // деревья: ели на ближних слоях
                long cell = (long) Math.floor(wx / 14);
                if (hash(cell, k) < (k == 3 ? 0.3 : 0.18) && Math.floorMod((long) Math.floor(wx), 14) < PX) {
                    tree(g, x, height(cell * 14.0 + 7, t, k, h), k == 3 ? 1f : 0.65f, 0xFF000000 | dark);
                }
            }
        }
    }

    private static int height(double wx, double t, int k, int h) {
        double f = FREQ[k];
        double n = fbm(wx * f, k) * 0.75 + fbm(wx * f * 0.5 + t * 0.004 * (k + 1), k + 7) * 0.25; // рельеф ещё и «дышит»
        int y = (int) (h * BASE[k] - n * h * AMP[k]);
        return y - Math.floorMod(y, PX);
    }

    private static void tree(GuiGraphics g, int x, int ground, float s, int c) {
        int u = Math.max(PX, Math.round(PX * s));
        g.fill(x, ground - 3 * u, x + u, ground, c);
        for (int i = 0; i < 4; i++) {
            int half = (4 - i) * u / 2 + u / 2;
            int y = ground - 3 * u - (i + 1) * 2 * u;
            g.fill(x + u / 2 - half, y, x + u / 2 + half, y + 2 * u, c);
        }
    }

    private static void stars(GuiGraphics g, int w, int horizon, double t, float amount) {
        if (amount <= 0.02f) return;
        for (int i = 0; i < 90; i++) {
            int x = (int) (hash(i, 101) * w), y = (int) (hash(i, 202) * horizon * 0.9);
            float tw = (float) (0.6 + 0.4 * Math.sin(t * (1.5 + hash(i, 303) * 2) + i));
            int a = (int) (clamp(amount, 0, 1) * tw * 255);
            int s = hash(i, 404) < 0.15 ? PX : 1;
            g.fill(x, y, x + s, y + s, (a << 24) | 0xFFFFFF);
        }
    }

    /** Квадратные солнце и луна, как в Minecraft. */
    private static void celestial(GuiGraphics g, int w, int horizon, double phase, float storm) {
        float vis = 1f - storm * 0.85f;
        body(g, w, horizon, phase, 16, 0xFFF3A6, vis);
        body(g, w, horizon, (phase + 0.5) % 1.0, 12, 0xDCE3F0, vis * 0.9f);
    }

    private static void body(GuiGraphics g, int w, int horizon, double p, int size, int colour, float vis) {
        if (p < 0.22 || p > 0.78 || vis <= 0.02f) return;
        double q = (p - 0.22) / 0.56;
        int x = (int) (q * (w + 2 * size)) - size;
        int y = horizon - (int) (Math.sin(q * Math.PI) * horizon * 0.8) - size / 2;
        int a = (int) (vis * 255);
        g.fill(x - size / 2, y - size / 2, x + size + size / 2, y + size + size / 2, ((a / 6) << 24) | colour);
        g.fill(x, y, x + size, y + size, (a << 24) | colour);
    }

    private static void clouds(GuiGraphics g, int w, int h, double t, float storm, float light, int low) {
        int count = 5 + (int) (storm * 9);
        int base = scale(mix(0xF4F4F4, 0x3A3F46, storm), Math.max(0.35f, light));
        base = mix(base, low, 0.25f);
        int a = (int) (150 + storm * 90);
        for (int i = 0; i < count; i++) {
            double speed = 3 + hash(i, 7) * 5 + storm * 6;
            int cw = 30 + (int) (hash(i, 8) * 60);
            int x = (int) Math.floorMod((long) (hash(i, 9) * (w + 200) + t * speed), (long) (w + 200)) - 100;
            int y = (int) (h * (0.05 + hash(i, 10) * 0.3));
            y -= Math.floorMod(y, PX);
            int c = (a << 24) | base;
            g.fill(x, y, x + cw, y + 6, c);
            g.fill(x + cw / 5, y - 4, x + cw - cw / 4, y, c);
            g.fill(x + cw / 3, y - 8, x + cw / 2 + 6, y - 4, c);
            g.fill(x + 4, y + 6, x + cw - 6, y + 8, (a << 24) | scale(base, 0.85f));
        }
    }

    private static void rain(GuiGraphics g, int w, int h, double t, float storm) {
        if (storm < 0.35f) return;
        float k = (storm - 0.35f) / 0.65f;
        int n = (int) (k * w * h / 900);
        int a = (int) (60 + k * 80);
        for (int i = 0; i < n; i++) {
            double sp = 260 + hash(i, 11) * 120;
            int y = (int) Math.floorMod((long) (hash(i, 12) * h + t * sp), (long) (h + 20)) - 10;
            int x = (int) Math.floorMod((long) (hash(i, 13) * w - y * 0.25 - t * 30), (long) w);
            g.fill(x, y, x + 1, y + 6, (a << 24) | 0xB8C6D6);
        }
    }

    // ---------------------------------------------------------------- молнии

    private static final double BOLT_SLOT = 6.0;

    private static float lightning(double t, float storm) {
        if (storm < 0.6f) return 0;
        long slot = (long) Math.floor(t / BOLT_SLOT);
        if (hash(slot, 21) > (storm - 0.5) * 1.4) return 0;
        double at = slot * BOLT_SLOT + hash(slot, 22) * (BOLT_SLOT - 1);
        double dt = t - at;
        if (dt < 0 || dt > 0.45) return 0;
        // двойная вспышка
        return (float) (dt < 0.12 ? 1 - dt / 0.12 * 0.5 : Math.max(0, 0.7 - (dt - 0.12) / 0.33 * 0.7));
    }

    private static void bolt(GuiGraphics g, int w, int horizon, double t, float flash) {
        long slot = (long) Math.floor(t / BOLT_SLOT);
        int x = (int) (w * (0.15 + hash(slot, 23) * 0.7));
        int a = (int) (flash * 255) << 24;
        int y = 0;
        for (int i = 0; y < horizon * 0.85; i++) {
            int len = 6 + (int) (hash(slot * 31 + i, 24) * 10);
            g.fill(x, y, x + PX, y + len, a | 0xF4F7FF);
            y += len;
            x += (hash(slot * 31 + i, 25) < 0.5 ? -1 : 1) * PX * (1 + (int) (hash(slot * 31 + i, 26) * 3));
        }
    }

    // ---------------------------------------------------------------- утилиты

    private static int sky(int[] keys, double phase) {
        double p = phase * keys.length;
        int i = (int) Math.floor(p) % keys.length;
        return mix(keys[i], keys[(i + 1) % keys.length], smooth((float) (p - Math.floor(p))));
    }

    private static int overcast(int c, float k) {
        int r = c >> 16 & 255, gg = c >> 8 & 255, b = c & 255;
        int grey = (int) ((r * 0.3 + gg * 0.59 + b * 0.11) * 0.8);
        return mix(c, grey << 16 | grey << 8 | grey, k);
    }

    static int mix(int a, int b, float k) {
        k = clamp(k, 0, 1);
        int r = (int) ((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * k);
        int g = (int) ((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * k);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * k);
        return r << 16 | g << 8 | bl;
    }

    private static int scale(int c, float k) {
        int r = Math.min(255, (int) ((c >> 16 & 255) * k));
        int g = Math.min(255, (int) ((c >> 8 & 255) * k));
        int b = Math.min(255, (int) ((c & 255) * k));
        return r << 16 | g << 8 | b;
    }

    private static float smooth(float x) {
        x = clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static double hash(long i, long salt) {
        long x = i * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        x ^= x >>> 31;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 29;
        return (x >>> 11) * 0x1.0p-53;
    }

    private static double noise(double x, int salt) {
        long i = (long) Math.floor(x);
        double f = x - i;
        double u = f * f * (3 - 2 * f);
        return hash(i, salt) * (1 - u) + hash(i + 1, salt) * u;
    }

    private static double fbm(double x, int salt) {
        return noise(x, salt) * 0.6 + noise(x * 2.1, salt + 1) * 0.28 + noise(x * 4.3, salt + 2) * 0.12;
    }
}
