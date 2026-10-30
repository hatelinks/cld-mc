package dev.cld.far.gen;

import dev.cld.tcw.gen.Macro;
import dev.cld.tcw.gen.Terrain;
import java.util.ArrayList;
import java.util.List;

// Каменные столбы tcw (карст, доломитовые шпили, Патагония) по той же формуле, что Terrain.pillars:
// центр, радиус и высота берутся из хеша ячейки, поэтому их можно нарисовать круглыми и точно на месте.
// Сетка тайла с шагом 8 и больше попадает в столб одной точкой, и раньше из него выходила широкая коробка.
public final class Pillars {
    // base: рельеф вокруг столба, top: его вершина, ph: высота самого столба над рельефом в центре
    public record P(double x, double z, double r, double base, double top, double ph, int style) {
        // высота купола столба на расстоянии d от центра, как в генераторе
        public double height(double d) {
            if (d >= r) return base;
            double k = Macro.ss(r, r - 2.5, d);
            return Math.max(base, top - ph + ph * (1 - 0.25 * (d / r) * (d / r)) * k);
        }
    }

    // ячейка, вероятность, радиусы, высоты, соль, стиль: те же числа, что в Terrain.column
    private record Kind(double cell, double prob, double r0, double r1, double h0, double h1, long salt, Macro.Style style) {}
    private static final Kind[] KINDS = {
            new Kind(30, 0.2, 6, 11, 35, 110, 0xA1L, Macro.Style.KARST),
            new Kind(70, 0.12, 7, 14, 30, 90, 0xA2L, Macro.Style.DOLOMITE),
            new Kind(90, 0.07, 6, 10, 40, 120, 0xA3L, Macro.Style.PATAGONIA),
    };

    private Pillars() {}

    // Сила столбов вида k в столбце рельефа (0, нет), как в Terrain.column.
    static double weight(int k, Terrain.Column c) {
        double w = c.style[KINDS[k].style.ordinal()];
        if (w <= 0.05) return 0;
        return switch (k) {
            case 0 -> c.coast > 80 ? w * Macro.ss(80, 400, c.coast) : 0;
            case 1 -> c.mountain > 0.25 ? w * Macro.ss(0.25, 0.6, c.mountain) : 0;
            default -> c.mountain > 0.4 ? w * Macro.ss(0.4, 0.8, c.mountain) : 0;
        };
    }

    // Столбы с центром в квадрате [x0 - pad, x0 + size + pad). maxWeight по видам: сколько их может быть в тайле
    // (по сетке тайла), чтобы не считать столбцы в ячейках, где столбов точно нет.
    public static List<P> find(Terrain t, double x0, double z0, double size, double pad, double[] maxWeight) {
        List<P> out = new ArrayList<>();
        Terrain.Column c = new Terrain.Column();
        long seed = t.seed();
        for (int k = 0; k < KINDS.length; k++) {
            if (maxWeight[k] <= 0) continue;
            Kind kd = KINDS[k];
            int ai = (int) Math.floor((x0 - pad - kd.cell) / kd.cell), bi = (int) Math.floor((x0 + size + pad) / kd.cell);
            int aj = (int) Math.floor((z0 - pad - kd.cell) / kd.cell), bj = (int) Math.floor((z0 + size + pad) / kd.cell);
            for (int j = aj; j <= bj; j++)
                for (int i = ai; i <= bi; i++) {
                    double hp = Macro.hashD(seed ^ kd.salt, i, j);
                    if (hp > kd.prob * maxWeight[k]) continue;
                    double cx = (i + 0.2 + 0.6 * Macro.hashD(seed ^ kd.salt ^ 1, i, j)) * kd.cell;
                    double cz = (j + 0.2 + 0.6 * Macro.hashD(seed ^ kd.salt ^ 2, i, j)) * kd.cell;
                    if (cx < x0 - pad || cz < z0 - pad || cx >= x0 + size + pad || cz >= z0 + size + pad) continue;
                    t.column(cx, cz, c);
                    double w = weight(k, c);
                    if (hp > kd.prob * w || c.waterKind != Terrain.WATER_NONE) continue;
                    double r = kd.r0 + (kd.r1 - kd.r0) * Macro.hashD(seed ^ kd.salt ^ 3, i, j);
                    double top = c.height;
                    double ph = (kd.h0 + (kd.h1 - kd.h0) * Macro.hashD(seed ^ kd.salt ^ 4, i, j)) * Math.min(1, w * 1.5);
                    // основание: рельеф сразу за краем столба с двух сторон
                    double base = (t.column(cx + r + 3, cz, c).height + t.column(cx - r - 3, cz, c).height) / 2;
                    if (top - base < 8) continue; // столб срезан рекой или почти не виден
                    out.add(new P(cx, cz, r, base, top, Math.min(ph, top - base), kd.style.ordinal()));
                }
        }
        return out;
    }
}
