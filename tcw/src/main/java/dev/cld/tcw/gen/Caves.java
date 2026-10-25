package dev.cld.tcw.gen;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Свои пещеры поверх ванильных: редкие гигантские залы (глубоко внизу с лавой), подземные реки,
 * ледяные ходы под ледниками и карстовые провалы-сеноты. Плотность: меньше нуля внутри пещеры.
 */
public final class Caves {
    public static final int NONE = 0, WATER = 1, LAVA = 2;
    public static final double HALL_CELL = 1100, SINK_CELL = 320, SOLID = 2.5;
    static final int MIN_Y = Macro.MIN_Y;

    final Terrain t;
    final long seed;
    final Simplex gal, galWx, galWz, galBase, galWide, galWet, hallN, hallN2, ice, iceW, sinkN;
    private final ConcurrentHashMap<Long, Hall> halls = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Sink> sinks = new ConcurrentHashMap<>();

    Caves(Terrain t) {
        this.t = t;
        seed = t.seed * 0x9E3779B97F4A7C15L + 77;
        gal = new Simplex(seed + 1); galWx = new Simplex(seed + 2); galWz = new Simplex(seed + 3); galBase = new Simplex(seed + 4);
        galWide = new Simplex(seed + 5); galWet = new Simplex(seed + 6); hallN = new Simplex(seed + 7); hallN2 = new Simplex(seed + 8);
        ice = new Simplex(seed + 9); iceW = new Simplex(seed + 10); sinkN = new Simplex(seed + 11);
    }

    // ---------- гигантские залы

    /** Зал: эллипсоид с плоским дном; fluid/level — озеро на дне. */
    public static final class Hall {
        public final double x, z, y, rx, rz, ry, cos, sin, level;
        public final int fluid;
        Hall(double x, double z, double y, double rx, double rz, double ry, double a, int fluid, double level) {
            this.x = x; this.z = z; this.y = y; this.rx = rx; this.rz = rz; this.ry = ry;
            cos = Math.cos(a); sin = Math.sin(a); this.fluid = fluid; this.level = level;
        }
        public double floor() { return y - ry * 0.6; }
    }

    private static final Hall NO_HALL = new Hall(0, 0, 0, 0, 0, 0, 0, NONE, 0);

    public Hall hall(int i, int j) {
        long key = ((long) i << 32) ^ (j & 0xffffffffL);
        Hall h = halls.get(key);
        if (h == null) {
            if (halls.size() > 4096) halls.clear();
            h = makeHall(i, j);
            halls.put(key, h);
        }
        return h == NO_HALL ? null : h;
    }

    private double hd(long salt, int i, int j) { return Macro.hashD(seed ^ salt, i, j); }

    private Hall makeHall(int i, int j) {
        if (hd(11, i, j) > 0.6) return NO_HALL;
        double cx = (i + 0.15 + 0.7 * hd(12, i, j)) * HALL_CELL, cz = (j + 0.15 + 0.7 * hd(13, i, j)) * HALL_CELL;
        double rx = 45 + 70 * hd(14, i, j), rz = rx * (0.6 + 0.6 * hd(15, i, j)), ry = 22 + 30 * hd(16, i, j);
        double a = Math.PI * hd(17, i, j);
        // потолок не тоньше 25 блоков над всей площадью зала
        Terrain.Column c = new Terrain.Column();
        double minH = 1e9;
        double r = Math.max(rx, rz) * 1.1;
        for (int k = -1; k <= 1; k++)
            for (int l = -1; l <= 1; l++) {
                t.column(cx + k * r, cz + l * r, c);
                minH = Math.min(minH, c.height);
            }
        double yMin = MIN_Y + 18 + ry * 0.6, yMax = minH - ry - 25;
        if (minH < Macro.SEA - 20 || yMax < yMin) return NO_HALL;
        double cy = yMin + (yMax - yMin) * Math.pow(hd(18, i, j), 1.4);
        double floor = cy - ry * 0.6;
        int fluid = NONE;
        double level = 0;
        if (floor < MIN_Y + 45) { fluid = LAVA; level = floor + 4; }
        else if (hd(19, i, j) < 0.45) { fluid = WATER; level = floor + 3 + 6 * hd(20, i, j); }
        return new Hall(cx, cz, cy, rx, rz, ry, a, fluid, level);
    }

