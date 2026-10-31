package dev.cld.menu;

import com.mojang.blaze3d.platform.NativeImage;
import dev.cld.menu.scene.MenuConfig;
import dev.cld.menu.scene.Raster;
import dev.cld.menu.scene.Scene;
import dev.cld.menu.scene.Season;
import dev.cld.menu.scene.Sky;
import dev.cld.menu.scene.Tex;
import dev.cld.menu.scene.Weather;
import dev.cld.menu.scene.WeatherKind;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

// Пейзаж меню: сцена рисуется в память и одной текстурой выводится на экран.
// Погода выбирается при запуске игры, время суток и время года берутся с часов компьютера.
public final class Landscape {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("cld_menu", "landscape");
    private static final long START = Util.getMillis();
    private static final Random RND = new Random();

    private static MenuConfig config;
    private static final Scene scene = new Scene();
    private static Weather weather;
    private static Season season;
    private static int hourShift;
    // пейзаж считается в отдельном потоке, поток игры только загружает готовый кадр в текстуру
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread th = new Thread(r, "cld_menu landscape");
        th.setDaemon(true);
        th.setPriority(Thread.NORM_PRIORITY - 1);
        return th;
    });
    private static Raster front, back;
    private static Future<?> job;
    private static boolean firstShown;
    private static float parallax;
    private static DynamicTexture texture;
    private static long lastFrame;
    private static boolean texturesReady;
    private static long lastShown;
    private static Sky sky;
    private static String note;
    private static long noteUntil;

    private Landscape() {}

    static MenuConfig config() {
        if (config == null) config = MenuConfig.load(FMLPaths.CONFIGDIR.get().resolve("cld_menu.properties"));
        return config;
    }

    private static double now() {
        return (Util.getMillis() - START) / 1000.0;
    }

    private static Season season() {
        if (season == null) season = config().fixedSeason(Season.of(LocalDate.now()));
        return season;
    }

    private static Weather weather() {
        if (weather == null) {
            WeatherKind k = config().fixedWeather();
            if (k == null) k = Weather.pick(season(), RND);
            weather = new Weather(k, RND.nextLong(), now());
            weather.listener = MenuSounds::strike;
        }
        return weather;
    }

    private static LocalDateTime clock() {
        LocalDateTime t = LocalDateTime.now();
        LocalTime fixed = config().fixedTime();
        if (fixed != null) t = t.with(fixed);
        return t.plusHours(hourShift);
    }

    public static void render(GuiGraphics g, int w, int h, float fade) {
        Minecraft mc = Minecraft.getInstance();
        int winW = Math.max(1, mc.getWindow().getWidth()), winH = Math.max(1, mc.getWindow().getHeight());
        // один пиксель пейзажа примерно 1/270 высоты окна
        int px = Math.max(1, Math.round(winH / 270f));
        int rw = (winW + px - 1) / px, rh = (winH + px - 1) / px;
        boolean idle = job == null || job.isDone();
        if (idle && (front == null || front.w != rw || front.h != rh)) resize(rw, rh);
        if (idle && !texturesReady) {
            texturesReady = true;
            scene.textures(Landscape::texture);
        }
        long ms = Util.getMillis();
        lastShown = ms;
        if (idle && job != null) {
            // кадр из фонового потока готов: показываем его, следующий рисуем в другой буфер
            job = null;
            Raster done = back;
            back = front;
            front = done;
            upload(front);
        }
        // свёрнутое или неактивное окно: пейзаж почти стоит, процессор свободен
        int fps = mc.isWindowActive() ? config().fps : 5;
        if (job == null && front != null && ms - lastFrame >= 1000L / fps) {
            lastFrame = ms;
            double t = now();
            Weather wx = weather();
            sky = new Sky(clock(), season());
            Sky sk = sky;
            float mouse = (float) (mc.mouseHandler.xpos() / winW * 2 - 1);
            parallax += (Math.max(-1, Math.min(1, mouse)) - parallax) * 0.08f;
            scene.parallax = parallax;
            Raster target = back;
            Runnable draw = () -> {
                wx.update(t);
                scene.render(target, sk, wx, t);
            };
            if (!firstShown) {
                // самый первый кадр рисуем сразу, чтобы не было пустого экрана
                draw.run();
                back = front;
                front = target;
                upload(front);
                firstShown = true;
            } else {
                job = WORKER.submit(draw);
            }
        }
        if (front != null) g.blit(ID, 0, 0, w, h, 0f, 0f, front.w, front.h, front.w, front.h);
        if (note != null && ms < noteUntil) {
            int a = (int) Math.min(255, (noteUntil - ms) / 4);
            if (a > 8) g.drawString(mc.font, note, 4, h - 10, (a << 24) | 0xFFFFFF, true);
        }
        if (fade < 1f) g.fill(0, 0, w, h, ((int) ((1 - fade) * 255) << 24));
    }

    private static void resize(int rw, int rh) {
        var tm = Minecraft.getInstance().getTextureManager();
        if (texture != null) tm.release(ID);
        front = new Raster(rw, rh);
        back = new Raster(rw, rh);
        texture = new DynamicTexture(rw, rh, false);
        tm.register(ID, texture);
        lastFrame = 0;
        firstShown = false;
    }

    private static void upload(Raster r) {
        NativeImage img = texture.getPixels();
        if (img == null) return;
        int[] p = r.px;
        for (int y = 0; y < r.h; y++) {
            int o = y * r.w;
            for (int x = 0; x < r.w; x++) {
                int c = p[o + x];
                // в NativeImage порядок ABGR
                img.setPixelRGBA(x, y, 0xFF000000 | (c & 0xFF) << 16 | (c & 0xFF00) | (c >> 16 & 0xFF));
            }
        }
        texture.upload();
    }

    // настройки поменяли на экране Наши моды: сохранить и, если надо, заново выбрать погоду
    static void applyConfig(boolean resetScene) {
        config().save(FMLPaths.CONFIGDIR.get().resolve("cld_menu.properties"));
        if (resetScene) {
            weather = null;
            season = null;
            hourShift = 0;
        }
        lastFrame = 0;
    }

    static boolean shownRecently() {
        return Util.getMillis() - lastShown < 500;
    }

    static Weather currentWeather() {
        return weather;
    }

    static Sky currentSky() {
        return sky;
    }

    // после смены ресурс-паков текстуры блоков перечитываются
    static void texturesChanged() {
        texturesReady = false;
    }

    // текстура из ресурс-паков игры, NativeImage хранит ABGR
    private static Tex texture(String id) {
        var res = Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.parse(id));
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            int w = img.getWidth(), h = img.getHeight();
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = img.getPixelRGBA(x, y);
                    px[y * w + x] = (c & 0xFF00FF00) | (c & 0xFF) << 16 | (c >> 16 & 0xFF);
                }
            }
            return new Tex(w, h, px);
        } catch (Exception e) {
            return null;
        }
    }

    // F9 следующая погода, Shift+F9 следующее время года, Ctrl+F9 плюс два часа
    static void cycle(boolean shift, boolean ctrl) {
        if (ctrl) {
            hourShift = (hourShift + 2) % 24;
            show("Время: " + String.format("%02d:%02d", clock().getHour(), clock().getMinute()));
        } else if (shift) {
            Season[] v = Season.values();
            season = v[(season().ordinal() + 1) % v.length];
            show("Время года: " + season.title);
        } else {
            WeatherKind[] v = WeatherKind.values();
            weather = new Weather(v[(weather().kind.ordinal() + 1) % v.length], RND.nextLong(), now());
            weather.listener = MenuSounds::strike;
            show("Погода: " + weather.kind.title);
        }
        lastFrame = 0;
    }

    private static void show(String s) {
        note = s;
        noteUntil = Util.getMillis() + 2500;
    }
}
