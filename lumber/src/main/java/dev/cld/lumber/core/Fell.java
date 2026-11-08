package dev.cld.lumber.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Поиск дерева от места спила. Без классов Minecraft, чтобы проверять вне игры (lumber/tools/FellTest).
public final class Fell {
    // Что лежит в клетке
    public static final int AIR = 0;      // воздух, трава, снег и прочее, что можно заменить
    public static final int GROUND = 1;   // любой твёрдый блок не из дерева, а ещё незагруженный чанк
    public static final int LOG = 2;      // бревно, древесина, обтёсанные варианты, ножки грибов
    public static final int LEAF = 3;     // природная листва (без persistent)
    public static final int KEPT_LEAF = 4; // листва, поставленная игроком
    public static final int CANOPY = 5;   // шляпки грибов и наросты незерских деревьев
    public static final int ATTACH = 6;   // лианы, какао, ульи и прочее, что висит на дереве
    public static final int ROOT = 7;     // корни мангра
    public static final int STEM = 8;     // ножка огромного гриба или незерского дерева

    // почему последний поиск отдал дерево Create (для отладки, -Dcld_lumber.debug=true)
    public static volatile String lastReason = "";

    private static Result fail(String why) {
        lastReason = why;
        return null;
    }

    public interface World {
        int kind(int x, int y, int z);
    }

    public static final class Result {
        public final ArrayList<Long> logs = new ArrayList<>();
        public final ArrayList<Long> leaves = new ArrayList<>();
        public final ArrayList<Long> attachments = new ArrayList<>();
    }

    // Пределы, чтобы не уйти в огромную постройку
    public static int maxLogs = 6000;
    public static int maxLeafScan = 80000;
    public static int radius = 40;         // по горизонтали от спила
    public static int height = 320;        // вверх от спила
    public static int cutRadius = 4;       // на уровне спила берём ствол и корни до 9x9 вокруг

    static final int LEAF_REACH = 8;       // листва дальше 6 от бревна и так осыпается
    static final int LEAF_SUPPORT = 6;     // как в игре: лист живёт, если бревно не дальше 6
    static final int FLOAT_REACH = 3;      // кусок кроны без ствола ищем не глубже 2 листьев
    static final int FLOAT_MAX = 1500;
    static final int ATTACH_CHAIN = 64;

    static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    static final int[][] AROUND = around();
    // шляпки грибов соединены и по рёбрам (у красного гриба бока шляпки касаются верха только углом)
    static final int[][] CANOPY_STEPS = canopySteps();

    private final World world;
    private final int px, py, pz;
    private final HashMap<Long, Integer> kinds = new HashMap<>();

    private Fell(World world, int px, int py, int pz) {
        this.world = world;
        this.px = px;
        this.py = py;
        this.pz = pz;
    }

