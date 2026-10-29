package dev.cld.tcw.gen;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Скальные формы, которых нет в карте высот: каменные арки и карнизы с навесами на обрывах.
 * add() — плотность добавляемого камня (больше нуля — камень), cut() — подрезка под карнизом (меньше нуля — воздух).
 */
public final class Rocks {
    /** Столбец рельефа по координатам (с кэшем вызывающей стороны). */
    public interface Columns { Terrain.Column at(int x, int z); }

    public static final double ARCH_CELL = 360, NONE_ADD = -1, NONE_CUT = 1;
    static final double SLOPE = 0.06;
    static final boolean OFF = Boolean.getBoolean("tcw.noRocks"), NO_LEDGES = Boolean.getBoolean("tcw.noLedges");

    final Terrain t;
    final long seed;
    final Simplex mask, phase, rough;
    private final ConcurrentHashMap<Long, Arch> arches = new ConcurrentHashMap<>();

    Rocks(Terrain t) {
        this.t = t;
        seed = t.seed * 0xC2B2AE3D27D4EB4FL + 91;
        mask = new Simplex(seed + 1); phase = new Simplex(seed + 2); rough = new Simplex(seed + 3);
    }

    // ---------- арки

    /** Полукольцо в вертикальной плоскости с ногами до земли. */
    public static final class Arch {
        public final double x, z, base, r, thick, cos, sin, tall;
        Arch(double x, double z, double base, double r, double thick, double a, double tall) {
            this.tall = tall;
            this.x = x; this.z = z; this.base = base; this.r = r; this.thick = thick;
            cos = Math.cos(a); sin = Math.sin(a);
        }
        public double top() { return base + r * tall + thick; }
    }

    private static final Arch NO_ARCH = new Arch(0, 0, 0, 0, 0, 0, 1);

    private double hd(long salt, int i, int j) { return Macro.hashD(seed ^ salt, i, j); }

    public Arch arch(int i, int j, Columns cols) {
        long key = Macro.cellKey(i, j);
        Arch a = arches.get(key);
        if (a == null) {
            if (arches.size() > 8192) arches.clear();
            a = makeArch(i, j, cols);
            arches.put(key, a);
        }
        return a == NO_ARCH ? null : a;
    }

    private Arch makeArch(int i, int j, Columns cols) {
        double cx = (i + 0.3 + 0.4 * hd(1, i, j)) * ARCH_CELL, cz = (j + 0.3 + 0.4 * hd(2, i, j)) * ARCH_CELL;
        Terrain.Column c = cols.at((int) cx, (int) cz);
        double[] w = c.style;
        double p = 0.08 + 0.55 * w[Macro.Style.MESA.ordinal()] + 0.4 * w[Macro.Style.KARST.ordinal()]
                + 0.25 * Macro.ss(0.12, 0.45, c.mountain) + 0.15 * w[Macro.Style.DOLOMITE.ordinal()];
        if (hd(3, i, j) > p) return NO_ARCH;
        double r = 9 + 17 * hd(4, i, j), thick = 2.6 + 0.12 * r * hd(5, i, j), ang = Math.PI * hd(6, i, j);
        if (c.waterKind != Terrain.WATER_NONE || c.riverDist < r + 10) return NO_ARCH;
        double hc = c.height;
        double ax = cx + Math.cos(ang) * r, az = cz + Math.sin(ang) * r, bx = cx - Math.cos(ang) * r, bz = cz - Math.sin(ang) * r;
        Terrain.Column fa = cols.at((int) ax, (int) az);
        double ha = fa.height;
        boolean wetA = fa.waterKind != Terrain.WATER_NONE || fa.riverDist < 8;
        Terrain.Column fb = cols.at((int) bx, (int) bz);
        double hb = fb.height;
        if (wetA || fb.waterKind != Terrain.WATER_NONE || fb.riverDist < 8) return NO_ARCH;
        if (Math.abs(ha - hb) > r * 0.9) return NO_ARCH; // слишком круто: одна нога висела бы
        double base = Math.max(ha, hb) - 2;
        double tall = 1.15 + 0.4 * hd(7, i, j);
        if (base < Macro.SEA + 3 || hc > base + r * tall - thick - 4) return NO_ARCH; // под аркой должен быть проём
        if (base + r * tall + thick > Macro.MAX_Y - 20) return NO_ARCH;
        return new Arch(cx, cz, base, r, thick, ang, tall);
    }

