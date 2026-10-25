package dev.cld.tcw.gen;

/**
 * Крупный рельеф (детали крупнее ~64 блоков): континенты, океаны, хребты, стили гор, климат.
 * По нему же строится сток рек, поэтому функция детерминирована и не зависит от мелких деталей.
 */
public final class Macro {
    public static final int SEA = 63, MIN_Y = -128, MAX_Y = 640;
    public static final double CS = 14000, STYLE_CELL = 2600, CONE_CELL = 1500;

    public enum Style { ALPINE, PATAGONIA, DOLOMITE, GRANITE, VOLCANIC, MESA, PLATEAU, KARST }
    public static final Style[] STYLES = Style.values();

    final long seed;
    public final Options opt;
    public final double cs;
    final Simplex wA, wB, wC, wD, coastN, hills, ridge1, ridge2, rmf, peak, temp, humid, trench, island, lift, needle, rWarpA, rWarpB;

    public Macro(long seed) { this(seed, Options.DEFAULT); }

    public Macro(long seed, Options opt) {
        this.seed = seed;
        this.opt = opt;
        cs = opt.cs();
        long s = seed * 0x9E3779B97F4A7C15L;
        wA = new Simplex(s + 1); wB = new Simplex(s + 2); wC = new Simplex(s + 3); wD = new Simplex(s + 4);
        coastN = new Simplex(s + 5); hills = new Simplex(s + 6); ridge1 = new Simplex(s + 7); ridge2 = new Simplex(s + 8);
        rmf = new Simplex(s + 9); peak = new Simplex(s + 10); temp = new Simplex(s + 11); humid = new Simplex(s + 12);
        trench = new Simplex(s + 13); island = new Simplex(s + 14); lift = new Simplex(s + 15); needle = new Simplex(s + 16);
        rWarpA = new Simplex(s + 17); rWarpB = new Simplex(s + 18);
    }

