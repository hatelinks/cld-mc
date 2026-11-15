package dev.cld.tcw.gen;

import java.util.Arrays;

// Где и сколько воды ставить в реке, по окну n x n вокруг чанка.
// Вход: lvl и bed (верхний блок воды и дно реки, NONE где не река), top (верхний твёрдый блок земли).
// Выход: water (верхний блок воды или NONE), bed (дно после поправки), bank (до какой высоты поднять берег или NONE).
// Вода не стоит выше места, куда она стекла бы через берег (заливка от суши, как у озёр),
// но на дне всегда лежит хотя бы один источник. Берег ниже воды на 1-4 блока досыпается.
public final class RiverWater {
    public static final int NONE = Integer.MIN_VALUE;
    // насколько можно досыпать берег и засыпать яму под дном
    static final int BANK = 4, FILL = 2;
    // земля выше воды на столько: река под навесом или в столбе, не трогаем
    static final int OVER = 8;

    public static void solve(int n, int[] lvl, int[] bed, int[] top, int[] water, int[] bank) {
        int nn = n * n;
        Arrays.fill(water, NONE);
        Arrays.fill(bank, NONE);
        boolean[] riv = new boolean[nn];
        for (int id = 0; id < nn; id++) {
            if (lvl[id] == NONE || top[id] - lvl[id] > OVER) continue;
            riv[id] = true;
            // яма под дном (сглаживание шума, пещера): мелкую засыпаем, глубокую берём за дно
            if (bed[id] - top[id] > FILL) bed[id] = top[id];
        }
        // берег: суша рядом с рекой чуть ниже воды досыпается до уровня воды
        int[] ground = new int[nn];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int id = j * n + i;
                if (riv[id]) continue;
                ground[id] = top[id];
                int need = NONE;
                for (int dj = -1; dj <= 1; dj++)
                    for (int di = -1; di <= 1; di++) {
                        int a = i + di, b = j + dj;
                        if (a < 0 || b < 0 || a >= n || b >= n) continue;
                        int k = b * n + a;
                        if (riv[k]) need = Math.max(need, lvl[k]);
                    }
                if (need != NONE && need > top[id] && need - top[id] <= BANK) {
                    bank[id] = need;
                    ground[id] = need;
                }
            }
        // заливка от суши: уровень воды не выше самого низкого перелива
        int[] level = new int[nn];
        boolean[] done = new boolean[nn];
        Hydro.Heap q = new Hydro.Heap(nn * 4);
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int id = j * n + i;
                boolean edge = i == 0 || j == 0 || i == n - 1 || j == n - 1;
                if (!riv[id]) level[id] = ground[id];
                else if (edge) level[id] = lvl[id];
                else continue;
                done[id] = true;
                q.push(id, level[id]);
            }
        while (q.size > 0) {
            int c = q.pop();
            int ci = c % n, cj = c / n;
            for (int d = 0; d < 4; d++) {
                int i = ci + (d == 0 ? 1 : d == 1 ? -1 : 0), j = cj + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if (i < 0 || j < 0 || i >= n || j >= n) continue;
                int id = j * n + i;
                if (done[id]) continue;
                done[id] = true;
                level[id] = Math.min(lvl[id], Math.max(bed[id], level[c]));
                q.push(id, level[id]);
            }
        }
        // источник всегда стоит на дне, не выше и не ниже
        for (int id = 0; id < nn; id++) if (riv[id]) water[id] = Math.max(bed[id] + 1, level[id]);
    }
}