    /** Плотность арки или NONE_ADD; u, v — координаты вдоль и поперёк арки, ty — высота над основанием. */
    private double archDensity(Arch a, double u, double v, double ty) {
        double au = Math.abs(u), d;
        double th = a.thick * (1 + 0.45 * Math.pow(Math.min(1, au / a.r), 6));
        if (ty >= 0) {
            double tt = ty / a.tall, rho = Math.sqrt(u * u + tt * tt);
            d = Math.sqrt((rho - a.r) * (rho - a.r) + 0.8 * v * v) - th;
        } else {
            d = Math.sqrt((au - a.r) * (au - a.r) + v * v) - a.thick * 1.45;
        }
        d += 0.9 * rough.noise(u / 6 + ty / 9, v / 6 + 50);
        return d < 3 ? -d * 0.12 : NONE_ADD;
    }

    // ---------- столбцы: кэш на поток

    /** Что известно о столбце: высота, крутизна по соседям, карниз (полосы), арка поблизости. */
    static final class Col {
        long key = Long.MIN_VALUE;
        Object owner;
        double h, s, depth, period, ph, dx, dz;
        boolean ledges;
        Arch arch;
        double u, v;
        /** Высоты, вне которых столбец ничего не меняет. */
        double lo, hi;
    }

    private static final int CACHE = 1 << 12;

    /** Кэш столбцов потока и последний столбец: add и cut спрашивают один и тот же блок подряд. */
    static final class Cache {
        final Col[] cols = new Col[CACHE];
        Col last;
        /** Последний блок combined(). */
        int rx, ry = Integer.MIN_VALUE, rz;
        Object rOwner;
        double rv;
        { for (int k = 0; k < CACHE; k++) cols[k] = new Col(); }
    }

    private static final ThreadLocal<Cache> COLS = ThreadLocal.withInitial(Cache::new);

