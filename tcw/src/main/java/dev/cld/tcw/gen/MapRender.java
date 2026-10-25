package dev.cld.tcw.gen;

import java.util.stream.IntStream;

/** Карта сверху по модели рельефа (для превью в игре): цвета по высоте, климату, снегу и воде, с отмывкой склонов. */
public final class MapRender {
    private MapRender() {}

    /** Данные столбцов квадрата n×n с шагом k блоков от (x0, z0). Строки считаются параллельно; done растёт по строкам. */
    public static final class Grid {
        public final int n;
        public final double x0, z0, k;
        public final float[] h, wl, tmp, hum, mnt;
        public final byte[] wk, st;
        public final java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
        public volatile boolean cancelled;
        public Grid(double x0, double z0, double k, int n) {
            this.n = n; this.x0 = x0; this.z0 = z0; this.k = k;
            h = new float[n * n]; wl = new float[n * n]; tmp = new float[n * n]; hum = new float[n * n]; mnt = new float[n * n];
            wk = new byte[n * n]; st = new byte[n * n];
        }
    }

    public static void compute(Terrain t, Grid g) {
        int n = g.n;
        IntStream.range(0, n).parallel().forEach(j -> {
            if (g.cancelled) return;
            Terrain.Column c = new Terrain.Column();
            for (int i = 0; i < n; i++) {
                int id = j * n + i;
                t.column(g.x0 + i * g.k, g.z0 + j * g.k, c);
                g.h[id] = (float) c.height; g.wl[id] = (float) c.water; g.wk[id] = (byte) c.waterKind;
                g.tmp[id] = (float) c.temp; g.hum[id] = (float) c.humid; g.mnt[id] = (float) c.mountain;
                g.st[id] = (byte) c.dominant().ordinal();
            }
            g.done.incrementAndGet();
        });
    }

    /** Цвет пикселя 0xRRGGBB. */
    public static int pixel(Terrain t, Grid g, int i, int j) {
        int n = g.n, id = j * n + i;
        float[] h = g.h;
        double k = g.k;
        double hx = (h[j * n + Math.min(n - 1, i + 1)] - h[j * n + Math.max(0, i - 1)]) / (2 * k);
        double hz = (h[Math.min(n - 1, j + 1) * n + i] - h[Math.max(0, j - 1) * n + i]) / (2 * k);
        double slope = Math.sqrt(hx * hx + hz * hz);
        double nxv = -hx, nyv = 1, nzv = -hz, nl = Math.sqrt(nxv * nxv + 1 + nzv * nzv);
        double lx = -0.6, ly = 0.6, lz = -0.5, ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
        double shade = Math.max(0, (nxv * lx + nyv * ly + nzv * lz) / (nl * ll));
        double[] col = color(t, g.x0 + i * k, g.z0 + j * k, h[id], g.wl[id], g.wk[id], g.tmp[id], g.hum[id], g.mnt[id], g.st[id], slope);
        double sh = g.wk[id] != 0 ? 1 : 0.35 + 0.85 * shade;
        return rgb(col[0] * sh, col[1] * sh, col[2] * sh);
    }

    public static int rgb(double r, double g, double b) {
        return ((int) Macro.clamp(r, 0, 255) << 16) | ((int) Macro.clamp(g, 0, 255) << 8) | (int) Macro.clamp(b, 0, 255);
    }

    static double[] mix(double[] a, double[] b, double t) {
        return new double[] { a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t };
    }

    public static double[] color(Terrain t, double x, double z, double h, double wl, int wk, double temp, double hum, double mnt, int st, double slope) {
        if (wk != 0) {
            double depth = wl - h;
            if (wk == Terrain.WATER_OCEAN) {
                if (temp < -0.55 && depth < 30) return new double[] { 200, 220, 235 };
                return mix(new double[] { 70, 140, 190 }, new double[] { 15, 35, 90 }, Macro.ss(0, 90, depth));
            }
            if (temp < -0.5 && wk == Terrain.WATER_LAKE) return new double[] { 190, 215, 235 };
            return new double[] { 50, 110, 200 };
        }
        double snow = t.snowLine(x, z, temp);
        Macro.Style s = Macro.STYLES[st];
        double[] rock = switch (s) {
            case DOLOMITE -> new double[] { 205, 200, 185 };
            case GRANITE -> new double[] { 175, 175, 170 };
            case VOLCANIC -> new double[] { 70, 65, 65 };
            case MESA -> ((int) (h / 6)) % 3 == 0 ? new double[] { 205, 120, 70 } : ((int) (h / 6)) % 3 == 1 ? new double[] { 225, 170, 110 } : new double[] { 170, 85, 55 };
            case KARST -> new double[] { 150, 150, 140 };
            default -> new double[] { 125, 122, 118 };
        };
        if (h > snow && slope < 1.1) {
            if (slope < 0.3 && temp < -0.15 && mnt > 0.25 && h > snow + 30) return new double[] { 205, 235, 250 };
            return new double[] { 245, 248, 252 };
        }
        if (slope > 1.0) return rock;
        if (h < Macro.SEA + 3 && temp > -0.5) return new double[] { 220, 205, 150 };
        double[] veg;
        if (temp > 0.35 && hum < -0.05) veg = new double[] { 220, 195, 130 };
        else if (temp > 0.35) veg = new double[] { 50, 120, 40 };
        else if (temp < -0.45) veg = new double[] { 150, 160, 140 };
        else if (temp < -0.1) veg = new double[] { 50, 90, 60 };
        else veg = hum > 0 ? new double[] { 60, 125, 50 } : new double[] { 110, 150, 70 };
        double tree = snow - 140;
        if (h > tree) veg = mix(veg, new double[] { 140, 170, 90 }, Macro.ss(tree, tree + 40, h));
        if (h > snow - 50) veg = mix(veg, rock, Macro.ss(snow - 50, snow - 10, h));
        return mix(veg, rock, Macro.ss(0.6, 1.0, slope));
    }
}
