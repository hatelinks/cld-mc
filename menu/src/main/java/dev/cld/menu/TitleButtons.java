package dev.cld.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Убирает кнопки Realms и «Моды» и подтягивает остальные кнопки вверх. */
final class TitleButtons {
    private static final Set<String> REMOVE = Set.of("menu.online", "fml.menu.mods");
    private static final int ROW = 24;

    private TitleButtons() {}

    static void onInit(ScreenEvent.Init.Post e) {
        if (!(e.getScreen() instanceof TitleScreen)) return;
        List<AbstractWidget> removed = new ArrayList<>();
        for (GuiEventListener l : List.copyOf(e.getListenersList())) {
            if (l instanceof AbstractWidget w && w.getMessage().getContents() instanceof TranslatableContents tc
                    && REMOVE.contains(tc.getKey())) {
                removed.add(w);
                e.removeListener(w);
            }
        }
        if (removed.isEmpty()) return;
        // Освободившиеся ряды; Realms и «Моды» могут стоять в одном ряду
        TreeSet<Integer> rows = new TreeSet<>();
        removed.forEach(w -> rows.add(w.getY()));
        int bottom = e.getScreen().height - 12; // копирайт внизу не трогаем
        for (GuiEventListener l : e.getListenersList()) {
            if (!(l instanceof AbstractWidget w) || w.getY() >= bottom) continue;
            int shift = rows.headSet(w.getY(), false).size() * ROW;
            if (shift > 0) w.setY(w.getY() - shift);
        }
    }
}
