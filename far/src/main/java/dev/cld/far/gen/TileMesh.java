package dev.cld.far.gen;

import dev.cld.tcw.gen.Macro;
import dev.cld.tcw.gen.MapRender;
import dev.cld.tcw.gen.Terrain;
import java.util.ArrayList;
import java.util.List;
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

    private TileMesh(double x0, double z0, double step, Quads ground, Quads water) {
        this.x0 = x0; this.z0 = z0; this.step = step;
        this.pos = ground.pos(); this.argb = ground.argb(); this.quads = ground.quads();
        this.waterPos = water != null ? water.pos() : null;
        this.waterArgb = water != null ? water.argb() : null;
        this.waterQuads = water != null ? water.quads() : 0;
    }

    // Настройки вида тайла. pillars: столбы генератора отдельной сеткой; peaks: острые пики в режиме блоков.
    // valleyShade: затенение впадин, colorNoise: пестрота, blockSides: бока блоков (земля, камень), mesaBands: полосы мезы.
    // trees: кроны лесов бугром над землёй, ice: замёрзшая вода льдом, shore: пена у берега и бирюзовое мелководье,
    // rivers: узкие реки видны и там, где сетка грубее реки.
    public record Style(int mountains, boolean clearWater, double smooth, int pixel, boolean pillars, boolean peaks,
            double valleyShade, double colorNoise, boolean blockSides, boolean mesaBands, boolean trees, boolean ice, boolean shore, boolean rivers) {
        public static final Style DEFAULT = new Style(2, false, 0.15, 2, true, true, 0.35, 0.5, true, true, true, true, true, true);
    }

    static final int ICE = 0xA9C6EE;

    // Что за верх у клетки: от этого зависят её бока в режиме блоков.
    static final int PLAIN = 0, GRASS = 1, SNOW = 2, MESA = 3;
    static final int DIRT = 0x86603F;
    // слои терракоты в пустошах: оранжевая, белая, красная, светло-серая, жёлтая, бурая, коричневая
    static final int[] BANDS = { 0xA0573A, 0xD1B2A1, 0x8F3D2E, 0x876A61, 0xBA8523, 0x9A5E44, 0x4D3323 };

    // Цвет слоя мезы на высоте y: слои по 4 блока, порядок по хешу (одинаковый во всех тайлах).
    static int band(double y) {
        long k = (long) Math.floor(y / 4) * 0x9E3779B97F4A7C15L;
        k ^= k >>> 29;
        return BANDS[(int) ((k >>> 33) % BANDS.length)];
    }

    // Пестрота: множитель яркости от 1 - a до 1 + a по хешу клетки.
    static double speck(long x, long z, double a) {
        long k = x * 0x632BE59BD9B4E019L ^ z * 0x9E3779B97F4A7C15L;
        k ^= k >>> 31;
        k *= 0xBF58476D1CE4E5B9L;
        return 1 + a * (((k >>> 40) & 0xFFFF) / 32767.5 - 1);
    }

    static int kind(int rgb, int st, boolean mesaBands) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        if (r > 225 && g > 225 && b > 225) return SNOW;
        if (mesaBands && Macro.STYLES[st] == Macro.Style.MESA) return MESA;
        return g > r + 8 && g >= b ? GRASS : PLAIN;
    }

    static int scale(int c, double k) {
        return MapRender.rgb(((c >> 16) & 255) * k, ((c >> 8) & 255) * k, (c & 255) * k);
    }

    // Бок блока от lo до hi: у травы сверху полоска травы и земля ниже, у снега полоска снега и камень,
    // у мезы слои терракоты по высоте. shade, освещение бока (0.6 и 0.8, как у блоков в игре).
    static void sideFaces(Quads q, float ax, float az, float bx, float bz, float lo, float hi, int top, int kind, int rock, double shade, float strip) {
        // низкие ступеньки (пол пустоши) без слоёв, иначе пол рябит полосами
        if (kind == PLAIN || hi - lo <= 0 || kind == MESA && hi - lo < 3) {
            side(q, ax, az, bx, bz, lo, hi, shadeRgb(top, shade));
            return;
        }
        if (kind == MESA) {
            float bh = 4;
            while ((hi - lo) / bh > 24) bh *= 2;
            float y = hi;
            while (y > lo) {
                float y2 = Math.max(lo, (float) (Math.ceil(y / bh) - 1) * bh);
                if (y2 >= y) y2 = Math.max(lo, y - bh);
                side(q, ax, az, bx, bz, y2, y, shadeRgb(band(y2 + 0.5), shade));
                y = y2;
            }
            return;
        }
        float s = Math.min(hi - lo, strip);
        side(q, ax, az, bx, bz, hi - s, hi, shadeRgb(top, shade));
        if (hi - s > lo) side(q, ax, az, bx, bz, lo, hi - s, shadeRgb(kind == GRASS ? DIRT : rock, shade));
    }

    // Растущий список квадов: 4 вершины, x y z и цвет на вершину.
    static final class Quads {
        private float[] p = new float[4096 * 3];
        private int[] c = new int[4096];
        private int v;

        void vertex(float x, float y, float z, int col) {
            if (v == c.length) {
                p = java.util.Arrays.copyOf(p, p.length * 2);
                c = java.util.Arrays.copyOf(c, c.length * 2);
            }
            p[v * 3] = x; p[v * 3 + 1] = y; p[v * 3 + 2] = z;
            c[v++] = col;
        }

        int quads() { return v / 4; }
        float[] pos() { return java.util.Arrays.copyOf(p, v * 3); }
        int[] argb() { return java.util.Arrays.copyOf(c, v); }
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
        // Густота леса биома: 0, нет деревьев, 1, обычный лес, больше 1, джунгли.
        default double trees(Terrain t, Terrain.Column c, double x, double z) { return 0; }
        // Вода здесь замерзает (холодный биом).
        default boolean frozen(Terrain t, Terrain.Column c, double x, double z) { return false; }
    }

    // Выборка рельефа для тайла: всё, что считается генератором и палитрой (дорого), без настроек вида.
    // Из неё сетка строится за миллисекунды (mesh), поэтому смена вида не пересчитывает рельеф, а выборку можно
    // держать в памяти и на диске (TileCache).
    public static final class Samples {
        public final double x0, z0, size, step;
        public final int n, m;
        // высота, крупный рельеф, среднее крупного рельефа по клетке (только вдали, иначе NaN), вода, климат,
        // расстояние до реки, густота леса и шум крон
        public final float[] h, mac, mac5, wl, tmp, hum, mnt, river, slope, dens;
        // вид воды, стиль рельефа, флаги (1, замерзает)
        public final byte[] wk, st, fl;
        public final int[] ground, wcol;
        public final List<Pillars.P> pillars;

        Samples(double x0, double z0, double size, int n, List<Pillars.P> pillars) {
            this.x0 = x0; this.z0 = z0; this.size = size; this.n = n; this.m = n + 3; this.step = size / n;
            int k = m * m;
            h = new float[k]; mac = new float[k]; mac5 = new float[k]; wl = new float[k]; tmp = new float[k]; hum = new float[k];
            mnt = new float[k]; river = new float[k]; slope = new float[k]; dens = new float[k];
            wk = new byte[k]; st = new byte[k]; fl = new byte[k];
            ground = new int[k]; wcol = new int[k];
            this.pillars = pillars;
        }

        public long bytes() { return (long) m * m * (10 * 4 + 3 + 8) + pillars.size() * 64L + 64; }

        public void write(java.io.DataOutputStream out) throws java.io.IOException {
            out.writeDouble(x0); out.writeDouble(z0); out.writeDouble(size); out.writeInt(n);
            for (float[] a : new float[][] { h, mac, mac5, wl, tmp, hum, mnt, river, slope, dens }) for (float v : a) out.writeFloat(v);
            out.write(wk); out.write(st); out.write(fl);
            for (int v : ground) out.writeInt(v);
            for (int v : wcol) out.writeInt(v);
            out.writeInt(pillars.size());
            for (Pillars.P p : pillars) {
                out.writeDouble(p.x()); out.writeDouble(p.z()); out.writeDouble(p.r()); out.writeDouble(p.base()); out.writeDouble(p.top());
                out.writeDouble(p.ph()); out.writeInt(p.style());
            }
        }

        public static Samples read(java.io.DataInputStream in) throws java.io.IOException {
            double x0 = in.readDouble(), z0 = in.readDouble(), size = in.readDouble();
            int n = in.readInt();
            if (n <= 0 || n > 4096) throw new java.io.IOException("bad tile");
            Samples s = new Samples(x0, z0, size, n, new ArrayList<>());
            for (float[] a : new float[][] { s.h, s.mac, s.mac5, s.wl, s.tmp, s.hum, s.mnt, s.river, s.slope, s.dens })
                for (int i = 0; i < a.length; i++) a[i] = in.readFloat();
            in.readFully(s.wk); in.readFully(s.st); in.readFully(s.fl);
            for (int i = 0; i < s.ground.length; i++) s.ground[i] = in.readInt();
            for (int i = 0; i < s.wcol.length; i++) s.wcol[i] = in.readInt();
            int np = in.readInt();
            for (int i = 0; i < np; i++)
                s.pillars.add(new Pillars.P(in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(), in.readInt()));
            return s;
        }
    }

    // Выборка тайла со стороной size от (x0, z0); null, если отменена. Обычно сетка NxN, но в горах (большой перепад
    // высот) в mountains раз мельче: на равнинах лишние треугольники не видны, а хребты без них выходят пирамидами.
    public static Samples sample(Terrain t, Palette palette, double x0, double z0, double size, int mountains, BooleanSupplier cancelled) {
        int n = mountains > 1 && mountainous(t, x0, z0, size) ? N * mountains : N;
        double step = size / n;
        int m = n + 3;
        double[] pillarW = new double[3];
        Terrain.Column c = new Terrain.Column();
        Macro.Sample ms = new Macro.Sample();
        float[] h = new float[m * m], mac = new float[m * m], mac5 = new float[m * m], wl = new float[m * m], tmp = new float[m * m],
                hum = new float[m * m], mnt = new float[m * m], river = new float[m * m], slope = new float[m * m], dens = new float[m * m];
        byte[] wk = new byte[m * m], st = new byte[m * m], fl = new byte[m * m];
        int[] ground = new int[m * m], wcol = new int[m * m];
        for (int j = 0; j < m; j++) {
            if (cancelled.getAsBoolean()) return null;
            for (int i = 0; i < m; i++) {
                int id = j * m + i;
                double gx = x0 + (i - 1) * step, gz = z0 + (j - 1) * step;
                t.column(gx, gz, c);
                for (int k = 0; k < 3; k++) pillarW[k] = Math.max(pillarW[k], Pillars.weight(k, c));
                h[id] = (float) c.height;
                mac[id] = (float) c.macroHeight;
                // среднее крупного рельефа по клетке нужно только для сглаживания вдали (шаг больше 12)
                mac5[id] = Float.NaN;
                if (step > 12 && c.waterKind == Terrain.WATER_NONE && c.height > Macro.SEA + 2) {
                    double r = step * 0.5;
                    mac5[id] = (float) ((c.macroHeight + t.macro.sample(gx - r, gz - r, ms).height + t.macro.sample(gx + r, gz - r, ms).height
                            + t.macro.sample(gx - r, gz + r, ms).height + t.macro.sample(gx + r, gz + r, ms).height) / 5);
                }
                wl[id] = (float) c.water;
                wk[id] = (byte) c.waterKind;
                tmp[id] = (float) c.temp; hum[id] = (float) c.humid; mnt[id] = (float) c.mountain;
                river[id] = (float) Math.min(1e6, c.riverDist);
                slope[id] = (float) c.slope;
                st[id] = (byte) c.dominant().ordinal();
                // палитра нужна и на полях за краем тайла: без неё у края вода считалась берегом (пена),
                // а лёд и кроны обрывались, и по стыкам тайлов шла светлая сетка
                boolean wet = c.waterKind != Terrain.WATER_NONE && c.water > c.height;
                if (wet) {
                    int w = palette.water(t, c, gx, gz);
                    wcol[id] = w >= 0 ? w : WATER;
                    if (palette.frozen(t, c, gx, gz)) fl[id] |= 1;
                }
                ground[id] = palette.ground(t, c, gx, gz);
                if (!wet && c.slope < 0.9) dens[id] = (float) palette.trees(t, c, gx, gz);
            }
        }
        // столбы генератора: рисуются отдельно. Вдали (шаг больше 64) их уже не различить
        List<Pillars.P> pillars = step <= 64 ? Pillars.find(t, x0, z0, size, 16 + step, pillarW) : List.of();
        Samples s = new Samples(x0, z0, size, n, pillars);
        System.arraycopy(h, 0, s.h, 0, h.length); System.arraycopy(mac, 0, s.mac, 0, h.length); System.arraycopy(mac5, 0, s.mac5, 0, h.length);
        System.arraycopy(wl, 0, s.wl, 0, h.length); System.arraycopy(tmp, 0, s.tmp, 0, h.length); System.arraycopy(hum, 0, s.hum, 0, h.length);
        System.arraycopy(mnt, 0, s.mnt, 0, h.length); System.arraycopy(river, 0, s.river, 0, h.length); System.arraycopy(slope, 0, s.slope, 0, h.length);
        System.arraycopy(dens, 0, s.dens, 0, h.length); System.arraycopy(wk, 0, s.wk, 0, h.length); System.arraycopy(st, 0, s.st, 0, h.length);
        System.arraycopy(fl, 0, s.fl, 0, h.length); System.arraycopy(ground, 0, s.ground, 0, h.length); System.arraycopy(wcol, 0, s.wcol, 0, h.length);
        return s;
    }

    public static TileMesh build(Terrain t, Palette palette, double x0, double z0, double size, Style o, BooleanSupplier cancelled) {
        Samples s = sample(t, palette, x0, z0, size, o.mountains(), cancelled);
        return s == null ? null : mesh(t, s, o);
    }

    // Сетка тайла из выборки по настройкам вида.
    // clearWater: дно и вода отдельно (вода полупрозрачная, как в игре), иначе вода, непрозрачная поверхность.
    // smooth от 0 до 1: насколько вдали сглаживать пики и гребни. pixel: 0 гладкая сетка, 1 гранями, 2 блоками.
    public static TileMesh mesh(Terrain t, Samples sm, Style o) {
        boolean clearWater = o.clearWater();
        double smooth = o.smooth();
        int pixel = o.pixel();
        int n = sm.n, m = sm.m;
        double step = sm.step, x0 = sm.x0, z0 = sm.z0, size = sm.size;
        float[] h = new float[m * m], top = new float[m * m], wl = new float[m * m], tmp = sm.tmp, hum = sm.hum, mnt = sm.mnt;
        byte[] wk = new byte[m * m], st = sm.st, tr = new byte[m * m];
        float[] canopy = new float[m * m];
        int[] ground = new int[m * m], wcol = new int[m * m];
        // вдали отдельные пики и гребни при точечной выборке выходят тонкими зубцами ("график"):
        // чем грубее сетка, тем ближе высота суши к крупному рельефу без мелких деталей
        double keep = 1 - smooth * (1 - Math.max(0.25, Math.min(1, 12 / step)));
        for (int j = 0; j < m; j++)
            for (int i = 0; i < m; i++) {
                int id = j * m + i;
                double hh = sm.h[id], water = sm.wl[id];
                int kind = sm.wk[id];
                // крупный рельеф сглаживается по клетке (среднее 5 точек), мелкие детали ослабляются
                if (keep < 1 && !Float.isNaN(sm.mac5[id])) hh = Math.max(Macro.SEA + 2, sm.mac5[id] + (hh - sm.mac[id]) * keep);
                double gx = x0 + (i - 1) * step, gz = z0 + (j - 1) * step;
                boolean wet0 = kind != Terrain.WATER_NONE && water > hh;
                // замёрзшая вода: сплошной лёд на уровне воды
                if (o.ice() && wet0 && (sm.fl[id] & 1) != 0) {
                    hh = Math.floor(water);
                    kind = Terrain.WATER_NONE;
                    tr[id] = 2;
                }
                // река уже клетки сетки: точка рядом с руслом становится водой, иначе вдали реки рвутся на куски
                if (o.rivers() && !wet0 && step > 4 && kind == Terrain.WATER_NONE && sm.river[id] < step * 0.5 && tr[id] != 2) {
                    water = hh + 0.6;
                    kind = Terrain.WATER_RIVER;
                }
                h[id] = (float) hh;
                wl[id] = (float) water;
                wk[id] = (byte) kind;
                boolean wet = kind != 0 && water > hh;
                // кроны леса: бугор над землёй, выше и гуще в джунглях
                if (o.trees() && !wet && tr[id] == 0 && sm.dens[id] > 0) {
                    double dens = sm.dens[id];
                    double cell = Math.max(4, step);
                    double r1 = speck(Math.round(gx / cell), Math.round(gz / cell), 1) / 2, r2 = speck(Math.round(gz / cell) + 77, Math.round(gx / cell), 1) / 2;
                    // вдали деревья сливаются: ровный полог, рядом отдельные кроны с прогалинами
                    double cover = step >= 24 ? 1 : r2 < Math.min(1, dens) ? 1 : 0;
                    canopy[id] = (float) (cover * Math.min(1.3, dens) * (4 + 5 * r1) * (step >= 24 ? 0.7 : 1));
                    if (canopy[id] > 0) tr[id] = 1;
                }
                top[id] = wet && !clearWater ? (float) water : h[id];
                ground[id] = tr[id] == 2 ? ICE : kind == 0 ? sm.ground[id] : -1;
                if (wet) wcol[id] = sm.wcol[id] != 0 ? sm.wcol[id] : WATER;
            }
        // столбы генератора: точки сетки, попавшие в столб, опускаются к его основанию, сам столб рисуется отдельно
        List<Pillars.P> pillars = o.pillars() ? sm.pillars : List.of();
        for (Pillars.P p : pillars) {
            int ia = Math.max(0, (int) Math.floor((p.x() - p.r() - x0) / step) + 1), ib = Math.min(m - 1, (int) Math.ceil((p.x() + p.r() - x0) / step) + 1);
            int ja = Math.max(0, (int) Math.floor((p.z() - p.r() - z0) / step) + 1), jb = Math.min(m - 1, (int) Math.ceil((p.z() + p.r() - z0) / step) + 1);
            for (int j = ja; j <= jb; j++)
                for (int i = ia; i <= ib; i++) {
                    int id = j * m + i;
                    double dx = x0 + (i - 1) * step - p.x(), dz = z0 + (j - 1) * step - p.z();
                    if (wk[id] != 0 || dx * dx + dz * dz >= p.r() * p.r() || h[id] <= p.base()) continue;
                    h[id] = top[id] = (float) p.base();
                }
        }
        // одиночные выборки на тонких столбах и шпилях (уже шага сетки) дают иглы-"график": срезаем вершину,
        // торчащую над всеми четырьмя соседями, до самого высокого соседа (плюс полшага)
        if (step >= 6 && smooth >= 0.5) {
            float[] old = h.clone();
            for (int j = 1; j < m - 1; j++)
                for (int i = 1; i < m - 1; i++) {
                    int id = j * m + i;
                    if (wk[id] != 0) continue;
                    float nb = Math.max(Math.max(old[id - 1], old[id + 1]), Math.max(old[id - m], old[id + m]));
                    if (old[id] > nb + step * 0.5) h[id] = top[id] = (float) (nb + step * 0.5);
                }
        }
        // вдали ещё и лёгкое сглаживание суши: горы массивнее, без пилы из соседних зубцов
        if (step >= 12 && smooth > 0) {
            float[] old = h.clone();
            float k = (float) (smooth * Math.min(0.6, (step - 8) / 40));
            for (int j = 1; j < m - 1; j++)
                for (int i = 1; i < m - 1; i++) {
                    int id = j * m + i;
                    if (wk[id] != 0 || wk[id - 1] != 0 || wk[id + 1] != 0 || wk[id - m] != 0 || wk[id + m] != 0) continue;
                    float avg = (old[id - 1] + old[id + 1] + old[id - m] + old[id + m]) / 4;
                    h[id] = top[id] = old[id] + (avg - old[id]) * k;
                }
        }
        for (int id = 0; id < m * m; id++) if (canopy[id] > 0) top[id] += canopy[id];
        // цвета вершин 0..n с отмывкой от фиксированного солнца
        int n1 = n + 1;
        int[] col = new int[n1 * n1], flat = new int[n1 * n1];
        for (int j = 0; j < n1; j++)
            for (int i = 0; i < n1; i++) {
                int id = (j + 1) * m + i + 1;
                double sx = (h[id + 1] - h[id - 1]) / (2 * step), sz = (h[id + m] - h[id - m]) / (2 * step);
                double slope = Math.sqrt(sx * sx + sz * sz);
                boolean wet = wk[id] != 0 && wl[id] > h[id];
                double depth = wet ? wl[id] - h[id] : 0;
                double[] rgb = ground[id] >= 0 ? land(t, x0 + i * step, z0 + j * step, h[id], tmp[id], st[id], slope, ground[id])
                        : wet ? seabed(depth) : MapRender.color(t, x0 + i * step, z0 + j * step, h[id], wl[id], wk[id], tmp[id], hum[id], mnt[id], st[id], slope);
                if (o.mesaBands() && !wet && Macro.STYLES[st[id]] == Macro.Style.MESA && slope > 0.4)
                    rgb = mix(rgb, unpack(band(h[id])), Macro.ss(0.4, 0.9, slope));
                if (wet && !clearWater) rgb = waterOver(rgb, o.shore() ? shoreTint(wcol[id], depth, tmp[id], shoreAt(wcol, id, m)) : wcol[id], depth);
                // впадины темнее: вершина ниже среднего восьми соседей
                double light = 1;
                if (o.valleyShade() > 0 && !wet) {
                    double avg = 0;
                    for (int dj = -1; dj <= 1; dj++)
                        for (int di = -1; di <= 1; di++) avg += top[id + dj * m + di];
                    avg = (avg - top[id]) / 8;
                    light -= o.valleyShade() * 0.45 * Macro.ss(0, step * 1.5, avg - top[id]);
                }
                if (o.colorNoise() > 0) light *= speck(Math.round((x0 + i * step) / step), Math.round((z0 + j * step) / step), 0.07 * o.colorNoise());
                rgb = new double[] { rgb[0] * light, rgb[1] * light, rgb[2] * light };
                double shade = 0.97;
                if (!wet || clearWater) {
                    double hx = (top[id + 1] - top[id - 1]) / (2 * step), hz = (top[id + m] - top[id - m]) / (2 * step);
                    double nl = Math.sqrt(hx * hx + 1 + hz * hz);
                    double dot = (-hx * LX + LY - hz * LZ) / (nl * LL);
                    shade = 0.5 + 0.55 * Math.max(0, dot);
                }
                col[j * n1 + i] = 0xFF000000 | MapRender.rgb(rgb[0] * shade, rgb[1] * shade, rgb[2] * shade);
                flat[j * n1 + i] = MapRender.rgb(rgb[0], rgb[1], rgb[2]);
            }
        Quads q = new Quads();
        Quads water = clearWater ? water(n, m, step, h, wl, wcol, tmp, o.shore()) : null;
        if (pixel >= 2) blocks(q, n, m, step, top, wk, st, tr, flat, o);
        else {
            if (pixel == 1) facets(n, m, step, top, flat, col);
            float s = (float) step;
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) {
                    q.vertex(i * s, top[(j + 1) * m + i + 1], j * s, col[j * n1 + i]);
                    q.vertex(i * s, top[(j + 2) * m + i + 1], (j + 1) * s, col[(j + 1) * n1 + i]);
                    q.vertex((i + 1) * s, top[(j + 2) * m + i + 2], (j + 1) * s, col[(j + 1) * n1 + i + 1]);
                    q.vertex((i + 1) * s, top[(j + 1) * m + i + 2], j * s, col[j * n1 + i + 1]);
                }
            // юбки по краям: закрывают щели на стыке с тайлом другого уровня
            float drop = 8 + 3 * s;
            for (int k = 0; k < n; k++) {
                skirt(q, k * s, 0, (k + 1) * s, 0, top[m + k + 1], top[m + k + 2], col[k], col[k + 1], drop);
                skirt(q, k * s, n * s, (k + 1) * s, n * s, top[(n + 1) * m + k + 1], top[(n + 1) * m + k + 2], col[n * n1 + k], col[n * n1 + k + 1], drop);
                skirt(q, 0, k * s, 0, (k + 1) * s, top[(k + 1) * m + 1], top[(k + 2) * m + 1], col[k * n1], col[(k + 1) * n1], drop);
                skirt(q, n * s, k * s, n * s, (k + 1) * s, top[(k + 1) * m + n + 1], top[(k + 2) * m + n + 1], col[k * n1 + n], col[(k + 1) * n1 + n], drop);
            }
        }
        for (Pillars.P p : pillars) {
            if (p.x() < x0 || p.z() < z0 || p.x() >= x0 + size || p.z() >= z0 + size) continue;
            int vi = (int) Math.max(0, Math.min(n, Math.round((p.x() - x0) / step))), vj = (int) Math.max(0, Math.min(n, Math.round((p.z() - z0) / step)));
            int cg = ground[(vj + 1) * m + vi + 1];
            double snow = t.snowLine(p.x(), p.z(), tmp[(vj + 1) * m + vi + 1]);
            pillar(q, p, x0, z0, step, pixel, pillarColor(p, cg, snow));
        }
        return new TileMesh(x0, z0, step, q, water);
    }

    // Цвет столба: камень его стиля, у карстовых башен верх зарастает (цвет биома), выше снеговой линии снег.
    static int[] pillarColor(Pillars.P p, int ground, double snow) {
        double[] rock = rock(Macro.STYLES[p.style()]);
        double[] cap = rock;
        if (p.top() > snow) cap = new double[] { 245, 248, 252 };
        else if (Macro.STYLES[p.style()] == Macro.Style.KARST && ground >= 0)
            cap = mix(rock, new double[] { (ground >> 16) & 255, (ground >> 8) & 255, ground & 255 }, 0.7);
        return new int[] { MapRender.rgb(rock[0], rock[1], rock[2]), MapRender.rgb(cap[0], cap[1], cap[2]) };
    }

    // Столб: в режиме блоков и рядом, круг из столбиков (по 2 блока у игрока, крупнее вдали) с куполом сверху,
    // иначе восьмигранник с конусом. Основание уходит чуть ниже рельефа, чтобы не было щели.
    static void pillar(Quads q, Pillars.P p, double x0, double z0, double step, int pixel, int[] color) {
        float bottom = (float) (p.base() - 4);
        if (pixel >= 2 && step <= 32) {
            int b = (int) Math.max(2, Math.min(8, Math.round(step / 4)));
            int ax = (int) Math.floor((p.x() - p.r()) / b), bx = (int) Math.floor((p.x() + p.r()) / b);
            int az = (int) Math.floor((p.z() - p.r()) / b), bz = (int) Math.floor((p.z() + p.r()) / b);
            int w = bx - ax + 1, d = bz - az + 1;
            float[] hc = new float[w * d];
            for (int j = 0; j < d; j++)
                for (int i = 0; i < w; i++) {
                    double cx = (ax + i + 0.5) * b - p.x(), cz = (az + j + 0.5) * b - p.z();
                    double y = p.height(Math.sqrt(cx * cx + cz * cz));
                    hc[j * w + i] = y > p.base() + 0.5 ? (float) Math.ceil(y) : Float.NaN;
                }
            for (int j = 0; j < d; j++)
                for (int i = 0; i < w; i++) {
                    float y = hc[j * w + i];
                    if (Float.isNaN(y)) continue;
                    float x = (float) ((ax + i) * b - x0), z = (float) ((az + j) * b - z0);
                    int c = 0xFF000000 | (y >= p.top() - b ? color[1] : color[0]);
                    q.vertex(x, y, z, c);
                    q.vertex(x, y, z + b, c);
                    q.vertex(x + b, y, z + b, c);
                    q.vertex(x + b, y, z, c);
                    float[] nb = { i > 0 ? hc[j * w + i - 1] : Float.NaN, i < w - 1 ? hc[j * w + i + 1] : Float.NaN,
                            j > 0 ? hc[(j - 1) * w + i] : Float.NaN, j < d - 1 ? hc[(j + 1) * w + i] : Float.NaN };
                    for (int k = 0; k < 4; k++) {
                        float lo = Float.isNaN(nb[k]) ? bottom : nb[k];
                        if (lo >= y) continue;
                        int sc = shadeRgb(color[0], k < 2 ? 0.6 : 0.8);
                        float ax2 = k == 1 ? x + b : x, az2 = k == 3 ? z + b : z;
                        float bx2 = k < 2 ? ax2 : x + b, bz2 = k < 2 ? z + b : az2;
                        side(q, ax2, az2, bx2, bz2, lo, y, sc);
                    }
                }
            return;
        }
        // восьмигранник: стенки до плеча купола, сверху конус к вершине
        float cx = (float) (p.x() - x0), cz = (float) (p.z() - z0);
        float shoulder = (float) p.height(p.r() * 0.75), apex = (float) p.top();
        float r = (float) (p.r() * 0.92), r2 = (float) (p.r() * 0.75);
        for (int k = 0; k < 8; k++) {
            double a0 = Math.PI * 2 * k / 8, a1 = Math.PI * 2 * (k + 1) / 8, am = (a0 + a1) / 2;
            float sx0 = (float) Math.cos(a0), sz0 = (float) Math.sin(a0), sx1 = (float) Math.cos(a1), sz1 = (float) Math.sin(a1);
            double lit = 0.62 + 0.3 * Math.max(0, -(Math.cos(am) * LX + Math.sin(am) * LZ) / Math.sqrt(LX * LX + LZ * LZ));
            int sc = shadeRgb(color[0], lit);
            q.vertex(cx + sx0 * r, shoulder, cz + sz0 * r, sc);
            q.vertex(cx + sx0 * r, bottom, cz + sz0 * r, shadeRgb(color[0], lit * 0.85));
            q.vertex(cx + sx1 * r, bottom, cz + sz1 * r, shadeRgb(color[0], lit * 0.85));
            q.vertex(cx + sx1 * r, shoulder, cz + sz1 * r, sc);
            int cc = shadeRgb(color[1], Math.min(1, lit + 0.12));
            q.vertex(cx + sx0 * r, shoulder, cz + sz0 * r, cc);
            q.vertex(cx + sx1 * r, shoulder, cz + sz1 * r, cc);
            q.vertex(cx + sx1 * r2 * 0.3f, apex, cz + sz1 * r2 * 0.3f, cc);
            q.vertex(cx + sx0 * r2 * 0.3f, apex, cz + sz0 * r2 * 0.3f, cc);
        }
    }

    // Гранями: у каждой клетки один цвет, затенение по наклону самой клетки. Меняет col на месте:
    // цвет вершины берётся по клетке, для которой она левый верхний угол, поэтому грани видны чётко.
    private static void facets(int n, int m, double step, float[] top, int[] flat, int[] col) {
        int n1 = n + 1;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int a = (j + 1) * m + i + 1;
                double hx = ((top[a + 1] + top[a + 1 + m]) - (top[a] + top[a + m])) / (2 * step);
                double hz = ((top[a + m] + top[a + m + 1]) - (top[a] + top[a + 1])) / (2 * step);
                double nl = Math.sqrt(hx * hx + 1 + hz * hz);
                double sh = 0.5 + 0.55 * Math.max(0, (-hx * LX + LY - hz * LZ) / (nl * LL));
                int c = flat[j * n1 + i];
                col[j * n1 + i] = 0xFF000000 | MapRender.rgb(((c >> 16) & 255) * sh, ((c >> 8) & 255) * sh, (c & 255) * sh);
            }
    }

    // Блоками: клетка сетки становится столбом с плоским верхом на высоте самого высокого угла,
    // с боками к более низким соседям, как у блоков в игре (верх ярче, бока темнее).
    // peaks: вершина выше всех восьми соседей не поднимает четыре клетки вокруг себя (из неё выходила широкая
    // коробка), а рисуется отдельной иглой, которая ступенями сужается кверху.
    private static void blocks(Quads q, int n, int m, double step, float[] top, byte[] wk, byte[] st, byte[] tr, int[] flat, Style o) {
        boolean peaks = o.peaks();
        float[] th = top;
        int n1 = n + 1;
        List<int[]> spikes = new ArrayList<>();
        if (peaks) {
            th = top.clone();
            float thr = (float) Math.max(4, step * 0.6);
            for (int j = 1; j < m - 1; j++)
                for (int i = 1; i < m - 1; i++) {
                    int a = j * m + i;
                    if (wk[a] != 0) continue;
                    float nb = -Float.MAX_VALUE;
                    for (int dj = -1; dj <= 1; dj++)
                        for (int di = -1; di <= 1; di++) if (di != 0 || dj != 0) nb = Math.max(nb, top[a + dj * m + di]);
                    if (top[a] <= nb + thr) continue;
                    th[a] = nb;
                    // своя игла только у вершин внутри тайла (общие вершины на краю рисует один тайл)
                    if (i >= 1 && j >= 1 && i <= n && j <= n) spikes.add(new int[] { i - 1, j - 1, a });
                }
        }
        int k = n + 2;
        int[] ct = new int[k * k];
        int[] cc = new int[k * k];
        byte[] ck = new byte[k * k], cs = new byte[k * k];
        for (int cj = -1; cj <= n; cj++)
            for (int ci = -1; ci <= n; ci++) {
                int a = (cj + 1) * m + ci + 1;
                float hi = Math.max(Math.max(th[a], th[a + 1]), Math.max(th[a + m], th[a + m + 1]));
                ct[(cj + 1) * k + ci + 1] = (int) Math.ceil(hi);
                if (ci < 0 || cj < 0 || ci >= n || cj >= n) continue;
                // цвет угла с наибольшей высотой
                int best = j1(ci, cj, n1);
                float bh = th[a];
                int ba = a;
                if (th[a + 1] > bh) { bh = th[a + 1]; best = j1(ci + 1, cj, n1); ba = a + 1; }
                if (th[a + m] > bh) { bh = th[a + m]; best = j1(ci, cj + 1, n1); ba = a + m; }
                if (th[a + m + 1] > bh) { best = j1(ci + 1, cj + 1, n1); ba = a + m + 1; }
                cc[(cj + 1) * k + ci + 1] = flat[best];
                cs[(cj + 1) * k + ci + 1] = st[ba];
                ck[(cj + 1) * k + ci + 1] = (byte) (o.blockSides() && wk[ba] == 0 && tr[ba] == 0 ? kind(flat[best], st[ba], o.mesaBands()) : PLAIN);
            }
        float s = (float) step, drop = 8 + 3 * s;
        int[] nb = { -1, 1, -k, k };
        for (int cj = 0; cj < n; cj++)
            for (int ci = 0; ci < n; ci++) {
                int id = (cj + 1) * k + ci + 1;
                float y = ct[id], x = ci * s, z = cj * s;
                int c = cc[id];
                q.vertex(x, y, z, 0xFF000000 | c);
                q.vertex(x, y, z + s, 0xFF000000 | c);
                q.vertex(x + s, y, z + s, 0xFF000000 | c);
                q.vertex(x + s, y, z, 0xFF000000 | c);
                for (int d = 0; d < 4; d++) {
                    int nid = id + nb[d];
                    boolean edge = d == 0 && ci == 0 || d == 1 && ci == n - 1 || d == 2 && cj == 0 || d == 3 && cj == n - 1;
                    float lo = edge ? Math.min(ct[nid], y) - drop : ct[nid];
                    if (lo >= y) continue;
                    // бок по x идёт вдоль z, бок по z вдоль x
                    float ax = d == 1 ? x + s : x, az = d == 3 ? z + s : z;
                    float bx = d < 2 ? ax : x + s, bz = d < 2 ? z + s : az;
                    sideFaces(q, ax, az, bx, bz, lo, y, c, ck[id], pack(rock(Macro.STYLES[cs[id]])), d < 2 ? 0.6 : 0.8, Math.max(1, s * 0.15f));
                }
            }
        for (int[] sp : spikes) spike(q, sp[0] * s, sp[1] * s, th[sp[2]], top[sp[2]], s, flat[j1(sp[0], sp[1], n1)]);
    }

    // Игла пика: ступени от основания до вершины, каждая уже предыдущей, с плоским верхом, как сложенные блоки.
    static void spike(Quads q, float x, float z, float base, float peak, float s, int c) {
        int tiers = (int) Math.max(2, Math.min(12, Math.ceil((peak - base) / Math.max(2, s * 0.5))));
        float lo = (float) Math.ceil(base) - 2;
        for (int t = 0; t < tiers; t++) {
            float hw = s * 0.5f * (1 - 0.85f * t / tiers);
            float hi = (float) Math.ceil(base + (peak - base) * (t + 1) / tiers);
            if (hi <= lo) continue;
            int ct = 0xFF000000 | c;
            q.vertex(x - hw, hi, z - hw, ct);
            q.vertex(x - hw, hi, z + hw, ct);
            q.vertex(x + hw, hi, z + hw, ct);
            q.vertex(x + hw, hi, z - hw, ct);
            side(q, x - hw, z - hw, x - hw, z + hw, lo, hi, shadeRgb(c, 0.6));
            side(q, x + hw, z - hw, x + hw, z + hw, lo, hi, shadeRgb(c, 0.6));
            side(q, x - hw, z - hw, x + hw, z - hw, lo, hi, shadeRgb(c, 0.8));
            side(q, x - hw, z + hw, x + hw, z + hw, lo, hi, shadeRgb(c, 0.8));
            lo = hi;
        }
    }

    private static int j1(int i, int j, int n1) { return j * n1 + i; }

    // Вода отдельным полупрозрачным слоем над дном.
    // Сухая точка рядом (берег): тут пена.
    static boolean shoreAt(int[] wcol, int id, int m) {
        for (int dj = -1; dj <= 1; dj++)
            for (int di = -1; di <= 1; di++) {
                int o = id + dj * m + di;
                if (o >= 0 && o < wcol.length && wcol[o] == 0) return true;
            }
        return false;
    }

    // Оттенок воды у берега: в тёплых морях мелководье бирюзовое, у самого берега светлая пена.
    static int shoreTint(int tint, double depth, double temp, boolean shore) {
        double[] c = unpack(tint);
        if (temp > 0.15 && depth < 5) c = mix(c, new double[] { 64, 214, 206 }, 0.55 * (1 - depth / 5) * Macro.ss(0.15, 0.5, temp));
        if (shore) c = mix(c, new double[] { 235, 242, 245 }, 0.35);
        return pack(c);
    }

    private static Quads water(int n, int m, double step, float[] h, float[] wl, int[] wcol, float[] tmp, boolean shore) {
        float s = (float) step;
        // вода: квад в каждой клетке, где мокрый хоть один угол; у сухих углов вода прозрачна (мягкий берег)
        Quads w = new Quads();
        int[] corner = { 0, m, m + 1, 1 };
        int[][] cxz = { { 0, 0 }, { 0, 1 }, { 1, 1 }, { 1, 0 } };
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int base = (j + 1) * m + i + 1;
                boolean any = false;
                for (int k = 0; k < 4; k++) if (wcol[base + corner[k]] != 0) any = true;
                if (!any) continue;
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
                    boolean foam = shore && wet && shoreAt(wcol, id, m);
                    int a = wet ? (int) (255 * Math.min(0.92, 0.5 + depth / 16 + (foam ? 0.2 : 0))) : 0;
                    double dark = 1 - 0.45 * Macro.ss(4, 40, depth);
                    int tc = shore && wet ? shoreTint(tint, depth, tmp[id], foam) : tint;
                    int rgb = MapRender.rgb(((tc >> 16) & 255) * dark, ((tc >> 8) & 255) * dark, (tc & 255) * dark);
                    w.vertex((i + cxz[k][0]) * s, wet ? wl[id] : level, (j + cxz[k][1]) * s, a << 24 | rgb);
                }
            }
        return w;
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

    // "Блочный" тайл nxn столбиков по b блоков (для подзорной трубы и скал рядом): у каждого столбика плоский верх
    // на высоте блока и боковые грани к более низким соседям, похоже на обычные чанки, но без пещер, деревьев и построек.
    public static TileMesh buildBlocky(Terrain t, Palette palette, double x0, double z0, int n, int b, Style o, BooleanSupplier cancelled) {
        int m = n + 2;
        double colorNoise = o.colorNoise();
        byte[] st = new byte[m * m];
        int[] top = new int[m * m], col = new int[m * m];
        boolean[] wet = new boolean[m * m];
        Terrain.Column c = new Terrain.Column();
        for (int j = 0; j < m; j++) {
            if (cancelled.getAsBoolean()) return null;
            for (int i = 0; i < m; i++) {
                int id = j * m + i;
                double x = x0 + (i - 1) * b, z = z0 + (j - 1) * b;
                t.column(x, z, c);
                boolean w = c.waterKind != Terrain.WATER_NONE && c.water > c.height;
                if (w && o.ice() && palette.frozen(t, c, x, z)) {
                    // лёд: сплошной на уровне воды
                    c.height = Math.floor(c.water);
                    c.waterKind = Terrain.WATER_NONE;
                    w = false;
                }
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
                double sp = colorNoise > 0 ? speck((long) Math.floor(x / b), (long) Math.floor(z / b), 0.07 * colorNoise) : 1;
                col[id] = MapRender.rgb(rgb[0] * sp, rgb[1] * sp, rgb[2] * sp);
                st[id] = (byte) c.dominant().ordinal();
            }
        }
        Quads q = new Quads();
        for (int j = 1; j <= n; j++)
            for (int i = 1; i <= n; i++) {
                int id = j * m + i, cc = col[id];
                float x = (i - 1) * b, z = (j - 1) * b, y = top[id] + (wet[id] ? -0.1f : 0);
                int ct = 0xFF000000 | cc;
                q.vertex(x, y, z, ct);
                q.vertex(x, y, z + b, ct);
                q.vertex(x + b, y, z + b, ct);
                q.vertex(x + b, y, z, ct);
                // бока с освещением как у блоков в игре: x, 0.6, z, 0.8
                int kd = o.blockSides() && !wet[id] ? kind(cc, st[id], o.mesaBands()) : PLAIN, rk = pack(rock(Macro.STYLES[st[id]]));
                float strip = 0.2f * b;
                if (top[id - 1] < top[id]) sideFaces(q, x, z, x, z + b, top[id - 1], y, cc, kd, rk, 0.6, strip);
                if (top[id + 1] < top[id]) sideFaces(q, x + b, z, x + b, z + b, top[id + 1], y, cc, kd, rk, 0.6, strip);
                if (top[id - m] < top[id]) sideFaces(q, x, z, x + b, z, top[id - m], y, cc, kd, rk, 0.8, strip);
                if (top[id + m] < top[id]) sideFaces(q, x, z + b, x + b, z + b, top[id + m], y, cc, kd, rk, 0.8, strip);
            }
        return new TileMesh(x0, z0, b, q, null);
    }

    private static void side(Quads q, float ax, float az, float bx, float bz, float lo, float hi, int c) {
        q.vertex(ax, hi, az, c);
        q.vertex(ax, lo, az, c);
        q.vertex(bx, lo, bz, c);
        q.vertex(bx, hi, bz, c);
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

    static double[] rock(Macro.Style st) {
        return switch (st) {
            case DOLOMITE -> new double[] { 205, 200, 185 };
            case GRANITE -> new double[] { 175, 175, 170 };
            case VOLCANIC -> new double[] { 70, 65, 65 };
            case KARST -> new double[] { 150, 150, 140 };
            default -> new double[] { 125, 122, 118 };
        };
    }

    // Земля цвета биома, но снег выше снеговой линии и скалы на крутых склонах, как ставит их генератор.
    static double[] land(Terrain t, double x, double z, double h, double temp, int st, double slope, int ground) {
        double snow = t.snowLine(x, z, temp);
        if (h > snow && slope < 1.1) return new double[] { 245, 248, 252 };
        double[] rock = rock(Macro.STYLES[st]);
        double[] g = { (ground >> 16) & 255, (ground >> 8) & 255, ground & 255 };
        if (h > snow - 50) g = mix(g, rock, Macro.ss(snow - 50, snow - 10, h));
        return mix(g, rock, Macro.ss(0.6, 1.0, slope));
    }

    static int pack(double[] c) { return MapRender.rgb(c[0], c[1], c[2]); }

    static double[] unpack(int c) { return new double[] { (c >> 16) & 255, (c >> 8) & 255, c & 255 }; }

    static double[] mix(double[] a, double[] b, double k) {
        return new double[] { a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k };
    }

    private static void skirt(Quads q, float ax, float az, float bx, float bz, float ha, float hb, int ca, int cb, float drop) {
        q.vertex(ax, ha, az, ca);
        q.vertex(ax, ha - drop, az, darker(ca));
        q.vertex(bx, hb - drop, bz, darker(cb));
        q.vertex(bx, hb, bz, cb);
    }

    private static int darker(int c) {
        return 0xFF000000 | (((c >> 16) & 255) * 3 / 4) << 16 | (((c >> 8) & 255) * 3 / 4) << 8 | (c & 255) * 3 / 4;
    }
}
