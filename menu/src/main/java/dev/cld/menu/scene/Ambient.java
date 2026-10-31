package dev.cld.menu.scene;

import java.util.Random;

// Звуки природы меню, синтез на лету: ветер, вьюга, шум дождя, птицы днём, сверчки ночью.
// Громкость каждого слоя задаётся из потока игры, звук считает поток звука, поэтому поля volatile.
public final class Ambient {
    public static final int RATE = 22050;
    public volatile float birds, crickets, wind, blizzard, rain;

    private final Random rnd;
    private float gBirds, gCrickets, gWind, gBlizzard, gRain;
    private long n;
    // ветер
    private float lp1, lp2, gust, gustTarget;
    // вьюга: резонансный фильтр
    private float bpLow, bpBand, whistle = 900, whistleTarget = 900;
    // дождь
    private float rainLp, drop, dropDecay;
    // птицы
    private int birdWait = RATE, notes, noteLen, notePos;
    private float f0, f1, phase, birdAmp;
    private int species;
    // сверчки
    private final float[] crPhase = new float[3], crFreq = {4300, 4650, 4900}, crRate = {0.62f, 0.83f, 0.71f};
    private final float[] crOffset = new float[3];

    public Ambient(long seed) {
        rnd = new Random(seed);
        for (int i = 0; i < 3; i++) crOffset[i] = rnd.nextFloat();
    }

    // заполняет out числами от -1 до 1
    public void fill(float[] out, int count) {
        for (int i = 0; i < count; i++, n++) {
            // громкости слоёв меняются плавно, без щелчков
            gBirds += (birds - gBirds) * 0.00005f;
            gCrickets += (crickets - gCrickets) * 0.00005f;
            gWind += (wind - gWind) * 0.00005f;
            gBlizzard += (blizzard - gBlizzard) * 0.00005f;
            gRain += (rain - gRain) * 0.00005f;
            float s = 0;
            if (gWind > 0.002f || gBlizzard > 0.002f) s += wind() * (gWind + gBlizzard * 0.6f);
            if (gBlizzard > 0.002f) s += whistle() * gBlizzard;
            if (gRain > 0.002f) s += rain() * gRain;
            if (gBirds > 0.002f) s += bird() * gBirds;
            if (gCrickets > 0.002f) s += crickets() * gCrickets;
            out[i] = (float) Math.tanh(s * 1.2f);
        }
    }

    private float white() {
        return rnd.nextFloat() * 2 - 1;
    }

    // ветер: шум через два фильтра, порывы меняют и громкость, и тембр
    private float wind() {
        if (n % 2205 == 0) gustTarget = 0.25f + rnd.nextFloat() * 0.75f;
        gust += (gustTarget - gust) * 0.00008f;
        float a = 0.01f + gust * 0.04f;
        lp1 += (white() - lp1) * a;
        lp2 += (lp1 - lp2) * a;
        return lp2 * (2f + gust * 2.5f);
    }

    // свист вьюги: узкая полоса шума, частота гуляет
    private float whistle() {
        if (n % 11025 == 0) whistleTarget = 500 + rnd.nextFloat() * 1100;
        whistle += (whistleTarget - whistle) * 0.0002f;
        float f = (float) (2 * Math.sin(Math.PI * whistle / RATE));
        float q = 0.06f;
        float high = white() - bpLow - q * bpBand;
        bpBand += f * high;
        bpLow += f * bpBand;
        return bpBand * 0.22f * (0.4f + gust);
    }

    // дождь: шипение и отдельные капли
    private float rain() {
        float w = white();
        rainLp += (w - rainLp) * 0.3f;
        float hiss = (w - rainLp) * 0.18f + rainLp * 0.12f;
        if (rnd.nextFloat() < 0.012f * gRain) {
            drop = (rnd.nextFloat() * 0.6f + 0.2f) * (rnd.nextBoolean() ? 1 : -1);
            dropDecay = 0.9f + rnd.nextFloat() * 0.08f;
        }
        drop *= dropDecay;
        return hiss + drop * 0.5f;
    }