    // null значит "не наше дерево", тогда работает обычный поиск Create
    public static Result find(World world, int x, int y, int z) {
        return new Fell(world, x, y, z).run();
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static int x(long p) {
        return (int) (p >> 38);
    }

    public static int y(long p) {
        return (int) (p << 52 >> 52);
    }

    public static int z(long p) {
        return (int) (p << 26 >> 38);
    }

    private int kind(long p) {
        Integer k = kinds.get(p);
        if (k == null) {
            k = world.kind(x(p), y(p), z(p));
            kinds.put(p, k);
        }
        return k;
    }

    private boolean inBox(long p) {
        int dy = y(p) - py;
        return dy >= 0 && dy <= height && Math.abs(x(p) - px) <= radius && Math.abs(z(p) - pz) <= radius;
    }

    private static boolean woody(int k) {
        return k == LOG || k == ROOT || k == STEM;
    }

    private Result run() {
        // 1. Брёвна, связанные со спилом, по 26 соседям, ниже спила не спускаемся
        Set<Long> logSet = new HashSet<>();
        List<Long> logs = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        long start = pack(px, py, pz);
        seen.add(start);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = 0; dy <= 1; dy++) {
                    long n = pack(px + dx, py + dy, pz + dz);
                    if (seen.add(n)) queue.add(n);
                }
        while (!queue.isEmpty()) {
            long p = queue.poll();
            if (!inBox(p) || !woody(kind(p))) continue;
            if (y(p) == py && Math.max(Math.abs(x(p) - px), Math.abs(z(p) - pz)) > cutRadius) continue;
            logs.add(p);
            logSet.add(p);
            if (logs.size() > maxLogs) return fail("больше " + maxLogs + " брёвен");
            for (int[] d : AROUND) {
                int ny = y(p) + d[1];
                if (ny < py) continue;
                long n = pack(x(p) + d[0], ny, z(p) + d[2]);
                if (seen.add(n)) queue.add(n);
            }
        }
        if (logs.isEmpty()) return fail("рядом со спилом нет брёвен");

        // 2. Обход листвы от наших брёвен. Чужие брёвна запоминаем, оторванные куски кроны забираем
        HashMap<Long, Integer> depth = new HashMap<>();
        Set<Long> foreign = new HashSet<>();
        Set<Long> grounded = new HashSet<>();
        ArrayDeque<Long> leafQueue = new ArrayDeque<>();
        int scan = LEAF_REACH + LEAF_SUPPORT;
        for (long l : logs) seedFrom(l, depth, leafQueue);
        while (!leafQueue.isEmpty()) {
            long p = leafQueue.poll();
            int d = depth.get(p);
            for (int[] f : steps(p)) {
                long n = pack(x(p) + f[0], y(p) + f[1], z(p) + f[2]);
                if (f.length == 4 && kind(n) != CANOPY) continue;
                int k = kind(n);
                if (k == LEAF || k == CANOPY) {
                    if (d + 1 > scan) continue;
                    Integer old = depth.get(n);
                    if (old != null && old <= d + 1) continue;
                    if (old == null && depth.size() >= maxLeafScan) continue;
                    depth.put(n, d + 1);
                    leafQueue.add(n);
                } else if (woody(k) && !logSet.contains(n) && !foreign.contains(n)) {
                    List<Long> piece = d + 1 <= FLOAT_REACH && !grounded.contains(n) ? floating(n, logSet) : null;
                    if (piece == null) {
                        if (!grounded.contains(n)) markGrounded(n, logSet, grounded);
                        foreign.add(n);
                        continue;
                    }
                    if (logs.size() + piece.size() > maxLogs) return fail("больше " + maxLogs + " брёвен");
                    for (long q : piece) {
                        logs.add(q);
                        logSet.add(q);
                    }
                    for (long q : piece) seedFrom(q, depth, leafQueue);
                }
            }
        }
        foreign.removeAll(logSet);

        // 3. Листва, которую держат чужие брёвна, остаётся на месте.
        // Пень и корни ниже спила держат только листву ниже спила, иначе маленькое дерево не срубится целиком
        Set<Long> held = new HashSet<>();
        HashMap<Long, Integer> hold = new HashMap<>();
        HashMap<Long, Integer> holdLow = new HashMap<>();
        List<Long> high = new ArrayList<>(), low = new ArrayList<>();
        for (long f : foreign) (y(f) < py ? low : high).add(f);
        holdPass(high, false, depth, hold, held);
        holdPass(low, true, depth, holdLow, held);
        for (Map.Entry<Long, Integer> e : holdLow.entrySet()) hold.merge(e.getKey(), e.getValue(), Math::min);

        // 4. Забираем листву, до которой можно дойти от наших брёвен мимо удержанной
        Result r = new Result();
        HashMap<Long, Integer> take = new HashMap<>();
        ArrayDeque<Long> takeQueue = new ArrayDeque<>();
        for (long l : logs) {
            for (int[] f : FACES) {
                long n = pack(x(l) + f[0], y(l) + f[1], z(l) + f[2]);
                if (!depth.containsKey(n) || held.contains(n) || take.containsKey(n)) continue;
                take.put(n, 1);
                takeQueue.add(n);
            }
        }
        // Признак дерева: природная листва не вплотную к брёвнам (крона), которая ближе к нашим брёвнам, чем к чужим.
        // Листва вплотную бывает и у стены дома рядом с деревом. Шляпки считаются только у грибов
        boolean fungus = false;
        for (long l : logs) if (kind(l) == STEM) fungus = true;
        int natural = 0;
        for (Map.Entry<Long, Integer> e : depth.entrySet()) {
            int d = e.getValue();
            if (d < 2 || d > LEAF_SUPPORT) continue;
            int k = kind(e.getKey());
            if (k != LEAF && !(k == CANOPY && fungus)) continue;
            Integer h = hold.get(e.getKey());
            if (h == null || d < h) natural++;
        }
        // Без природной листвы это не дерево, а постройка или сухое дерево: пусть решает сам Create
        if (natural == 0 || natural * 8 < logs.size()) return fail("мало кроны: " + natural + " листьев на " + logs.size() + " брёвен");
        while (!takeQueue.isEmpty()) {
            long p = takeQueue.poll();
            int d = take.get(p);
            if (d > LEAF_REACH) continue;
            r.leaves.add(p);
            for (int[] f : steps(p)) {
                long n = pack(x(p) + f[0], y(p) + f[1], z(p) + f[2]);
                if (f.length == 4 && kind(n) != CANOPY) continue;
                if (!depth.containsKey(n) || held.contains(n) || take.containsKey(n)) continue;
                take.put(n, d + 1);
                takeQueue.add(n);
            }
        }
        r.logs.addAll(logs);

        // 5. Лианы, какао, ульи на наших брёвнах и листве, лианы тянем вниз цепочкой
        Set<Long> parts = new HashSet<>(logSet);
        parts.addAll(r.leaves);
        Set<Long> attached = new HashSet<>();
        ArrayDeque<long[]> attachQueue = new ArrayDeque<>();
        for (long p : parts) {
            for (int[] f : FACES) {
                long n = pack(x(p) + f[0], y(p) + f[1], z(p) + f[2]);
                if (y(n) < py || attached.contains(n) || kind(n) != ATTACH) continue;
                attached.add(n);
                attachQueue.add(new long[]{n, 0});
            }
        }
        while (!attachQueue.isEmpty()) {
            long[] a = attachQueue.poll();
            r.attachments.add(a[0]);
            if (a[1] >= ATTACH_CHAIN) continue;
            long below = pack(x(a[0]), y(a[0]) - 1, z(a[0]));
            if (y(below) < py || attached.contains(below) || kind(below) != ATTACH) continue;
            attached.add(below);
            attachQueue.add(new long[]{below, a[1] + 1});
        }
        return r;
    }

