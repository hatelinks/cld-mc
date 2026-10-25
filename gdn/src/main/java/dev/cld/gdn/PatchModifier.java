package dev.cld.gdn;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Изменения биома из jjtm, которые применяются только в мире с указанным типом измерения:
 * замена фич и карверов (по id), климат и цвета.
 */
public record PatchModifier(ResourceLocation dimensionType, HolderSet<Biome> biomes, List<Swap> features,
                            List<Swap> carvers, Optional<Climate> climate, Optional<Effects> effects)
        implements BiomeModifier {
    private static final Logger LOG = LogUtils.getLogger();

    /** step — номер шага декорации (features) или имя стадии карверов (carvers). */
    public record Swap(String step, List<ResourceLocation> remove, List<ResourceLocation> add) {
        static final Codec<Swap> FEATURES = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("step").forGetter(s -> Integer.parseInt(s.step)),
                ResourceLocation.CODEC.listOf().optionalFieldOf("remove", List.of()).forGetter(Swap::remove),
                ResourceLocation.CODEC.listOf().optionalFieldOf("add", List.of()).forGetter(Swap::add)
        ).apply(i, (st, r, a) -> new Swap(Integer.toString(st), r, a)));
        static final Codec<Swap> CARVERS = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("stage").forGetter(Swap::step),
                ResourceLocation.CODEC.listOf().optionalFieldOf("remove", List.of()).forGetter(Swap::remove),
                ResourceLocation.CODEC.listOf().optionalFieldOf("add", List.of()).forGetter(Swap::add)
        ).apply(i, Swap::new));
    }

    public record Climate(Optional<Float> temperature, Optional<Float> downfall, Optional<Boolean> hasPrecipitation,
                          Optional<Biome.TemperatureModifier> temperatureModifier) {
        static final Codec<Climate> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("temperature").forGetter(Climate::temperature),
                Codec.FLOAT.optionalFieldOf("downfall").forGetter(Climate::downfall),
                Codec.BOOL.optionalFieldOf("has_precipitation").forGetter(Climate::hasPrecipitation),
                Biome.TemperatureModifier.CODEC.optionalFieldOf("temperature_modifier").forGetter(Climate::temperatureModifier)
        ).apply(i, Climate::new));
    }

    public record Effects(Optional<Integer> fog, Optional<Integer> water, Optional<Integer> waterFog, Optional<Integer> sky,
                          Optional<Integer> foliage, Optional<Integer> grass,
                          Optional<BiomeSpecialEffects.GrassColorModifier> grassModifier) {
        static final Codec<Effects> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("fog_color").forGetter(Effects::fog),
                Codec.INT.optionalFieldOf("water_color").forGetter(Effects::water),
                Codec.INT.optionalFieldOf("water_fog_color").forGetter(Effects::waterFog),
                Codec.INT.optionalFieldOf("sky_color").forGetter(Effects::sky),
                Codec.INT.optionalFieldOf("foliage_color").forGetter(Effects::foliage),
                Codec.INT.optionalFieldOf("grass_color").forGetter(Effects::grass),
                BiomeSpecialEffects.GrassColorModifier.CODEC.optionalFieldOf("grass_color_modifier").forGetter(Effects::grassModifier)
        ).apply(i, Effects::new));
    }

    public static final MapCodec<PatchModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("dimension_type").forGetter(PatchModifier::dimensionType),
            RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(PatchModifier::biomes),
            Swap.FEATURES.listOf().optionalFieldOf("features", List.of()).forGetter(PatchModifier::features),
            Swap.CARVERS.listOf().optionalFieldOf("carvers", List.of()).forGetter(PatchModifier::carvers),
            Climate.CODEC.optionalFieldOf("climate").forGetter(PatchModifier::climate),
            Effects.CODEC.optionalFieldOf("effects").forGetter(PatchModifier::effects)
    ).apply(i, PatchModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.MODIFY || !biomes.contains(biome)) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        RegistryAccess access = server.registryAccess();
        if (!WorldType.overworldType(access).map(dimensionType::equals).orElse(false)) return;

        var gen = builder.getGenerationSettings();
        Registry<net.minecraft.world.level.levelgen.placement.PlacedFeature> placed = access.registryOrThrow(Registries.PLACED_FEATURE);
        for (Swap s : features) {
            int step = Integer.parseInt(s.step);
            if (step < 0 || step >= GenerationStep.Decoration.values().length) continue;
            var list = gen.getFeatures(GenerationStep.Decoration.values()[step]);
            Set<ResourceLocation> rm = new HashSet<>(s.remove);
            list.removeIf(h -> h.unwrapKey().map(k -> rm.contains(k.location())).orElse(false));
            for (ResourceLocation id : s.add) {
                placed.getHolder(ResourceKey.create(Registries.PLACED_FEATURE, id))
                        .ifPresentOrElse(list::add, () -> LOG.warn("gdn: нет фичи {}", id));
            }
        }
        var carverReg = access.registryOrThrow(Registries.CONFIGURED_CARVER);
        for (Swap s : carvers) {
            GenerationStep.Carving stage;
            try {
                stage = GenerationStep.Carving.valueOf(s.step.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            var list = gen.getCarvers(stage);
            Set<ResourceLocation> rm = new HashSet<>(s.remove);
            list.removeIf(h -> h.unwrapKey().map(k -> rm.contains(k.location())).orElse(false));
            for (ResourceLocation id : s.add) {
                carverReg.getHolder(ResourceKey.create(Registries.CONFIGURED_CARVER, id))
                        .ifPresentOrElse(list::add, () -> LOG.warn("gdn: нет карвера {}", id));
            }
        }
        climate.ifPresent(c -> {
            var cs = builder.getClimateSettings();
            c.temperature.ifPresent(cs::setTemperature);
            c.downfall.ifPresent(cs::setDownfall);
            c.hasPrecipitation.ifPresent(cs::setHasPrecipitation);
            c.temperatureModifier.ifPresent(cs::setTemperatureModifier);
        });
        effects.ifPresent(e -> {
            var fx = builder.getSpecialEffects();
            e.fog.ifPresent(fx::fogColor);
            e.water.ifPresent(fx::waterColor);
            e.waterFog.ifPresent(fx::waterFogColor);
            e.sky.ifPresent(fx::skyColor);
            e.foliage.ifPresent(fx::foliageColorOverride);
            e.grass.ifPresent(fx::grassColorOverride);
            e.grassModifier.ifPresent(fx::grassColorModifier);
        });
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}
