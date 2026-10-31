import dev.cld.menu.scene.Ambient;
import dev.cld.menu.scene.Season;
import dev.cld.menu.scene.Sky;
import dev.cld.menu.scene.Weather;
import dev.cld.menu.scene.WeatherKind;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.LocalDateTime;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

// Звуки меню без игры: пишет WAV на 20 секунд.
// java -cp out AmbientWav out.wav погода сезон ЧЧ:ММ
public class AmbientWav {
    public static void main(String[] a) throws Exception {
        WeatherKind k = WeatherKind.parse(a[1]);
        Season se = Season.parse(a[2], Season.SUMMER);
        String[] hm = a[3].split(":");
        Sky sky = new Sky(LocalDateTime.of(2026, se == Season.WINTER ? 1 : se == Season.SUMMER ? 7 : 10, 15,
                Integer.parseInt(hm[0]), Integer.parseInt(hm[1])), se);
        Weather wx = new Weather(k, 3, 0);
        Ambient amb = new Ambient(5);
        int secs = 20;
        float[] buf = new float[Ambient.RATE / 10];
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        double peak = 0, sum = 0;
        long count = 0;
        for (int i = 0; i < secs * 10; i++) {
            wx.update(i / 10.0);
            amb.target(wx, sky);
            amb.fill(buf, buf.length);
            for (float v : buf) {
                peak = Math.max(peak, Math.abs(v));
                sum += v * v;
                count++;
                int s = Math.round(v * 32767);
                bytes.write(s & 255);
                bytes.write(s >> 8 & 255);
            }
        }
        byte[] b = bytes.toByteArray();
        AudioFormat f = new AudioFormat(Ambient.RATE, 16, 1, true, false);
        AudioSystem.write(new AudioInputStream(new java.io.ByteArrayInputStream(b), f, b.length / 2), AudioFileFormat.Type.WAVE, new File(a[0]));
        System.out.printf("%s: пик %.2f, средняя громкость %.3f%n", a[0], peak, Math.sqrt(sum / count));
    }
}
