package dev.cld.menu;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.components.SplashRenderer;

// Свои надписи у логотипа: в половине запусков, в праздники ванильные
public final class Splashes {
    private static final List<String> LINES = List.of(
            "Колонисты ждут!", "Шестерёнки крутятся!", "Теперь с погодой!", "Слышишь гром?", "Снег по календарю!",
            "Ищи радугу!", "Кирка наготове!", "Посади семечко!", "Не копай вниз!", "Крипер не дремлет!",
            "Рельеф до горизонта!", "Тёплый чай и печка!", "Строим вместе!", "Мельница скрипит!");
    private static final Random RND = new Random();

    private Splashes() {}

    public static SplashRenderer pick(SplashRenderer vanilla) {
        if (vanilla == SplashRenderer.CHRISTMAS || vanilla == SplashRenderer.NEW_YEAR || vanilla == SplashRenderer.HALLOWEEN) return null;
        LocalDate d = LocalDate.now();
        if (d.getMonthValue() == 12 && d.getDayOfMonth() >= 20) return new SplashRenderer("Скоро Новый год!");
        if (RND.nextBoolean()) return null;
        return new SplashRenderer(LINES.get(RND.nextInt(LINES.size())));
    }
}
