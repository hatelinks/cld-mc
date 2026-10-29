package dev.cld.far.client;

import com.mojang.datafixers.util.Pair;
import dev.cld.far.gen.TileMesh;
import dev.cld.tcw.gen.Macro;
import dev.cld.tcw.gen.Simplex;
import dev.cld.tcw.gen.Terrain;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Цвет земли по биому без чанков. Все параметры климата у TelipailoCraft берутся из модели рельефа (Terrain.climate),
 * поэтому выбор биома повторяется на клиенте: сначала свои горные биомы tcw (как TcwBiomeSource.pick у поверхности),
 * иначе ванильная таблица OverworldBiomeBuilder. Цвет травы и воды — из реестра биомов, который сервер присылает клиенту.
 */
final class BiomePalette implements TileMesh.Palette {
    private static final Logger LOG = LoggerFactory.getLogger("cld_far");
    private static final Simplex N = new Simplex(4242);

    private final Climate.ParameterList<ResourceKey<Biome>> params;
    private final Registry<Biome> biomes;
    private final Map<ResourceKey<Biome>, Integer> look = new ConcurrentHashMap<>();
    private final ResourceKey<Biome> subalpine = tcw("subalpine_forest"), meadow = tcw("alpine_meadow"), tundra = tcw("alpine_tundra"),
            scree = tcw("scree_slopes"), glacier = tcw("glacier"), volcanic = tcw("volcanic_slopes");

    private BiomePalette(Climate.ParameterList<ResourceKey<Biome>> params, Registry<Biome> biomes) {
        this.params = params;
        this.biomes = biomes;
    }

    /** null, если ванильную таблицу биомов достать не удалось (тогда цвета из модели). */
    static TileMesh.Palette create(RegistryAccess access) {
        try {
            List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> list = new ArrayList<>();
            Method add = OverworldBiomeBuilder.class.getDeclaredMethod("addBiomes", Consumer.class);
            add.setAccessible(true);
            Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> sink = list::add;
            add.invoke(new OverworldBiomeBuilder(), sink);
            return new BiomePalette(new Climate.ParameterList<>(list), access.registryOrThrow(Registries.BIOME));
        } catch (Throwable e) {
            LOG.warn("Дальний рельеф: таблица биомов недоступна, цвета по модели рельефа", e);
            return null;
        }
    }

    private static ResourceKey<Biome> tcw(String name) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("tcw", name));
    }

    @Override
    public int ground(Terrain t, Terrain.Column c, double x, double z) {
        return look.computeIfAbsent(biome(t, c, x, z), this::look);
    }

    @Override
    public int water(Terrain t, Terrain.Column c, double x, double z) {
        Biome b = biomes.get(biome(t, c, x, z));
        return b != null ? b.getWaterColor() & 0xFFFFFF : -1;
    }

    private ResourceKey<Biome> biome(Terrain t, Terrain.Column c, double x, double z) {
        ResourceKey<Biome> k = pick(t, c, x, z);
        if (k == null) {
            double y = c.height;
            k = params.findValue(Climate.target((float) t.climate(c, Terrain.P_TEMPERATURE, x, y, z), (float) t.climate(c, Terrain.P_VEGETATION, x, y, z),
                    (float) t.climate(c, Terrain.P_CONTINENTS, x, y, z), (float) t.climate(c, Terrain.P_EROSION, x, y, z),
                    (float) t.climate(c, Terrain.P_DEPTH, x, y, z), (float) t.climate(c, Terrain.P_RIDGES, x, y, z)));
        }
        return k;
    }

    /** Свои биомы tcw у поверхности (повторяет TcwBiomeSource.pick при y ≈ высота). */
    private ResourceKey<Biome> pick(Terrain t, Terrain.Column c, double x, double z) {
        double h = c.height;
        if (c.waterKind != Terrain.WATER_NONE || c.riverDist < 6) return null;
        Macro.Style st = c.dominant();
        if (st == Macro.Style.MESA || st == Macro.Style.KARST) return null;
        double sl = t.snowLine(x, z, c.temp) + 10 * N.noise(x / 60.0, z / 60.0);
        double tree = sl - 115;
        if (h < tree - 55 || c.mountain < 0.12) return null;
        if (c.style[Macro.Style.VOLCANIC.ordinal()] > 0.5 && h < sl) return volcanic;
        if (h > sl + 10) return c.temp < -0.2 && c.slope < 0.45 ? glacier : null;
        if (h > sl - 45) return c.slope > 0.35 ? scree : tundra;
        if (h > tree) {
            if (c.slope > 0.8) return scree;
            return c.humid > -0.25 && c.temp > -0.45 ? meadow : tundra;
        }
        return c.temp > -0.55 ? subalpine : null;
    }

    /** Цвет поверхности биома: песок, снег, камень или трава/листва с его оттенком. */
    private int look(ResourceKey<Biome> k) {
        String p = k.location().getPath();
        Biome b = biomes.get(k);
        int grass = b != null ? b.getGrassColor(0, 0) : 0x79C05A, leaves = b != null ? b.getFoliageColor() : 0x59AE30;
        int c;
        if (p.contains("desert") || p.contains("beach") && !p.contains("snowy")) c = 0xDBCFA3;
        else if (p.contains("badlands")) c = p.contains("wooded") ? 0xA0703F : 0xC0683A;
        else if (p.contains("snowy") || p.contains("frozen") || p.contains("ice") || p.contains("grove") || p.equals("glacier")) c = 0xF0F4F8;
        else if (p.contains("stony") || p.contains("scree") || p.contains("windswept_gravelly")) c = 0x8A8986;
        else if (p.contains("volcanic")) c = 0x48433F;
        else if (p.contains("mushroom")) c = 0x6F6369;
        else if (p.contains("forest") || p.contains("jungle") || p.contains("taiga") || p.contains("swamp")) c = scale(leaves, 0.62);
        else c = scale(grass, 0.78);
        return c;
    }

    private static int scale(int c, double k) {
        return ((int) (((c >> 16) & 255) * k) << 16) | ((int) (((c >> 8) & 255) * k) << 8) | (int) ((c & 255) * k);
    }
}