    private void holdPass(List<Long> sources, boolean lowOnly, Map<Long, Integer> depth, Map<Long, Integer> hold, Set<Long> held) {
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (long f : sources) {
            for (int[] fc : FACES) {
                long n = pack(x(f) + fc[0], y(f) + fc[1], z(f) + fc[2]);
                if (!depth.containsKey(n) || hold.containsKey(n) || (lowOnly && y(n) >= py)) continue;
                hold.put(n, 1);
                queue.add(n);
            }
        }
        while (!queue.isEmpty()) {
            long p = queue.poll();
            int d = hold.get(p);
            if (d > LEAF_SUPPORT) continue;
            held.add(p);
            for (int[] f : steps(p)) {
                long n = pack(x(p) + f[0], y(p) + f[1], z(p) + f[2]);
                if (f.length == 4 && kind(n) != CANOPY) continue;
                if (!depth.containsKey(n) || hold.containsKey(n) || (lowOnly && y(n) >= py)) continue;
                hold.put(n, d + 1);
                queue.add(n);
            }
        }
    }

    private void seedFrom(long l, Map<Long, Integer> depth, ArrayDeque<Long> queue) {
        for (int[] f : FACES) {
            long n = pack(x(l) + f[0], y(l) + f[1], z(l) + f[2]);
            int k = kind(n);
            if (k != LEAF && k != CANOPY) continue;
            Integer old = depth.get(n);
            if (old != null && old <= 1) continue;
            if (old == null && depth.size() >= maxLeafScan) continue;
            depth.put(n, 1);
            queue.add(n);
        }
    }

    // Кусок брёвен, который ни на что не опирается (ветка в кроне). null если он стоит на земле
    private List<Long> floating(long from, Set<Long> ours) {
        List<Long> piece = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        seen.add(from);
        queue.add(from);
        while (!queue.isEmpty()) {
            long p = queue.poll();
            if (y(p) < py || !inBox(p)) return null;
            piece.add(p);
            if (piece.size() > FLOAT_MAX) return null;
            long below = pack(x(p), y(p) - 1, z(p));
            if (kind(below) == GROUND) return null;
            for (int[] d : AROUND) {
                long n = pack(x(p) + d[0], y(p) + d[1], z(p) + d[2]);
                if (ours.contains(n)) return null;
                if (!woody(kind(n)) || !seen.add(n)) continue;
                queue.add(n);
            }
        }
        return piece;
    }

    // Запоминаем весь стоящий на земле кусок, чтобы не проверять его заново от каждого листа
    private void markGrounded(long from, Set<Long> ours, Set<Long> grounded) {
        ArrayDeque<Long> queue = new ArrayDeque<>();
        grounded.add(from);
        queue.add(from);
        int count = 0;
        while (!queue.isEmpty() && count++ < FLOAT_MAX) {
            long p = queue.poll();
            for (int[] d : AROUND) {
                long n = pack(x(p) + d[0], y(p) + d[1], z(p) + d[2]);
                if (ours.contains(n) || grounded.contains(n) || !woody(kind(n))) continue;
                grounded.add(n);
                queue.add(n);
            }
        }
    }

    private int[][] steps(long p) {
        return kind(p) == CANOPY ? CANOPY_STEPS : FACES;
    }

    // ходы по граням как есть, ходы по рёбрам и углам помечены четвёртым числом
    private static int[][] canopySteps() {
        int[][] a = new int[26][];
        int i = 0;
        for (int[] d : around()) {
            int sum = Math.abs(d[0]) + Math.abs(d[1]) + Math.abs(d[2]);
            a[i++] = sum == 1 ? d : new int[]{d[0], d[1], d[2], 1};
        }
        return a;
    }

    private static int[][] around() {
        int[][] a = new int[26][];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    if (dx != 0 || dy != 0 || dz != 0) a[i++] = new int[]{dx, dy, dz};
        return a;
    }
}
