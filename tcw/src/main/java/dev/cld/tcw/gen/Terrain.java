package dev.cld.tcw.gen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Итоговая высота поверхности: крупный рельеф + эрозия + скалы + долины рек, и где стоит вода. */
public final class Terrain {
    public static final int WATER_NONE = 0, WATER_OCEAN = 1, WATER_RIVER = 2, WATER_LAKE = 3;

    static final int CACHE = Integer.getInteger("tcw.hydroCache", 6);
    public final Macro macro;
    public final Caves caves;
    final long seed;
    final Simplex fine, rough, meanderA, meanderB, snowN;
    private final Map<Long, CompletableFuture<Hydro>> hydros = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, CompletableFuture<Hydro>> e) { return size() > CACHE; }
    };

    public Terrain(long seed) {
        this.seed = seed;
        macro = new Macro(seed);
        long s = seed * 0xD1B54A32D192ED03L;
        fine = new Simplex(s + 1); rough = new Simplex(s + 2); meanderA = new Simplex(s + 3); meanderB = new Simplex(s + 4);
        snowN = new Simplex(s + 5);
        caves = new Caves(this);
    }

    public long seed() { return seed; }

    public Hydro hydro(int ci, int cj) {
        if (!macro.landCell(ci, cj)) return null;
        long key = ((long) ci << 32) ^ (cj & 0xffffffffL);
        CompletableFuture<Hydro> f, mine = null;
        synchronized (hydros) {
            f = hydros.get(key);
            if (f == null) hydros.put(key, f = mine = new CompletableFuture<>());
        }
        if (mine != null) {
            try { mine.complete(new Hydro(macro, ci, cj)); }
            catch (Throwable t) { mine.completeExceptionally(t); throw t; }
        }
        return f.join();
    }

    public static final class Column {
        public double height, macroHeight, water, temp, humid, mountain, coast, riverDist, slope;
        public int waterKind;
        public final double[] style = new double[Macro.STYLES.length];
        final Macro.Sample s = new Macro.Sample(), sx = new Macro.Sample(), sz = new Macro.Sample();
        public Macro.Style dominant() {
            int b = 0;
            for (int i = 1; i < style.length; i++) if (style[i] > style[b]) b = i;
            return Macro.STYLES[b];
        }
    }

    public Column column(double x, double z, Column c) {
        Macro.Sample s = macro.sample(x, z, c.s);
        double h0 = s.height;
        double gx = (macro.sample(x + 6, z, c.sx).height - h0) / 6, gz = (macro.sample(x, z + 6, c.sz).height - h0) / 6;
        double slope = Math.sqrt(gx * gx + gz * gz);
        c.macroHeight = h0;
        c.temp = s.temp;
        c.humid = s.humid;
        c.mountain = s.mountain;
        c.coast = s.coast;
        c.slope = slope;
        System.arraycopy(s.style, 0, c.style, 0, s.style.length);
        double[] w = s.style;
        int MESA = Macro.Style.MESA.ordinal(), KARST = Macro.Style.KARST.ordinal(), DOLO = Macro.Style.DOLOMITE.ordinal(),
                GRAN = Macro.Style.GRANITE.ordinal(), PATA = Macro.Style.PATAGONIA.ordinal();

        // эрозия: борозды вдоль склона (фильтр Клея Джона)
        double h = h0;
        double land = Macro.ss(-20, 60, s.coast);
        if (slope > 0.02) {
            double dx = gz / slope, dz = -gx / slope;
            double sum = 0, a = 1, f = 1.0 / 55, hdx = 0, hdz = 0, norm = 0;
            for (int o = 0; o < 4; o++) {
                double ddx = dx + hdz * 0.6, ddz = dz - hdx * 0.6, len = Math.max(1e-6, Math.sqrt(ddx * ddx + ddz * ddz));
                double[] e = gully(x * f, z * f, ddx / len, ddz / len);
                sum += e[0] * a;
                hdx += e[1] * a;
                hdz += e[2] * a;
                norm += a;
                a *= 0.45;
                f *= 2;
            }
            double strength = Macro.ss(0.04, 0.7, slope) * (10 + 0.11 * Math.max(0, h0 - Macro.SEA))
                    * (1 - 0.6 * w[GRAN] - 0.5 * w[MESA] + 0.3 * w[PATA] - 0.55 * w[Macro.Style.VOLCANIC.ordinal()]);
            h += strength * (sum / norm - 0.25) * land;
        }
        // шероховатость
        h += Macro.fbm(rough, x / 46, z / 46, 3) * (1.5 + 9 * s.mountain + 6 * Math.min(1, slope)) * land;
        h += Macro.fbm(fine, x / 170, z / 170, 2) * 4 * land;

        // карстовые столбы и доломитовые шпили
        if (w[KARST] > 0.05 && s.coast > 80) h = Math.max(h, pillars(x, z, h, 30, 0.2, 6, 11, 35, 110, w[KARST] * Macro.ss(80, 400, s.coast), 0xA1L));
        if (w[DOLO] > 0.05 && s.mountain > 0.25) h = Math.max(h, pillars(x, z, h, 70, 0.12, 7, 14, 30, 90, w[DOLO] * Macro.ss(0.25, 0.6, s.mountain), 0xA2L));
        if (w[PATA] > 0.05 && s.mountain > 0.4) h = Math.max(h, pillars(x, z, h, 90, 0.07, 6, 10, 40, 120, w[PATA] * Macro.ss(0.4, 0.8, s.mountain), 0xA3L));

        // реки и озёра
        c.water = Double.NaN;
        c.waterKind = WATER_NONE;
        c.riverDist = Double.POSITIVE_INFINITY;
        Hydro hy = s.cont.landCell ? hydro(s.cont.ci, s.cont.cj) : null;
        if (hy != null) {
            int[] bk = hy.bucket(x, z);
            if (bk != null) {
                double mx = x + 16 * meanderA.noise(x / 120, z / 120), mz = z + 16 * meanderB.noise(x / 120, z / 120);
                double cold = Macro.ss(-0.2, -0.5, s.temp) * Macro.ss(0.1, 0.4, s.mountain + 0.3 * Macro.ss(1500, 300, s.coast));
                double canyon = w[MESA] * Macro.ss(0.4, 0.8, w[MESA]);
                double hmin = h;
                for (int si : bk) {
                    double ax = hy.sax[si], az = hy.saz[si], ex = hy.sbx[si] - ax, ez = hy.sbz[si] - az;
                    double l2 = ex * ex + ez * ez;
                    double t = l2 > 0 ? Macro.clamp(((mx - ax) * ex + (mz - az) * ez) / l2, 0, 1) : 0;
                    double px = ax + ex * t - mx, pz = az + ez * t - mz, d = Math.sqrt(px * px + pz * pz);
                    if (d >= hy.sR[si]) continue;
                    double ba = hy.sba[si], bb = hy.sbb[si];
                    double b = ba - bb > 3 ? (t < 0.7 ? ba : t > 0.78 ? bb : ba + (bb - ba) * Macro.ss(0.7, 0.78, t)) : ba + (bb - ba) * t;
                    double wch = hy.sw[si], relief = h - b;
                    double hc;
                    if (d <= wch) {
                        hc = b - hy.sdep[si] * (1 - (d / wch) * (d / wch));
                        if (d < c.riverDist) c.riverDist = d;
                        if (hc < b - 0.6 && (c.waterKind != WATER_RIVER || b - 0.5 > c.water)) {
                            c.water = b - 0.5;
                            c.waterKind = WATER_RIVER;
                        }
                    } else {
                        if (relief <= 0) continue;
                        double k = 2.3 * (1 - cold - canyon) + 1.3 * cold + 0.45 * canyon;
                        double reff = Math.min(hy.sR[si], wch + Math.max(5, relief * k) + 12 * cold);
                        if (d >= reff) continue;
                        double tt = (d - wch) / (reff - wch);
                        double pv = Math.pow(tt, 0.9) * (0.4 + 0.6 * tt * (3 - 2 * tt));
                        double pu = Math.pow(tt, 1.8);
                        double pc = canyonProfile(tt, relief);
                        double prof = pv * (1 - cold - canyon) + pu * cold + pc * canyon;
                        hc = b + relief * prof;
                    }
                    if (hc < hmin) hmin = hc;
                }
                h = hmin;
            }
            double ll = hy.lakeLevel(x, z);
            if (!Double.isNaN(ll) && h < ll - 0.3 && (c.waterKind == WATER_NONE || ll > c.water)) {
                c.water = Math.floor(ll);
                c.waterKind = WATER_LAKE;
            }
        }
        if (h < Macro.SEA - 0.3 && c.waterKind == WATER_NONE) {
            c.water = Macro.SEA;
            c.waterKind = WATER_OCEAN;
        }
        c.height = Math.min(h, Macro.MAX_Y - 4 - 6 * Macro.ss(Macro.MAX_Y - 60, Macro.MAX_Y + 40, h));
        c.height = Math.max(c.height, Macro.MIN_Y + 8);
        return c;
    }

    private final ThreadLocal<Macro.Cont> contTmp = ThreadLocal.withInitial(Macro.Cont::new);

    /**
     * Вода рек и озёр в столбце без расчёта рельефа (для фичи воды): out = {уровень воды, дно русла}.
     * Возвращает вид воды; дно русла NaN для озёр.
     */
    public int water(double x, double z, double[] out) {
        out[0] = out[1] = Double.NaN;
        Macro.Cont c = macro.cont(x, z, contTmp.get());
        if (!c.landCell) return WATER_NONE;
        Hydro hy = hydro(c.ci, c.cj);
        int kind = WATER_NONE;
        int[] bk = hy.bucket(x, z);
        if (bk != null) {
            double mx = x + 16 * meanderA.noise(x / 120, z / 120), mz = z + 16 * meanderB.noise(x / 120, z / 120);
            for (int si : bk) {
                double ax = hy.sax[si], az = hy.saz[si], ex = hy.sbx[si] - ax, ez = hy.sbz[si] - az;
                double l2 = ex * ex + ez * ez;
                double t = l2 > 0 ? Macro.clamp(((mx - ax) * ex + (mz - az) * ez) / l2, 0, 1) : 0;
                double px = ax + ex * t - mx, pz = az + ez * t - mz, d = Math.sqrt(px * px + pz * pz);
                double wch = hy.sw[si];
                if (d > wch) continue;
                double ba = hy.sba[si], bb = hy.sbb[si];
                double b = ba - bb > 3 ? (t < 0.7 ? ba : t > 0.78 ? bb : ba + (bb - ba) * Macro.ss(0.7, 0.78, t)) : ba + (bb - ba) * t;
                double bottom = b - hy.sdep[si] * (1 - (d / wch) * (d / wch));
                if (kind != WATER_RIVER || b - 0.5 > out[0]) {
                    out[0] = b - 0.5;
                    out[1] = bottom;
                    kind = WATER_RIVER;
                }
            }
        }
        double ll = hy.lakeLevel(x, z);
        if (!Double.isNaN(ll) && (kind == WATER_NONE || ll > out[0])) {
            out[0] = Math.floor(ll);
            out[1] = Double.NaN;
            kind = WATER_LAKE;
        }
        return kind;
    }

    public static final int P_CONTINENTS = 0, P_EROSION = 1, P_RIDGES = 2, P_TEMPERATURE = 3, P_VEGETATION = 4, P_DEPTH = 5;

    /** Параметры климата для ванильного выбора биомов, согласованные с нашим рельефом. */
    public double climate(Column c, int p, double x, double y, double z) {
        double h = c.height, elev = h - Macro.SEA;
        double[] w = c.style;
        switch (p) {
            case P_CONTINENTS: {
                if (c.waterKind == WATER_OCEAN) return h < 25 ? -0.7 : -0.32;
                if (elev < 5 && c.coast < 120) return -0.15;
                return -0.1 + 0.95 * Macro.ss(0, 4500, c.coast);
            }
            case P_EROSION: {
                if (c.waterKind == WATER_OCEAN) return 0.2;
                double rel = 0.55 * Macro.ss(25, 420, elev) + 0.45 * Math.max(c.mountain, Macro.ss(0.3, 1.2, c.slope));
                // болота (эрозия > 0.55) только в сырых низинах у воды
                if (rel < 0.05 && elev < 10 && c.humid > 0.15 && c.riverDist < 60) return 0.6;
                return 0.4 - 1.35 * rel;
            }
            case P_RIDGES: {
                if (c.waterKind == WATER_RIVER || c.waterKind == WATER_LAKE || c.riverDist < 5) return 0;
                double v = elev > 330 && c.mountain > 0.35 ? 0.66 : elev > 160 ? 0.48 : elev > 55 ? 0.33 : 0.16;
                return snowN.noise(x / 700 + 100, z / 700) > 0 ? v : -v;
            }
            case P_TEMPERATURE:
                return c.temp;
            case P_VEGETATION:
                return c.humid;
            default:
                return (h - y) / 128.0;
        }
    }

    static double canyonProfile(double t, double relief) {
        double steps = Math.max(1, relief / 22), q = t * steps, f = Math.floor(q);
        return Math.min(1, (f + Macro.ss(0.15, 0.4, q - f)) / steps);
    }

    /** Борозда-волна вдоль направления (Clay John, «eroded terrain noise»): {значение, d/dx, d/dz}. */
    double[] gully(double px, double pz, double dirx, double dirz) {
        double ix = Math.floor(px), iz = Math.floor(pz), fx = px - ix, fz = pz - iz;
        double va = 0, vb = 0, vc = 0, wt = 0, tau = Math.PI * 2;
        for (int i = -2; i <= 1; i++)
            for (int j = -2; j <= 1; j++) {
                long cx = (long) ix - i, cz = (long) iz - j;
                double hx = (Macro.hashD(seed, cx, cz) - 0.5), hz = (Macro.hashD(seed ^ 0x5bd1e995L, cx, cz) - 0.5);
                double ppx = fx + i - hx, ppz = fz + j - hz;
                double d = ppx * ppx + ppz * ppz, w = Math.exp(-d * 2);
                wt += w;
                double mag = ppx * dirx + ppz * dirz;
                va += Math.cos(mag * tau) * w;
                double sn = -Math.sin(mag * tau) * w;
                vb += sn * dirx;
                vc += sn * dirz;
            }
        return new double[] { va / wt, vb / wt, vc / wt };
    }

    /** Отдельно стоящие каменные столбы: max(h, h + высота столба). */
    double pillars(double x, double z, double h, double cell, double prob, double r0, double r1, double h0, double h1, double weight, long salt) {
        int gi = (int) Math.floor(x / cell), gj = (int) Math.floor(z / cell);
        double best = h;
        for (int i = gi - 1; i <= gi + 1; i++)
            for (int j = gj - 1; j <= gj + 1; j++) {
                if (Macro.hashD(seed ^ salt, i, j) > prob * weight) continue;
                double cx = (i + 0.2 + 0.6 * Macro.hashD(seed ^ salt ^ 1, i, j)) * cell;
                double cz = (j + 0.2 + 0.6 * Macro.hashD(seed ^ salt ^ 2, i, j)) * cell;
                double r = r0 + (r1 - r0) * Macro.hashD(seed ^ salt ^ 3, i, j);
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d >= r) continue;
                double ph = (h0 + (h1 - h0) * Macro.hashD(seed ^ salt ^ 4, i, j)) * Math.min(1, weight * 1.5);
                double top = h + ph * (1 - 0.25 * (d / r) * (d / r)) * Macro.ss(r, r - 2.5, d);
                best = Math.max(best, top);
            }
        return best;
    }

    /** Снеговая линия по температуре. */
    public double snowLine(double x, double z, double temp) {
        return 330 + 240 * temp + 18 * snowN.noise(x / 300, z / 300);
    }
}
