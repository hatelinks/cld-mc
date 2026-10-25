package dev.cld.pregen.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.cld.pregen.server.ChunkSample;
import dev.cld.pregen.server.MapData;
import dev.cld.pregen.server.PregenTask;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.WorldgenRandom;

/** Экран предпросмотра: слева панель настроек, справа карта генерации. */
final class PreviewScreen extends Screen {
    private static final int PANEL_W = 180;
    private static final int BG = 0xFF0E1116, PANEL = 0xF0171B22, LINE = 0xFF2A313C;
    private static final int ACCENT = 0xFF4FC3A1, TEXT = 0xFFE6E9EF, DIM = 0xFF8A93A3;
    private static final int[] PRIORITY_INFLIGHT = {12, 48, 128};

    private final PreviewSession session;
    private MapView map;
    private EditBox seedBox;
    private Button startStop;
    private int radius = 64, msPerTick = 20, priority = 2;
    private PregenTask.Shape shape = PregenTask.Shape.SQUARE;
    private MapView.Mode mode = MapView.Mode.BLOCKS;
    private boolean showProgress = true, showSlime, showStructures = true;
    private double viewX, viewZ, zoom = 1.0;
    private String status = "";
    private long statusUntil;
    private int lastDone;
    private long lastSpeedAt;
    private double speed;