    double hallShape(Hall h, double x, double y, double z) {
        double dx = x - h.x, dz = z - h.z;
        double u = (dx * h.cos + dz * h.sin) / h.rx, v = (-dx * h.sin + dz * h.cos) / h.rz;
        double dy = y - h.y, w = dy / (dy > 0 ? h.ry : h.ry * 0.6);
        double q = u * u + v * v + w * w;
        if (q > 2.2) return 9;
        double d = Math.sqrt(q) + 0.18 * hallN.noise(x / 22.0 + y / 40.0, z / 22.0 - y / 35.0) + 0.08 * hallN2.noise(x / 9.0 + y / 13.0, z / 9.0);
        double col = hallN2.noise(x / 26.0 + 300, z / 26.0);
        if (col > 0.8) d += (col - 0.8) * 14; // редкие толстые колонны от пола до потолка
        return d;
    }

    // ---------- подземные реки (галереи)

    /** Галерея в столбце: центр по высоте, полуширина, полувысота, уровень воды, жидкость; null — нет. */
    static final class Gallery { double y, rw, rv, dist, level; int fluid; boolean on; double lx = Double.NaN, lz; Caves owner; }

    private final ThreadLocal<Gallery> galTL = ThreadLocal.withInitial(Gallery::new);

    Gallery gallery(double x, double z, Terrain.Column c, Gallery g) {
        if (g.lx == x && g.lz == z && g.owner == this) return g;
        g.lx = x; g.lz = z; g.owner = this;
        g.on = false;
        if (c.height < Macro.SEA - 6) return g;
        double wx = x + 140 * galWx.noise(x / 650, z / 650), wz = z + 140 * galWz.noise(x / 650, z / 650);
        double f = 1.0 / 1100;
        double n = gal.noise(wx * f, wz * f);
        if (Math.abs(n) > 0.05) return g;
        double nx = gal.noise((wx + 3) * f, wz * f) - n, nz = gal.noise(wx * f, (wz + 3) * f) - n;
        double grad = Math.sqrt(nx * nx + nz * nz) / 3;
        if (grad < 1e-7) return g;
        double dist = Math.abs(n) / grad;
        double wide = 0.5 + 0.5 * galWide.noise(x / 420, z / 420);
        double rw = 4 + 5 * wide;
        if (dist > rw * 1.6 || galWide.noise(x / 3100 + 40, z / 3100) < -0.45) return g;
        double rv = 3.5 + 0.4 * rw;
        double y = 10 + 45 * galBase.noise(x / 2500, z / 2500);
        y = Math.min(y, Math.min(c.macroHeight - 28, c.height - 12) - rv);
        if (y < MIN_Y + 14) return g;
        g.on = true;
        g.y = y; g.rw = rw; g.rv = rv; g.dist = dist;
        int fl = (int) Math.floor(y - rv * 0.7);
        g.level = ((fl + 5) >> 2) << 2;
        g.fluid = y < MIN_Y + 45 ? LAVA : galWet.noise(x / 1500, z / 1500) > -0.25 ? WATER : NONE;
        return g;
    }

    /** Быстрая проверка без столбца рельефа: рядом ли линия подземной реки. */
    public boolean nearGallery(double x, double z) {
        double wx = x + 140 * galWx.noise(x / 650, z / 650), wz = z + 140 * galWz.noise(x / 650, z / 650);
        return Math.abs(gal.noise(wx / 1100, wz / 1100)) < 0.02;
    }

    double galleryShape(Gallery g, double x, double y, double z) {
        double dy = y - g.y, w = dy / (dy > 0 ? g.rv : g.rv * 0.7), u = g.dist / g.rw;
        double q = u * u + w * w;
        if (q > 2.2) return 9;
        return Math.sqrt(q) + 0.15 * hallN.noise(x / 11.0 + y / 9.0, z / 11.0);
    }

    // ---------- ледяные ходы

