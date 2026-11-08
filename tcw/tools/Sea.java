import dev.cld.tcw.gen.Options;
import dev.cld.tcw.gen.Terrain;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.stream.IntStream;

// Мир End Sea вне игры: java Sea <seed> <cx> <cz> <размер> <блоков на пиксель> out.png
// слева вид сверху (пустота чёрная), справа разрез по z = cz
public class Sea {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]);
        int cx = Integer.parseInt(a[1]), cz = Integer.parseInt(a[2]), size = Integer.parseInt(a[3]);
        double k = Double.parseDouble(a[4]);
        int n = (int) (size / k);
        Terrain t = new Terrain(seed, Options.DEFAULT.withWorld(1));
        BufferedImage img = new BufferedImage(n * 2, n, BufferedImage.TYPE_INT_RGB);
        int[] land = new int[1];
        IntStream.range(0, n).parallel().forEach(j -> {
            Terrain.Column c = new Terrain.Column();
            for (int i = 0; i < n; i++) {
                double x = cx - size / 2.0 + i * k, z = cz - size / 2.0 + j * k;
                t.column(x, z, c);
                double e = t.arch.edge(x, z, c.height);
                int rgb = 0x0c0a14;
                if (e > 0) {
                    int g = (int) Math.max(40, Math.min(255, 60 + (c.height - 63) * 0.8));
                    rgb = c.height > 300 ? (g << 16 | g << 8 | g) : ((g / 3) << 16 | g << 8 | g / 3);
                    if (!Double.isNaN(c.water) && c.water > c.height) rgb = 0x3060c0;
                    synchronized (land) { land[0]++; }
                }
                img.setRGB(i, j, rgb);
            }
        });
        // разрез: по вертикали от -128 до 640
        Terrain.Column c = new Terrain.Column();
        for (int i = 0; i < n; i++) {
            double x = cx - size / 2.0 + i * k;
            t.column(x, cz, c);
            double e = t.arch.edge(x, cz, c.height);
            double b = e > 0 ? t.arch.bottom(x, cz, c.height, e) : 1e9;
            for (int j = 0; j < n; j++) {
                double y = 640 - j * 768.0 / n;
                int rgb = 0x9a8fc0;
                if (y <= c.height && y >= b) rgb = y > c.height - 4 ? 0x3fae3f : 0x707070;
                img.setRGB(n + i, j, rgb);
            }
        }
        ImageIO.write(img, "png", new File(a[5]));
        System.out.printf("land %.1f%%%n", 100.0 * land[0] / n / n);
    }
}
