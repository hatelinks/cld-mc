package dev.cld.menu.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Погода меню: вид выбирается при запуске, силы дождя, ветра и тумана немного гуляют со временем
public final class Weather {
    public final WeatherKind kind;
    public float cloud, dark, rain, snow, wind, fog, rainbow;
    public final List<Strike> strikes = new ArrayList<>();
    public StrikeListener listener;
    private final Random rnd;
    private final double start;
    private double nextStrike;

    public interface StrikeListener {
        void strike(Strike s);
    }

    // удар молнии: x от 0 до 1 по ширине, dist от 0 (рядом) до 1 (у горизонта)
    public static final class Strike {
        public final double at;
        public final float x, dist;
        public final long seed;

        Strike(double at, float x, float dist, long seed) {
            this.at = at;
            this.x = x;
            this.dist = dist;
            this.seed = seed;
        }

        public boolean near() {
            return dist < 0.4f;
        }

        // яркость вспышки, двойное мигание как в жизни
        public float flash(double t) {
            double dt = t - at;
            if (dt < 0 || dt > 0.7) return 0;
            float f = (float) (dt < 0.08 ? 1 : dt < 0.16 ? 0.35 : dt < 0.24 ? 0.85 : Math.max(0, 0.85 - (dt - 0.24) / 0.46 * 0.85));
            return f * (near() ? 1f : 0.35f);
        }
    }

    // start это время сцены, когда погода началась (для радуги и первой молнии)
    public Weather(WeatherKind kind, long seed, double start) {
        this.kind = kind;
        this.rnd = new Random(seed);
        this.start = start;
        this.nextStrike = start + 2.5;
        update(start);
    }

    // зимой снег почти всегда (4 из 5), летом снега нет
    public static WeatherKind pick(Season season, Random r) {
        double x = r.nextDouble();
        if (season == Season.WINTER) {
            if (x < 0.55) return WeatherKind.SNOW;
            if (x < 0.80) return WeatherKind.BLIZZARD;
            return x < 0.92 ? WeatherKind.CLEAR : WeatherKind.FOG;
        }
        double[] w = season == Season.AUTUMN ? new double[] {0.26, 0.24, 0.12, 0.24, 0.14}
                : season == Season.SPRING ? new double[] {0.30, 0.20, 0.15, 0.15, 0.20}
                : new double[] {0.36, 0.14, 0.22, 0.10, 0.18};
        WeatherKind[] k = {WeatherKind.CLEAR, WeatherKind.RAIN, WeatherKind.THUNDER, WeatherKind.FOG, WeatherKind.RAINBOW};
        for (int i = 0; i < w.length; i++) {
            if (x < w[i]) return k[i];
            x -= w[i];
        }
        return WeatherKind.CLEAR;
    }

    public void update(double t) {
        double since = t - start;
        float wave = (float) (Noise.noise(t / 9.0, 41) - 0.5);
        float gust = (float) (Noise.noise(t / 3.0, 42) - 0.5);
        cloud = dark = rain = snow = wind = fog = rainbow = 0;
        switch (kind) {
            case CLEAR -> { cloud = 0.25f; fog = 0.08f; wind = 0.2f + gust * 0.2f; }
            case RAIN -> { cloud = 0.85f; dark = 0.45f; rain = 0.65f + wave * 0.5f; fog = 0.35f; wind = 0.35f + gust * 0.3f; }
            case THUNDER -> { cloud = 1f; dark = 0.75f; rain = 0.85f + wave * 0.3f; fog = 0.4f; wind = 0.6f + gust * 0.5f; }
            case SNOW -> { cloud = 0.75f; dark = 0.25f; snow = 0.6f + wave * 0.4f; fog = 0.35f; wind = 0.15f + gust * 0.2f; }
            case FOG -> { cloud = 0.45f; dark = 0.12f; fog = 0.85f + wave * 0.2f; wind = 0.05f; }
            case BLIZZARD -> { cloud = 1f; dark = 0.45f; snow = 1f; fog = 0.7f + wave * 0.3f; wind = 0.95f + gust * 0.4f; }
            case RAINBOW -> {
                // сначала дождик стихает, потом проступает радуга
                float after = Col.smooth((float) (since - 6) / 14f);
                cloud = 0.7f - after * 0.3f;
                dark = 0.3f * (1 - after);
                rain = 0.4f * (1 - Col.smooth((float) since / 9f));
                fog = 0.25f;
                wind = 0.15f + gust * 0.15f;
                rainbow = after;
            }
        }
        rain = Col.clamp(rain, 0, 1);
        snow = Col.clamp(snow, 0, 1);
        fog = Col.clamp(fog, 0, 1);
        wind = Col.clamp(wind, 0, 1.2f);
        if (kind == WeatherKind.THUNDER) thunder(t);
        strikes.removeIf(s -> t - s.at > 1.0);
    }

    private void thunder(double t) {
        if (t < nextStrike) return;
        float dist = rnd.nextFloat() < 0.35f ? rnd.nextFloat() * 0.35f : 0.45f + rnd.nextFloat() * 0.55f;
        Strike s = new Strike(t, 0.1f + rnd.nextFloat() * 0.8f, dist, rnd.nextLong());
        strikes.add(s);
        if (listener != null) listener.strike(s);
        nextStrike = t + 3 + rnd.nextDouble() * 9;
    }

    public float flash(double t) {
        float f = 0;
        for (Strike s : strikes) f = Math.max(f, s.flash(t));
        return f;
    }
}