    // птицы: короткие фразы из свистов с изменением высоты
    private float bird() {
        if (notes == 0) {
            if (--birdWait > 0) return 0;
            species = rnd.nextInt(3);
            notes = species == 2 ? 10 + rnd.nextInt(8) : 2 + rnd.nextInt(5);
            birdAmp = 0.15f + rnd.nextFloat() * 0.2f;
            startNote();
        }
        float p = notePos / (float) noteLen;
        float f = f0 + (f1 - f0) * p;
        phase += (float) (2 * Math.PI * f / RATE);
        if (phase > Math.PI * 2) phase -= (float) (Math.PI * 2);
        float env = (float) Math.sin(Math.PI * p);
        float s = (float) (Math.sin(phase) + 0.15 * Math.sin(phase * 2)) * env * env * birdAmp;
        if (++notePos >= noteLen) {
            if (--notes > 0) startNote();
            else birdWait = (int) (RATE * (0.6 + rnd.nextFloat() * 3.5));
        }
        return s;
    }

    private void startNote() {
        notePos = 0;
        switch (species) {
            case 0 -> { // свист вверх
                f0 = 2400 + rnd.nextFloat() * 800;
                f1 = f0 + 900 + rnd.nextFloat() * 900;
                noteLen = (int) (RATE * (0.06 + rnd.nextFloat() * 0.06));
            }
            case 1 -> { // свист вниз
                f0 = 4200 + rnd.nextFloat() * 900;
                f1 = f0 - 1500 - rnd.nextFloat() * 700;
                noteLen = (int) (RATE * (0.08 + rnd.nextFloat() * 0.07));
            }
            default -> { // трель
                f0 = 3600 + rnd.nextFloat() * 400;
                f1 = f0 + (rnd.nextBoolean() ? 300 : -300);
                noteLen = (int) (RATE * 0.03);
            }
        }
    }

    // сверчки: три сверчка, каждый трещит пачками из трёх импульсов
    private float crickets() {
        float s = 0;
        double t = n / (double) RATE;
        for (int i = 0; i < 3; i++) {
            double cyc = (t / crRate[i] + crOffset[i]) % 1.0;
            double pt = cyc * crRate[i];
            int pulse = (int) (pt / 0.045);
            double inPulse = pt - pulse * 0.045;
            if (pulse < 3 && inPulse < 0.028) {
                crPhase[i] += (float) (2 * Math.PI * crFreq[i] / RATE);
                if (crPhase[i] > Math.PI * 2) crPhase[i] -= (float) (Math.PI * 2);
                float env = (float) Math.sin(Math.PI * inPulse / 0.028);
                s += (float) Math.sin(crPhase[i]) * env * (i == 0 ? 0.12f : 0.07f);
            }
        }
        return s;
    }

    // сколько слоёв звучит для этой погоды и времени суток
    public void target(Weather wx, Sky sky) {
        float day = sky.day, night = 1 - day;
        boolean winter = sky.season == Season.WINTER;
        float b = switch (wx.kind) {
            case CLEAR, FOG -> 1f;
            case RAINBOW -> 0.4f + wx.rainbow * 0.6f;
            case RAIN -> 0.12f;
            case SNOW -> 0.25f;
            default -> 0f;
        };
        birds = b * day * (winter ? 0.35f : 1f) * (1 - wx.fog * 0.4f);
        float c = switch (wx.kind) {
            case CLEAR, RAINBOW -> 1f;
            case FOG -> 0.6f;
            case RAIN -> 0.15f;
            default -> 0f;
        };
        crickets = winter ? 0 : c * night * (sky.season == Season.SUMMER ? 1f : 0.55f);
        wind = 0.08f + wx.wind * (wx.kind == WeatherKind.THUNDER ? 0.55f : 0.4f);
        blizzard = wx.kind == WeatherKind.BLIZZARD ? 0.55f : wx.snow * 0.15f;
        rain = wx.rain * 0.9f;
    }
}