    static final class Ice { double y, rw, rv, dist; boolean on; double lx = Double.NaN, lz; Caves owner; }

    private final ThreadLocal<Ice> iceTL = ThreadLocal.withInitial(Ice::new);

    /** Холодные высокие горы с ледниками (то же условие, что у ледников в SnowFeature, с запасом). */
    public boolean glacial(Terrain.Column c, double x, double z) {
        return c.temp < -0.2 && c.mountain > 0.2 && c.height > t.snowLine(x, z, c.temp) + 15;
    }

    Ice iceTunnel(double x, double z, Terrain.Column c, Ice g) {
        if (g.lx == x && g.lz == z && g.owner == this) return g;
        g.lx = x; g.lz = z; g.owner = this;
        g.on = false;
        if (!glacial(c, x, z) || c.slope > 0.5) return g;
        double wx = x + 40 * iceW.noise(x / 200, z / 200), wz = z + 40 * iceW.noise(x / 200 + 50, z / 200);
        double f = 1.0 / 170;
        double n = ice.noise(wx * f, wz * f);
        if (Math.abs(n) > 0.12) return g;
        double nx = ice.noise((wx + 2) * f, wz * f) - n, nz = ice.noise(wx * f, (wz + 2) * f) - n;
        double grad = Math.sqrt(nx * nx + nz * nz) / 2;
        if (grad < 1e-7) return g;
        g.dist = Math.abs(n) / grad;
        g.rw = 4 + 2 * (0.5 + 0.5 * iceW.noise(x / 60, z / 60));
        if (g.dist > g.rw * 1.6) return g;
        g.rv = 5.5;
        g.y = c.height - 9 - 10 * (0.5 + 0.5 * iceW.noise(x / 90 + 20, z / 90));
        if (iceW.noise(x / 45 + 90, z / 45) > 0.72) g.y = c.height - 3; // вход с поверхности
        g.on = true;
        return g;
    }

    // ---------- карстовые провалы (сеноты)

    public static final class Sink {
        public final double x, z, top, r, cy, rc, ryc, level;
        Sink(double x, double z, double top, double r, double cy, double rc, double ryc) {
            this.x = x; this.z = z; this.top = top; this.r = r; this.cy = cy; this.rc = rc; this.ryc = ryc; level = cy - 1;
        }
    }

    private static final Sink NO_SINK = new Sink(0, 0, 0, 0, 0, 0, 0);

    public Sink sink(int i, int j) {
        long key = ((long) i << 32) ^ (j & 0xffffffffL);
        Sink s = sinks.get(key);
        if (s == null) {
            if (sinks.size() > 16384) sinks.clear();
            s = makeSink(i, j);
            sinks.put(key, s);
        }
        return s == NO_SINK ? null : s;
    }

    private Sink makeSink(int i, int j) {
        double cx = (i + 0.15 + 0.7 * hd(31, i, j)) * SINK_CELL, cz = (j + 0.15 + 0.7 * hd(32, i, j)) * SINK_CELL;
        Terrain.Column c = new Terrain.Column();
        t.column(cx, cz, c);
        double karst = c.style[Macro.Style.KARST.ordinal()];
        double p = 0.04 + 0.45 * karst + (c.temp > 0.3 && c.humid > 0.2 ? 0.08 : 0);
        if (hd(30, i, j) > p || c.temp < -0.3 || c.height < Macro.SEA + 3 || c.slope > 0.6 || c.waterKind != Terrain.WATER_NONE) return NO_SINK;
        double r = 6 + 10 * hd(33, i, j), depth = 25 + 30 * hd(34, i, j);
        double cy = c.height - depth;
        if (cy < MIN_Y + 30) return NO_SINK;
        return new Sink(cx, cz, c.height + 12, r, cy, r * 1.9, 9 + r * 0.4);
    }

