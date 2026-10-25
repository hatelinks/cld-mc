package dev.cld.tcw.gen;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Сток воды по одному континенту: заполнение впадин от океана (priority flood), накопление стока,
 * русла рек и озёра. Каждая река по построению спускается к океану.
 */
public final class Hydro {
    public static final int G = 40, A_MIN = 50, B = 128;
    static final float EPS = 0.002f, BREACH = 45;

    public final int ci, cj, nx, nz;
    public final double ox, oz;
    final float[] h, filled, acc;
    final int[] recv;
    final byte[] kind; // 0 суша, 1 океан, 2 чужая ячейка, 3 озеро

    // отрезки рек
    int segN;
    float[] sax, saz, sbx, sbz, sba, sbb, sw, sdep, sR, sAcc;
    // индекс отрезков по корзинам B×B
    final int bnx, bnz;
    int[][] buckets;

    public Hydro(Macro m, int ci, int cj) {
        this.ci = ci;
        this.cj = cj;
        double half = m.cs * 1.05 + 1700;
        ox = Math.floor((m.cellCenterX(ci, cj) - half) / G) * G;
        oz = Math.floor((m.cellCenterZ(ci, cj) - half) / G) * G;
        nx = (int) (2 * half / G) + 2;
        nz = nx;
        int n = nx * nz;
        h = new float[n];
        filled = new float[n];
        acc = new float[n];
        recv = new int[n];
        kind = new byte[n];
        float[] rain = new float[n], mnt = new float[n], tmp = new float[n], cst = new float[n];

        IntStream.range(0, nz).parallel().forEach(k -> {
            Macro.Sample s = new Macro.Sample();
            for (int i = 0; i < nx; i++) {
                int id = k * nx + i;
                m.sample(ox + i * G, oz + k * G, s);
                h[id] = (float) s.height;
                mnt[id] = (float) s.mountain;
                tmp[id] = (float) s.temp;
                cst[id] = (float) s.coast;
                if (s.cont.ci != ci || s.cont.cj != cj) kind[id] = 2;
                else if (s.height < Macro.SEA) kind[id] = 1;
                rain[id] = (float) (0.3 + 0.7 * (s.humid + 1) / 2 + 0.3 * Macro.ss(250, 500, s.height));
            }
        });

        // priority flood
        Arrays.fill(recv, -1);
        boolean[] done = new boolean[n];
        int[] order = new int[n];
        int on = 0;
        Heap q = new Heap(n);
        for (int id = 0; id < n; id++) {
            if (kind[id] == 2) { done[id] = true; continue; }
            if (kind[id] == 1) {
                done[id] = true;
                filled[id] = Macro.SEA;
                order[on++] = id;
                q.push(id, Macro.SEA);
            }
        }
        while (q.size > 0) {
            int c = q.pop();
            int ci0 = c % nx, ck0 = c / nx;
            for (int dk = -1; dk <= 1; dk++)
                for (int di = -1; di <= 1; di++) {
                    if (di == 0 && dk == 0) continue;
                    int i = ci0 + di, k = ck0 + dk;
                    if (i < 0 || k < 0 || i >= nx || k >= nz) continue;
                    int id = k * nx + i;
                    if (done[id]) continue;
                    done[id] = true;
                    recv[id] = c;
                    float need = filled[c] + EPS - h[id];
                    if (need > 0 && need <= BREACH) {
                        // прорезаем выход из впадины вместо озера
                        filled[id] = h[id];
                        int cur = id, p = c;
                        while (p >= 0 && kind[p] != 1 && filled[p] > filled[cur] - EPS) {
                            filled[p] = filled[cur] - EPS;
                            cur = p;
                            p = recv[p];
                        }
                    } else {
                        filled[id] = Math.max(h[id], filled[c] + EPS);
                        if (filled[id] - h[id] > 1.5f) kind[id] = 3;
                    }
                    order[on++] = id;
                    q.push(id, filled[id]);
                }
        }
        // накопление стока: от истоков к устьям
        for (int id = 0; id < n; id++) acc[id] = kind[id] == 0 || kind[id] == 3 ? rain[id] : 0;
        for (int o = on - 1; o >= 0; o--) {
            int id = order[o];
            if (recv[id] >= 0) acc[recv[id]] += acc[id];
        }
        // углубление русел, монотонно вниз по течению
        float[] bed = new float[n];
        for (int o = 0; o < on; o++) {
            int id = order[o];
            if (kind[id] == 1) { bed[id] = Math.min(Macro.SEA - 2, h[id]); continue; }
            float a = acc[id];
            double cold = Macro.ss(-0.15, -0.55, tmp[id]);
            double deep = 1.5 + Math.min(4, 0.2 * Math.sqrt(a)) + mnt[id] * Math.min(22, 0.8 * Math.sqrt(a)) * (1 + 0.5 * cold);
            deep += 60 * cold * Macro.ss(0.15, 0.5, mnt[id]) * Macro.ss(1400, 150, cst[id]);
            float b = kind[id] == 3 ? filled[id] : (float) (filled[id] - deep);
            int r = recv[id];
            bed[id] = r >= 0 ? Math.max(b, bed[r] + EPS) : b;
        }
        // отрезки
        int cnt = 0;
        for (int id = 0; id < n; id++) if (isRiver(id)) cnt++;
        sax = new float[cnt]; saz = new float[cnt]; sbx = new float[cnt]; sbz = new float[cnt];
        sba = new float[cnt]; sbb = new float[cnt]; sw = new float[cnt]; sdep = new float[cnt]; sR = new float[cnt]; sAcc = new float[cnt];
        bnx = (int) Math.ceil((double) nx * G / B) + 1;
        bnz = bnx;
        int[] bc = new int[bnx * bnz];
        int[][] tmpB = new int[bnx * bnz][];
        for (int id = 0; id < n; id++) {
            if (!isRiver(id)) continue;
            int r = recv[id], s = segN++;
            sax[s] = (float) px(id); saz[s] = (float) pz(id);
            sbx[s] = (float) px(r); sbz[s] = (float) pz(r);
            sba[s] = bed[id];
            sbb[s] = kind[r] == 1 ? Math.min(Macro.SEA - 3, bed[r]) : bed[r];
            float a = acc[id];
            sAcc[s] = a;
            sw[s] = (float) Math.min(40, 1.5 + 0.55 * Math.sqrt(a));
            sdep[s] = (float) Math.min(9, 1.2 + 0.18 * Math.sqrt(a));
            float mx = 0;
            int i0 = id % nx, k0 = id / nx;
            for (int dk = -6; dk <= 6; dk++)
                for (int di = -6; di <= 6; di++) {
                    int i = i0 + di, k = k0 + dk;
                    if (i < 0 || k < 0 || i >= nx || k >= nz) continue;
                    mx = Math.max(mx, h[k * nx + i]);
                }
            sR[s] = (float) Math.min(480, sw[s] + 30 + 1.9 * Math.max(0, mx + 40 - bed[id]));
            int b0 = (int) Math.floor((Math.min(sax[s], sbx[s]) - sR[s] - ox) / B), b1 = (int) Math.floor((Math.max(sax[s], sbx[s]) + sR[s] - ox) / B);
            int c0 = (int) Math.floor((Math.min(saz[s], sbz[s]) - sR[s] - oz) / B), c1 = (int) Math.floor((Math.max(saz[s], sbz[s]) + sR[s] - oz) / B);
            for (int bk = Math.max(0, c0); bk <= Math.min(bnz - 1, c1); bk++)
                for (int bi = Math.max(0, b0); bi <= Math.min(bnx - 1, b1); bi++) {
                    int bid = bk * bnx + bi;
                    int[] arr = tmpB[bid];
                    if (arr == null) tmpB[bid] = arr = new int[8];
                    else if (bc[bid] == arr.length) tmpB[bid] = arr = Arrays.copyOf(arr, arr.length * 2);
                    arr[bc[bid]++] = s;
                }
        }
        buckets = new int[bnx * bnz][];
        for (int b = 0; b < tmpB.length; b++) if (tmpB[b] != null) buckets[b] = Arrays.copyOf(tmpB[b], bc[b]);
    }

