package dev.cld.gdn;

import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod("jjthunder_to_the_max")
public final class GdnMod {
    public static final String NS = "gdn";
    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, NS);

    static {
        MODIFIERS.register("patch", () -> PatchModifier.CODEC);
    }

    public GdnMod(IEventBus modBus) {
        MODIFIERS.register(modBus);
        NeoForge.EVENT_BUS.addListener(WorldType::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(WorldType::onServerStopped);
    }
}
