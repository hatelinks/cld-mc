package dev.cld.far.gen;

// Выбор тайлов дальнего рельефа: дерево квадрантов вокруг камеры, чем ближе, тем мельче.
// Тайл делится на 4, пока до него ближе split x его размер. Пока не готовы все 4 потомка, рисуется сам тайл,
// поэтому дыр не бывает: сначала считаются грубые уровни, потом мельче.
public final class LodTree {
    public interface Sink {
        boolean ready(long key);
        void draw(long key, int level, int tx, int tz);
        void want(long key, int level, int tx, int tz, double priority);
    }

    private LodTree() {}

    public static long key(int level, int tx, int tz) {
        return ((long) level << 58) | ((tx & 0x1FFFFFFFL) << 29) | (tz & 0x1FFFFFFFL);
    }

    public static int level(long key) { return (int) (key >>> 58); }
    public static int tx(long key) { return (int) (key << 6 >> 35); }
    public static int tz(long key) { return (int) (key << 35 >> 35); }

    // Верхний уровень: самый крупный тайл не меньше половины дальности.
    public static int maxLevel(double tile0, double dist) {
        int l = 0;
        while (tile0 * (1L << l) * 2 < dist && l < 20) l++;
        return l;
    }

    final static class Q {
        final double cx, cz, dist, clip, split, tile0, dy, dy0;
        final int maxLevel;
        final Sink sink;
        Q(double cx, double cy, double cz, double dist, double clip, double split, double tile0, Sink sink) {
            // высоко над землёй мелкая сетка не нужна: деление считается по расстоянию с учётом высоты
            this.dy = Math.max(0, cy - 320);
            // игра не рисует чанки дальше прорисовки и по вертикали: высоко над землёй под игроком рисуем свой рельеф
            this.dy0 = Math.max(0, cy - 200);
            this.cx = cx; this.cz = cz; this.dist = dist; this.clip = clip; this.split = split; this.tile0 = tile0; this.sink = sink;
            maxLevel = maxLevel(tile0, dist);
        }
        double size(int l) { return tile0 * (1L << l); }
        double near(int l, int tx, int tz) {
            double s = size(l), x0 = tx * s, z0 = tz * s;
            double dx = Math.max(0, Math.max(x0 - cx, cx - x0 - s)), dz = Math.max(0, Math.max(z0 - cz, cz - z0 - s));
            return Math.sqrt(dx * dx + dz * dz);
        }
        boolean skip(int l, int tx, int tz, double d) {
            if (d > dist) return true;
            double s = size(l), x0 = tx * s, z0 = tz * s;
            double fx = Math.max(Math.abs(x0 - cx), Math.abs(x0 + s - cx)), fz = Math.max(Math.abs(z0 - cz), Math.abs(z0 + s - cz));
            return fx * fx + fz * fz + dy0 * dy0 < clip * clip;
        }
        boolean wantsSplit(int l, double d) { return l > 0 && Math.sqrt(d * d + dy * dy) < split * size(l); }

        boolean covered(int l, int tx, int tz) {
            double d = near(l, tx, tz);
            if (skip(l, tx, tz, d)) return true;
            if (wantsSplit(l, d)) {
                return covered(l - 1, tx * 2, tz * 2) && covered(l - 1, tx * 2 + 1, tz * 2)
                        && covered(l - 1, tx * 2, tz * 2 + 1) && covered(l - 1, tx * 2 + 1, tz * 2 + 1);
            }
            return sink.ready(key(l, tx, tz));
        }

        void visit(int l, int tx, int tz) {
            double d = near(l, tx, tz);
            if (skip(l, tx, tz, d)) return;
            boolean split = wantsSplit(l, d);
            if (split && covered(l - 1, tx * 2, tz * 2) && covered(l - 1, tx * 2 + 1, tz * 2)
                    && covered(l - 1, tx * 2, tz * 2 + 1) && covered(l - 1, tx * 2 + 1, tz * 2 + 1)) {
                for (int k = 0; k < 4; k++) visit(l - 1, tx * 2 + (k & 1), tz * 2 + (k >> 1));
                return;
            }
            long key = key(l, tx, tz);
            if (sink.ready(key)) sink.draw(key, l, tx, tz);
            else sink.want(key, l, tx, tz, priority(l, d));
            if (split) for (int k = 0; k < 4; k++) request(l - 1, tx * 2 + (k & 1), tz * 2 + (k >> 1));
        }

        void request(int l, int tx, int tz) {
            double d = near(l, tx, tz);
            if (skip(l, tx, tz, d)) return;
            long key = key(l, tx, tz);
            if (!sink.ready(key)) sink.want(key, l, tx, tz, priority(l, d));
            if (wantsSplit(l, d)) for (int k = 0; k < 4; k++) request(l - 1, tx * 2 + (k & 1), tz * 2 + (k >> 1));
        }

        // Сначала грубые уровни (быстро закрыть весь горизонт), внутри уровня сначала ближние.
        double priority(int l, double d) { return (maxLevel - l) * 1e7 + d; }
    }

    // tile0: сторона тайла нижнего уровня, блоков
    // clip: радиус, внутри которого рисует сама игра (тайлы целиком внутри пропускаются)
    // cy: высота камеры: высоко над землёй тайлы грубее
    public static void select(double cx, double cy, double cz, double dist, double clip, double split, double tile0, Sink sink) {
        Q q = new Q(cx, cy, cz, dist, clip, split, tile0, sink);
        double s = q.size(q.maxLevel);
        int ax = (int) Math.floor((cx - dist) / s), bx = (int) Math.floor((cx + dist) / s);
        int az = (int) Math.floor((cz - dist) / s), bz = (int) Math.floor((cz + dist) / s);
        for (int tz = az; tz <= bz; tz++)
            for (int tx = ax; tx <= bx; tx++) q.visit(q.maxLevel, tx, tz);
    }
}
