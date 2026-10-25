package dev.cld.tcw.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.cld.tcw.Spawn;
import dev.cld.tcw.gen.MapRender;
import dev.cld.tcw.gen.Terrain;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Карта мира по модели рельефа (чанки не генерируются): колесо — масштаб, перетаскивание — сдвиг.
 * Красная точка — игрок, жёлтая — точка появления.
 */
public final class MapScreen extends Screen {
    private static final int N = 384;
    private static final int[] SCALES = { 2, 4, 8, 16, 32, 64, 128 };
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath("tcw", "map_preview");

    private final Screen parent;
    private final Terrain t;
    private final String note;
    private double cx, cz;
    private int scaleIdx = 4;
    private final double[] marks; // игрок x,z, точка появления x,z (или null)
    private BlockPos spawn;
    private DynamicTexture tex;
    private MapRender.Grid grid, shown;
    private double dragX, dragZ;
    private boolean dragging;

    public MapScreen(Screen parent, Terrain t, double cx, double cz, double[] marks, String note) {
        super(Component.literal("Карта TelipailoCraft"));
        this.parent = parent;
        this.t = t;
        this.cx = cx;
        this.cz = cz;
        this.marks = marks;
        this.note = note;
        if (marks == null) CompletableFuture.supplyAsync(() -> Spawn.find(t), Util.backgroundExecutor()).thenAccept(p -> spawn = p);
    }

    private int scale() { return SCALES[scaleIdx]; }

    private int size() { return Math.max(64, Math.min(width - 20, height - 56)); }

    private int left() { return (width - size()) / 2; }

    private int top() { return 28; }

    @Override
    protected void init() {
        int y = height - 24;
        addRenderableWidget(Button.builder(Component.literal("−"), b -> zoom(1, cx, cz)).bounds(width / 2 - 104, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> zoom(-1, cx, cz)).bounds(width / 2 - 80, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Готово"), b -> onClose()).bounds(width / 2 - 50, y, 154, 20).build());
        if (tex == null) {
            tex = new DynamicTexture(new NativeImage(N, N, false));
            minecraft.getTextureManager().register(TEX, tex);
        }
        if (grid == null || shown == null) start();
    }

    private void start() {
        if (grid != null) grid.cancelled = true;
        double k = scale() * (double) size() / N;
        MapRender.Grid g = new MapRender.Grid(cx - k * N / 2, cz - k * N / 2, k, N);
        grid = g;
        DynamicTexture target = tex;
        CompletableFuture.runAsync(() -> MapRender.compute(t, g), Util.backgroundExecutor()).thenRun(() -> {
            if (g.cancelled || target == null) return;
            NativeImage img = target.getPixels();
            if (img == null) return;
            int[] px = new int[N * N];
            for (int j = 0; j < N; j++)
                for (int i = 0; i < N; i++) px[j * N + i] = MapRender.pixel(t, g, i, j);
            minecraft.execute(() -> {
                if (g != grid || target != tex) return;
                for (int j = 0; j < N; j++)
                    for (int i = 0; i < N; i++) {
                        int c = px[j * N + i];
                        img.setPixelRGBA(i, j, 0xFF000000 | (c & 0xFF) << 16 | (c & 0xFF00) | (c >> 16 & 0xFF));
                    }
                target.upload();
                shown = g;
            });
        });
    }

    private void zoom(int d, double ax, double az) {
        int ni = Math.max(0, Math.min(SCALES.length - 1, scaleIdx + d));
        if (ni == scaleIdx) return;
        double f = (double) SCALES[ni] / scale();
        cx = ax + (cx - ax) * f;
        cz = az + (cz - az) * f;
        scaleIdx = ni;
        start();
    }

    /** Мировые координаты под точкой экрана. */
    private double[] world(double mx, double my) {
        double s = scale();
        return new double[] { cx + (mx - left() - size() / 2.0) * s, cz + (my - top() - size() / 2.0) * s };
    }

    private boolean inside(double mx, double my) {
        return mx >= left() && mx < left() + size() && my >= top() && my < top() + size();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!inside(mx, my) || sy == 0) return false;
        double[] w = world(mx, my);
        zoom(sy > 0 ? -1 : 1, w[0], w[1]);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && inside(mx, my)) { dragging = true; dragX = dragZ = 0; return true; }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging) return super.mouseDragged(mx, my, button, dx, dy);
        dragX += dx;
        dragZ += dy;
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            if (Math.abs(dragX) + Math.abs(dragZ) > 2) {
                cx -= dragX * scale();
                cz -= dragZ * scale();
                dragX = dragZ = 0;
                start();
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int l = left(), tp = top(), s = size();
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        g.fill(l - 1, tp - 1, l + s + 1, tp + s + 1, 0xFF000000);
        if (shown != null) {
            // пока считается новая карта, старая сдвигается вместе с мышью
            double ox = (shown.x0 + shown.k * N / 2 - cx) / scale() + (dragging ? dragX : 0);
            double oz = (shown.z0 + shown.k * N / 2 - cz) / scale() + (dragging ? dragZ : 0);
            int dw = (int) Math.round(shown.k * N / scale());
            g.enableScissor(l, tp, l + s, tp + s);
            g.blit(TEX, (int) Math.round(l + s / 2.0 + ox - dw / 2.0), (int) Math.round(tp + s / 2.0 + oz - dw / 2.0), dw, dw, 0, 0, N, N, N, N);
            g.disableScissor();
        }
        if (grid != shown) {
            int pct = grid == null ? 0 : grid.done.get() * 100 / N;
            g.drawCenteredString(font, "Считаю карту… " + pct + "%", width / 2, tp + s / 2, 0xFFFFFF);
        }
        g.enableScissor(l, tp, l + s, tp + s);
        if (marks != null) {
            dot(g, marks[2], marks[3], 0xFFFFDD00);
            dot(g, marks[0], marks[1], 0xFFFF2020);
        } else if (spawn != null) dot(g, spawn.getX(), spawn.getZ(), 0xFFFFDD00);
        g.disableScissor();
        String info = "1 пиксель = " + scale() + " блоков";
        if (inside(mx, my) && !dragging) {
            double[] w = world(mx, my);
            info = "X " + (int) w[0] + "  Z " + (int) w[1] + heightAt(w[0], w[1]) + "   " + info;
        }
        g.drawString(font, info, l, tp + s + 4, 0xE0E0E0);
        if (note != null) g.drawString(font, note, l, 8, 0xA0A0A0);
    }

    private String heightAt(double x, double z) {
        MapRender.Grid gr = shown;
        if (gr == null) return "";
        int i = (int) ((x - gr.x0) / gr.k), j = (int) ((z - gr.z0) / gr.k);
        if (i < 0 || j < 0 || i >= N || j >= N) return "";
        return "  высота " + (int) gr.h[j * N + i];
    }

    private void dot(GuiGraphics g, double x, double z, int color) {
        double s = scale();
        int px = (int) Math.round(left() + size() / 2.0 + (x - cx) / s + (dragging ? dragX : 0));
        int py = (int) Math.round(top() + size() / 2.0 + (z - cz) / s + (dragging ? dragZ : 0));
        g.fill(px - 3, py - 3, px + 3, py + 3, 0xFF000000);
        g.fill(px - 2, py - 2, px + 2, py + 2, color);
    }

    @Override
    public void onClose() {
        if (grid != null) grid.cancelled = true;
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        minecraft.getTextureManager().release(TEX);
        tex = null;
    }

    @Override
    public boolean isPauseScreen() { return true; }
}
