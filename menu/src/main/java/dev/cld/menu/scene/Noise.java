package dev.cld.menu.scene;

// Детерминированный хеш и одномерный шум для пейзажа
public final class Noise {
    private Noise() {}

    public static double hash(long i, long salt) {
        long x = i * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        x ^= x >>> 31;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 29;
        x *= 0x94D049BB133111EBL;
        x ^= x >>> 32;
        return (x >>> 11) * 0x1.0p-53;
    }

    public static double noise(double x, long salt) {
        long i = (long) Math.floor(x);
        double f = x - i;
        double u = f * f * (3 - 2 * f);
        return hash(i, salt) * (1 - u) + hash(i + 1, salt) * u;
    }

    public static double fbm(double x, long salt) {
        return noise(x, salt) * 0.55 + noise(x * 2.07, salt + 1) * 0.27 + noise(x * 4.3, salt + 2) * 0.12
                + noise(x * 9.1, salt + 3) * 0.06;
    }

    // острые гребни для дальних гор
    public static double ridge(double x, long salt) {
        double n = 1 - Math.abs(noise(x, salt) * 2 - 1);
        double m = 1 - Math.abs(noise(x * 2.3, salt + 5) * 2 - 1);
        return n * n * 0.7 + m * m * 0.3;
    }
}
