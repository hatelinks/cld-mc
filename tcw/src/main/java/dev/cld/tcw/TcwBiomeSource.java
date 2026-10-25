package dev.cld.tcw;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.cld.tcw.gen.Macro;
import dev.cld.tcw.gen.Options;
import dev.cld.tcw.gen.Simplex;
import dev.cld.tcw.gen.Terrain;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.neoforged.fml.ModList;

/**
 * Биомы TelipailoCraft: обычные биомы Minecraft по климату (как в ванильном выборе), а в горах — пояса по высоте
 * от снеговой линии: подгольцовый лес, альпийские луга и тундра, осыпи, ледники; на вулканах — вулканические склоны.
 * Под землёй в холодных и жарких сухих краях — пещерные биомы YUNG's Cave Biomes, если мод установлен.
 */
public final class TcwBiomeSource extends BiomeSource {
    static final com.mojang.serialization.Codec<Options> OPTIONS_CODEC = RecordCodecBuilder.create(i -> i.group(
            com.mojang.serialization.Codec.INT.optionalFieldOf("continents", 1).forGetter(Options::continents),
            com.mojang.serialization.Codec.INT.optionalFieldOf("mountains", 1).forGetter(Options::mountains),
            com.mojang.serialization.Codec.INT.optionalFieldOf("height", 1).forGetter(Options::height),
            com.mojang.serialization.Codec.INT.optionalFieldOf("climate", 1).forGetter(Options::climate),
            com.mojang.serialization.Codec.INT.optionalFieldOf("snow", 1).forGetter(Options::snow),
            com.mojang.serialization.Codec.INT.optionalFieldOf("halls", 1).forGetter(Options::halls)
    ).apply(i, Options::new));

    public static final MapCodec<TcwBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            MultiNoiseBiomeSourceParameterList.CODEC.fieldOf("preset").forGetter(s -> s.preset),
            RegistryOps.retrieveGetter(Registries.BIOME),
            OPTIONS_CODEC.optionalFieldOf("options", Options.DEFAULT).forGetter(TcwBiomeSource::options)
    ).apply(i, TcwBiomeSource::new));

    private static final Simplex N = new Simplex(4242);

    private final Holder<MultiNoiseBiomeSourceParameterList> preset;
    private final MultiNoiseBiomeSource base;
    private final Holder<Biome> subalpine, meadow, tundra, scree, glacier, volcanic, frostCaves, sandCaves;
    private final Options options;

    private TcwBiomeSource(TcwBiomeSource o, Options options) {
        preset = o.preset; base = o.base; subalpine = o.subalpine; meadow = o.meadow; tundra = o.tundra; scree = o.scree;
        glacier = o.glacier; volcanic = o.volcanic; frostCaves = o.frostCaves; sandCaves = o.sandCaves;
        this.options = options;
    }

    /** Тот же источник с другими настройками мира. */
    public TcwBiomeSource withOptions(Options o) { return new TcwBiomeSource(this, o); }

    public Options options() { return options; }

    private TcwBiomeSource(Holder<MultiNoiseBiomeSourceParameterList> preset, HolderGetter<Biome> biomes, Options options) {
        this.preset = preset;
        this.options = options;
        base = MultiNoiseBiomeSource.createFromPreset(preset);
        subalpine = own(biomes, "subalpine_forest");
        meadow = own(biomes, "alpine_meadow");
        tundra = own(biomes, "alpine_tundra");
        scree = own(biomes, "scree_slopes");
        glacier = own(biomes, "glacier");
        volcanic = own(biomes, "volcanic_slopes");
        boolean ycb = ModList.get() != null && ModList.get().isLoaded("yungscavebiomes");
        frostCaves = ycb ? optional(biomes, "yungscavebiomes", "frosted_caves") : null;
        sandCaves = ycb ? optional(biomes, "yungscavebiomes", "lost_caves") : null;
    }

    private static Holder<Biome> own(HolderGetter<Biome> g, String name) {
        return g.getOrThrow(ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(TcwMod.NS, name)));
    }

    private static Holder<Biome> optional(HolderGetter<Biome> g, String ns, String name) {
        return g.get(ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(ns, name))).orElse(null);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() { return CODEC; }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        List<Holder<Biome>> own = new ArrayList<>(List.of(subalpine, meadow, tundra, scree, glacier, volcanic));
        if (frostCaves != null) own.add(frostCaves);
        if (sandCaves != null) own.add(sandCaves);
        return Stream.concat(base.possibleBiomes().stream(), own.stream());
    }

    @Override
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
        if (sampler.temperature() instanceof ColumnDensity cd && cd.terrain() != null) {
            Holder<Biome> b = pick(cd.terrain(), (qx << 2) + 2, (qy << 2) + 2, (qz << 2) + 2);
            if (b != null) return b;
        }
        return base.getNoiseBiome(qx, qy, qz, sampler);
    }

    /** Свой биом в точке или null (тогда ванильный выбор по климату). */
    private Holder<Biome> pick(Terrain t, int x, int y, int z) {
        Terrain.Column c = Worlds.column(t, x, z);
        double h = c.height;
        if (y < h - 28) {
            // пещерные биомы YUNG's Cave Biomes пятнами
            if (y > Macro.MIN_Y + 20 && y < h - 40 && N.noise(x / 180.0 + y / 90.0, z / 180.0) > 0.35) {
                if (frostCaves != null && c.temp < -0.3) return frostCaves;
                if (sandCaves != null && c.temp > 0.45 && c.humid < -0.15) return sandCaves;
            }
            return null;
        }
        if (y < h - 12 || c.waterKind != Terrain.WATER_NONE || c.riverDist < 6) return null;
        Macro.Style st = c.dominant();
        if (st == Macro.Style.MESA || st == Macro.Style.KARST) return null;
        double sl = t.snowLine(x, z, c.temp) + 10 * N.noise(x / 60.0, z / 60.0);
        double tree = sl - 115;
        if (h < tree - 55 || c.mountain < 0.12) return null;
        if (c.style[Macro.Style.VOLCANIC.ordinal()] > 0.5 && h < sl) return volcanic;
        if (h > sl + 10) {
            // ледники на пологих местах в холодных горах, остальное — ванильные пики
            return c.temp < -0.2 && c.slope < 0.45 ? glacier : null;
        }
        if (h > sl - 45) return c.slope > 0.35 ? scree : tundra;
        if (h > tree) {
            if (c.slope > 0.8) return scree;
            return c.humid > -0.25 && c.temp > -0.45 ? meadow : tundra;
        }
        return c.temp > -0.55 ? subalpine : null;
    }
}