    private boolean isRiver(int id) {
        int r = recv[id];
        if (r < 0 || acc[id] < A_MIN || kind[id] == 1 || kind[id] == 2) return false;
        return !(kind[id] == 3 && kind[r] == 3);
    }

    double px(int id) { return ox + (id % nx) * G + (Macro.hashD(ci * 31L + cj, id, 1) - 0.5) * G * 0.7; }
    double pz(int id) { return oz + (id / nx) * G + (Macro.hashD(ci * 31L + cj, id, 2) - 0.5) * G * 0.7; }

    /** Уровень озера рядом с точкой или NaN. */
    public double lakeLevel(double x, double z) {
        double fx = (x - ox) / G, fz = (z - oz) / G;
        int i = (int) Math.floor(fx), k = (int) Math.floor(fz);
        double best = Double.NaN;
        for (int dk = 0; dk <= 1; dk++)
            for (int di = 0; di <= 1; di++) {
                int ii = i + di, kk = k + dk;
                if (ii < 0 || kk < 0 || ii >= nx || kk >= nz) continue;
                int id = kk * nx + ii;
                if (kind[id] == 3 && !(filled[id] <= best)) best = filled[id];
            }
        return best;
    }

    /** Наибольшая глубина озера (уровень минус крупный рельеф) в узлах рядом с точкой или 0. */
    public double lakeDepth(double x, double z) {
        double fx = (x - ox) / G, fz = (z - oz) / G;
        int i = (int) Math.floor(fx), k = (int) Math.floor(fz);
        double best = 0;
        for (int dk = 0; dk <= 1; dk++)
            for (int di = 0; di <= 1; di++) {
                int ii = i + di, kk = k + dk;
                if (ii < 0 || kk < 0 || ii >= nx || kk >= nz) continue;
                int id = kk * nx + ii;
                if (kind[id] == 3) best = Math.max(best, filled[id] - h[id]);
            }
        return best;
    }

