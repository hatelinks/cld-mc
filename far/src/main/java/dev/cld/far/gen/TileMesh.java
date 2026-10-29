package dev.cld.far.gen;

import dev.cld.tcw.gen.Macro;
import dev.cld.tcw.gen.MapRender;
import dev.cld.tcw.gen.Terrain;
import java.util.function.BooleanSupplier;

// Сетка одного тайла дальнего рельефа по модели TelipailoCraft: квады по 4 вершины с цветом, координаты от угла тайла.
public final class TileMesh {
    // Клеток по стороне тайла.
    public static final int N = 32;
    static final double LX = -0.45, LY = 0.8, LZ = -0.35, LL = Math.sqrt(LX * LX + LY * LY + LZ * LZ);

    public final double x0, z0, step;
    // x, y, z на вершину; y абсолютный.
    public final float[] pos;
    // 0xAARRGGBB на вершину.
    public final int[] argb;
    public final int quads;
    // Прозрачная вода отдельными квадами (только в режиме clearWater, иначе waterQuads = 0).
    public final float[] waterPos;
    public final int[] waterArgb;
    public final int waterQuads;

    private TileMesh(double x0, double z0, double step, float[] pos, int[] argb, int quads, float[] waterPos, int[] waterArgb, int waterQuads) {
        this.x0 = x0; this.z0 = z0; this.step = step; this.pos = pos; this.argb = argb; this.quads = quads;
        this.waterPos = waterPos; this.waterArgb = waterArgb; this.waterQuads = waterQuads;
    }

    public int vertices() { return quads * 4; }
    public int waterVertices() { return waterQuads * 4; }

    // Цвет воды в ванильной игре по умолчанию (океан).
    static final int WATER = 0x3F76E4;

    // Цвет земли по биому (в игре, по настоящему выбору биомов); -1, цвет из модели рельефа, как у карты /tcwmap.
    public interface Palette {
        Palette MODEL = (t, c, x, z) -> -1;
        int ground(Terrain t, Terrain.Column c, double x, double z);
        // Оттенок воды биома (0xRRGGBB); -1, ванильный океанский.
        default int water(Terrain t, Terrain.Column c, double x, double z) { return -1; }
    }

