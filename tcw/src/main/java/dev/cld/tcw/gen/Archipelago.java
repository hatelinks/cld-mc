package dev.cld.tcw.gen;

// Мир End Sea: рельеф TelipailoCraft разрезан на летающие острова, между ними пустота.
// Маска острова: сумма трёх шумов разного масштаба выше порога, на месте океанов tcw всегда пустота.
// Под каждым островом скалистый низ: чем дальше от края, тем глубже, у больших островов до двухсот блоков.
public final class Archipelago {
    static final double T = 0.16;
    private final Simplex big, mid, small, under;

    Archipelago(long seed) {
        long s = seed * 0xC2B2AE3D27D4EB4FL + 0xE5;
        big = new Simplex(s + 1); mid = new Simplex(s + 2); small = new Simplex(s + 3); under = new Simplex(s + 4);
    }

    // насколько точка внутри острова: больше 0 остров, меньше или 0 пустота
    public double edge(double x, double z, double height) {
        double m = 0.75 * big.noise(x / 1500.0, z / 1500.0) + 0.35 * mid.noise(x / 430.0, z / 430.0)
                + 0.1 * small.noise(x / 90.0, z / 90.0) - T;
        // берег и океан tcw: у самой воды остров сходит на нет
        double land = (height - Macro.SEA - 3) * 0.01;
        return Math.min(m, land);
    }

    // низ острова в точке (самый нижний блок), для e > 0
    public double bottom(double x, double z, double height, double e) {
        double k = Math.min(1, e * 12);
        double depth = 6 + 34 * k + 150 * ss(0.05, 0.5, e) + 9 * under.noise(x / 24.0, z / 24.0) * k;
        return Math.max(Macro.MIN_Y + 8, height - Math.max(3, depth));
    }

    static double ss(double a, double b, double v) {
        double t = Math.max(0, Math.min(1, (v - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
