import dev.cld.lumber.core.Fell;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

// Проверка поиска дерева вне игры: javac -d /tmp/lumber lumber/src/main/java/dev/cld/lumber/core/Fell.java lumber/tools/FellTest.java && java -cp /tmp/lumber FellTest
public class FellTest {
    static int failed = 0;

    static final class W implements Fell.World {
        final HashMap<Long, Integer> m = new HashMap<>();
        int ground = 64;

        public int kind(int x, int y, int z) {
            Integer k = m.get(Fell.pack(x, y, z));
            if (k != null) return k;
            return y < ground ? Fell.GROUND : Fell.AIR;
        }

        void set(int x, int y, int z, int k) {
            m.put(Fell.pack(x, y, z), k);
        }

        int get(int x, int y, int z) {
            return kind(x, y, z);
        }

        // ствол и шар листвы, как у дуба
        void oak(int x, int z, int h) {
            for (int y = 64; y < 64 + h; y++) set(x, y, z, Fell.LOG);
            int top = 64 + h - 1;
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    for (int dy = -2; dy <= 1; dy++) {
                        if (Math.abs(dx) + Math.abs(dz) + Math.max(0, dy) > 3) continue;
                        if (get(x + dx, top + dy, z + dz) == Fell.AIR) set(x + dx, top + dy, z + dz, Fell.LEAF);
                    }
            if (get(x, top + 1, z) == Fell.AIR) set(x, top + 1, z, Fell.LEAF);
        }

        // имитация пилы: блок уже сломан
        Fell.Result cut(int x, int y, int z) {
            set(x, y, z, Fell.AIR);
            return Fell.find(this, x, y, z);
        }

        int count(int k) {
            int c = 0;
            for (int v : m.values()) if (v == k) c++;
            return c;
        }
    }

    static Set<Long> set(java.util.List<Long> l) {
        return new HashSet<>(l);
    }

    static void check(String name, boolean ok) {
        System.out.println((ok ? "ok   " : "FAIL ") + name);
        if (!ok) failed++;
    }

