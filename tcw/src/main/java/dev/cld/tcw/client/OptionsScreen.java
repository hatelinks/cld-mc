package dev.cld.tcw.client;

import dev.cld.tcw.gen.Options;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;

/** «Настроить» для типа мира TelipailoCraft. */
public final class OptionsScreen extends Screen {
    private final CreateWorldScreen parent;
    private final int[] v;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    private static final String[][] ROWS = {
            { "Континенты", "Небольшие", "Огромные", "Гигантские" },
            { "Горы", "Реже", "Обычно", "Чаще" },
            { "Высота гор", "Ниже", "Обычная", "Выше" },
            { "Климатические зоны", "Узкие", "Обычные", "Широкие" },
            { "Снеговая линия", "Ниже", "Обычная", "Выше" },
            { "Гигантские залы", "Редко", "Обычно", "Часто" },
    };

    public OptionsScreen(CreateWorldScreen parent) {
        super(Component.literal("Настройки TelipailoCraft"));
        this.parent = parent;
        Options o = ClientSetup.options(parent.getUiState());
        v = new int[] { o.continents(), o.mountains(), o.height(), o.climate(), o.snow(), o.halls() };
    }

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);
        GridLayout grid = new GridLayout().rowSpacing(4);
        GridLayout.RowHelper rows = grid.createRowHelper(1);
        for (int r = 0; r < ROWS.length; r++) {
            int row = r;
            String[] names = ROWS[r];
            rows.addChild(CycleButton.<Integer>builder(i -> Component.literal(names[i + 1]))
                    .withValues(List.of(0, 1, 2)).withInitialValue(v[r])
                    .create(0, 0, 260, 20, Component.literal(names[0]), (b, val) -> v[row] = val));
        }
        layout.addToContents(grid);
        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(Component.literal("По умолчанию"), b -> {
            ClientSetup.setOptions(parent.getUiState(), Options.DEFAULT);
            minecraft.setScreen(new OptionsScreen(parent));
        }).width(100).build());
        footer.addChild(Button.builder(Component.translatable("gui.done"), b -> {
            ClientSetup.setOptions(parent.getUiState(), new Options(v[0], v[1], v[2], v[3], v[4], v[5]));
            onClose();
        }).width(100).build());
        footer.addChild(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).width(100).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void repositionElements() { layout.arrangeElements(); }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
    }
}