    PreviewScreen(PreviewSession session) {
        super(Component.translatable("cld_pregen.title"));
        this.session = session;
        this.viewX = session.center.getMiddleBlockX();
        this.viewZ = session.center.getMiddleBlockZ();
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    private static net.minecraft.network.chat.MutableComponent tr(String key, Object... args) { return Component.translatable("cld_pregen." + key, args); }

    @Override
    protected void init() {
        if (map == null) map = new MapView(radius, session.sea, session.maxY);
        int x = 8, w = PANEL_W - 16, half = (w - 4) / 2, y = 24, row = 22;

        seedBox = new EditBox(font, x, y, w, 18, tr("seed"));
        seedBox.setMaxLength(32);
        seedBox.setValue(String.valueOf(session.seed));
        addRenderableWidget(seedBox);
        y += row;
        addRenderableWidget(Button.builder(tr("randomize"), b -> seedBox.setValue(String.valueOf(new Random().nextLong())))
                .bounds(x, y, half, 20).build());
        addRenderableWidget(Button.builder(tr("apply_seed"), b -> applySeed()).bounds(x + half + 4, y, half, 20).build());
        y += row;
        addRenderableWidget(slider(x, y, w, 8, 500, radius, v -> radius = v, v -> tr("radius", v)));
        y += row;
        addRenderableWidget(CycleButton.<PregenTask.Shape>builder(s -> tr("shape." + s.name().toLowerCase()))
                .withValues(PregenTask.Shape.values()).withInitialValue(shape).displayOnlyValue()
                .create(x, y, half, 20, tr("shape"), (b, v) -> shape = v));
        addRenderableWidget(CycleButton.<Integer>builder(p -> tr("priority." + p))
                .withValues(0, 1, 2).withInitialValue(priority).displayOnlyValue()
                .create(x + half + 4, y, half, 20, tr("priority"), (b, v) -> { priority = v; pushSettings(); }));
        y += row;
        addRenderableWidget(slider(x, y, w, 1, 50, msPerTick, v -> { msPerTick = v; pushSettings(); }, v -> tr("ms", v)));
        y += row;
        startStop = addRenderableWidget(Button.builder(tr("start"), b -> toggle()).bounds(x, y, w, 20).build());
        y += row;
        addRenderableWidget(CycleButton.<MapView.Mode>builder(m -> tr("view." + m.name().toLowerCase()))
                .withValues(MapView.Mode.values()).withInitialValue(mode)
                .create(x, y, w, 20, tr("view"), (b, v) -> { mode = v; map.setMode(v, MapData.get()); }));
        y += row;
        int third = (w - 8) / 3;
        addRenderableWidget(toggle(x, y, third, "progress", showProgress, v -> showProgress = v));
        addRenderableWidget(toggle(x + third + 4, y, third, "slime", showSlime, v -> showSlime = v));
        addRenderableWidget(toggle(x + 2 * (third + 4), y, third, "structures", showStructures, v -> showStructures = v));
        y += row;
        addRenderableWidget(Button.builder(tr("screenshot"), b -> screenshot()).bounds(x, y, half, 20).build());
        addRenderableWidget(Button.builder(tr("gc"), b -> { System.gc(); flash(tr("gc_done")); }).bounds(x + half + 4, y, half, 20).build());

        int by = height - 26;
        addRenderableWidget(Button.builder(tr("keep_world"), b -> keepWorld()).bounds(x, by, half, 20).build());
        addRenderableWidget(Button.builder(tr("back"), b -> back()).bounds(x + half + 4, by, half, 20).build());
        updateStartStop();
    }

    private CycleButton<Boolean> toggle(int x, int y, int w, String key, boolean init, java.util.function.Consumer<Boolean> set) {
        return CycleButton.booleanBuilder(tr(key).withStyle(s -> s.withColor(ACCENT)), tr(key).withStyle(s -> s.withColor(DIM)))
                .withInitialValue(init).displayOnlyValue().create(x, y, w, 20, tr(key), (b, v) -> set.accept(v));
    }

    private AbstractSliderButton slider(int x, int y, int w, int min, int max, int value, IntConsumer set,
                                        java.util.function.IntFunction<Component> label) {
        return new AbstractSliderButton(x, y, w, 20, label.apply(value), (value - min) / (double) (max - min)) {
            private int current() { return min + (int) Math.round(this.value * (max - min)); }
            @Override protected void updateMessage() { setMessage(label.apply(current())); }
            @Override protected void applyValue() { set.accept(current()); }
        };
    }

    private void pushSettings() {
        int inflight = PRIORITY_INFLIGHT[priority], ms = msPerTick;
        session.server.execute(() -> {
            PregenTask t = PregenTask.active();
            if (t != null) { t.maxInFlight = inflight; t.msPerTick = ms; }
        });
    }

    private boolean running() {
        PregenTask t = PregenTask.active();
        return t != null && t.running();
    }

    private void toggle() {
        if (running()) session.stop();
        else {
            if (map.bpp != MapView.bppFor(radius)) { // крупный радиус требует другого масштаба тайлов
                map.close();
                map = new MapView(radius, session.sea, session.maxY);
                map.setMode(mode, MapData.get());
            }
            session.start(radius, shape, PRIORITY_INFLIGHT[priority], msPerTick);
            lastDone = 0;
            lastSpeedAt = System.currentTimeMillis();
        }
    }

    private void updateStartStop() {
        if (startStop != null) startStop.setMessage(running() ? tr("stop") : tr("start"));
    }

    private void applySeed() {
        String s = seedBox.getValue().trim();
        if (s.equals(String.valueOf(session.seed))) { flash(tr("same_seed")); return; }
        minecraft.setScreen(new ConfirmScreen(ok -> {
            if (ok) { closeMap(); session.discard(s); } else minecraft.setScreen(this);
        }, tr("apply_seed.title"), tr("apply_seed.body")));
    }

    private void keepWorld() {
        PregenTask t = PregenTask.active();
        if (t != null && !t.finished()) {
            minecraft.setScreen(new ConfirmScreen(ok -> {
                if (ok) { closeMap(); session.keep(); } else minecraft.setScreen(this);
            }, tr("keep.title"), tr("keep.body")));
        } else session.keep();
    }

    private void back() {
        minecraft.setScreen(new ConfirmScreen(ok -> {
            if (ok) { closeMap(); session.discard(null); } else minecraft.setScreen(this);
        }, tr("back.title"), tr("back.body")));
    }

    private void flash(Component c) { status = c.getString(); statusUntil = System.currentTimeMillis() + 4000; }

    private void screenshot() {
        MapData data = MapData.get();
        int r = radius;
        int cx = session.center.x, cz = session.center.z;
        try (NativeImage img = map.snapshot(data, cx - r, cz - r, cx + r, cz + r)) {
            File dir = new File(minecraft.gameDirectory, "screenshots");
            dir.mkdirs();
            String name = "preview_" + session.seed + "_" + mode.name().toLowerCase() + "_"
                    + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + ".png";
            img.writeToFile(new File(dir, name));
            flash(tr("screenshot_saved", name));
        } catch (Exception e) {
            flash(tr("screenshot_failed", e.getMessage()));
        }
    }

    @Override
    public void tick() {
        updateStartStop();
        PregenTask t = PregenTask.active();
        long now = System.currentTimeMillis();
        if (t != null && now - lastSpeedAt >= 1000) {
            int d = t.done();
            double inst = (d - lastDone) * 1000.0 / (now - lastSpeedAt);
            speed = speed == 0 ? inst : speed * 0.7 + inst * 0.3;
            lastDone = d;
            lastSpeedAt = now;
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // фон экрана = карта и панель; виджеты Screen.render рисует поверх
        g.fill(0, 0, width, height, BG);
        if (map == null) return;
        map.update(MapData.get());
        int mapX = PANEL_W, mapW = width - PANEL_W;
        g.enableScissor(mapX, 0, width, height);
        renderGrid(g, mapX, mapW);
        map.render(g, mapX, 0, mapW, height, viewX, viewZ, zoom);
        renderOverlays(g, mapX, mapW, mx, my);
        g.disableScissor();
        renderPanel(g);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderHover(g, PANEL_W, mx, my);
    }

    private int sx(double blockX, int mapX, int mapW) { return (int) Math.floor(mapX + mapW / 2.0 + (blockX - viewX) * zoom); }
    private int sy(double blockZ) { return (int) Math.floor(height / 2.0 + (blockZ - viewZ) * zoom); }

    private void renderGrid(GuiGraphics g, int mapX, int mapW) {
        // сетка регионов 512x512 для ориентира
        double step = 512 * zoom;
        if (step < 24) return;
        double startX = Math.floor((viewX - mapW / 2.0 / zoom) / 512) * 512;
        for (double bx = startX; sx(bx, mapX, mapW) < width; bx += 512) {
            int px = sx(bx, mapX, mapW);
            g.fill(px, 0, px + 1, height, 0xFF161A20);
        }
        double startZ = Math.floor((viewZ - height / 2.0 / zoom) / 512) * 512;
        for (double bz = startZ; sy(bz) < height; bz += 512) {
            int py = sy(bz);
            g.fill(mapX, py, width, py + 1, 0xFF161A20);
        }
    }

    private void renderOverlays(GuiGraphics g, int mapX, int mapW, int mx, int my) {
        MapData data = MapData.get();
        int cx = session.center.x, cz = session.center.z, r = radius;
        if (showProgress) {
            int color = 0x804FC3A1;
            if (shape == PregenTask.Shape.SQUARE) {
                outline(g, sx((cx - r) * 16, mapX, mapW), sy((cz - r) * 16), sx((cx + r + 1) * 16, mapX, mapW), sy((cz + r + 1) * 16), color);
            } else {
                for (int dz = -r; dz <= r; dz++) {
                    int dx = (int) Math.floor(Math.sqrt((double) r * r - (double) dz * dz));
                    int y0 = sy((cz + dz) * 16), y1 = sy((cz + dz + 1) * 16);
                    int xa = sx((cx - dx) * 16, mapX, mapW), xb = sx((cx + dx + 1) * 16, mapX, mapW);
                    g.fill(xa, y0, xa + 1, Math.max(y1, y0 + 1), color);
                    g.fill(xb - 1, y0, xb, Math.max(y1, y0 + 1), color);
                }
            }
            int spx = sx(session.center.getMiddleBlockX(), mapX, mapW), spy = sy(session.center.getMiddleBlockZ());
            g.fill(spx - 3, spy - 1, spx + 4, spy + 2, 0xFFFF5555);
            g.fill(spx - 1, spy - 3, spx + 2, spy + 4, 0xFFFF5555);
        }
        // видимые чанки
        int c0x = Math.floorDiv((int) (viewX - mapW / 2.0 / zoom), 16) - 1, c1x = Math.floorDiv((int) (viewX + mapW / 2.0 / zoom), 16) + 1;
        int c0z = Math.floorDiv((int) (viewZ - height / 2.0 / zoom), 16) - 1, c1z = Math.floorDiv((int) (viewZ + height / 2.0 / zoom), 16) + 1;
        long visible = (long) (c1x - c0x + 1) * (c1z - c0z + 1);
        if (showSlime && visible <= 40000 && zoom >= 0.25) {
            for (int x = c0x; x <= c1x; x++)
                for (int z = c0z; z <= c1z; z++)
                    if (data.at(x, z) != null && WorldgenRandom.seedSlimeChunk(x, z, session.seed, 987234911L).nextInt(10) == 0)
                        g.fill(sx(x * 16, mapX, mapW), sy(z * 16), sx(x * 16 + 16, mapX, mapW), sy(z * 16 + 16), 0x6040FF40);
        }
        if (showStructures && visible <= 250000) {
            for (int x = c0x; x <= c1x; x++) {
                for (int z = c0z; z <= c1z; z++) {
                    ChunkSample s = data.at(x, z);
                    if (s == null || s.structures.length == 0) continue;
                    int px = sx(x * 16 + 8, mapX, mapW), py = sy(z * 16 + 8);
                    int c = 0xFF000000 | (s.structures[0].hashCode() * 0x9E3779B1 >>> 8) | 0x404040;
                    g.fill(px - 3, py - 3, px + 3, py + 3, 0xFF000000);
                    g.fill(px - 2, py - 2, px + 2, py + 2, c);
                }
            }
        }
    }

    private static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0, y0, x1, y0 + 1, c); g.fill(x0, y1 - 1, x1, y1, c);
        g.fill(x0, y0, x0 + 1, y1, c); g.fill(x1 - 1, y0, x1, y1, c);
    }