    public static void main(String[] a) {
        // 1. простой дуб, спил снизу: всё дерево
        {
            W w = new W();
            w.oak(0, 0, 6);
            int logs = w.count(Fell.LOG) - 1, leaves = w.count(Fell.LEAF);
            Fell.Result r = w.cut(0, 64, 0);
            check("дуб целиком", r != null && r.logs.size() == logs && r.leaves.size() == leaves);
        }
        // 2. ствол 2x2, пилим один угол: все четыре столба
        {
            W w = new W();
            for (int y = 64; y < 80; y++)
                for (int dx = 0; dx < 2; dx++)
                    for (int dz = 0; dz < 2; dz++) w.set(dx, y, dz, Fell.LOG);
            for (int dx = -3; dx <= 4; dx++)
                for (int dz = -3; dz <= 4; dz++)
                    for (int y = 76; y < 82; y++) if (w.get(dx, y, dz) == Fell.AIR) w.set(dx, y, dz, Fell.LEAF);
            int logs = w.count(Fell.LOG) - 1;
            Fell.Result r = w.cut(0, 64, 0);
            check("ствол 2x2 одним спилом", r != null && r.logs.size() == logs && r.leaves.size() == w.count(Fell.LEAF));
        }
        // 3. ветка вниз и обтёсанное бревно внутри ствола (для поиска это тоже бревно); часть ветки ниже спила остаётся
        {
            W w = new W();
            w.oak(0, 0, 10);
            for (int i = 1; i <= 5; i++) w.set(i, 72 - i, 0, Fell.LOG); // ветка вниз по диагонали до y=67
            w.set(6, 65, 0, Fell.LOG); // ниже уровня спила (66) отдельно стоящее бревно рядом
            w.set(7, 64, 0, Fell.LOG);
            w.set(5, 67, 1, Fell.LEAF);
            Fell.Result r = w.cut(0, 66, 0);
            Set<Long> s = set(r == null ? java.util.List.of() : r.logs);
            boolean branch = true;
            for (int i = 1; i <= 5; i++) branch &= s.contains(Fell.pack(i, 72 - i, 0));
            check("ветка вниз до уровня спила", r != null && branch);
            check("ниже спила не трогаем", r != null && !s.contains(Fell.pack(6, 65, 0)) && !s.contains(Fell.pack(0, 65, 0)) && !s.contains(Fell.pack(0, 64, 0)));
        }
        // 4. два дерева рядом, кроны касаются: соседнее не трогаем, его листва остаётся
        {
            W w = new W();
            w.oak(0, 0, 6);
            w.oak(4, 0, 6);
            Fell.Result r = w.cut(0, 64, 0);
            Set<Long> logs = set(r.logs), leaves = set(r.leaves);
            boolean other = true;
            for (int y = 64; y < 70; y++) other &= !logs.contains(Fell.pack(4, y, 0));
            boolean held = true;
            for (long p : leaves) if (Math.abs(Fell.x(p) - 4) + Math.abs(Fell.y(p) - 69) + Math.abs(Fell.z(p)) <= 3 && Fell.x(p) >= 3) held = false;
            check("соседнее дерево цело", other);
            check("листва соседа на месте", held && leaves.contains(Fell.pack(-2, 69, 0)));
        }
        // 5. дом из брёвен без листвы: не наше дело, решает Create
        {
            W w = new W();
            for (int x = 0; x < 6; x++)
                for (int y = 64; y < 68; y++) {
                    w.set(x, y, 0, Fell.LOG);
                    w.set(x, y, 5, Fell.LOG);
                }
            Fell.Result r = w.cut(2, 64, 0);
            check("дом без листвы не рубим", r == null);
        }
        // 6. дом с листвой от игрока
        {
            W w = new W();
            for (int x = 0; x < 6; x++)
                for (int y = 64; y < 68; y++) w.set(x, y, 0, Fell.LOG);
            for (int x = 0; x < 6; x++) w.set(x, 68, 0, Fell.KEPT_LEAF);
            Fell.Result r = w.cut(2, 64, 0);
            check("дом с листвой игрока не рубим", r == null);
        }
        // 7. дом рядом с деревом, листва дерева касается стены: дом не рубим
        {
            W w = new W();
            w.oak(0, 0, 6);
            for (int z = -5; z <= 5; z++)
                for (int y = 64; y < 70; y++) w.set(3, y, z, Fell.LOG);
            w.set(2, 69, 0, Fell.LEAF);
            Fell.Result r = w.cut(3, 64, 4);
            check("стена рядом с кроной не рубится", r == null);
            Fell.Result t = w.cut(0, 64, 0);
            boolean wall = true;
            for (long p : t.logs) if (Fell.x(p) == 3) wall = false;
            check("дерево у стены рубится без стены", t != null && wall);
        }
        // 8. кусок кроны из брёвен без связи со стволом (как у пальм и больших деревьев WWOO)
        {
            W w = new W();
            w.oak(0, 0, 8);
            w.set(0, 72, 0, Fell.LEAF);
            for (int i = 0; i < 3; i++) w.set(0, 73 + i, 0, Fell.LOG);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) w.set(dx, 75, dz, Fell.LEAF);
            Fell.Result r = w.cut(0, 64, 0);
            check("оторванная часть кроны", r != null && set(r.logs).contains(Fell.pack(0, 75, 0)) && set(r.leaves).contains(Fell.pack(1, 75, 1)));
        }
        // 9. спил посередине ствола: пень и листва у пня остаются
        {
            W w = new W();
            for (int y = 64; y < 74; y++) w.set(0, y, 0, Fell.LOG);
            w.set(1, 65, 0, Fell.LEAF);
            w.set(1, 66, 0, Fell.LEAF);
            w.set(1, 67, 0, Fell.LEAF);
            w.set(0, 74, 0, Fell.LEAF);
            w.set(1, 73, 0, Fell.LEAF);
            w.set(1, 74, 0, Fell.LEAF);
            w.set(2, 73, 0, Fell.LEAF);
            Fell.Result r = w.cut(0, 67, 0);
            Set<Long> leaves = set(r.leaves), logs = set(r.logs);
            check("пень остаётся", !logs.contains(Fell.pack(0, 66, 0)) && logs.contains(Fell.pack(0, 73, 0)) && logs.size() == 6);
            check("листву у пня держит пень", !leaves.contains(Fell.pack(1, 66, 0)) && !leaves.contains(Fell.pack(1, 65, 0)) && leaves.contains(Fell.pack(0, 74, 0)));
        }
        // 10. огромный куб из брёвен: предел, отдаём Create
        {
            W w = new W();
            for (int x = 0; x < 20; x++)
                for (int y = 64; y < 84; y++)
                    for (int z = 0; z < 20; z++) w.set(x, y, z, Fell.LOG);
            w.set(5, 84, 5, Fell.LEAF);
            Fell.Result r = w.cut(1, 64, 1);
            check("огромная постройка не рубится", r == null);
        }
        // 11. лианы на стволе тянутся вниз, лианы ниже спила остаются
        {
            W w = new W();
            w.oak(0, 0, 8);
            for (int y = 64; y < 71; y++) w.set(1, y, 1, Fell.ATTACH);
            w.set(1, 70, 0, Fell.ATTACH);
            Fell.Result r = w.cut(0, 66, 0);
            Set<Long> at = set(r.attachments);
            check("лианы на дереве", at.contains(Fell.pack(1, 70, 0)) && !at.contains(Fell.pack(1, 65, 1)));
        }
        // 12. корни по уровню спила (WWOO): берём до 4 блоков от спила, дальше остаются
        {
            W w = new W();
            w.oak(0, 0, 8);
            for (int x = 1; x <= 6; x++) w.set(x, 64, 0, Fell.LOG);
            Fell.Result r = w.cut(0, 64, 0);
            Set<Long> logs = set(r.logs);
            check("корни у земли дальше 4 не берём", logs.contains(Fell.pack(4, 64, 0)) && !logs.contains(Fell.pack(5, 64, 0)));
        }
        // 13. огромный гриб: шляпка
        {
            W w = new W();
            for (int y = 64; y < 70; y++) w.set(0, y, 0, Fell.STEM);
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) if (Math.abs(dx) + Math.abs(dz) < 6) w.set(dx, 70, dz, Fell.CANOPY);
            Fell.Result r = w.cut(0, 64, 0);
            check("огромный гриб", r != null && r.leaves.size() == w.count(Fell.CANOPY) && r.logs.size() == 5);
        }
        // 13б. красный гриб как в игре: бока шляпки касаются верха только рёбрами
        {
            W w = new W();
            int h = 6;
            for (int y = 64; y < 64 + h; y++) w.set(0, y, 0, Fell.STEM);
            for (int y = 64 + h - 3; y <= 64 + h; y++) {
                int rad = y < 64 + h ? 2 : 1;
                for (int dx = -rad; dx <= rad; dx++)
                    for (int dz = -rad; dz <= rad; dz++) {
                        boolean edgeX = Math.abs(dx) == rad, edgeZ = Math.abs(dz) == rad;
                        if (y < 64 + h && !(edgeX ^ edgeZ)) continue;
                        if (w.get(dx, y, dz) == Fell.AIR) w.set(dx, y, dz, Fell.CANOPY);
                    }
            }
            int caps = w.count(Fell.CANOPY);
            Fell.Result r = w.cut(0, 64, 0);
            check("красный гриб целиком (" + caps + " блоков шляпки)", r != null && r.leaves.size() == caps && r.logs.size() == h - 1);
        }
        // 13в. олива WWOO: под стволом блок древесины вместо земли, маленькая крона рядом с ним
        {
            W w = new W();
            w.set(0, 63, 0, Fell.LOG);
            w.set(0, 64, 0, Fell.LOG);
            w.set(0, 65, 0, Fell.LOG);
            w.set(1, 66, 0, Fell.LOG);
            for (int dx = -1; dx <= 2; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    for (int y = 66; y <= 67; y++) if (w.get(dx, y, dz) == Fell.AIR) w.set(dx, y, dz, Fell.LEAF);
            int leaves = w.count(Fell.LEAF);
            Fell.Result r = w.cut(0, 64, 0);
            check("маленькое дерево на древесине вместо земли", r != null && r.leaves.size() == leaves && r.logs.size() == 2);
        }
        // 14. шляпки рядом с бревенчатым домом: дом не рубим
        {
            W w = new W();
            for (int x = 0; x < 6; x++)
                for (int y = 64; y < 68; y++) w.set(x, y, 0, Fell.LOG);
            w.set(2, 68, 0, Fell.CANOPY);
            Fell.Result r = w.cut(2, 64, 0);
            check("дом с грибными блоками не рубим", r == null);
        }
        // 15. упаковка координат
        {
            boolean ok = true;
            int[][] t = {{0, 0, 0}, {-1, -1, -1}, {30000000, 640, -30000000}, {-29999999, -128, 29999999}, {123, -64, -456}};
            for (int[] c : t) {
                long p = Fell.pack(c[0], c[1], c[2]);
                ok &= Fell.x(p) == c[0] && Fell.y(p) == c[1] && Fell.z(p) == c[2];
            }
            check("упаковка координат", ok);
        }
        // 16. большое дерево WWOO: толстый ствол, корни, ветки во все стороны, листва
        {
            W w = new W();
            java.util.Random rnd = new java.util.Random(1);
            for (int y = 64; y < 100; y++)
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) w.set(dx, y, dz, Fell.LOG);
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}};
            for (int[] d : dirs) {
                int x = 0, y = 85 + rnd.nextInt(10), z = 0;
                for (int i = 0; i < 12; i++) {
                    x += d[0];
                    z += d[1];
                    y += rnd.nextInt(3) - 1;
                    w.set(x, y, z, Fell.LOG);
                    for (int lx = -2; lx <= 2; lx++)
                        for (int ly = -1; ly <= 2; ly++)
                            for (int lz = -2; lz <= 2; lz++)
                                if (w.get(x + lx, y + ly, z + lz) == Fell.AIR) w.set(x + lx, y + ly, z + lz, Fell.LEAF);
                }
            }
            int logs = w.count(Fell.LOG) - 1, leaves = w.count(Fell.LEAF);
            long t0 = System.nanoTime();
            Fell.Result r = w.cut(0, 64, 0);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            check("большое дерево целиком (" + logs + " брёвен, " + leaves + " листьев, " + ms + " мс)", r != null && r.logs.size() == logs && r.leaves.size() == leaves);
        }
        System.out.println(failed == 0 ? "все проверки прошли" : "провалено: " + failed);
        if (failed > 0) System.exit(1);
    }
}
