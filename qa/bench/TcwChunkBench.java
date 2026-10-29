import dev.cld.tcw.gen.*;

/**
 * Нагрузочный тест генератора tcw без Minecraft: повторяет вызовы, которые делает игра на один чанк
 * (настройки шума tcw: ячейка 4×8, rock и climate — без интерполяции).
 * Запуск: см. qa/bench/tcw.sh. Аргументы: [сид] [чанков] [потоков]
 */
public class TcwChunkBench {
    static final int MIN_Y = -128, H = 768;

    /** Кэш столбцов на поток, как Worlds.column. */
    static final int N = Integer.getInteger("cols", 1024);
    static final class Cols {
        final long[] keys = new long[N]; final Terrain.Column[] cols = new Terrain.Column[N];
        { java.util.Arrays.fill(keys, Long.MIN_VALUE); }
        Terrain.Column at(Terrain t, int x, int z) {
            long key = ((long) x << 32) ^ (z & 0xffffffffL);
            long h = key; h ^= h >>> 33; h *= 0xff51afd7ed558ccdL; h ^= h >>> 33;
            int s = (int) (h & (N - 1));
            if (keys[s] == key) return cols[s];
            if (cols[s] == null) cols[s] = new Terrain.Column();
            t.column(x, z, cols[s]); keys[s] = key; return cols[s];
        }
    }

    static double chunk(Terrain t, Cols cc, int cx, int cz, long[] ns) {
        Rocks.Columns rc = (x, z) -> cc.at(t, x, z);
        double acc = 0; long t0 = System.nanoTime();
        int bx = cx * 16, bz = cz * 16;
        // 1) углы ячеек шума 4×8: surface + caves (interpolated)
        for (int i = 0; i <= 16; i += 4) for (int k = 0; k <= 16; k += 4)
            for (int y = MIN_Y; y <= MIN_Y + H; y += 8) {
                Terrain.Column c = cc.at(t, bx + i, bz + k);
                acc += c.height - y;
                acc += t.caves.density(bx + i, y, bz + k, c);
            }
        long t1 = System.nanoTime(); ns[0] += t1 - t0;
        // 2) rock — на каждый блок, дважды (два вхождения в final_density)
        for (int i = 0; i < 16; i++) for (int k = 0; k < 16; k++) for (int y = MIN_Y; y < MIN_Y + H; y++) {
            acc += t.rocks.combined(bx + i, y, bz + k, rc);
            acc += t.rocks.combined(bx + i, y, bz + k, rc);
        }
        long t1b = System.nanoTime(); ns[1] += t1b - t1;
        // 2б) то же ещё раз: столбцы уже в кэше — чистая цена rock на блок
        for (int i = 0; i < 16; i++) for (int k = 0; k < 16; k++) for (int y = MIN_Y; y < MIN_Y + H; y++) {
            acc += t.rocks.combined(bx + i, y, bz + k, rc);
        }
        long t2 = System.nanoTime(); ns[4] += t2 - t1b;
        // 3) биомы: кварты 4×4×4, 6 параметров климата
        for (int i = 0; i < 16; i += 4) for (int k = 0; k < 16; k += 4) for (int y = MIN_Y; y < MIN_Y + H; y += 4) {
            Terrain.Column c = cc.at(t, bx + i, bz + k);
            for (int p = 0; p < 6; p++) acc += t.climate(c, p, bx + i, y, bz + k);
        }
        long t3 = System.nanoTime(); ns[2] += t3 - t2;
        // 4) декорации: столбцы 16×16 + жидкости пещер (CaveFeature)
        for (int i = 0; i < 16; i++) for (int k = 0; k < 16; k++) {
            Terrain.Column c = cc.at(t, bx + i, bz + k);
            int top = t.caves.topFluidLevel(bx + i, bz + k, c);
            for (int y = top; y > Math.max(MIN_Y, top - 64); y--) acc += t.caves.fluid(bx + i, y, bz + k, c);
        }
        ns[3] += System.nanoTime() - t3;
        return acc;
    }

    public static void main(String[] a) throws Exception {
        long seed = a.length > 0 ? Long.parseLong(a[0]) : 12345;
        int n = a.length > 1 ? Integer.parseInt(a[1]) : 400;
        int threads = a.length > 2 ? Integer.parseInt(a[2]) : 1;
        Terrain t0 = new Terrain(seed);
        Macro m = t0.macro;
        // место: самая «горная» точка около центра первого континента
        double ox = m.cellCenterX(0, 0), oz = m.cellCenterZ(0, 0), best = -1;
        Macro.Sample s = new Macro.Sample();
        for (int i = -40; i <= 40; i++) for (int k = -40; k <= 40; k++) {
            double x = m.cellCenterX(0, 0) + i * 100, z = m.cellCenterZ(0, 0) + k * 100;
            m.sample(x, z, s);
            if (s.mountain > best) { best = s.mountain; ox = x; oz = z; }
        }
        long h0 = System.nanoTime(); t0.hydro(0, 0);
        System.out.printf("сид %d, место %.0f %.0f (горы %.2f), сток рек континента: %.0f мс%n", seed, ox, oz, best, (System.nanoTime() - h0) / 1e6);
        int side = (int) Math.ceil(Math.sqrt(n));
        int ccx = (int) Math.floor(ox / 16) - side / 2, ccz = (int) Math.floor(oz / 16) - side / 2;
        int passes = Integer.getInteger("passes", 4);
        for (int pass = 0; pass < passes; pass++) {   // 0 — прогрев JIT на соседнем участке; дальше тот же участок заново (новый Terrain — холодные кэши)
            int offx = pass == 0 ? side + 4 : 0;
            final Terrain t = pass == 0 ? t0 : new Terrain(seed);
            t.hydro(0, 0);
            long[][] ns = new long[threads][5];
            double[] sums = new double[threads];
            java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
            Thread[] th = new Thread[threads];
            long w0 = System.nanoTime();
            for (int q = 0; q < threads; q++) {
                final int qq = q;
                th[q] = new Thread(() -> { Cols cc = new Cols(); int id; double acc = 0;
                    while ((id = next.getAndIncrement()) < n) acc += chunk(t, cc, ccx + offx + id % side, ccz + id / side, ns[qq]);
                    sums[qq] = acc; });
                th[q].start();
            }
            for (Thread x : th) x.join();
            double wall = (System.nanoTime() - w0) / 1e6;
            if (pass == 0) continue;
            long[] sum = new long[4];
            long pure = 0;
            for (long[] r : ns) { for (int i = 0; i < 4; i++) sum[i] += r[i]; pure += r[4]; }
            double tot = (sum[0] + sum[1] + sum[2] + sum[3]) / 1e6 / n;
            double check = 0; for (double v : sums) check += v;
            System.out.printf("контрольная сумма %.17g (должна совпадать до и после оптимизаций)%n", check);
            System.out.printf("%d чанков, %d поток(ов): %.0f мс всего, %.1f чанков/с; на чанк %.2f мс:%n", n, threads, wall, n / wall * 1000, tot);
            String[] names = {"углы ячеек (surface+caves)", "rock на каждый блок ×2", "климат для биомов", "жидкости пещер (декорация)"};
            for (int i = 0; i < 4; i++) System.out.printf("  %-30s %6.2f мс (%2.0f%%)%n", names[i], sum[i] / 1e6 / n, 100.0 * sum[i] / (sum[0] + sum[1] + sum[2] + sum[3]));
            System.out.printf("  (из них rock ×1 при готовых столбцах: %.2f мс — цена самих вызовов на блок)%n", pure / 1e6 / n);
        }
    }
}
