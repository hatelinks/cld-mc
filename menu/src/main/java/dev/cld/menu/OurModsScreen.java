package dev.cld.menu;

import dev.cld.menu.scene.MenuConfig;
import dev.cld.menu.scene.Season;
import dev.cld.menu.scene.WeatherKind;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// Экран «Наши моды»: слева разделы по модам и датапакам сборки, справа их настройки.
// Открывается из настроек и из меню паузы. У каждой настройки своя кнопка «по умолчанию».
public final class OurModsScreen extends Screen {
    private static final int ROW = 24;
    private static String lastSection = "cld_menu";
    private final Screen parent;
    private final List<Section> sections = new ArrayList<>();
    private Section current;
    private final List<String[]> lines = new ArrayList<>();

    private record Section(String id, String title, Runnable build) {}

    public OurModsScreen(Screen parent) {
        super(Component.translatable("cld_menu.ourmods.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        sections.clear();
        sections.add(new Section("cld_menu", Component.translatable("cld_menu.ourmods.menu").getString(), this::menuSection));
        for (var info : ModList.get().getMods()) {
            String id = info.getModId();
            if (id.equals("cld_menu") || !(id.startsWith("cld_") || id.equals("tcw") || id.equals("sable_android"))) continue;
            sections.add(new Section(id, info.getDisplayName(), () -> modSection(id)));
        }
        sections.add(new Section("startup", Component.translatable("cld_menu.ourmods.startup_title").getString(), this::startupSection));
        sections.add(new Section("datapacks", Component.translatable("cld_menu.ourmods.datapacks").getString(), this::datapackSection));
        current = sections.stream().filter(s -> s.id.equals(lastSection)).findFirst().orElse(sections.get(0));
        int y = 32;
        for (Section s : sections) {
            Button b = Button.builder(Component.literal(s.title), btn -> select(s)).bounds(8, y, leftW(), 18).build();
            b.active = s != current;
            addRenderableWidget(b);
            y += 20;
        }
        lines.clear();
        current.build.run();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 100, height - 26, 200, 20).build());
    }

    private void select(Section s) {
        lastSection = s.id;
        rebuildWidgets();
    }

    // при крупном интерфейсе (guiScale 6) экран узкий, колонки сжимаются
    private int leftW() {
        return width < 480 ? 104 : 130;
    }

    private int rightX() {
        return leftW() + 16;
    }

    private int rightW() {
        return Math.min(380, width - rightX() - 8);
    }

    // строка настройки: подпись слева, элемент справа, кнопка сброса в конце
    private void row(int i, String label, AbstractWidget control, Runnable reset) {
        int y = 36 + i * ROW;
        int cw = Math.max(90, Math.min(170, rightW() - 150));
        control.setX(rightX() + rightW() - cw - 56);
        control.setY(y);
        control.setWidth(cw);
        addRenderableWidget(control);
        addRenderableWidget(Button.builder(Component.translatable("cld_menu.ourmods.reset"), b -> {
            reset.run();
            rebuildWidgets();
        }).bounds(rightX() + rightW() - 52, y, 52, 20).build());
        lines.add(new String[] {label, "" + (y + 6)});
    }

    private void menuSection() {
        MenuConfig c = Landscape.config();
        MenuConfig d = new MenuConfig();
        List<String> weathers = new ArrayList<>(List.of("auto"));
        for (WeatherKind k : WeatherKind.values()) weathers.add(k.name().toLowerCase());
        row(0, tr("weather"), cycle(weathers, c.weather, v -> v.equals("auto") ? tr("auto_weather") : WeatherKind.parse(v).title,
                v -> { c.weather = v; Landscape.applyConfig(true); }), () -> { c.weather = d.weather; Landscape.applyConfig(true); });
        List<String> seasons = new ArrayList<>(List.of("auto"));
        for (Season s : Season.values()) seasons.add(s.name().toLowerCase());
        row(1, tr("season"), cycle(seasons, c.season, v -> v.equals("auto") ? tr("auto_calendar") : Season.parse(v, Season.SUMMER).title,
                v -> { c.season = v; Landscape.applyConfig(true); }), () -> { c.season = d.season; Landscape.applyConfig(true); });
        List<String> times = List.of("auto", "06:00", "09:00", "12:00", "15:00", "18:00", "20:30", "23:00", "03:00");
        row(2, tr("time"), cycle(times, times.contains(c.time) ? c.time : "auto", v -> v.equals("auto") ? tr("auto_clock") : v,
                v -> { c.time = v; Landscape.applyConfig(false); }), () -> { c.time = d.time; Landscape.applyConfig(false); });
        row(3, tr("sounds"), slider(() -> c.sounds, v -> { c.sounds = v; Landscape.applyConfig(false); }),
                () -> { c.sounds = d.sounds; Landscape.applyConfig(false); });
        row(4, tr("music_storm"), slider(() -> c.musicInStorm, v -> { c.musicInStorm = v; Landscape.applyConfig(false); }),
                () -> { c.musicInStorm = d.musicInStorm; Landscape.applyConfig(false); });
        List<Integer> fps = List.of(20, 30, 60, 120);
        row(5, tr("fps"), cycle(fps, fps.contains(c.fps) ? c.fps : 60, v -> v + " " + tr("per_second"),
                v -> { c.fps = v; Landscape.applyConfig(false); }), () -> { c.fps = d.fps; Landscape.applyConfig(false); });
        hint(6, tr("hint_f9"));
        if (StartupTimer.summary() != null) hint(7, tr("startup") + " " + StartupTimer.summary());
    }