    public int[] bucket(double x, double z) {
        int bi = (int) Math.floor((x - ox) / B), bk = (int) Math.floor((z - oz) / B);
        if (bi < 0 || bk < 0 || bi >= bnx || bk >= bnz) return null;
        return buckets[bk * bnx + bi];
    }

    public int segments() { return segN; }

    /** Ближайший отрезок реки, подходящий под условие: {x, z, уровень воды} или null. */
    public double[] nearestSegment(double x, double z, java.util.function.IntPredicate ok, boolean atStep) {
        double best = Double.MAX_VALUE;
        double[] r = null;
        for (int s = 0; s < segN; s++) {
            if (!ok.test(s)) continue;
            double t = atStep ? 0.74 : 0.5;
            double px = sax[s] + (sbx[s] - sax[s]) * t, pz = saz[s] + (sbz[s] - saz[s]) * t;
            double d = (px - x) * (px - x) + (pz - z) * (pz - z);
            if (d < best) { best = d; r = new double[] { px, pz, atStep ? sba[s] : sba[s] + (sbb[s] - sba[s]) * t }; }
        }
        return r;
    }

    public boolean big(int s) { return sAcc[s] > 400; }
    public boolean step(int s) { return sba[s] - sbb[s] > 3 && sAcc[s] > 80; }

    /** Ближайший узел озера: {x, z, уровень} или null. */
    public double[] nearestLake(double x, double z) {
        double best = Double.MAX_VALUE;
        double[] r = null;
        for (int id = 0; id < kind.length; id++) {
            if (kind[id] != 3) continue;
            double px = ox + (id % nx) * G, pz = oz + (id / nx) * G;
            double d = (px - x) * (px - x) + (pz - z) * (pz - z);
            if (d < best) { best = d; r = new double[] { px, pz, filled[id] }; }
        }
        return r;
    }

    /** Минимальная куча по ключу float. */
    static final class Heap {
        final int[] ids;
        final float[] keys;
        int size;
        Heap(int cap) { ids = new int[cap]; keys = new float[cap]; }
        void push(int id, float k) {
            int i = size++;
            while (i > 0) {
                int p = (i - 1) >> 1;
                if (keys[p] <= k) break;
                ids[i] = ids[p]; keys[i] = keys[p]; i = p;
            }
            ids[i] = id; keys[i] = k;
        }
        int pop() {
            int top = ids[0];
            int id = ids[--size];
            float k = keys[size];
            int i = 0;
            while (true) {
                int l = 2 * i + 1;
                if (l >= size) break;
                int c = l + 1 < size && keys[l + 1] < keys[l] ? l + 1 : l;
                if (keys[c] >= k) break;
                ids[i] = ids[c]; keys[i] = keys[c]; i = c;
            }
            ids[i] = id; keys[i] = k;
            return top;
        }
    }
}
