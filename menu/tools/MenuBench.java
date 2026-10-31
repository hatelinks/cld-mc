import dev.cld.menu.scene.*;

import java.time.LocalDateTime;

// Замер пейзажа меню: сколько миллисекунд уходит на кадр без игры.
// java -Dmc.jar=client-extra.jar -cp классы MenuBench [ширина] [высота] [кадров]
public class MenuBench {
    public static void main(String[] a) {
        int w = a.length > 0 ? Integer.parseInt(a[0]) : 480, h = a.length > 1 ? Integer.parseInt(a[1]) : 270;
        int n = a.length > 2 ? Integer.parseInt(a[2]) : 600;
        Scene s = new Scene();
        s.textures(MenuPreview::texture);
        Raster r = new Raster(w, h);
        String[][] cases = {{"CLEAR", "SUMMER", "13:00"}, {"RAIN", "AUTUMN", "15:00"}, {"THUNDER", "SUMMER", "21:00"},
                {"SNOW", "WINTER", "12:00"}, {"BLIZZARD", "WINTER", "17:00"}, {"FOG", "AUTUMN", "08:00"}, {"RAINBOW", "SPRING", "18:00"}};
        double sum = 0;
        for (String[] c : cases) {
            WeatherKind k = WeatherKind.valueOf(c[0]);
            Season se = Season.valueOf(c[1]);
            int month = switch (se) { case WINTER -> 1; case SPRING -> 4; case SUMMER -> 7; case AUTUMN -> 10; };
            Sky sky = new Sky(LocalDateTime.of(2026, month, 15, Integer.parseInt(c[2].substring(0, 2)), 0), se);
            Weather wx = new Weather(k, 7, 0);
            double t = 30;
            // разогрев JIT, потом замер; время идёт как при 60 кадрах в секунду
            for (int i = 0; i < 300; i++, t += 1 / 60.0) { wx.update(t); s.render(r, sky, wx, t); }
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++, t += 1 / 60.0) { wx.update(t); s.render(r, sky, wx, t); }
            double ms = (System.nanoTime() - t0) / 1e6 / n;
            sum += ms;
            System.out.printf("%-9s %-7s %6.2f мс%n", c[0], c[1], ms);
        }
        System.out.printf("среднее   %6.2f мс на кадр, при 60 кадрах %4.0f%% одного ядра%n", sum / cases.length, sum / cases.length * 60 / 10);
    }
}
