package dev.cld.menu.scene;

import java.time.LocalDate;

public enum Season {
    WINTER("Зима"), SPRING("Весна"), SUMMER("Лето"), AUTUMN("Осень");

    public final String title;

    Season(String title) {
        this.title = title;
    }

    // зима это декабрь, январь и февраль
    public static Season of(LocalDate d) {
        return switch (d.getMonthValue()) {
            case 12, 1, 2 -> WINTER;
            case 3, 4, 5 -> SPRING;
            case 6, 7, 8 -> SUMMER;
            default -> AUTUMN;
        };
    }

    public static Season parse(String s, Season fallback) {
        for (Season v : values()) if (v.name().equalsIgnoreCase(s)) return v;
        return fallback;
    }
}
