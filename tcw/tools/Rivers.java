import dev.cld.tcw.gen.RiverWater;
import dev.cld.tcw.gen.Terrain;

import java.util.Arrays;
import java.util.stream.IntStream;

// Проверка воды рек вне игры: java Rivers <seed> <cx> <cz> <размер>
// Считает воду как WaterFeature (окна по чанкам) на земле из Terrain.column и печатает,
// сколько боковых граней воды торчит над сушей (столбы) и сколько перепадов между водой больше блока.
public class Rivers {
    public static void main(String[] a) {
        long seed = Long.parseLong(a[0]);
        int cx = Integer.parseInt(a[1]), cz = Integer.parseInt(a[2]), n = Integer.parseInt(a[3]);
        Terrain t = new Terrain(seed);
        int x0 = cx - n / 2, z0 = cz - n / 2, nn = n * n;
        int[] g = new int[nn], lv = new int[nn], bd = new int[nn];
        IntStream.range(0, n).parallel().forEach(j -> {
            Terrain.Column c = new Terrain.Column();
            double[] w = new double[2];
            int[] o = new int[2];
            for (int i = 0; i < n; i++) {
                int id = j * n + i;
                g[id] = (int) Math.floor(t.column(x0 + i, z0 + j, c).height);
                if (t.riverBlocks(x0 + i, z0 + j, w, o)) { lv[id] = o[0]; bd[id] = o[1]; }
                else lv[id] = bd[id] = RiverWater.NONE;
            }
        });
        int m = 8, wn = 16 + 2 * m;
        int[] water = new int[nn], bank = new int[nn];
        Arrays.fill(water, RiverWater.NONE);
        Arrays.fill(bank, RiverWater.NONE);
        int[] L = new int[wn * wn], B = new int[wn * wn], T = new int[wn * wn], W = new int[wn * wn], K = new int[wn * wn];
        for (int bz = m; bz + 16 + m <= n; bz += 16)
            for (int bx = m; bx + 16 + m <= n; bx += 16) {
                for (int j = 0; j < wn; j++)
                    for (int i = 0; i < wn; i++) {
                        int s = (bz - m + j) * n + bx - m + i, d = j * wn + i;
                        L[d] = lv[s]; B[d] = bd[s]; T[d] = g[s];
                    }
                RiverWater.solve(wn, L, B, T, W, K);
                for (int j = 0; j < 16; j++)
                    for (int i = 0; i < 16; i++) {
                        int s = (bz + j) * n + bx + i, d = (j + m) * wn + i + m;
                        water[s] = W[d]; bank[s] = K[d];
                    }
            }
        long cells = 0, wall = 0, wall3 = 0, step = 0, step3 = 0, banks = 0;
        for (int j = 1; j < n - 1; j++)
            for (int i = 1; i < n - 1; i++) {
                int id = j * n + i;
                if (bank[id] != RiverWater.NONE) banks++;
                if (water[id] == RiverWater.NONE) continue;
                cells++;
                for (int d = 0; d < 4; d++) {
                    int k = id + (d == 0 ? 1 : d == 1 ? -1 : d == 2 ? n : -n);
                    if (water[k] != RiverWater.NONE) {
                        int df = water[id] - water[k];
                        if (df > 1) { step += df; if (df >= 3) step3++; }
                    } else {
                        int e = water[id] - (bank[k] != RiverWater.NONE ? bank[k] : g[k]);
                        if (e > 0) { wall += e; if (e >= 3) wall3++; }
                    }
                }
            }
        System.out.printf("вода %d столбцов, грани над сушей %d (от 3 блоков: %d), перепады больше блока %d (от 3: %d), досыпано берега %d%n",
                cells, wall, wall3, step, step3, banks);
    }
}
