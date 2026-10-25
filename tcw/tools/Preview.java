import dev.cld.tcw.gen.Macro;
import dev.cld.tcw.gen.Terrain;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.stream.IntStream;

/** Превью рельефа: карта сверху с отмывкой рельефа. java Preview <seed> <cx> <cz> <размер блоков> <блоков на пиксель> <out.png> */
public class Preview {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]);
        double cx = Double.parseDouble(a[1]), cz = Double.parseDouble(a[2]), size = Double.parseDouble(a[3]), k = Double.parseDouble(a[4]);
        int n = (int) (size / k);
        Terrain t = new Terrain(seed);
        double x0 = cx - size / 2, z0 = cz - size / 2;
        float[] h = new float[n * n], wl = new float[n * n], tmp = new float[n * n], hum = new float[n * n], mnt = new float[n * n];
        byte[] wk = new byte[n * n], st = new byte[n * n];
        long t0 = System.currentTimeMillis();
        IntStream.range(0, n).parallel().forEach(j -> {
            Terrain.Column c = new Terrain.Column();
            for (int i = 0; i < n; i++) {
                int id = j * n + i;
                t.column(x0 + i * k, z0 + j * k, c);
                h[id] = (float) c.height;
                wl[id] = (float) c.water;
                wk[id] = (byte) c.waterKind;
                tmp[id] = (float) c.temp;
                hum[id] = (float) c.humid;
                mnt[id] = (float) c.mountain;
                st[id] = (byte) c.dominant().ordinal();
            }
        });
        System.err.println("columns " + (System.currentTimeMillis() - t0) + " ms");
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        int[] colors = new int[n * n];
        float hmax = -999;
        for (int id = 0; id < n * n; id++) hmax = Math.max(hmax, h[id]);
        System.err.println("max height " + hmax);
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int id = j * n + i;
                double hx = (h[j * n + Math.min(n - 1, i + 1)] - h[j * n + Math.max(0, i - 1)]) / (2 * k);
                double hz = (h[Math.min(n - 1, j + 1) * n + i] - h[Math.max(0, j - 1) * n + i]) / (2 * k);
                double slope = Math.sqrt(hx * hx + hz * hz);
                double nxv = -hx, nyv = 1, nzv = -hz, nl = Math.sqrt(nxv * nxv + 1 + nzv * nzv);
                double lx = -0.6, ly = 0.6, lz = -0.5, ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
                double shade = Math.max(0, (nxv * lx + nyv * ly + nzv * lz) / (nl * ll));
                double[] col = color(t, x0 + i * k, z0 + j * k, h[id], wl[id], wk[id], tmp[id], hum[id], mnt[id], st[id], slope);
                double sh = wk[id] != 0 ? 1 : 0.35 + 0.85 * shade;
                colors[id] = rgb(col[0] * sh, col[1] * sh, col[2] * sh);
                img.setRGB(i, j, colors[id]);
            }
        ImageIO.write(img, "png", new File(a[5]));
        if (a.length > 6) {
            float[] top = new float[n * n];
            for (int id = 0; id < n * n; id++) top[id] = wk[id] != 0 ? Math.max(h[id], wl[id]) : h[id];
            double yaw = a.length > 7 ? Math.toRadians(Double.parseDouble(a[7])) : 0;
            ImageIO.write(oblique(top, colors, n, k, yaw), "png", new File(a[6]));
        }
    }

    /** Вид сбоку с высоты птичьего полёта (алгоритм «voxel space»), 1 блок по высоте = 1 блок по горизонтали. */
    static BufferedImage oblique(float[] h, int[] col, int n, double k, double yaw) {
        int W = 1600, H = 900;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        double size = n * k;
        double camX = size / 2 - Math.sin(yaw) * size * 0.62, camZ = size / 2 + Math.cos(yaw) * size * 0.62;
        double gh = sampleH(h, n, k, camX, camZ);
        double camY = Math.max(gh, 63) + size * 0.09;
        double focal = W / 2.0 / Math.tan(Math.toRadians(32)), horizon = H * 0.30;
        for (int y = 0; y < H; y++) {
            double t = Math.min(1, (double) y / (horizon + 40));
            int sky = rgb(120 + 60 * t, 165 + 45 * t, 225 + 20 * t);
            for (int x = 0; x < W; x++) img.setRGB(x, y, sky);
        }
        for (int sx = 0; sx < W; sx++) {
            double ang = yaw + Math.atan((sx - W / 2.0) / focal);
            double dx = Math.sin(ang), dz = -Math.cos(ang), cosc = Math.cos(ang - yaw);
            int ybuf = H;
            for (double d = 10; d < size * 1.3; d += Math.max(k * 0.5, d * 0.004)) {
                double px = camX + dx * d, pz = camZ + dz * d;
                if (px < 0 || pz < 0 || px >= size - k || pz >= size - k) continue;
                double hh = sampleH(h, n, k, px, pz);
                int sy = (int) (horizon + (camY - hh) / (d * cosc) * focal);
                if (sy < ybuf) {
                    int c = col[(int) (pz / k) * n + (int) (px / k)];
                    double fog = Math.min(0.75, Math.pow(d / (size * 1.3), 1.4));
                    int r = (int) (((c >> 16) & 255) * (1 - fog) + 175 * fog), g = (int) (((c >> 8) & 255) * (1 - fog) + 200 * fog), b = (int) ((c & 255) * (1 - fog) + 235 * fog);
                    int cc = rgb(r, g, b);
                    for (int y = Math.max(0, sy); y < ybuf; y++) img.setRGB(sx, y, cc);
                    ybuf = Math.max(0, sy);
                    if (ybuf == 0) break;
                }
            }
        }
        return img;
    }

    static double sampleH(float[] h, int n, double k, double x, double z) {
        double fx = x / k, fz = z / k;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        i = Math.max(0, Math.min(n - 2, i));
        j = Math.max(0, Math.min(n - 2, j));
        double tx = fx - i, tz = fz - j;
        double a = h[j * n + i] * (1 - tx) + h[j * n + i + 1] * tx, b = h[(j + 1) * n + i] * (1 - tx) + h[(j + 1) * n + i + 1] * tx;
        return a * (1 - tz) + b * tz;
    }

    static int rgb(double r, double g, double b) {
        return ((int) Macro.clamp(r, 0, 255) << 16) | ((int) Macro.clamp(g, 0, 255) << 8) | (int) Macro.clamp(b, 0, 255);
    }

    static double[] mix(double[] a, double[] b, double t) {
        return new double[] { a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t };
    }

    static double[] color(Terrain t, double x, double z, double h, double wl, int wk, double temp, double hum, double mnt, int st, double slope) {
        if (wk != 0) {
            double depth = wl - h;
            if (wk == Terrain.WATER_OCEAN) {
                if (temp < -0.55 && depth < 30) return new double[] { 200, 220, 235 };
                return mix(new double[] { 70, 140, 190 }, new double[] { 15, 35, 90 }, Macro.ss(0, 90, depth));
            }
            if (temp < -0.5 && wk == Terrain.WATER_LAKE) return new double[] { 190, 215, 235 };
            return new double[] { 50, 110, 200 };
        }
        double snow = t.snowLine(x, z, temp);
        Macro.Style s = Macro.STYLES[st];
        double[] rock = switch (s) {
            case DOLOMITE -> new double[] { 205, 200, 185 };
            case GRANITE -> new double[] { 175, 175, 170 };
            case VOLCANIC -> new double[] { 70, 65, 65 };
            case MESA -> ((int) (h / 6)) % 3 == 0 ? new double[] { 205, 120, 70 } : ((int) (h / 6)) % 3 == 1 ? new double[] { 225, 170, 110 } : new double[] { 170, 85, 55 };
            case KARST -> new double[] { 150, 150, 140 };
            default -> new double[] { 125, 122, 118 };
        };
        if (h > snow && slope < 1.1) {
            if (slope < 0.3 && temp < -0.15 && mnt > 0.25 && h > snow + 30) return new double[] { 205, 235, 250 };
            return new double[] { 245, 248, 252 };
        }
        if (slope > 1.0) return rock;
        if (h < Macro.SEA + 3 && temp > -0.5) return new double[] { 220, 205, 150 };
        double[] veg;
        if (temp > 0.35 && hum < -0.05) veg = new double[] { 220, 195, 130 };
        else if (temp > 0.35) veg = new double[] { 50, 120, 40 };
        else if (temp < -0.45) veg = new double[] { 150, 160, 140 };
        else if (temp < -0.1) veg = new double[] { 50, 90, 60 };
        else veg = hum > 0 ? new double[] { 60, 125, 50 } : new double[] { 110, 150, 70 };
        double tree = snow - 140;
        if (h > tree) veg = mix(veg, new double[] { 140, 170, 90 }, Macro.ss(tree, tree + 40, h));
        if (h > snow - 50) veg = mix(veg, rock, Macro.ss(snow - 50, snow - 10, h));
        return mix(veg, rock, Macro.ss(0.6, 1.0, slope));
    }
}
