import dev.cld.tcw.gen.Islands;
import dev.cld.tcw.gen.Terrain;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

// Небесные острова вне игры: java Sky <seed> <клеток> <out.png>
// печатает острова в квадрате клеток вокруг 0,0 и рисует ближайшую группу: сверху и срез через центр
public class Sky {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]);
        int n = Integer.parseInt(a[1]);
        Terrain t = new Terrain(seed);
        int groups = 0, total = 0;
        Islands.Island first = null;
        long t0 = System.currentTimeMillis();
        for (int i = -n; i < n; i++)
            for (int j = -n; j < n; j++) {
                Islands.Island[] c = t.islands.cell(i, j);
                if (c.length == 0) continue;
                groups++;
                total += c.length;
                Islands.Island m = c[0];
                if (first == null || Math.hypot(m.x, m.z) < Math.hypot(first.x, first.z)) first = m;
                System.out.printf("%6d %6d r=%2d top=%3d n=%d %s%n", m.x, m.z, m.r, m.top, c.length, Islands.BIOMES[m.biome]);
            }
        System.out.printf("групп %d из %d клеток, островов %d, %d мс%n", groups, 4 * n * n, total, System.currentTimeMillis() - t0);
        if (first == null) return;
        int S = 320, H = 200;
        BufferedImage img = new BufferedImage(S * 2, S, BufferedImage.TYPE_INT_RGB);
        Islands.Island[] all = t.islands.near(first.x, first.z);
        int[] col = new int[2];
        Terrain.Column tc = new Terrain.Column();
        for (int i = 0; i < S; i++)
            for (int j = 0; j < S; j++) {
                int x = first.x - S / 2 + i, z = first.z - S / 2 + j;
                int best = Integer.MIN_VALUE;
                for (Islands.Island is : all) if (t.islands.column(is, x, z, col)) best = Math.max(best, col[1]);
                int rgb;
                if (best != Integer.MIN_VALUE) { int g = Math.max(0, Math.min(255, 80 + (best - first.top) * 8)); rgb = (g / 3) << 16 | g << 8 | g / 4; }
                else { t.column(x, z, tc); int g = (int) Math.max(0, Math.min(255, tc.height)); rgb = g / 2 << 16 | g / 2 << 8 | g / 2; if (!Double.isNaN(tc.water) && tc.water > tc.height) rgb = 0x2050a0; }
                img.setRGB(i, j, rgb);
            }
        // срез по x через центр: y от top-H+40 до top+40
        int yTop = first.top + 40;
        for (int i = 0; i < S; i++) {
            int x = first.x - S / 2 + i, z = first.z;
            t.column(x, z, tc);
            for (int j = 0; j < S; j++) {
                int y = yTop - j * (H + 60) / S;
                int rgb = 0x9ec6ff;
                if (y <= tc.height) rgb = 0x707070;
                else if (!Double.isNaN(tc.water) && y <= tc.water) rgb = 0x2050a0;
                for (Islands.Island is : all)
                    if (t.islands.column(is, x, z, col) && y >= col[0] && y <= col[1]) rgb = y == col[1] ? 0x3fae3f : y > col[1] - 4 ? 0x7a5a3a : 0x888888;
                img.setRGB(S + i, j, rgb);
            }
        }
        ImageIO.write(img, "png", new File(a[2]));
        System.out.println("нарисовано " + first.x + " " + first.z);
    }
}
