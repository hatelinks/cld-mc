package dev.cld.tcw;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/** Фичи и карверы, которые меняются только в мире TelipailoCraft (по типу измерения Верхнего мира). */
public record WorldModifier(ResourceLocation dimensionType, HolderSet<Biome> biomes, List<Add> features,
                            List<ResourceLocation> removeCarvers) implements BiomeModifier {
    private static final Logger LOG = LogUtils.getLogger();

    public record Add(GenerationStep.Decoration step, ResourceLocation feature) {
        static final Codec<Add> CODEC = RecordCodecBuilder.create(i -> i.group(
                GenerationStep.Decoration.CODEC.fieldOf("step").forGetter(Add::step),
                ResourceLocation.CODEC.fieldOf("feature").forGetter(Add::feature)
        ).apply(i, Add::new));
    }

    public static final MapCodec<WorldModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("dimension_type").forGetter(WorldModifier::dimensionType),
            RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(WorldModifier::biomes),
            Add.CODEC.listOf().optionalFieldOf("features", List.of()).forGetter(WorldModifier::features),
            ResourceLocation.CODEC.listOf().optionalFieldOf("remove_carvers", List.of()).forGetter(WorldModifier::removeCarvers)
    ).apply(i, WorldModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (!biomes.contains(biome)) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        RegistryAccess access = server.registryAccess();
        if (!Worlds.overworldType(access).map(dimensionType::equals).orElse(false)) return;
        var gen = builder.getGenerationSettings();
        if (phase == Phase.REMOVE) {
            Set<ResourceLocation> rm = new HashSet<>(removeCarvers);
            for (GenerationStep.Carving stage : GenerationStep.Carving.values())
                gen.getCarvers(stage).removeIf(h -> h.unwrapKey().map(k -> rm.contains(k.location())).orElse(false));
        } else if (phase == Phase.AFTER_EVERYTHING) {
            var placed = access.registryOrThrow(Registries.PLACED_FEATURE);
            for (Add a : features)
                placed.getHolder(ResourceKey.create(Registries.PLACED_FEATURE, a.feature))
                        .ifPresentOrElse(h -> gen.addFeature(a.step, h), () -> LOG.warn("tcw: нет фичи {}", a.feature));
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}
