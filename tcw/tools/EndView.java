import dev.cld.tcw.gen.EndLands;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

// Край tcw вне игры: java EndView <seed> <cx> <cz> <размер> <блоков на пиксель> out.png
// слева сверху (цвет по высоте, хорусовые леса фиолетовым), справа разрез по z = cz, высота 0..256
public class EndView {
    public static void main(String[] a) throws Exception {
        EndLands e = new EndLands(Long.parseLong(a[0]));
        int cx = Integer.parseInt(a[1]), cz = Integer.parseInt(a[2]), size = Integer.parseInt(a[3]);
        double k = Double.parseDouble(a[4]);
        int n = (int) (size / k), land = 0;
        BufferedImage img = new BufferedImage(n * 2, n, BufferedImage.TYPE_INT_RGB);
        EndLands.Col c = new EndLands.Col();
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double x = cx - size / 2.0 + i * k, z = cz - size / 2.0 + j * k;
                e.column(x, z, c);
                int rgb = 0x0a0812;
                if (c.island()) {
                    land++;
                    int g = (int) Math.max(30, Math.min(255, c.top));
                    rgb = e.erosion(x, z, c) > 0.25 ? (g / 2 << 16 | g / 4 << 8 | g / 2) : (g << 16 | g << 8 | (int) (g * 0.7));
                }
                img.setRGB(i, j, rgb);
            }
        for (int i = 0; i < n; i++) {
            double x = cx - size / 2.0 + i * k;
            e.column(x, cz, c);
            for (int j = 0; j < n; j++) {
                double y = 256 - j * 256.0 / n;
                int rgb = 0x1a1626;
                if (c.island() && y <= c.top && y >= c.bottom) rgb = y < c.bottom + e.darkBelt(x, cz, c) ? 0x2a2233 : 0xdedb9c;
                img.setRGB(n + i, j, rgb);
            }
        }
        ImageIO.write(img, "png", new File(a[5]));
        System.out.printf("land %.1f%%%n", 100.0 * land / n / n);
    }
}
