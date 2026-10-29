package dev.cld.far.gen;

import dev.cld.tcw.gen.Macro;
import dev.cld.tcw.gen.MapRender;
import dev.cld.tcw.gen.Terrain;
import java.util.function.BooleanSupplier;

/** Сетка одного тайла дальнего рельефа по модели TelipailoCraft: квады по 4 вершины с цветом, координаты от угла тайла. */
public final class TileMesh {
    /** Клеток по стороне тайла. */
    public static final int N = 32;
    static final double LX = -0.45, LY = 0.8, LZ = -0.35, LL = Math.sqrt(LX * LX + LY * LY + LZ * LZ);

    public final double x0, z0, step;
    /** x, y, z на вершину; y абсолютный. */
    public final float[] pos;
    /** 0xAARRGGBB на вершину. */
    public final int[] argb;
    public final int quads;

    private TileMesh(double x0, double z0, double step, float[] pos, int[] argb, int quads) {
        this.x0 = x0; this.z0 = z0; this.step = step; this.pos = pos; this.argb = argb; this.quads = quads;
    }

    public int vertices() { return quads * 4; }

    /** Цвет земли по биому (в игре — по настоящему выбору биомов); -1 — цвет из модели рельефа, как у карты /tcwmap. */
    public interface Palette {
        Palette MODEL = (t, c, x, z) -> -1;
        int ground(Terrain t, Terrain.Column c, double x, double z);
    }

