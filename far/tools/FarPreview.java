import dev.cld.far.gen.LodTree;
import dev.cld.far.gen.TileMesh;
import dev.cld.tcw.gen.Terrain;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

// Проверка дальней прорисовки вне игры: какие тайлы выбираются, сколько их считать и как это выглядит.
// java FarPreview <seed> <x> <y> <z> <yaw град.> <дальность> <шаг> <split> <clip> <out.png> [threads] [горы 1..3] [сглаживание 0..1] [пикселизация 0..2] [столбы 0|1]
// Рисует сетки тайлов программным растеризатором (z-буфер) с той же перспективой и туманом, что в игре.
public class FarPreview {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]);
        double cx = Double.parseDouble(a[1]), cz = Double.parseDouble(a[3]);
        Terrain t = new Terrain(seed);
        double ground = t.column(cx, cz, new Terrain.Column()).height;
        double cy = a[2].startsWith("+") ? Math.max(ground, 63) + Double.parseDouble(a[2].substring(1)) : Double.parseDouble(a[2]);
        System.out.printf("camera y %.0f (ground %.0f)%n", cy, ground);
        double yaw = Math.toRadians(Double.parseDouble(a[4]));
        double dist = Double.parseDouble(a[5]), step = Double.parseDouble(a[6]), split = Double.parseDouble(a[7]), clip = Double.parseDouble(a[8]);
        int threads = a.length > 10 ? Integer.parseInt(a[10]) : 1;
        int mountains = a.length > 11 ? Integer.parseInt(a[11]) : 2;
        double smooth = a.length > 12 ? Double.parseDouble(a[12]) : 0.15;
        int pixel = a.length > 13 ? Integer.parseInt(a[13]) : 2;
        boolean pillars = a.length <= 14 || !a[14].equals("0");
        double tile0 = TileMesh.N * step;
        List<long[]> want = new ArrayList<>();
        LodTree.select(cx, cy, cz, dist, clip, split, tile0, new LodTree.Sink() {
            public boolean ready(long key) { return true; }
            public void draw(long key, int l, int tx, int tz) { want.add(new long[] { l, tx, tz }); }
            public void want(long key, int l, int tx, int tz, double p) {}
        });
        int[] perLevel = new int[24];
        for (long[] w : want) perLevel[(int) w[0]]++;
        StringBuilder sb = new StringBuilder();
        for (int l = 0; l < 24; l++) if (perLevel[l] > 0) sb.append(" L").append(l).append('=').append(perLevel[l]);
        System.out.println("tiles " + want.size() + ":" + sb);
        long t0 = System.nanoTime();
        AtomicLong verts = new AtomicLong();
        TileMesh[] meshes = new TileMesh[want.size()];
        java.util.concurrent.ForkJoinPool pool = new java.util.concurrent.ForkJoinPool(threads);
        pool.submit(() -> IntStream.range(0, want.size()).parallel().forEach(i -> {
            long[] w = want.get(i);
            double s = tile0 * (1L << w[0]);
            meshes[i] = TileMesh.build(t, TileMesh.Palette.MODEL, w[1] * s, w[2] * s, s, new TileMesh.Style(mountains, false, smooth, pixel, pillars, true), () -> false);
            verts.addAndGet(meshes[i].vertices());
        })).get();
        double sec = (System.nanoTime() - t0) / 1e9;
        System.out.printf("built in %.2f s on %d threads, %d vertices (%.1f MB GPU)%n", sec, threads, verts.get(), verts.get() * 16 / 1e6);
        render(meshes, cx, cy, cz, yaw, dist, clip, new File(a[9]));
    }

    static void render(TileMesh[] meshes, double cx, double cy, double cz, double yaw, double dist, double clip, File out) throws Exception {
        int W = 1600, H = 900;
        double focal = H / 2.0 / Math.tan(Math.toRadians(35)), pitch = Math.toRadians(8);
        int[] img = new int[W * H];
        float[] zb = new float[W * H];
        java.util.Arrays.fill(zb, Float.POSITIVE_INFINITY);
        int fog = 0xB4C8E6;
        for (int y = 0; y < H; y++) {
            double k = Math.min(1, y / (H * 0.5));
            int sky = rgb(110 + 70 * k, 160 + 40 * k, 225 + 10 * k);
            for (int x = 0; x < W; x++) img[y * W + x] = sky;
        }
        double sy = Math.sin(yaw), cyw = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        double[][] v = new double[4][4];
        int[] vc = new int[4];
        for (TileMesh m : meshes) {
            for (int q = 0; q < m.quads; q++) {
                boolean cut = false;
                for (int k = 0; k < 4; k++) {
                    int id = q * 4 + k;
                    double wx = m.x0 + m.pos[id * 3] - cx, wy = m.pos[id * 3 + 1] - cy, wz = m.z0 + m.pos[id * 3 + 2] - cz;
                    // камера смотрит вдоль (sin yaw, -cos yaw), наклон вниз на pitch
                    double rx = wx * cyw + wz * sy, rz = wx * sy - wz * cyw;
                    double ry = wy * cp + rz * sp, fz = rz * cp - wy * sp;
                    if (fz < 4) cut = true;
                    v[k][0] = W / 2.0 + rx / fz * focal; v[k][1] = H / 2.0 - ry / fz * focal; v[k][2] = fz;
                    v[k][3] = Math.sqrt(wx * wx + wz * wz);
                    vc[k] = m.argb[id];
                }
                if (cut) continue;
                tri(img, zb, W, H, v[0], v[1], v[2], vc[0], vc[1], vc[2], dist, clip, fog);
                tri(img, zb, W, H, v[0], v[2], v[3], vc[0], vc[2], vc[3], dist, clip, fog);
            }
        }
        BufferedImage bi = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        bi.setRGB(0, 0, W, H, img, 0, W);
        ImageIO.write(bi, "png", out);
    }

    static void tri(int[] img, float[] zb, int W, int H, double[] a, double[] b, double[] c, int ca, int cb, int cc, double dist, double clip, int fog) {
        int x0 = (int) Math.max(0, Math.floor(Math.min(a[0], Math.min(b[0], c[0])))), x1 = (int) Math.min(W - 1, Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        int y0 = (int) Math.max(0, Math.floor(Math.min(a[1], Math.min(b[1], c[1])))), y1 = (int) Math.min(H - 1, Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        double area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
        if (Math.abs(area) < 1e-9 || x0 > x1 || y0 > y1) return;
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                double px = x + 0.5, py = y + 0.5;
                double w0 = ((b[0] - px) * (c[1] - py) - (b[1] - py) * (c[0] - px)) / area;
                double w1 = ((c[0] - px) * (a[1] - py) - (c[1] - py) * (a[0] - px)) / area;
                double w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                // перспективно-корректная интерполяция через 1/z
                double iz = w0 / a[2] + w1 / b[2] + w2 / c[2];
                float z = (float) (1 / iz);
                int id = y * W + x;
                if (z >= zb[id]) continue;
                double p0 = w0 / a[2] / iz, p1 = w1 / b[2] / iz, p2 = w2 / c[2] / iz;
                double d = p0 * a[3] + p1 * b[3] + p2 * c[3];
                if (d < clip) continue;
                zb[id] = z;
                double f = smooth(dist * 0.55, dist, d);
                double r = ch(ca, 16) * p0 + ch(cb, 16) * p1 + ch(cc, 16) * p2;
                double g = ch(ca, 8) * p0 + ch(cb, 8) * p1 + ch(cc, 8) * p2;
                double bl = ch(ca, 0) * p0 + ch(cb, 0) * p1 + ch(cc, 0) * p2;
                img[id] = rgb(r + (ch(fog, 16) - r) * f, g + (ch(fog, 8) - g) * f, bl + (ch(fog, 0) - bl) * f);
            }
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static int ch(int c, int s) { return (c >> s) & 255; }

    static int rgb(double r, double g, double b) {
        return ((int) Math.max(0, Math.min(255, r)) << 16) | ((int) Math.max(0, Math.min(255, g)) << 8) | (int) Math.max(0, Math.min(255, b));
    }
}
