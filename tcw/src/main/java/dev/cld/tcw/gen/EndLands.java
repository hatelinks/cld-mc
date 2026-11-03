package dev.cld.tcw.gen;

// Край в мирах TelipailoCraft: ванильный центр с драконом и кольцо пустоты до INNER, дальше свои острова.
// Острова крупнее ванильных (от сотни блоков до километра), на разной высоте по районам, на части гор вершины.
// Высота Края как в ванили, 0..256. Чистая Java: по сиду и координатам, без Minecraft.
public final class EndLands {
    // до INNER ванильный Край (остров дракона и пустота), наши острова начинаются за RAMP после него
    public static final int INNER = 1000, RAMP = 300;
    static final double T = 0.14;
    private final Simplex big, mid, small, alt, hill, ridge, mount, under, forest, belt;

    public EndLands(long seed) {
        long s = seed * 0x94D049BB133111EBL + 0xE4D;
        big = new Simplex(s + 1); mid = new Simplex(s + 2); small = new Simplex(s + 3); alt = new Simplex(s + 4);
        hill = new Simplex(s + 5); ridge = new Simplex(s + 6); mount = new Simplex(s + 7); under = new Simplex(s + 8);
        forest = new Simplex(s + 9); belt = new Simplex(s + 10);
    }

    // столбец острова: e > 0 остров, top и bottom включительно
    public static final class Col {
        int x, z;
        boolean set;
        public double e, top, bottom;
        public boolean island() { return e > 0; }
    }

    // столбец по целым координатам через кэш потока (плотность спрашивает один столбец много раз)
    private final ThreadLocal<Col[]> cache = ThreadLocal.withInitial(() -> new Col[1024]);

    public Col at(int x, int z) {
        Col[] a = cache.get();
        int i = (x * 31 + z) & 1023;
        Col c = a[i];
        if (c == null) a[i] = c = new Col();
        else if (c.set && c.x == x && c.z == z) return c;
        column(x, z, c);
        c.x = x; c.z = z; c.set = true;
        return c;
    }

    // ванильная часть Края (центр с драконом)
    public static boolean vanilla(double x, double z) { return x * x + z * z < (double) INNER * INNER; }

    public Col column(double x, double z, Col c) {
        double d = Math.sqrt(x * x + z * z);
        double m = 0.7 * big.noise(x / 700.0, z / 700.0) + 0.35 * mid.noise(x / 220.0, z / 220.0)
                + 0.12 * small.noise(x / 60.0, z / 60.0) - T;
        // около центра островов нет: кольцо пустоты как в ванили
        m -= 1.2 * (1 - ss(INNER, INNER + RAMP, d));
        c.e = m;
        if (m <= 0) { c.top = c.bottom = Double.NaN; return c; }
        double k = Math.min(1, m * 10);
        // высота района от 50 до 140, холмы и в части районов горы до 230
        double base = 95 + 45 * alt.noise(x / 1300.0, z / 1300.0);
        double hills = 7 * hill.noise(x / 70.0, z / 70.0);
        double mt = ss(0.15, 0.55, mount.noise(x / 900.0, z / 900.0));
        double r = 1 - Math.abs(ridge.noise(x / 180.0, z / 180.0));
        double peaks = mt * 95 * r * r * ss(0.02, 0.3, m);
        c.top = Math.min(240, base + (2 + hills) * k + peaks);
        double depth = 5 + 30 * k + 45 * ss(0.05, 0.45, m) + 7 * under.noise(x / 20.0, z / 20.0) * k;
        c.bottom = Math.max(4, base - depth);
        if (c.bottom > c.top - 2) c.bottom = c.top - 2;
        return c;
    }

    // значение для выбора биома Края (как end_islands у ванили): выше 0.25 нагорья с хорусом, около 0 середина,
    // от -0.21 до -0.06 пустоши, ниже мелкие острова
    public double erosion(double x, double z, Col c) {
        if (!c.island()) return -0.5;
        double f = forest(x, z);
        // край острова и голые плато: пустоши без хоруса
        if (c.e < 0.03 || f < -0.3) return -0.1;
        return f > 0.1 || c.top > 150 ? 0.4 : 0.0;
    }

    // плотность для шума: внутри острова плюс, снаружи минус, на границе 0 с точностью до блока
    public double density(int x, int y, int z) {
        Col c = at(x, z);
        if (!c.island()) return -1;
        double v = Math.min(c.top + 0.5 - y, y - c.bottom + 0.5) * 0.2;
        return Math.max(-1, Math.min(1, v));
    }

    // районы хорусовых лесов (больше 0.1) и голых плато (меньше -0.3)
    public double forest(double x, double z) { return forest.noise(x / 500.0, z / 500.0); }

    // толщина нижнего тёмного пояса над низом острова
    public double darkBelt(double x, double z, Col c) {
        return 3 + 0.35 * (c.top - c.bottom) + 3 * belt.noise(x / 30.0, z / 30.0);
    }

    static double ss(double a, double b, double v) {
        double t = Math.max(0, Math.min(1, (v - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
