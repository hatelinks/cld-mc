package dev.cld.tcw.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

// Небесные острова: редкие группы летающих островов высоко над рельефом, цель для первых кораблей.
// Мир делится на клетки CELL x CELL, в части клеток одна группа: большой остров и до четырёх маленьких рядом.
// Группа целиком лежит внутри своей клетки, поэтому для любой точки хватает одной клетки.
// Высота острова считается по самому высокому месту рельефа под ним, низ всегда выше рельефа с запасом.
public final class Islands {
    public static final int CELL = 1024;
    static final double CHANCE = 0.5;
    // от края клетки до центра большого острова, с запасом под спутники
    static final int EDGE = 220;
    // верх острова не выше этого (мир до 640, нужно место под деревья)
    public static final int MAX_TOP = 600;
    // зазор между низом острова и рельефом под ним
    static final int CLEAR = 40;

    // биомы островов, выбор по климату под центром группы
    public static final String[] BIOMES = {
        "minecraft:snowy_taiga", "minecraft:taiga", "minecraft:birch_forest", "minecraft:forest", "minecraft:flower_forest",
        "minecraft:cherry_grove", "minecraft:meadow", "minecraft:savanna", "minecraft:sparse_jungle"
    };

    public static final class Island {
        public final int x, z, r, top, biome;
        public final boolean main;
        final double depth;

        Island(int x, int z, int r, int top, double depth, int biome, boolean main) {
            this.x = x; this.z = z; this.r = r; this.top = top; this.depth = depth; this.biome = biome; this.main = main;
        }

        // радиус, дальше которого точно нет блоков острова
        public int reach() { return (int) Math.ceil(r * 1.35) + 1; }

        // самый нижний возможный блок
        public int lowest() { return top - 1 - (int) Math.ceil(depth * 1.1 + 9); }
    }

    static final Island[] NONE = {};

    private final Terrain t;
    private final long seed;
    private final Simplex edge, edgeFine, bump, belly, spikes;
    private final ConcurrentHashMap<Long, Island[]> cells = new ConcurrentHashMap<>();

    Islands(Terrain t) {
        this.t = t;
        this.seed = t.seed() * 0x9E3779B97F4A7C15L + 0x51ED;
        edge = new Simplex(seed + 1); edgeFine = new Simplex(seed + 2); bump = new Simplex(seed + 3);
        belly = new Simplex(seed + 4); spikes = new Simplex(seed + 5);
    }

    // острова группы в клетке (ci, cj), большой первым
    public Island[] cell(int ci, int cj) {
        long key = ((long) ci << 32) ^ (cj & 0xffffffffL);
        Island[] v = cells.get(key);
        if (v != null) return v;
        // считаем без блокировки: результат зависит только от сида, повтор в другом потоке даст то же
        v = make(ci, cj);
        if (cells.size() > 4096) cells.clear();
        Island[] old = cells.putIfAbsent(key, v);
        return old != null ? old : v;
    }