    // Тайл со стороной size от (x0, z0); null, если отменён. Обычно сетка NxN, но в горах (большой перепад высот)
    // в mountainDetail раз мельче: на равнинах лишние треугольники не видны, а хребты без них выходят пирамидами.
    // clearWater: дно и вода отдельно (вода полупрозрачная, как в игре), иначе вода, непрозрачная поверхность.
    public static TileMesh build(Terrain t, Palette palette, double x0, double z0, double size, int mountainDetail, boolean clearWater,
            BooleanSupplier cancelled) {
        int n = mountainDetail > 1 && mountainous(t, x0, z0, size) ? N * mountainDetail : N;
        double step = size / n;
        int m = n + 3;
        float[] h = new float[m * m], top = new float[m * m], wl = new float[m * m], tmp = new float[m * m], hum = new float[m * m], mnt = new float[m * m];
        byte[] wk = new byte[m * m], st = new byte[m * m];
        int[] ground = new int[m * m], wcol = new int[m * m];
        // вдали отдельные пики и гребни при точечной выборке выходят тонкими зубцами ("график"):
        // чем грубее сетка, тем ближе высота суши к крупному рельефу без мелких деталей
        double keep = Math.max(0.25, Math.min(1, 12 / step));
        Terrain.Column c = new Terrain.Column();
        Macro.Sample ms = new Macro.Sample();
        for (int j = 0; j < m; j++) {
            if (cancelled.getAsBoolean()) return null;
            for (int i = 0; i < m; i++) {
                int id = j * m + i;
                t.column(x0 + (i - 1) * step, z0 + (j - 1) * step, c);
                double hh = c.height;
                if (keep < 1 && c.waterKind == Terrain.WATER_NONE && hh > Macro.SEA + 2) {
                    // крупный рельеф сглаживается по клетке (среднее 5 точек), мелкие детали ослабляются
                    double x = x0 + (i - 1) * step, z = z0 + (j - 1) * step, r = step * 0.5, mac = c.macroHeight;
                    mac += t.macro.sample(x - r, z - r, ms).height + t.macro.sample(x + r, z - r, ms).height
                            + t.macro.sample(x - r, z + r, ms).height + t.macro.sample(x + r, z + r, ms).height;
                    hh = Math.max(Macro.SEA + 2, mac / 5 + (hh - c.macroHeight) * keep);
                }
                h[id] = (float) hh;
                wl[id] = (float) c.water;
                wk[id] = (byte) c.waterKind;
                boolean wet = wk[id] != 0 && c.water > hh;
                top[id] = wet && !clearWater ? (float) c.water : h[id];
                tmp[id] = (float) c.temp; hum[id] = (float) c.humid; mnt[id] = (float) c.mountain;
                st[id] = (byte) c.dominant().ordinal();
                boolean inner = i > 0 && j > 0 && i < m - 1 && j < m - 1;
                ground[id] = inner && wk[id] == 0 ? palette.ground(t, c, x0 + (i - 1) * step, z0 + (j - 1) * step) : -1;
                if (inner && wet) {
                    int w = palette.water(t, c, x0 + (i - 1) * step, z0 + (j - 1) * step);
                    wcol[id] = w >= 0 ? w : WATER;
                }
            }
        }
        // одиночные выборки на тонких столбах и шпилях (уже шага сетки) дают иглы-"график": срезаем вершину,
        // торчащую над всеми четырьмя соседями, до самого высокого соседа (плюс полшага)
        if (step >= 6) {
            float[] o = h.clone();
            for (int j = 1; j < m - 1; j++)
                for (int i = 1; i < m - 1; i++) {
                    int id = j * m + i;
                    if (wk[id] != 0) continue;
                    float nb = Math.max(Math.max(o[id - 1], o[id + 1]), Math.max(o[id - m], o[id + m]));
                    if (o[id] > nb + step * 0.5) h[id] = top[id] = (float) (nb + step * 0.5);
                }
        }
        // вдали ещё и лёгкое сглаживание суши: горы массивнее, без пилы из соседних зубцов
        if (step >= 12) {
            float[] o = h.clone();
            float k = (float) Math.min(0.6, (step - 8) / 40);
            for (int j = 1; j < m - 1; j++)
                for (int i = 1; i < m - 1; i++) {
                    int id = j * m + i;
                    if (wk[id] != 0 || wk[id - 1] != 0 || wk[id + 1] != 0 || wk[id - m] != 0 || wk[id + m] != 0) continue;
                    float avg = (o[id - 1] + o[id + 1] + o[id - m] + o[id + m]) / 4;
                    h[id] = top[id] = o[id] + (avg - o[id]) * k;
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
                boolean wet = wk[id] != 0 && wl[id] > h[id];
                double depth = wet ? wl[id] - h[id] : 0;
                double[] rgb = ground[id] >= 0 ? land(t, x0 + i * step, z0 + j * step, h[id], tmp[id], st[id], slope, ground[id])
                        : wet ? seabed(depth) : MapRender.color(t, x0 + i * step, z0 + j * step, h[id], wl[id], wk[id], tmp[id], hum[id], mnt[id], st[id], slope);
                if (wet && !clearWater) rgb = waterOver(rgb, wcol[id], depth);
                double shade = 0.97;
                if (!wet || clearWater) {
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
        if (!clearWater) return new TileMesh(x0, z0, step, pos, argb, quads, null, null, 0);

        // вода: квад в каждой клетке, где мокрый хоть один угол; у сухих углов вода прозрачна (мягкий берег)
        int wq = 0;
        boolean[] cellWet = new boolean[n * n];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                for (int k = 0; k < 4; k++) {
                    int id = (j + 1 + (k >> 1)) * m + i + 1 + (k & 1);
                    if (wcol[id] != 0) cellWet[j * n + i] = true;
                }
                if (cellWet[j * n + i]) wq++;
            }
        float[] wpos = new float[wq * 12];
        int[] wargb = new int[wq * 4];
        int[] w = { 0 };
        int[] corner = { 0, m, m + 1, 1 };
        int[][] cxz = { { 0, 0 }, { 0, 1 }, { 1, 1 }, { 1, 0 } };
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                if (!cellWet[j * n + i]) continue;
                int base = (j + 1) * m + i + 1;
                float level = -Float.MAX_VALUE;
                int tint = WATER;
                for (int k = 0; k < 4; k++) {
                    int id = base + corner[k];
                    if (wcol[id] != 0 && wl[id] > level) { level = wl[id]; tint = wcol[id]; }
                }
                for (int k = 0; k < 4; k++) {
                    int id = base + corner[k];
                    boolean wet = wcol[id] != 0;
                    double depth = wet ? wl[id] - h[id] : 0;
                    int a = wet ? (int) (255 * Math.min(0.92, 0.5 + depth / 16)) : 0;
                    double dark = 1 - 0.45 * Macro.ss(4, 40, depth);
                    int rgb = MapRender.rgb(((tint >> 16) & 255) * dark, ((tint >> 8) & 255) * dark, (tint & 255) * dark);
                    vertex(wpos, wargb, w, (i + cxz[k][0]) * s, wet ? wl[id] : level, (j + cxz[k][1]) * s, a << 24 | rgb);
                }
            }
        return new TileMesh(x0, z0, step, pos, argb, quads, wpos, wargb, wq);
    }

    // Дно: песок на мелководье, гравий и глина глубже.
    static double[] seabed(double depth) {
        return mix(new double[] { 200, 190, 150 }, new double[] { 110, 108, 100 }, Macro.ss(3, 25, depth));
    }

    // Непрозрачная вода как смесь дна и оттенка воды: на мелководье видно дно, в глубине темнее.
    static double[] waterOver(double[] bed, int tint, double depth) {
        double a = Math.min(1, 0.55 + depth / 14), dark = 1 - 0.45 * Macro.ss(4, 40, depth);
        double[] w = { ((tint >> 16) & 255) * dark, ((tint >> 8) & 255) * dark, (tint & 255) * dark };
        return mix(new double[] { bed[0] * 0.8, bed[1] * 0.8, bed[2] * 0.8 }, w, a);
    }

    // "Блочный" тайл nxn блоков с шагом 1 (для подзорной трубы): у каждого столбца плоский верх на высоте блока
    // и боковые грани к более низким соседям, похоже на обычные чанки, но без пещер, деревьев и построек.
    public static TileMesh buildBlocky(Terrain t, Palette palette, double x0, double z0, int n, BooleanSupplier cancelled) {
        int m = n + 2;
        int[] top = new int[m * m], col = new int[m * m];
        boolean[] wet = new boolean[m * m];
        Terrain.Column c = new Terrain.Column();
        for (int j = 0; j < m; j++) {
            if (cancelled.getAsBoolean()) return null;
            for (int i = 0; i < m; i++) {
                int id = j * m + i;
                double x = x0 + i - 1, z = z0 + j - 1;
                t.column(x, z, c);
                boolean w = c.waterKind != Terrain.WATER_NONE && c.water > c.height;
                wet[id] = w;
                top[id] = w ? (int) Math.floor(c.water) : (int) Math.ceil(c.height);
                if (i == 0 || j == 0 || i == m - 1 || j == m - 1) continue;
                double slope = c.slope;
                double[] rgb;
                if (w) {
                    int tint = palette.water(t, c, x, z);
                    rgb = waterOver(seabed(c.water - c.height), tint >= 0 ? tint : WATER, c.water - c.height);
                } else {
                    int g = palette.ground(t, c, x, z);
                    rgb = g >= 0 ? land(t, x, z, c.height, c.temp, c.dominant().ordinal(), slope, g)
                            : MapRender.color(t, x, z, c.height, c.water, c.waterKind, c.temp, c.humid, c.mountain, c.dominant().ordinal(), slope);
                }
                col[id] = MapRender.rgb(rgb[0], rgb[1], rgb[2]);
            }
        }
        // считаем грани: верх у каждого столбца, бок, где сосед ниже
        int quads = 0;
        int[] nb = { -1, 1, -m, m };
        for (int j = 1; j <= n; j++)
            for (int i = 1; i <= n; i++) {
                int id = j * m + i;
                quads++;
                for (int d : nb) if (top[id + d] < top[id]) quads++;
            }
        float[] pos = new float[quads * 12];
        int[] argb = new int[quads * 4];
        int[] q = { 0 };
        for (int j = 1; j <= n; j++)
            for (int i = 1; i <= n; i++) {
                int id = j * m + i, cc = col[id];
                float x = i - 1, z = j - 1, y = top[id] + (wet[id] ? -0.1f : 0);
                int ct = 0xFF000000 | cc;
                vertex(pos, argb, q, x, y, z, ct);
                vertex(pos, argb, q, x, y, z + 1, ct);
                vertex(pos, argb, q, x + 1, y, z + 1, ct);
                vertex(pos, argb, q, x + 1, y, z, ct);
                // бока с освещением как у блоков в игре: x, 0.6, z, 0.8
                if (top[id - 1] < top[id]) side(pos, argb, q, x, z, x, z + 1, top[id - 1], y, shadeRgb(cc, 0.6));
                if (top[id + 1] < top[id]) side(pos, argb, q, x + 1, z, x + 1, z + 1, top[id + 1], y, shadeRgb(cc, 0.6));
                if (top[id - m] < top[id]) side(pos, argb, q, x, z, x + 1, z, top[id - m], y, shadeRgb(cc, 0.8));
                if (top[id + m] < top[id]) side(pos, argb, q, x, z + 1, x + 1, z + 1, top[id + m], y, shadeRgb(cc, 0.8));
            }
        return new TileMesh(x0, z0, 1, pos, argb, quads, null, null, 0);
    }

    private static void side(float[] pos, int[] argb, int[] q, float ax, float az, float bx, float bz, float lo, float hi, int c) {
        vertex(pos, argb, q, ax, hi, az, c);
        vertex(pos, argb, q, ax, lo, az, c);
        vertex(pos, argb, q, bx, lo, bz, c);
        vertex(pos, argb, q, bx, hi, bz, c);
    }

    private static int shadeRgb(int c, double k) {
        return 0xFF000000 | MapRender.rgb(((c >> 16) & 255) * k, ((c >> 8) & 255) * k, (c & 255) * k);
    }

    // Горы в тайле: перепад высот или горная маска по сетке 5x5.
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

    // Земля цвета биома, но снег выше снеговой линии и скалы на крутых склонах, как ставит их генератор.
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