    /**
     * Тайл со стороной size от (x0, z0); null, если отменён. Обычно сетка N×N, но в горах (большой перепад высот)
     * в mountainDetail раз мельче: на равнинах лишние треугольники не видны, а хребты без них выходят пирамидами.
     */
    public static TileMesh build(Terrain t, Palette palette, double x0, double z0, double size, int mountainDetail, BooleanSupplier cancelled) {
        int n = mountainDetail > 1 && mountainous(t, x0, z0, size) ? N * mountainDetail : N;
        double step = size / n;
        int m = n + 3;
        float[] h = new float[m * m], top = new float[m * m], wl = new float[m * m], tmp = new float[m * m], hum = new float[m * m], mnt = new float[m * m];
        byte[] wk = new byte[m * m], st = new byte[m * m];
        int[] ground = new int[m * m];
        Terrain.Column c = new Terrain.Column();
        for (int j = 0; j < m; j++) {
            if (cancelled.getAsBoolean()) return null;
            for (int i = 0; i < m; i++) {
                int id = j * m + i;
                t.column(x0 + (i - 1) * step, z0 + (j - 1) * step, c);
                h[id] = (float) c.height;
                wl[id] = (float) c.water;
                wk[id] = (byte) c.waterKind;
                top[id] = wk[id] != 0 && c.water > c.height ? (float) c.water : h[id];
                tmp[id] = (float) c.temp; hum[id] = (float) c.humid; mnt[id] = (float) c.mountain;
                st[id] = (byte) c.dominant().ordinal();
                boolean inner = i > 0 && j > 0 && i < m - 1 && j < m - 1;
                ground[id] = inner && wk[id] == 0 ? palette.ground(t, c, x0 + (i - 1) * step, z0 + (j - 1) * step) : -1;
            }
        }
        // цвета вершин 0..n с отмывкой от фиксированного солнца
        int n1 = n + 1;
        int[] col = new int[n1 * n1];
        for (int j = 0; j < n1; j++)
            for (int i = 0; i < n1; i++) {
                int id = (j + 1) * m + i + 1;
                double sx = (h[id + 1] - h[id - 1]) / (2 * step), sz = (h[id + m] - h[id - m]) / (2 * step);
                double slope = Math.sqrt(sx * sx + sz * sz);
                double[] rgb = ground[id] >= 0 ? land(t, x0 + i * step, z0 + j * step, h[id], tmp[id], st[id], slope, ground[id])
                        : MapRender.color(t, x0 + i * step, z0 + j * step, h[id], wl[id], wk[id], tmp[id], hum[id], mnt[id], st[id], slope);
                double shade = 0.97;
                if (wk[id] == 0 || wl[id] <= h[id]) {
                    double hx = (top[id + 1] - top[id - 1]) / (2 * step), hz = (top[id + m] - top[id - m]) / (2 * step);
                    double nl = Math.sqrt(hx * hx + 1 + hz * hz);
                    double dot = (-hx * LX + LY - hz * LZ) / (nl * LL);
                    shade = 0.5 + 0.55 * Math.max(0, dot);
                }
                col[j * n1 + i] = 0xFF000000 | MapRender.rgb(rgb[0] * shade, rgb[1] * shade, rgb[2] * shade);
            }
        int quads = n * n + 4 * n;
        float[] pos = new float[quads * 12];
        int[] argb = new int[quads * 4];
        int[] q = { 0 };
        float s = (float) step;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                vertex(pos, argb, q, i * s, top[(j + 1) * m + i + 1], j * s, col[j * n1 + i]);
                vertex(pos, argb, q, i * s, top[(j + 2) * m + i + 1], (j + 1) * s, col[(j + 1) * n1 + i]);
                vertex(pos, argb, q, (i + 1) * s, top[(j + 2) * m + i + 2], (j + 1) * s, col[(j + 1) * n1 + i + 1]);
                vertex(pos, argb, q, (i + 1) * s, top[(j + 1) * m + i + 2], j * s, col[j * n1 + i + 1]);
            }
        // юбки по краям: закрывают щели на стыке с тайлом другого уровня
        float drop = 8 + 3 * s;
        for (int k = 0; k < n; k++) {
            skirt(pos, argb, q, k * s, 0, (k + 1) * s, 0, top[m + k + 1], top[m + k + 2], col[k], col[k + 1], drop);
            skirt(pos, argb, q, k * s, n * s, (k + 1) * s, n * s, top[(n + 1) * m + k + 1], top[(n + 1) * m + k + 2], col[n * n1 + k], col[n * n1 + k + 1], drop);
            skirt(pos, argb, q, 0, k * s, 0, (k + 1) * s, top[(k + 1) * m + 1], top[(k + 2) * m + 1], col[k * n1], col[(k + 1) * n1], drop);
            skirt(pos, argb, q, n * s, k * s, n * s, (k + 1) * s, top[(k + 1) * m + n + 1], top[(k + 2) * m + n + 1], col[k * n1 + n], col[(k + 1) * n1 + n], drop);
        }
        return new TileMesh(x0, z0, step, pos, argb, quads);
    }

    /** Горы в тайле: перепад высот или горная маска по сетке 5×5. */
    static boolean mountainous(Terrain t, double x0, double z0, double size) {
        Terrain.Column c = new Terrain.Column();
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE, mnt = 0;
        for (int j = 0; j <= 4; j++)
            for (int i = 0; i <= 4; i++) {
                t.column(x0 + size * i / 4, z0 + size * j / 4, c);
                lo = Math.min(lo, c.height);
                hi = Math.max(hi, c.height);
                mnt = Math.max(mnt, c.mountain);
            }
        return hi - lo > Math.min(160, 48 + size / 24) && mnt > 0.2;
    }

    /** Земля цвета биома, но снег выше снеговой линии и скалы на крутых склонах — как ставит их генератор. */
    static double[] land(Terrain t, double x, double z, double h, double temp, int st, double slope, int ground) {
        double snow = t.snowLine(x, z, temp);
        if (h > snow && slope < 1.1) return new double[] { 245, 248, 252 };
        double[] rock = switch (Macro.STYLES[st]) {
            case DOLOMITE -> new double[] { 205, 200, 185 };
            case GRANITE -> new double[] { 175, 175, 170 };
            case VOLCANIC -> new double[] { 70, 65, 65 };
            case KARST -> new double[] { 150, 150, 140 };
            default -> new double[] { 125, 122, 118 };
        };
        double[] g = { (ground >> 16) & 255, (ground >> 8) & 255, ground & 255 };
        if (h > snow - 50) g = mix(g, rock, Macro.ss(snow - 50, snow - 10, h));
        return mix(g, rock, Macro.ss(0.6, 1.0, slope));
    }

    static double[] mix(double[] a, double[] b, double k) {
        return new double[] { a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k };
    }

    private static void vertex(float[] pos, int[] argb, int[] q, float x, float y, float z, int c) {
        int v = q[0]++;
        pos[v * 3] = x; pos[v * 3 + 1] = y; pos[v * 3 + 2] = z;
        argb[v] = c;
    }

    private static void skirt(float[] pos, int[] argb, int[] q, float ax, float az, float bx, float bz, float ha, float hb, int ca, int cb, float drop) {
        vertex(pos, argb, q, ax, ha, az, ca);
        vertex(pos, argb, q, ax, ha - drop, az, darker(ca));
        vertex(pos, argb, q, bx, hb - drop, bz, darker(cb));
        vertex(pos, argb, q, bx, hb, bz, cb);
    }

    private static int darker(int c) {
        return 0xFF000000 | (((c >> 16) & 255) * 3 / 4) << 16 | (((c >> 8) & 255) * 3 / 4) << 8 | (c & 255) * 3 / 4;
    }
}