    // ---------- утилиты
    public static double ss(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
    public static double clamp(double x, double a, double b) { return x < a ? a : x > b ? b : x; }

    static double fbm(Simplex n, double x, double z, int oct) {
        double v = 0, a = 1, sum = 0;
        for (int i = 0; i < oct; i++) {
            v += a * n.noise(x, z);
            sum += a;
            a *= 0.5;
            x = x * 2.03 + 17.1;
            z = z * 2.03 - 31.7;
        }
        return v / sum;
    }

    /** Ридж-мультифрактал Масгрейва, примерно [0, 1]. */
    static double ridged(Simplex n, double x, double z, int oct) {
        double v = 0, w = 1, a = 1, sum = 0;
        for (int i = 0; i < oct; i++) {
            double s = 1 - Math.abs(n.noise(x, z));
            s *= s;
            s *= w;
            w = clamp(s * 2, 0, 1);
            v += s * a;
            sum += a;
            a *= 0.5;
            x = x * 2.1 + 5.3;
            z = z * 2.1 - 9.1;
        }
        return v / sum * 1.6;
    }

    public static long hash(long seed, long a, long b) {
        long h = seed ^ (a * 0x632BE59BD9B4E019L) ^ (b * 0x9E3779B97F4A7C15L);
        h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 29)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 32);
    }
    public static double hashD(long seed, long a, long b) { return (hash(seed, a, b) >>> 11) * 0x1.0p-53; }

    // ---------- климат, [-1, 1]
    public double temperature(double x, double z) { return clamp(fbm(temp, x / (8000 * opt.climateMul()), z / (8000 * opt.climateMul()), 3) * 1.9, -1, 1); }
    public double humidity(double x, double z) { return clamp(fbm(humid, x / (6000 * opt.climateMul()), z / (6000 * opt.climateMul()), 3) * 1.9, -1, 1); }

    // ---------- континенты
    /** Результат разбора точки: ячейка континента и расстояние до берега (плюс — суша). */
    public static final class Cont {
        public int ci, cj;
        public boolean landCell;
        public double coast, qx, qz;
    }

    public double cellCenterX(int i, int j) { return (i + 0.5 + 0.6 * (hashD(seed, i * 2L, j) - 0.5)) * cs; }
    public double cellCenterZ(int i, int j) { return (j + 0.5 + 0.6 * (hashD(seed, i * 2L + 1, j) - 0.5)) * cs; }
    public boolean landCell(int i, int j) { return hashD(seed ^ 0x55L, i, j) > 0.15; }

    public Cont cont(double x, double z, Cont out) {
        double qx = x + 1400 * wA.noise(x / 3800, z / 3800) + 160 * wC.noise(x / 650, z / 650);
        double qz = z + 1400 * wB.noise(x / 3800, z / 3800) + 160 * wD.noise(x / 650, z / 650);
        int gi = (int) Math.floor(qx / cs), gj = (int) Math.floor(qz / cs);
        int bi = 0, bj = 0;
        double bd = Double.MAX_VALUE;
        for (int i = gi - 1; i <= gi + 1; i++)
            for (int j = gj - 1; j <= gj + 1; j++) {
                double dx = qx - cellCenterX(i, j), dz = qz - cellCenterZ(i, j), d = dx * dx + dz * dz;
                if (d < bd) { bd = d; bi = i; bj = j; }
            }
        double cx = cellCenterX(bi, bj), cz = cellCenterZ(bi, bj), edge = Double.MAX_VALUE, coast = Double.MAX_VALUE;
        double wn = 550 * coastN.noise(qx / 1800, qz / 1800) + 120 * coastN.noise(qx / 300 + 40, qz / 300);
        for (int i = bi - 2; i <= bi + 2; i++)
            for (int j = bj - 2; j <= bj + 2; j++) {
                if (i == bi && j == bj) continue;
                double ox = cellCenterX(i, j), oz = cellCenterZ(i, j);
                double dx = qx - ox, dz = qz - oz, d2 = dx * dx + dz * dz;
                double ex = ox - cx, ez = oz - cz, len = Math.sqrt(ex * ex + ez * ez);
                double e = (d2 - bd) / (2 * len);
                edge = Math.min(edge, e);
                // ширина пролива своя у каждой пары соседей: от узких проливов до морей
                long a1 = (long) bi * 73856093L ^ (long) bj * 19349663L, a2 = (long) i * 73856093L ^ (long) j * 19349663L;
                double ph = hashD(seed ^ 0x5EAL, Math.min(a1, a2), Math.max(a1, a2));
                double width = (600 + 2600 * ph * ph * ph) * Math.min(1, cs / 14000);
                coast = Math.min(coast, e - width - wn);
            }
        out.ci = bi; out.cj = bj; out.qx = qx; out.qz = qz;
        out.landCell = landCell(bi, bj);
        out.coast = out.landCell ? coast : -edge - 1500;
        return out;
    }

    // ---------- стили
    private final java.util.concurrent.ConcurrentHashMap<Long, Style> styleCache = new java.util.concurrent.ConcurrentHashMap<>();

    public Style cellStyle(int i, int j) {
        long key = ((long) i << 32) ^ (j & 0xffffffffL);
        Style st = styleCache.get(key);
        if (st == null) {
            if (styleCache.size() > 4096) styleCache.clear();
            styleCache.put(key, st = computeStyle(i, j));
        }
        return st;
    }

    private Style computeStyle(int i, int j) {
        double cx = (i + 0.5 + 0.7 * (hashD(seed ^ 0x77L, i, j) - 0.5)) * STYLE_CELL;
        double cz = (j + 0.5 + 0.7 * (hashD(seed ^ 0x78L, i, j) - 0.5)) * STYLE_CELL;
        double t = temperature(cx, cz), u = humidity(cx, cz), r = hashD(seed ^ 0x79L, i, j);
        if (t < -0.3) {
            if (u < -0.3 && r < 0.3) return Style.PLATEAU;
            return r < 0.45 ? Style.ALPINE : r < 0.75 ? Style.PATAGONIA : r < 0.9 ? Style.VOLCANIC : Style.GRANITE;
        }
        if (t > 0.3 && u < -0.05) return r < 0.65 ? Style.MESA : r < 0.85 ? Style.GRANITE : Style.VOLCANIC;
        if (t > 0.3) return r < 0.5 ? Style.KARST : r < 0.8 ? Style.ALPINE : Style.VOLCANIC;
        if (u < -0.35 && r < 0.25) return Style.PLATEAU;
        return r < 0.33 ? Style.ALPINE : r < 0.56 ? Style.DOLOMITE : r < 0.76 ? Style.GRANITE : r < 0.88 ? Style.PATAGONIA : Style.VOLCANIC;
    }

    /** Веса стилей в точке (мягкие переходы между ячейками). */
    public void styleWeights(double x, double z, double[] w) {
        java.util.Arrays.fill(w, 0);
        double qx = x + 300 * wC.noise(x / 900 + 7, z / 900), qz = z + 300 * wD.noise(x / 900 + 7, z / 900);
        int gi = (int) Math.floor(qx / STYLE_CELL), gj = (int) Math.floor(qz / STYLE_CELL);
        double[] ds = new double[9];
        int[] st = new int[9];
        double dmin = Double.MAX_VALUE;
        int k = 0;
        for (int i = gi - 1; i <= gi + 1; i++)
            for (int j = gj - 1; j <= gj + 1; j++, k++) {
                double cx = (i + 0.5 + 0.7 * (hashD(seed ^ 0x77L, i, j) - 0.5)) * STYLE_CELL;
                double cz = (j + 0.5 + 0.7 * (hashD(seed ^ 0x78L, i, j) - 0.5)) * STYLE_CELL;
                ds[k] = Math.sqrt((qx - cx) * (qx - cx) + (qz - cz) * (qz - cz));
                st[k] = cellStyle(i, j).ordinal();
                dmin = Math.min(dmin, ds[k]);
            }
        double sum = 0;
        for (k = 0; k < 9; k++) {
            double e = Math.exp(-(ds[k] - dmin) / 220);
            w[st[k]] += e;
            sum += e;
        }
        for (int i = 0; i < w.length; i++) w[i] /= sum;
    }

    // ---------- высота крупного рельефа
    public static final class Sample {
        public double height, coast, mountain, temp, humid;
        public final double[] style = new double[STYLES.length];
        public final Cont cont = new Cont();
    }

    public Sample sample(double x, double z, Sample s) {
        Cont c = cont(x, z, s.cont);
        s.coast = c.coast;
        s.temp = temperature(x, z);
        s.humid = humidity(x, z);
        styleWeights(x, z, s.style);
        // климат подтягивается под стиль гор: каньоны жаркие и сухие, карст жаркий и влажный
        double wm = s.style[Style.MESA.ordinal()], wk = s.style[Style.KARST.ordinal()];
        s.temp += (0.72 - s.temp) * wm + (0.65 - s.temp) * wk;
        s.humid += (-0.6 - s.humid) * wm + (0.5 - s.humid) * wk;
        double cd = c.coast;

        // низменности и шельф
        double base;
        if (cd >= 0) {
            double in = ss(0, 3500, cd);
            base = SEA + 3 + 55 * in + (4 + 18 * in) * fbm(hills, x / 900, z / 900, 3);
        } else {
            double off = -cd;
            base = SEA - 3 - 30 * ss(0, 260, off) - 75 * ss(200, 1600, off) + 8 * fbm(hills, x / 500, z / 500, 2);
            double tr = 1 - Math.abs(trench.noise(x / 2600, z / 2600));
            base -= 70 * ss(0.92, 0.99, tr) * ss(1200, 2600, off);
            double is = fbm(island, x / 900, z / 900, 3);
            base += ss(0.5, 0.68, is) * ss(500, 1100, off) * (95 + 40 * hills.noise(x / 300, z / 300));
        }
        // коралловые отмели и прибрежная гладкость
        double land = ss(-60, 120, cd);

        // хребты в искажённых координатах
        double rx = x + 700 * rWarpA.noise(x / 2200, z / 2200), rz = z + 700 * rWarpB.noise(x / 2200, z / 2200);
        double r1 = 1 - Math.abs(ridge1.noise(rx / 5200, rz / 5200));
        double r2 = 1 - Math.abs(ridge2.noise(rx / 3300 + 50, rz / 3300));
        double rs = opt.ridgeShift();
        double m = Math.max(ss(0.64 + rs, 0.93 + rs * 0.5, r1), 0.85 * ss(0.76 + rs, 0.96 + rs * 0.3, r2));
        m *= ss(-150, 700, cd);
        s.mountain = m;

        double pk = 0.5 + 0.5 * fbm(peak, x / 1700, z / 1700, 2);
        double rm = ridged(rmf, rx / 1150, rz / 1150, 4);
        double mount = Math.pow(m, 1.1) * (200 + 360 * pk) * (0.42 + 0.58 * rm) * opt.heightMul();
        double foot = Math.pow(m, 0.5) * 45 * (0.5 + 0.5 * fbm(hills, x / 400 + 9, z / 400, 2));

        double[] w = s.style;
        double inl = ss(100, 1800, cd);
        double hm = 0, lifted = 0;
        for (int i = 0; i < w.length; i++) {
            if (w[i] < 1e-3) continue;
            double v, l = 0;
            switch (STYLES[i]) {
                case PATAGONIA -> {
                    double nd = ridged(needle, rx / 420, rz / 420, 2);
                    v = mount * (0.55 + 0.75 * nd * nd * nd);
                }
                case DOLOMITE -> {
                    double st = 70 + 25 * hills.noise(x / 600, z / 600), q = mount / st, f = Math.floor(q);
                    v = (f + ss(0.5, 0.85, q - f)) * st * 0.75 + 0.25 * mount;
                    l = 40 * inl;
                }
                case GRANITE -> {
                    double mx = 200 + 360 * pk, t = mount / mx;
                    v = mx * (0.25 * t + 0.75 * ss(0.18, 0.42, t) * (0.6 + 0.4 * t));
                }
                case VOLCANIC -> v = 0.5 * mount + cones(x, z) * land;
                case MESA -> {
                    double st = 26, mm = mount * 0.45, q = mm / st, f = Math.floor(q);
                    v = (f + ss(0.6, 0.85, q - f)) * st;
                    double p = 0.5 + 0.5 * fbm(lift, x / 2000, z / 2000, 2);
                    double pl = (70 + 110 * p) * inl, pq = pl / 30, pf = Math.floor(pq);
                    l = (pf + ss(0.65, 0.9, pq - pf)) * 30;
                }
                case PLATEAU -> {
                    v = mount * 0.55;
                    l = (210 + 60 * fbm(lift, x / 1600, z / 1600, 3)) * inl;
                }
                case KARST -> v = mount * 0.6;
                default -> v = mount;
            }
            hm += w[i] * v;
            lifted += w[i] * l;
        }
        double hh = base + (hm + foot) * land + lifted;
        // мягкий потолок под верх мира
        if (hh > 590) hh = 590 + 42 * Math.tanh((hh - 590) / 42);
        s.height = hh;
        return s;
    }

    /** Вулканы: конусы с кратерами, по одному на ячейку сетки CONE_CELL (не в каждой). */
    public double cones(double x, double z) {
        int gi = (int) Math.floor(x / CONE_CELL), gj = (int) Math.floor(z / CONE_CELL);
        double best = 0;
        for (int i = gi - 1; i <= gi + 1; i++)
            for (int j = gj - 1; j <= gj + 1; j++) {
                if (hashD(seed ^ 0xC0L, i, j) > 0.55) continue;
                double cx = (i + 0.2 + 0.6 * hashD(seed ^ 0xC1L, i, j)) * CONE_CELL;
                double cz = (j + 0.2 + 0.6 * hashD(seed ^ 0xC2L, i, j)) * CONE_CELL;
                double hh = 220 + 300 * hashD(seed ^ 0xC3L, i, j), r = hh * 2.6;
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d >= r) continue;
                double t = 1 - d / r, v = hh * Math.pow(t, 1.6);
                double rc = 30 + hh * 0.18;
                if (d < rc) v -= (hh * 0.22) * (1 - (d / rc) * (d / rc));
                best = Math.max(best, v);
            }
        return best;
    }

    /** Вулкан ячейки (i, j): {cx, cz, высота} или null, если в ячейке его нет. */
    public double[] cone(int i, int j) {
        if (hashD(seed ^ 0xC0L, i, j) > 0.55) return null;
        double cx = (i + 0.2 + 0.6 * hashD(seed ^ 0xC1L, i, j)) * CONE_CELL;
        double cz = (j + 0.2 + 0.6 * hashD(seed ^ 0xC2L, i, j)) * CONE_CELL;
        return new double[] { cx, cz, 220 + 300 * hashD(seed ^ 0xC3L, i, j) };
    }

    /** Центр кратера ближайшего вулкана, если точка внутри кратера: {cx, cz, rc, rimHeight}. */
    public double[] crater(double x, double z) {
        int gi = (int) Math.floor(x / CONE_CELL), gj = (int) Math.floor(z / CONE_CELL);
        for (int i = gi - 1; i <= gi + 1; i++)
            for (int j = gj - 1; j <= gj + 1; j++) {
                if (hashD(seed ^ 0xC0L, i, j) > 0.55) continue;
                double cx = (i + 0.2 + 0.6 * hashD(seed ^ 0xC1L, i, j)) * CONE_CELL;
                double cz = (j + 0.2 + 0.6 * hashD(seed ^ 0xC2L, i, j)) * CONE_CELL;
                double hh = 220 + 300 * hashD(seed ^ 0xC3L, i, j), rc = 30 + hh * 0.18;
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d < rc) return new double[] { cx, cz, rc, hh };
            }
        return null;
    }
}
