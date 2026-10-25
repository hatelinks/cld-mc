package dev.cld.tcw;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(TcwMod.NS)
public final class TcwMod {
    public static final String NS = "tcw";
    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, NS);
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, NS);
    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, NS);

    private static final DeferredRegister<MapCodec<? extends net.minecraft.world.level.biome.BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, NS);

    static {
        BIOME_SOURCES.register("telipailo", () -> TcwBiomeSource.CODEC);
        DENSITY.register("column", () -> ColumnDensity.CODEC.codec());
        FEATURES.register("water", () -> new WaterFeature(NoneFeatureConfiguration.CODEC));
        FEATURES.register("rock", () -> new RockFeature(NoneFeatureConfiguration.CODEC));
        FEATURES.register("caves", () -> new CaveFeature(NoneFeatureConfiguration.CODEC));
        FEATURES.register("snow", () -> new SnowFeature(NoneFeatureConfiguration.CODEC));
        MODIFIERS.register("world", () -> WorldModifier.CODEC);
    }

    public TcwMod(IEventBus modBus) {
        DENSITY.register(modBus);
        FEATURES.register(modBus);
        MODIFIERS.register(modBus);
        BIOME_SOURCES.register(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Spawn::onCreateSpawn);
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) dev.cld.tcw.client.ClientSetup.init(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(LocateCommand::register);
    }
}
