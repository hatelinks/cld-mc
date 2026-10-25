package dev.cld.pregen.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.cld.pregen.PregenMod;
import dev.cld.pregen.server.ChunkSample;
import dev.cld.pregen.server.MapData;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;

/** Карта из тайлов по 32x32 чанка, каждый тайл — отдельная текстура. */
final class MapView {
    enum Mode { BLOCKS, BIOMES, HEIGHTS }

    static final int TILE_CHUNKS = 32;
    static final int TILE_BLOCKS = TILE_CHUNKS * 16;

    final int bpp;      // блоков на пиксель
    final int tilePx;
    final int sea, maxY;
    Mode mode = Mode.BLOCKS;
    private final Map<Long, Tile> tiles = new HashMap<>();
    private final ArrayDeque<ChunkSample> repaint = new ArrayDeque<>();
    private static int texCounter;

    private final class Tile {
        final NativeImage img = new NativeImage(tilePx, tilePx, true);
        final DynamicTexture tex = new DynamicTexture(img);
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath(PregenMod.MODID, "map/" + texCounter++);
        boolean dirty;
        Tile() {
            img.fillRect(0, 0, tilePx, tilePx, 0);
            Minecraft.getInstance().getTextureManager().register(id, tex);
        }
    }

    MapView(int radiusChunks, int sea, int maxY) {
        this.bpp = bppFor(radiusChunks);
        this.tilePx = TILE_BLOCKS / bpp;
        this.sea = sea; this.maxY = maxY;
    }

    static int bppFor(int radiusChunks) { return radiusChunks <= 160 ? 1 : radiusChunks <= 320 ? 2 : 4; }

    static long tileKey(int tx, int tz) { return ((long) tx << 32) | (tz & 0xFFFFFFFFL); }

    void setMode(Mode m, MapData data) {
        mode = m;
        repaint.clear();
        repaint.addAll(data.chunks.values());
    }

    void update(MapData data) {
        int budget = 3000;
        ChunkSample s;
        while (budget-- > 0 && (s = data.fresh.poll()) != null) {
            paint(data, s);
            ChunkSample south = data.at(s.x, s.z + 1); // тень зависит от северного соседа
            if (south != null) paint(data, south);
        }
        while (budget-- > 0 && (s = repaint.poll()) != null) paint(data, s);
        int uploads = 12;
        for (Tile t : tiles.values()) {
            if (t.dirty && uploads-- > 0) { t.tex.upload(); t.dirty = false; }
        }
    }

    private void paint(MapData data, ChunkSample s) {
        int tx = Math.floorDiv(s.x, TILE_CHUNKS), tz = Math.floorDiv(s.z, TILE_CHUNKS);
        Tile t = tiles.computeIfAbsent(tileKey(tx, tz), k -> new Tile());
        int ox = (Math.floorMod(s.x, TILE_CHUNKS) * 16) / bpp, oz = (Math.floorMod(s.z, TILE_CHUNKS) * 16) / bpp;
        ChunkSample north = data.at(s.x, s.z - 1);
        for (int lz = 0; lz < 16; lz += bpp) {
            for (int lx = 0; lx < 16; lx += bpp) {
                t.img.setPixelRGBA(ox + lx / bpp, oz + lz / bpp, color(s, north, lx, lz));
            }
        }
        t.dirty = true;
    }