    private Col col(int x, int z, Columns cols, Cache cache) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        Col c = cache.last;
        if (c != null && c.key == key && c.owner == this) return c;
        long m = key * 0x9E3779B97F4A7C15L;
        c = cache.cols[(int) (m >>> 52) & (CACHE - 1)];
        cache.last = c;
        if (c.key == key && c.owner == this) return c;
        c.key = key;
        c.owner = this;
        Terrain.Column tc = cols.at(x, z);
        double h = tc.height;
        c.h = h;
        // карнизы: только на настоящих обрывах в горах и каньонах, вдали от воды
        c.ledges = false;
        c.arch = null;
        c.lo = Double.MAX_VALUE;
        c.hi = -Double.MAX_VALUE;
        boolean dry = tc.waterKind == Terrain.WATER_NONE && tc.riverDist > 30;
        double rocky = Math.max(Macro.ss(0.1, 0.35, tc.mountain), Math.max(tc.style[Macro.Style.MESA.ordinal()], tc.style[Macro.Style.KARST.ordinal()]));
        if (dry && rocky > 0.3 && !NO_LEDGES) {
            double gx = (cols.at(x + 4, z).height - cols.at(x - 4, z).height) / 8;
            double gz = (cols.at(x, z + 4).height - cols.at(x, z - 4).height) / 8;
            c.s = Math.sqrt(gx * gx + gz * gz);
            c.dx = gx / Math.max(1e-6, c.s);
            c.dz = gz / Math.max(1e-6, c.s);
            double mk = mask.noise(x / 160.0, z / 160.0);
            if (c.s > 1.2 && mk > -0.2) {
                c.depth = 3 + 5 * Macro.ss(-0.2, 0.6, mk);
                // только на ровном склоне: и выше, и ниже по склону рельеф идёт так же (не гребень, не дно)
                double up = shifted(c, x, z, c.depth + 2, cols), down = shifted(c, x, z, -c.depth - 2, cols);
                c.ledges = up - h > c.depth && h - down > c.depth;
                if (c.ledges) { c.lo = down - 1; c.hi = Math.max(up, h) + 2; }
                c.period = 22 + 14 * (phase.noise(x / 3000.0 + 30, z / 3000.0) + 1) / 2;
                c.ph = 120 * phase.noise(x / 2500.0, z / 2500.0) + 3 * phase.noise(x / 40.0, z / 40.0);
            }
        }
        // арка своей ячейки
        int ai = (int) Math.floor(x / ARCH_CELL), aj = (int) Math.floor(z / ARCH_CELL);
        Arch a = arch(ai, aj, cols);
        if (a != null) {
            double dx = x - a.x, dz = z - a.z;
            double u = dx * a.cos + dz * a.sin, v = -dx * a.sin + dz * a.cos;
            if (Math.abs(u) < a.r + a.thick * 1.5 + 4 && Math.abs(v) < a.thick * 1.6 + 4) {
                c.arch = a;
                c.u = u;
                c.v = v;
                c.lo = Math.min(c.lo, h - 5);
                c.hi = Math.max(c.hi, a.top() + a.thick + 5);
            }
        }
        return c;
    }

    /** Доля полосы: 1 — карниз, -1 — подрезка под ним, 0 — обычный склон. */
    private static double band(Col c, int y) {
        double f = (y + c.ph) / c.period;
        f -= Math.floor(f);
        if (f < 0.2) return Macro.ss(0, 0.03, f) * Macro.ss(0.2, 0.15, f);
        if (f > 0.74) return -Macro.ss(0.74, 0.8, f);
        return 0;
    }

    /** Высота рельефа в точке, сдвинутой от столбца вверх по склону (d > 0) или вниз (d < 0). */
    private static double shifted(Col c, int x, int z, double d, Columns cols) {
        return cols.at((int) Math.round(x + c.dx * d), (int) Math.round(z + c.dz * d)).height;
    }

    /**
     * Одно значение на блок вместо двух (так генерация быстрее): 0 — ничего, A + 2 — добавленный камень A,
     * C - 2 — подрезка C. Итоговая плотность: max(min(F, R + 2), R - 2).
     */
    public double combined(int x, int y, int z, Columns cols) {
        if (OFF) return 0;
        // rock дважды входит в final_density (max(min(F, R + 2), R - 2)) и спрашивается для одного блока подряд
        Cache cache = COLS.get();
        if (cache.rx == x && cache.ry == y && cache.rz == z && cache.rOwner == this) return cache.rv;
        Col c = col(x, z, cols, cache);
        double v = 0;
        // вне [lo, hi] столбец ничего не меняет: ни камня, ни подрезки (подрезка тоже только при c.lo <= y)
        if (y >= c.lo && y <= c.hi) {
            double a = add(c, x, y, z, cols);
            if (a > NONE_ADD) v = a + 2;
            else {
                double u = cut(c, x, y, z, cols);
                v = u < NONE_CUT ? u - 2 : 0;
            }
        }
        cache.rx = x; cache.ry = y; cache.rz = z; cache.rOwner = this; cache.rv = v;
        return v;
    }

    public double add(int x, int y, int z, Columns cols) {
        if (OFF) return NONE_ADD;
        return add(col(x, z, cols, COLS.get()), x, y, z, cols);
    }

    private double add(Col c, int x, int y, int z, Columns cols) {
        if (y < c.lo || y > c.hi) return NONE_ADD;
        double best = NONE_ADD;
        if (c.ledges && y > c.h - 1) {
            // карниз: в полосе склон выдвинут наружу — берём высоту чуть выше по склону
            double b = band(c, y);
            if (b > 0) {
                double he = shifted(c, x, z, c.depth * b, cols);
                if (y < he) best = Math.min(0.3, (he - y) * SLOPE);
            }
        }
        if (c.arch != null) {
            double ty = y - c.arch.base;
            if (ty < c.arch.r * c.arch.tall + c.arch.thick * 1.5 + 4 && y > c.h - 4) best = Math.max(best, archDensity(c.arch, c.u, c.v, ty));
        }
        return best;
    }

    public double cut(int x, int y, int z, Columns cols) {
        if (OFF) return NONE_CUT;
        return cut(col(x, z, cols, COLS.get()), x, y, z, cols);
    }

    private double cut(Col c, int x, int y, int z, Columns cols) {
        if (!c.ledges || y > c.h + 1 || y < c.lo) return NONE_CUT;
        // подрезка: под карнизом склон отступает внутрь — высота чуть ниже по склону
        double b = band(c, y);
        if (b >= 0) return NONE_CUT;
        double he = shifted(c, x, z, c.depth * 0.8 * b, cols);
        return y > he ? Math.max(-0.3, (he - y) * SLOPE) : NONE_CUT;
    }
}
