package dev.cld.menu.scene;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.Properties;

// Настройки меню в config/cld_menu.properties, файл создаётся сам при первом запуске
public final class MenuConfig {
    public String weather = "auto";
    public String season = "auto";
    public String time = "auto";
    public int fps = 60;
    public float sounds = 0.7f;
    public float musicInStorm = 0.45f;

    public static MenuConfig load(Path file) {
        MenuConfig c = new MenuConfig();
        if (Files.isRegularFile(file)) {
            Properties p = new Properties();
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(in);
                c.weather = p.getProperty("weather", c.weather).trim();
                c.season = p.getProperty("season", c.season).trim();
                c.time = p.getProperty("time", c.time).trim();
                c.sounds = Math.max(0, Math.min(1, Float.parseFloat(p.getProperty("sounds", "" + c.sounds).trim())));
                c.musicInStorm = Math.max(0, Math.min(1, Float.parseFloat(p.getProperty("musicInStorm", "" + c.musicInStorm).trim())));
                c.fps = Math.max(10, Math.min(240, Integer.parseInt(p.getProperty("fps", "" + c.fps).trim())));
            } catch (IOException | RuntimeException e) {
                System.err.println("[cld_menu] config: " + e);
            }
        }
        c.save(file);
        return c;
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                out.write("# Главное меню сборки (cld_menu)\n");
                out.write("# погода: auto (случайная при каждом запуске), clear, rain, thunder, snow, fog, blizzard, rainbow\n");
                out.write("weather=" + weather + "\n");
                out.write("# время года: auto (по календарю), winter, spring, summer, autumn\n");
                out.write("season=" + season + "\n");
                out.write("# время суток: auto (по часам компьютера) или например 21:30\n");
                out.write("time=" + time + "\n");
                out.write("# сколько раз в секунду перерисовывать пейзаж\n");
                out.write("fps=" + fps + "\n");
                out.write("# громкость звуков меню от 0 до 1 (тот же ползунок есть в настройках звука)\n");
                out.write("sounds=" + sounds + "\n");
                out.write("# громкость музыки в меню в грозу, дождь и метель (доля от обычной)\n");
                out.write("musicInStorm=" + musicInStorm + "\n");
            }
        } catch (IOException e) {
            System.err.println("[cld_menu] config save: " + e);
        }
    }

    public WeatherKind fixedWeather() {
        return WeatherKind.parse(weather);
    }

    public Season fixedSeason(Season fallback) {
        return Season.parse(season, fallback);
    }

    public LocalTime fixedTime() {
        try {
            return "auto".equalsIgnoreCase(time) ? null : LocalTime.parse(time.length() == 4 ? "0" + time : time);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
