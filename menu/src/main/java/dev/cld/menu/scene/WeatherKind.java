package dev.cld.menu.scene;

public enum WeatherKind {
    CLEAR("Ясно"), RAIN("Дождь"), THUNDER("Гроза"), SNOW("Снегопад"), FOG("Туман"), BLIZZARD("Метель"), RAINBOW("Радуга после дождя");

    public final String title;

    WeatherKind(String title) {
        this.title = title;
    }

    public boolean snowy() {
        return this == SNOW || this == BLIZZARD;
    }

    public static WeatherKind parse(String s) {
        for (WeatherKind v : values()) if (v.name().equalsIgnoreCase(s)) return v;
        return null;
    }
}
