import dev.cld.menu.scene.Raster;
import dev.cld.menu.scene.Scene;
import dev.cld.menu.scene.Season;
import dev.cld.menu.scene.Sky;
import dev.cld.menu.scene.Tex;
import dev.cld.menu.scene.Weather;
import dev.cld.menu.scene.WeatherKind;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

// Превью пейзажа меню без игры.
// java -cp out MenuPreview out.png [ширина высота погода сезон ЧЧ:ММ секунда]
// текстуры блоков берутся из client-*-extra.jar, путь в -Dmc.jar=...
// погода grid рисует сетку всех видов погоды; можно перечислить через запятую, например thunder,snow
public class MenuPreview {
    public static void main(String[] a) throws Exception {
        String out = a.length > 0 ? a[0] : "menu.png";
        int w = a.length > 2 ? Integer.parseInt(a[1]) : 480, h = a.length > 2 ? Integer.parseInt(a[2]) : 270;
        String kinds = a.length > 3 ? a[3] : "grid";
        Season season = a.length > 4 && !a[4].equals("auto") ? Season.parse(a[4], Season.SUMMER) : Season.of(LocalDate.now());
        LocalTime time = a.length > 5 ? LocalTime.parse(a[5]) : LocalTime.of(14, 0);
        double t = a.length > 6 ? Double.parseDouble(a[6]) : 30;
        WeatherKind[] list;
        if (kinds.equals("grid")) list = WeatherKind.values();
        else {
            String[] p = kinds.split(",");
            list = new WeatherKind[p.length];
            for (int i = 0; i < p.length; i++) list[i] = WeatherKind.parse(p[i]);
        }
        int cols = Math.min(list.length, 4), rows = (list.length + cols - 1) / cols;
        BufferedImage img = new BufferedImage(w * cols, h * rows, BufferedImage.TYPE_INT_RGB);
        LocalDateTime now = LocalDateTime.of(LocalDate.of(2026, monthOf(season), 15), time);
        long total = 0;
        for (int i = 0; i < list.length; i++) {
            Raster r = new Raster(w, h);
            Scene s = new Scene();
            if (JAR != null) s.textures(MenuPreview::texture);
            Weather wx = new Weather(list[i], 7, 0);
            // догоняем время, чтобы молнии и радуга успели появиться
            double tt = list[i] == WeatherKind.THUNDER ? findStrike(wx, t) : t;
            if (list[i] != WeatherKind.THUNDER) wx.update(tt);
            Sky sky = new Sky(now, season);
            long t0 = System.nanoTime();
            for (int k = 0; k < 20; k++) s.render(r, sky, wx, tt);
            total += (System.nanoTime() - t0) / 20;
            img.setRGB((i % cols) * w, (i / cols) * h, w, h, r.px, 0, w);
        }
        ImageIO.write(img, "png", new File(out));
        System.out.printf("кадр %dx%d: %.2f мс%n", w, h, total / 1e6 / list.length);
    }

    private static final ZipFile JAR = open();

    private static ZipFile open() {
        try {
            String p = System.getProperty("mc.jar");
            return p == null || p.isEmpty() ? null : new ZipFile(p);
        } catch (Exception e) {
            return null;
        }
    }

    // minecraft:textures/block/dirt.png из jar
    static Tex texture(String id) {
        try {
            String path = "assets/" + id.replace(":", "/");
            var e = JAR.getEntry(path);
            if (e == null) return null;
            BufferedImage im = ImageIO.read(JAR.getInputStream(e));
            int[] px = im.getRGB(0, 0, im.getWidth(), im.getHeight(), null, 0, im.getWidth());
            return new Tex(im.getWidth(), im.getHeight(), px);
        } catch (Exception ex) {
            return null;
        }
    }

    private static double findStrike(Weather wx, double from) {
        for (double t = 0; t < from + 60; t += 0.02) {
            wx.update(t);
            if (t >= from && wx.flash(t) > 0.6f) return t;
        }
        return from;
    }

    private static int monthOf(Season s) {
        return switch (s) {
            case WINTER -> 1;
            case SPRING -> 4;
            case SUMMER -> 7;
            case AUTUMN -> 10;
        };
    }
}
