package dev.cld.tcw.gen;

/** Настройки мира TelipailoCraft (экран «Настроить»): каждая — 0, 1 или 2, где 1 — как по умолчанию. */
public record Options(int continents, int mountains, int height, int climate, int snow, int halls) {
    public static final Options DEFAULT = new Options(1, 1, 1, 1, 1, 1);

    private static int c(int v) { return Math.max(0, Math.min(2, v)); }

    public Options {
        continents = c(continents); mountains = c(mountains); height = c(height);
        climate = c(climate); snow = c(snow); halls = c(halls);
    }

    /** Размер ячейки континента, блоков. */
    public double cs() { return new double[] { 8000, 14000, 20000 }[continents]; }
    /** Сдвиг порогов маски хребтов: плюс — гор меньше. */
    public double ridgeShift() { return new double[] { 0.07, 0, -0.08 }[mountains]; }
    public double heightMul() { return new double[] { 0.75, 1, 1.15 }[height]; }
    /** Множитель размера климатических зон. */
    public double climateMul() { return new double[] { 0.5, 1, 2 }[climate]; }
    public double snowOffset() { return new double[] { -60, 0, 60 }[snow]; }
    public double hallChance() { return new double[] { 0.3, 0.6, 0.85 }[halls]; }
}