    double sinkShape(Sink s, double x, double y, double z) {
        double dx = x - s.x, dz = z - s.z, dh = Math.sqrt(dx * dx + dz * dz);
        double best = 9;
        if (y >= s.cy && y <= s.top) {
            double r = s.r * (1 + 0.2 * sinkN.noise(x / 7.0, z / 7.0) + 0.1 * sinkN.noise(y / 6.0, dh / 6.0));
            best = dh / r;
        }
        double dy = y - s.cy, w = dy / (dy > 0 ? s.ryc : s.ryc * 0.7), u = dh / s.rc;
        double q = u * u + w * w;
        if (q < 2.2) best = Math.min(best, Math.sqrt(q) + 0.12 * hallN.noise(x / 10.0 + y / 8.0, z / 10.0));
        return best;
    }

    // ---------- плотность и жидкости

    /** Плотность пещер: < 0 внутри. Вне пещер SOLID. */
    public double density(int x, int y, int z, Terrain.Column c) {
        if (y > c.height + 14) return SOLID;
        double d = 9;
        int hi = (int) Math.floor(x / HALL_CELL), hj = (int) Math.floor(z / HALL_CELL);
        Hall h = hall(hi, hj);
        if (h != null) d = hallShape(h, x, y, z);
        Gallery g = gallery(x, z, c, galTL.get());
        if (g.on) d = Math.min(d, galleryShape(g, x, y, z));
        Ice ic = iceTunnel(x, z, c, iceTL.get());
        if (ic.on) {
            double dy = (y - ic.y) / ic.rv, u = ic.dist / ic.rw;
            d = Math.min(d, Math.sqrt(u * u + dy * dy) + 0.15 * hallN2.noise(x / 8.0 + y / 6.0, z / 8.0));
        }
        int si = (int) Math.floor(x / SINK_CELL), sj = (int) Math.floor(z / SINK_CELL);
        Sink s = sink(si, sj);
        if (s != null) d = Math.min(d, sinkShape(s, x, y, z));
        return Math.min(SOLID, (d - 1) * 2.5);
    }

    /**
     * Жидкость для воздуха в точке, если она внутри нашей пещеры ниже уровня её озера/реки.
     * out[0] — уровень (верхний блок жидкости) для этого столбца, если есть.
     */
    public int fluid(int x, int y, int z, Terrain.Column c) {
        Hall h = hall((int) Math.floor(x / HALL_CELL), (int) Math.floor(z / HALL_CELL));
        if (h != null && h.fluid != NONE && y <= h.level && hallShape(h, x, y, z) < 1.05) return h.fluid;
        Gallery g = gallery(x, z, c, galTL.get());
        if (g.on && g.fluid != NONE && y <= g.level && galleryShape(g, x, y, z) < 1.1) return g.fluid;
        Sink s = sink((int) Math.floor(x / SINK_CELL), (int) Math.floor(z / SINK_CELL));
        if (s != null && y <= s.level && sinkShape(s, x, y, z) < 1.1) return WATER;
        return NONE;
    }

    /** Самый высокий уровень жидкости наших пещер в столбце (или MIN_Y - 1). */
    public int topFluidLevel(int x, int z, Terrain.Column c) {
        double top = MIN_Y - 1;
        Hall h = hall((int) Math.floor(x / HALL_CELL), (int) Math.floor(z / HALL_CELL));
        if (h != null && h.fluid != NONE) top = Math.max(top, h.level);
        Gallery g = gallery(x, z, c, galTL.get());
        if (g.on && g.fluid != NONE) top = Math.max(top, g.level);
        Sink s = sink((int) Math.floor(x / SINK_CELL), (int) Math.floor(z / SINK_CELL));
        if (s != null) top = Math.max(top, s.level);
        return (int) Math.floor(top);
    }

    /** Есть ли в столбце подземная река (для поиска): уровень воды или NaN. */
    public double riverLevel(double x, double z, Terrain.Column c) {
        Gallery g = gallery(x, z, c, galTL.get());
        return g.on && g.fluid == WATER && g.dist < g.rw * 0.5 ? g.level : Double.NaN;
    }

    /** Есть ли в столбце ледяной ход: высота пола или NaN. */
    public double iceLevel(double x, double z, Terrain.Column c) {
        Ice g = iceTunnel(x, z, c, iceTL.get());
        return g.on && g.dist < g.rw * 0.5 ? g.y - g.rv + 1 : Double.NaN;
    }
}
