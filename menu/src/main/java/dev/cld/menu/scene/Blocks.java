package dev.cld.menu.scene;

import java.util.HashMap;
import java.util.Map;

// Ближние слои пейзажа из настоящих блоков Minecraft: трава, земля, камень, вода, деревья, цветы.
// Текстуры берутся из ресурс-пака игры, цвет травы и листвы по сезону, свет и дымка как у гор.
final class Blocks {
    static final String B = "minecraft:textures/block/";
    private final Tex.Source src;
    private final Map<String, Tex> raw = new HashMap<>();
    private final Map<Long, int[]> shaded = new HashMap<>();
    private int lightCol, low;
    private float haze;
    boolean ok;

    Blocks(Tex.Source src) {
        this.src = src;
        ok = tex("grass_block_side") != null && tex("dirt") != null && tex("stone") != null;
    }

    Tex tex(String name) {
        return raw.computeIfAbsent(name, n -> {
            try {
                return src.get(B + n + ".png");
            } catch (RuntimeException e) {
                return null;
            }
        });
    }

    // свет кадра: сбрасываем подготовленные текстуры
    void frame(int lightCol, int low, float haze) {
        this.lightCol = lightCol;
        this.low = low;
        this.haze = haze;
        shaded.clear();
    }

    // текстура с оттенком (трава, листва, вода), светом и дымкой; dark затемняет блоки в глубине
    int[] shade(String name, int tint, int frame, float dark) {
        long key = name.hashCode() * 31L + tint * 131L + frame * 7919L + Float.floatToIntBits(dark);
        int[] got = shaded.get(key);
        if (got != null) return got;
        Tex t = tex(name);
        if (t == null) return null;
        if (t.h > t.w) t = t.frame(frame);
        Tex overlay = name.equals("grass_block_side") ? tex("grass_block_side_overlay") : null;
        int[] out = new int[256];
        for (int i = 0; i < 256; i++) {
            int x = i & 15, y = i >> 4;
            int c = t.px[(y * t.h / 16) * t.w + x * t.w / 16];
            int a = c >>> 24;
            int rgb = c & 0xFFFFFF;
            if (overlay != null) {
                int o = overlay.px[(y * overlay.h / 16) * overlay.w + x * overlay.w / 16];
                if ((o >>> 24) > 0) rgb = Col.mul(o & 0xFFFFFF, tint);
            } else if (tint != 0xFFFFFF) {
                rgb = Col.mul(rgb, tint);
            }
            if (dark > 0) rgb = Col.scale(rgb, 1 - dark);
            rgb = Col.mix(Col.mul(rgb, lightCol), low, haze);
            out[i] = a << 24 | rgb;
        }
        shaded.put(key, out);
        return out;
    }

    // блок со стороной bs в точке (sx, sy); прозрачные пиксели пропускаются, полупрозрачные смешиваются
    static void draw(Raster r, int[] tex, int sx, int sy, int bs, int alpha) {
        if (tex == null || sx >= r.w || sy >= r.h || sx + bs <= 0 || sy + bs <= 0) return;
        int x0 = Math.max(0, sx), x1 = Math.min(r.w, sx + bs), y0 = Math.max(0, sy), y1 = Math.min(r.h, sy + bs);
        for (int y = y0; y < y1; y++) {
            int ty = (y - sy) * 16 / bs << 4, o = y * r.w;
            for (int x = x0; x < x1; x++) {
                int c = tex[ty | (x - sx) * 16 / bs];
                int a = (c >>> 24) * alpha / 255;
                if (a == 0) continue;
                if (a >= 250) r.px[o + x] = c & 0xFFFFFF;
                else r.dot(x, y, c & 0xFFFFFF, a);
            }
        }
    }

    // только верхняя часть блока (слой снега на листве и земле)
    static void drawTop(Raster r, int[] tex, int sx, int sy, int bs, int rows) {
        if (tex == null) return;
        int h = Math.max(1, bs * rows / 16);
        for (int y = Math.max(0, sy); y < Math.min(r.h, sy + h); y++) {
            int ty = (y - sy) * 16 / bs << 4, o = y * r.w;
            for (int x = Math.max(0, sx); x < Math.min(r.w, sx + bs); x++) {
                int c = tex[ty | (x - sx) * 16 / bs];
                if ((c >>> 24) > 0) r.px[o + x] = c & 0xFFFFFF;
            }
        }
    }
}