    private void modSection(String id) {
        ModContainer mod = ModList.get().getModContainerById(id).orElse(null);
        if (mod == null) return;
        var info = mod.getModInfo();
        hint(0, tr("version") + " " + info.getVersion());
        String desc = info.getDescription().replace('\n', ' ').trim();
        int i = 1;
        for (var part : font.split(Component.literal(desc), rightW())) {
            if (i > 4) break;
            StringBuilder sb = new StringBuilder();
            part.accept((idx, style, cp) -> {
                sb.appendCodePoint(cp);
                return true;
            });
            hint(i++, sb.toString());
        }
        var factory = mod.getCustomExtension(IConfigScreenFactory.class);
        if (factory.isPresent()) {
            addRenderableWidget(Button.builder(Component.translatable("cld_menu.ourmods.open"),
                    b -> minecraft.setScreen(factory.get().createScreen(mod, this))).bounds(rightX(), 36 + 6 * ROW, 200, 20).build());
        } else {
            hint(6, tr("no_settings"));
        }
        if (id.equals("cld_far")) hint(7, tr("hint_far"));
    }

    private void startupSection() {
        List<String> h = StartupTimer.history(8);
        if (h.isEmpty()) hint(0, tr("startup_none"));
        for (int i = 0; i < h.size(); i++) hint(i, h.get(i));
        hint(Math.max(1, h.size()) + 1, tr("startup_file"));
        hint(Math.max(1, h.size()) + 2, tr("hint_file"));
    }

    private void datapackSection() {
        var server = minecraft.getSingleplayerServer();
        if (server == null) {
            hint(0, tr("datapacks_world"));
            return;
        }
        int i = 0;
        for (var pack : server.getPackRepository().getAvailablePacks()) {
            String id = pack.getId();
            if (!(id.contains("pack_fixes") || id.contains("colony_guard") || id.contains("cld"))) continue;
            boolean on = server.getPackRepository().getSelectedIds().contains(id);
            hint(i++, (on ? tr("on") : tr("off")) + "  " + pack.getTitle().getString());
        }
        if (i == 0) hint(0, tr("datapacks_none"));
        hint(i + 1, tr("datapacks_later"));
    }

    private void hint(int i, String text) {
        lines.add(new String[] {text, "" + (36 + i * ROW + 6), "hint"});
    }

    private static String tr(String key) {
        return Component.translatable("cld_menu.ourmods." + key).getString();
    }

    private static <T> CycleButton<T> cycle(List<T> values, T initial, java.util.function.Function<T, String> name, Consumer<T> set) {
        return CycleButton.<T>builder(v -> Component.literal(name.apply(v))).withValues(values).withInitialValue(initial)
                .displayOnlyValue().create(0, 0, 150, 20, Component.empty(), (b, v) -> set.accept(v));
    }

    private static AbstractSliderButton slider(Supplier<Float> get, Consumer<Float> set) {
        return new AbstractSliderButton(0, 0, 150, 20, Component.empty(), get.get()) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.literal(value <= 0 ? tr("off") : Math.round(value * 100) + "%"));
            }

            @Override
            protected void applyValue() {
                set.accept((float) value);
            }
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        g.drawString(font, current.title, rightX(), 22, 0x8FE3A8);
        for (String[] l : lines) {
            int color = l.length > 2 ? 0xC8CCD0 : 0xFFFFFF;
            // подписи настроек не залезают на кнопки, подсказки обрезаются по ширине колонки
            int max = l.length > 2 ? rightW() : rightW() - Math.max(90, Math.min(170, rightW() - 150)) - 60;
            g.drawString(font, font.plainSubstrByWidth(l[0], max), rightX(), Integer.parseInt(l[1]), color);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new OurModsScreen(mc.screen));
    }
}