    /** Цвет столбца в формате ABGR. */
    int color(ChunkSample s, ChunkSample north, int lx, int lz) {
        int i = lz * 16 + lx;
        int h = s.height[i], w = s.water[i];
        int nh = lz >= bpp ? s.height[i - 16 * bpp] : north != null ? north.height[(16 - bpp) * 16 + lx] : h;
        MapColor.Brightness br;
        if (w > 0) {
            double d = w * 0.1 + ((lx + lz) / bpp & 1) * 0.2;
            br = d < 0.5 ? MapColor.Brightness.HIGH : d > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
        } else {
            br = h > nh ? MapColor.Brightness.HIGH : h < nh ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
        }
        float k = br == MapColor.Brightness.HIGH ? 1.0f : br == MapColor.Brightness.LOW ? 0.80f : 0.90f;
        return switch (mode) {
            case BLOCKS -> {
                MapColor mc = MapColor.byId(s.color[i] & 0xFF);
                yield mc == MapColor.NONE ? 0xFF000000 : mc.calculateRGBColor(br);
            }
            case BIOMES -> Palettes.abgr(Palettes.shade(Palettes.biome(s.biomes[(lz / 4) * 4 + lx / 4]), w > 0 ? 0.9f : k + 0.05f));
            case HEIGHTS -> Palettes.abgr(Palettes.shade(Palettes.height(h, w, sea, maxY), w > 0 ? 1f : k + 0.1f));
        };
    }

    /** Рисует карту: (viewX, viewZ) — блок в центре области, zoom — пикселей экрана на блок. */
    void render(GuiGraphics g, int x0, int y0, int w, int h, double viewX, double viewZ, double zoom) {
        double left = viewX - w / 2.0 / zoom, top = viewZ - h / 2.0 / zoom;
        int tx0 = Math.floorDiv((int) Math.floor(left), TILE_BLOCKS);
        int tz0 = Math.floorDiv((int) Math.floor(top), TILE_BLOCKS);
        int tx1 = Math.floorDiv((int) Math.ceil(left + w / zoom), TILE_BLOCKS);
        int tz1 = Math.floorDiv((int) Math.ceil(top + h / zoom), TILE_BLOCKS);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x0 + w / 2.0 - viewX * zoom, y0 + h / 2.0 - viewZ * zoom, 0);
        pose.scale((float) zoom, (float) zoom, 1f);
        for (int tx = tx0; tx <= tx1; tx++) {
            for (int tz = tz0; tz <= tz1; tz++) {
                Tile t = tiles.get(tileKey(tx, tz));
                if (t == null) continue;
                g.blit(t.id, tx * TILE_BLOCKS, tz * TILE_BLOCKS, TILE_BLOCKS, TILE_BLOCKS, 0, 0, tilePx, tilePx, tilePx, tilePx);
            }
        }
        pose.popPose();
    }

    /** Картинка всей сгенерированной области для скриншота. */
    NativeImage snapshot(MapData data, int minCx, int minCz, int maxCx, int maxCz) {
        int spanBlocks = Math.max(maxCx - minCx + 1, maxCz - minCz + 1) * 16;
        int step = 1;
        while (spanBlocks / step > 8192) step *= 2;
        int s16 = Math.max(1, 16 / step);
        int wpx = (maxCx - minCx + 1) * 16 / step, hpx = (maxCz - minCz + 1) * 16 / step;
        NativeImage img = new NativeImage(Math.max(1, wpx), Math.max(1, hpx), true);
        img.fillRect(0, 0, img.getWidth(), img.getHeight(), 0xFF101010);
        for (ChunkSample s : data.chunks.values()) {
            if (s.x < minCx || s.x > maxCx || s.z < minCz || s.z > maxCz) continue;
            ChunkSample north = data.at(s.x, s.z - 1);
            int ox = (s.x - minCx) * 16 / step, oz = (s.z - minCz) * 16 / step;
            for (int pz = 0; pz < s16; pz++)
                for (int px = 0; px < s16; px++)
                    img.setPixelRGBA(ox + px, oz + pz, colorAt(s, north, Math.min(15, px * step), Math.min(15, pz * step)));
        }
        return img;
    }

    private int colorAt(ChunkSample s, ChunkSample north, int lx, int lz) {
        return color(s, north, lx - lx % bpp, lz - lz % bpp);
    }

    void close() {
        var tm = Minecraft.getInstance().getTextureManager();
        for (Tile t : tiles.values()) tm.release(t.id);
        tiles.clear();
    }
}