    private void renderPanel(GuiGraphics g) {
        g.fill(0, 0, PANEL_W, height, PANEL);
        g.fill(PANEL_W - 1, 0, PANEL_W, height, LINE);
        g.drawString(font, title, 8, 8, ACCENT);
        PregenTask t = PregenTask.active();
        int y = height - 26 - 46;
        g.fill(8, y - 4, PANEL_W - 8, y - 3, LINE);
        if (t != null) {
            float p = t.total == 0 ? 1 : t.done() / (float) t.total;
            g.fill(8, y, PANEL_W - 8, y + 6, 0xFF252B34);
            g.fill(8, y, 8 + (int) ((PANEL_W - 16) * Math.min(1f, p)), y + 6, ACCENT);
            g.drawString(font, tr("progress_line", t.done(), t.total, String.format("%.1f", p * 100)), 8, y + 9, TEXT, false);
            String eta = speed > 0.1 ? formatTime((long) ((t.total - t.done()) / speed)) : "—";
            g.drawString(font, tr("speed_line", String.format("%.0f", speed), eta), 8, y + 20, DIM, false);
        } else {
            g.drawString(font, tr("idle"), 8, y + 9, DIM, false);
        }
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) >> 20, max = rt.maxMemory() >> 20;
        g.drawString(font, tr("memory_line", used, max), 8, y + 31, used > max * 0.85 ? 0xFFFF6B6B : DIM, false);
        if (System.currentTimeMillis() < statusUntil)
            g.drawString(font, status, PANEL_W + 8, height - 14, ACCENT, true);
    }

    private void renderHover(GuiGraphics g, int mapX, int mx, int my) {
        if (mx < mapX) return;
        int mapW = width - mapX;
        double bx = viewX + (mx - mapX - mapW / 2.0) / zoom, bz = viewZ + (my - height / 2.0) / zoom;
        int ix = Mth.floor(bx), iz = Mth.floor(bz);
        ChunkSample s = MapData.get().at(ix >> 4, iz >> 4);
        String line;
        if (s == null) line = tr("hover_empty", ix, iz).getString();
        else {
            int i = (iz & 15) * 16 + (ix & 15);
            var biome = s.biomes[((iz & 15) / 4) * 4 + (ix & 15) / 4];
            String biomeName = biome.unwrapKey()
                    .map(k -> Component.translatable("biome." + k.location().getNamespace() + "." + k.location().getPath()).getString())
                    .orElse("?");
            line = tr("hover", ix, iz, s.height[i], biomeName).getString();
            if (s.structures.length > 0) line += "  [" + String.join(", ", s.structures) + "]";
        }
        int w = font.width(line) + 8;
        g.fill(mapX + 4, 4, mapX + 4 + w, 18, 0xC0000000);
        g.drawString(font, line, mapX + 8, 7, TEXT, false);
    }

    private static String formatTime(long sec) {
        return sec >= 3600 ? String.format("%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60)
                : String.format("%d:%02d", sec / 60, sec % 60);
    }

    private boolean dragging;

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (mx >= PANEL_W) { dragging = true; setFocused(null); return true; }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            viewX -= dx / zoom;
            viewZ -= dy / zoom;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < PANEL_W) return super.mouseScrolled(mx, my, sx, sy);
        int mapW = width - PANEL_W;
        double bx = viewX + (mx - PANEL_W - mapW / 2.0) / zoom, bz = viewZ + (my - height / 2.0) / zoom;
        double nz = Mth.clamp(zoom * (sy > 0 ? 1.25 : 0.8), 1.0 / 32, 8);
        viewX = bx - (mx - PANEL_W - mapW / 2.0) / nz;
        viewZ = bz - (my - height / 2.0) / nz;
        zoom = nz;
        return true;
    }

    private void closeMap() {
        if (map != null) { map.close(); map = null; }
    }

    @Override
    public void removed() {
        // экран может временно смениться на окно подтверждения — карту закрываем, только когда сеанс завершён
        if (!session.active) closeMap();
    }
}
