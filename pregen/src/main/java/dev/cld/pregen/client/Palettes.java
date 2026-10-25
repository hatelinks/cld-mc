package dev.cld.pregen.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/** Цвета биомов и высот для карты. Все цвета в формате ARGB, перевод в ABGR через {@link #abgr}. */
final class Palettes {
    private static final Map<String, Integer> BIOMES = new HashMap<>();

    static {
        String[] table = {
            "ocean", "000070", "deep_ocean", "000030", "warm_ocean", "0000AC", "lukewarm_ocean", "000090",
            "deep_lukewarm_ocean", "000040", "cold_ocean", "202070", "deep_cold_ocean", "202038",
            "frozen_ocean", "7070D6", "deep_frozen_ocean", "404090", "river", "0000FF", "frozen_river", "A0A0FF",
            "beach", "FADE55", "snowy_beach", "FAF0C0", "stony_shore", "A2A284", "mushroom_fields", "FF00FF",
            "plains", "8DB360", "sunflower_plains", "B5DB88", "snowy_plains", "FFFFFF", "ice_spikes", "B4DCDC",
            "desert", "FA9418", "swamp", "07F9B2", "mangrove_swamp", "2CCC8E", "forest", "056621",
            "flower_forest", "2D8E49", "birch_forest", "307444", "old_growth_birch_forest", "589C6C",
            "dark_forest", "40511A", "taiga", "0B6659", "snowy_taiga", "31554A", "old_growth_pine_taiga", "596651",
            "old_growth_spruce_taiga", "818E79", "jungle", "537B09", "sparse_jungle", "628B17",
            "bamboo_jungle", "768E14", "savanna", "BDB25F", "savanna_plateau", "A79D64", "windswept_savanna", "E5DA87",
            "badlands", "D94515", "eroded_badlands", "FF6D3D", "wooded_badlands", "B09765", "meadow", "60A445",
            "cherry_grove", "FFB7D5", "grove", "47726C", "snowy_slopes", "C4C4C4", "frozen_peaks", "A0A0C8",
            "jagged_peaks", "DCDCDC", "stony_peaks", "888888", "windswept_hills", "606060",
            "windswept_gravelly_hills", "789878", "windswept_forest", "507050", "dripstone_caves", "7E5D3A",
            "lush_caves", "3C8C2E", "deep_dark", "0F2A33", "the_void", "000000", "nether_wastes", "BF3B3B",
            "soul_sand_valley", "5E3830", "crimson_forest", "DD0808", "warped_forest", "49907B", "basalt_deltas", "403636",
            "the_end", "8080FF", "end_highlands", "B5B536", "end_midlands", "C5C566", "small_end_islands", "4B4BAB",
            "end_barrens", "7A7AC6",
        };
        for (int i = 0; i < table.length; i += 2) BIOMES.put("minecraft:" + table[i], 0xFF000000 | Integer.parseInt(table[i + 1], 16));
    }

    static int biome(Holder<Biome> h) {
        String key = h.unwrapKey().map(k -> k.location().toString()).orElse("?");
        Integer c = BIOMES.get(key);
        if (c != null) return c;
        int hash = key.hashCode() * 0x9E3779B1;
        return 0xFF000000 | (64 + (hash >>> 24) % 160) << 16 | (64 + (hash >>> 16 & 255) % 160) << 8 | (64 + (hash >>> 8 & 255) % 160);
    }

    /** Карта высот: вода синяя по глубине, суша от зелёного у моря до белого у потолка мира. */
    static int height(int y, int water, int sea, int maxY) {
        if (water > 0) {
            float t = Math.min(1f, water / 40f);
            return lerp(0xFF3A7BD5, 0xFF0B1E4A, t);
        }
        float t = Math.max(0f, Math.min(1f, (y - sea) / (float) Math.max(1, maxY - sea)));
        int[] stops = {0xFF3E8E41, 0xFF9CB84C, 0xFFD9C27A, 0xFF8C6A48, 0xFF8A8A8A, 0xFFFFFFFF};
        float p = t * (stops.length - 1);
        int i = Math.min(stops.length - 2, (int) p);
        return lerp(stops[i], stops[i + 1], p - i);
    }

    static int shade(int argb, float k) {
        int r = Math.min(255, (int) (((argb >> 16) & 255) * k));
        int g = Math.min(255, (int) (((argb >> 8) & 255) * k));
        int b = Math.min(255, (int) ((argb & 255) * k));
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    static int lerp(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    static int abgr(int argb) {
        return (argb & 0xFF00FF00) | (argb & 0xFF) << 16 | (argb >> 16) & 0xFF;
    }

    private Palettes() {}
}
