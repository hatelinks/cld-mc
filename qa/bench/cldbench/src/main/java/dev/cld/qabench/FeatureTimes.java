package dev.cld.qabench;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

// Время по фичам (верхний уровень applyBiomeDecoration): сколько наносекунд и вызовов на каждую placed_feature
public final class FeatureTimes {
    public static volatile boolean on;
    static final Map<String, LongAdder[]> T = new ConcurrentHashMap<>();

    public static void add(String id, long ns) {
        LongAdder[] a = T.computeIfAbsent(id, k -> new LongAdder[]{new LongAdder(), new LongAdder()});
        a[0].add(ns);
        a[1].increment();
    }

    static void reset() { T.clear(); }

    static void print(int top) {
        List<Map.Entry<String, LongAdder[]>> l = new ArrayList<>(T.entrySet());
        l.sort((x, y) -> Long.compare(y.getValue()[0].sum(), x.getValue()[0].sum()));
        long all = 0;
        for (var e : l) all += e.getValue()[0].sum();
        System.out.printf("QAFEAT total %.1f s%n", all / 1e9);
        for (int i = 0; i < Math.min(top, l.size()); i++) {
            var e = l.get(i);
            long ns = e.getValue()[0].sum(), n = e.getValue()[1].sum();
            System.out.printf("QAFEAT %6.1f%% %8.2f s %9d calls %s%n", 100.0 * ns / Math.max(1, all), ns / 1e9, n, e.getKey());
        }
    }
}
