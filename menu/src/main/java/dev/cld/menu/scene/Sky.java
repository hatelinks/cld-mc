package dev.cld.menu.scene;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

// Положение солнца и луны по часам компьютера: длина дня меняется по сезону, фаза луны по календарю
public final class Sky {
    public final double hour;
    public final double rise, set;
    public final double sunQ;     // путь солнца по небу от 0 до 1, вне дня меньше 0 или больше 1
    public final double moonQ;    // путь луны ночью
    public final float alt;       // высота солнца от -1 до 1
    public final float day;       // 0 ночь, 1 день
    public final float glow;      // заря
    public final double moonPhase; // 0 новолуние, 0.5 полнолуние
    public final Season season;

    public Sky(LocalDateTime now, Season season) {
        this.season = season;
        hour = now.getHour() + now.getMinute() / 60.0 + now.getSecond() / 3600.0;
        int doy = now.getDayOfYear();
        // широта около 55: летом день около 17 часов, зимой около 7
        double len = 12 + 5 * Math.cos(2 * Math.PI * (doy - 172) / 365.0);
        double noon = 12.75;
        rise = noon - len / 2;
        set = noon + len / 2;
        sunQ = (hour - rise) / (set - rise);
        double nightLen = 24 - len;
        double sinceSet = (hour - set + 24) % 24;
        moonQ = sinceSet / nightLen;
        if (sunQ >= 0 && sunQ <= 1) alt = (float) Math.sin(Math.PI * sunQ);
        else alt = (float) -Math.sin(Math.PI * Math.min(1, moonQ));
        day = Col.smooth((alt + 0.12f) / 0.4f);
        glow = Math.max(0, 1 - Math.abs(alt + 0.03f) / 0.22f);
        moonPhase = moonPhase(now);
    }

    public boolean morning() {
        return hour < 12.75;
    }

    // от известного новолуния 6 января 2000 года, синодический месяц 29.53 суток
    static double moonPhase(LocalDateTime now) {
        double days = (now.toEpochSecond(ZoneOffset.UTC) - 947182440L) / 86400.0;
        double p = days / 29.530588853;
        return p - Math.floor(p);
    }
}
