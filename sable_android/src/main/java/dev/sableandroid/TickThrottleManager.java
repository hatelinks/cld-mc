package dev.sableandroid;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

// Вызывается из начала tick() блоков Simulated, вставку делает coremod
public class TickThrottleManager {
    private static final WeakHashMap<Object, int[]> COUNTERS = new WeakHashMap<>();
    private static final ConcurrentHashMap<String, Integer> LIVE_INTERVALS = new ConcurrentHashMap<>();

    // true раз в interval тиков; interval приходит из скрипта, LIVE_INTERVALS может его переопределить
    public static boolean shouldTick(Object blockEntity, int interval) {
        String name = blockEntity.getClass().getName().replace('.', '/');
        int every = LIVE_INTERVALS.getOrDefault(name, interval);
        if (every <= 1) return every == 1;
        int[] counter = COUNTERS.computeIfAbsent(blockEntity, k -> new int[]{0});
        counter[0]++;
        if (counter[0] >= every) {
            counter[0] = 0;
            return true;
        }
        return false;
    }

    public static void updateIntervals(Map<String, Integer> intervals) {
        LIVE_INTERVALS.clear();
        LIVE_INTERVALS.putAll(intervals);
        COUNTERS.clear();
    }
}