    // острова, которые могут задеть точку
    public Island[] near(int x, int z) {
        return cell(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
    }

    // столбец острова в точке: out[0] низ, out[1] верх (включительно). false, если острова тут нет
    public boolean column(Island is, int x, int z, int[] out) {
        double dx = x - is.x, dz = z - is.z, d2 = dx * dx + dz * dz;
        int reach = is.reach();
        if (d2 > (double) reach * reach) return false;
        double e = Math.sqrt(d2) / is.r + 0.22 * edge.noise(x / 22.0, z / 22.0) + 0.07 * edgeFine.noise(x / 7.0, z / 7.0);
        if (e >= 1) return false;
        double f = 1 - Math.max(0, e);
        int top = is.top + (int) Math.floor(2.5 * Math.sqrt(f) + 1.5 * bump.noise(x / 30.0, z / 30.0) - 0.5);
        double sp = Math.max(0, spikes.noise(x / 6.0, z / 6.0));
        int bottom = is.top - 1 - (int) Math.floor(is.depth * Math.pow(f, 0.75) * (0.8 + 0.3 * belly.noise(x / 16.0, z / 16.0))
                + 9 * sp * sp * Math.sqrt(f));
        out[0] = Math.min(bottom, top);
        out[1] = top;
        return true;
    }

    // самый нижний блок островов в точке или Integer.MIN_VALUE
    public int bottom(int x, int z) {
        Island[] all = near(x, z);
        if (all.length == 0) return Integer.MIN_VALUE;
        int[] c = new int[2];
        int b = Integer.MIN_VALUE;
        for (Island is : all)
            if (column(is, x, z, c)) b = b == Integer.MIN_VALUE ? c[0] : Math.min(b, c[0]);
        return b;
    }

    // остров, к которому относится точка на высоте y (с запасом под деревья сверху), или null
    public Island at(int x, int y, int z) {
        Island[] all = near(x, z);
        if (all.length == 0) return null;
        int[] c = new int[2];
        for (Island is : all)
            if (y >= is.lowest() - 4 && y <= is.top + 24 && column(is, x, z, c) && y >= c[0] - 4) return is;
        return null;
    }

    private Island[] make(int ci, int cj) {
        long h = seed ^ ci * 0x632BE59BD9B4E019L ^ cj * 0x8CB92BA72F3D8DD7L;
        SplittableRandom rnd = new SplittableRandom(h);
        if (rnd.nextDouble() >= CHANCE) return NONE;
        int cx = ci * CELL + EDGE + rnd.nextInt(CELL - 2 * EDGE), cz = cj * CELL + EDGE + rnd.nextInt(CELL - 2 * EDGE);
        int r = 26 + rnd.nextInt(28);
        double depth = r * (0.95 + 0.35 * rnd.nextDouble());
        int top = (int) Math.ceil(ground(cx, cz, r) + depth * 1.1 + 10 + CLEAR + rnd.nextInt(40));
        if (top > MAX_TOP) return NONE;
        Macro.Sample s = t.macro.sample(cx, cz, new Macro.Sample());
        int biome = biome(s, rnd);
        List<Island> out = new ArrayList<>();
        out.add(new Island(cx, cz, r, top, depth, biome, true));
        int sats = rnd.nextInt(5);
        for (int k = 0; k < sats; k++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            int sr = 7 + rnd.nextInt(10);
            double dist = r + sr + 6 + rnd.nextInt(45);
            int sx = cx + (int) Math.round(Math.cos(a) * dist), sz = cz + (int) Math.round(Math.sin(a) * dist);
            double sd = sr * (1.0 + 0.4 * rnd.nextDouble());
            int st = Math.max(top - 14 + rnd.nextInt(24), (int) Math.ceil(ground(sx, sz, sr) + sd * 1.1 + 10 + CLEAR));
            if (st > MAX_TOP) continue;
            out.add(new Island(sx, sz, sr, st, sd, biome, false));
        }
        return out.toArray(NONE);
    }

    // самая высокая точка рельефа или воды под кругом радиуса r (с запасом на неровный край)
    private double ground(int cx, int cz, int r) {
        Terrain.Column c = new Terrain.Column();
        double m = Macro.SEA;
        int R = (int) Math.ceil(r * 1.35) + 8;
        for (int dx = -R; dx <= R; dx += 6)
            for (int dz = -R; dz <= R; dz += 6) {
                if (dx * dx + dz * dz > R * R) continue;
                t.column(cx + dx, cz + dz, c);
                m = Math.max(m, c.height);
                if (!Double.isNaN(c.water)) m = Math.max(m, c.water);
            }
        return m;
    }

    private static int biome(Macro.Sample s, SplittableRandom rnd) {
        if (s.temp < -0.55) return 0;
        if (s.temp < -0.25) return 1;
        if (s.temp > 0.45) return s.humid > 0.2 ? 8 : 7;
        int[] mild = {2, 3, 3, 4, 5, 6};
        return mild[rnd.nextInt(mild.length)];
    }
}
