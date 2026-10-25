package dev.cld.tcw.gen;

import java.util.SplittableRandom;

/** Двумерный симплекс-шум с аналитическими производными, значения примерно в [-1, 1]. */
public final class Simplex {
    private static final double F2 = 0.5 * (Math.sqrt(3) - 1), G2 = (3 - Math.sqrt(3)) / 6;
    private static final double[] GX = new double[16], GY = new double[16];
    static {
        for (int i = 0; i < 16; i++) {
            double a = (i + 0.5) * Math.PI * 2 / 16;
            GX[i] = Math.cos(a);
            GY[i] = Math.sin(a);
        }
    }
    private final byte[] perm = new byte[512];

    public Simplex(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        SplittableRandom r = new SplittableRandom(seed);
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1), t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = (byte) p[i & 255];
    }

    private int hash(int i, int j) {
        return perm[(i & 255) + (perm[j & 255] & 255)] & 15;
    }

    public double noise(double x, double y) {
        return noise(x, y, null);
    }

    /** d (если не null) получает производные по x и y. */
    public double noise(double x, double y, double[] d) {
        double s = (x + y) * F2;
        int i = (int) Math.floor(x + s), j = (int) Math.floor(y + s);
        double t = (i + j) * G2;
        double x0 = x - (i - t), y0 = y - (j - t);
        int i1 = x0 > y0 ? 1 : 0, j1 = 1 - i1;
        double v = 0, dx = 0, dy = 0;
        for (int c = 0; c < 3; c++) {
            double cx, cy;
            int h;
            if (c == 0) { cx = x0; cy = y0; h = hash(i, j); }
            else if (c == 1) { cx = x0 - i1 + G2; cy = y0 - j1 + G2; h = hash(i + i1, j + j1); }
            else { cx = x0 - 1 + 2 * G2; cy = y0 - 1 + 2 * G2; h = hash(i + 1, j + 1); }
            double tt = 0.5 - cx * cx - cy * cy;
            if (tt <= 0) continue;
            double t2 = tt * tt, t4 = t2 * t2, dot = GX[h] * cx + GY[h] * cy;
            v += t4 * dot;
            if (d != null) {
                double k = -8 * t2 * tt * dot;
                dx += k * cx + t4 * GX[h];
                dy += k * cy + t4 * GY[h];
            }
        }
        if (d != null) { d[0] = dx * 99; d[1] = dy * 99; }
        return v * 99;
    }
}
