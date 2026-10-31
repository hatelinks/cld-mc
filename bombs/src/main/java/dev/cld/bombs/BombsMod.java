package dev.cld.bombs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

// Бомбы с уровнями: дополнение к бомбам Supplementaries, сам мод не меняется.
@Mod(BombsMod.NS)
public final class BombsMod {
    public static final String NS = "cld_bombs";

    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, NS);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, NS);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, NS);
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT = DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, NS);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> LEVEL = COMPONENTS.registerComponentType("level",
            b -> b.persistent(Codec.intRange(1, 10)).networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final Supplier<AttachmentType<Integer>> ENTITY_LEVEL = ATTACHMENTS.register("level",
            () -> AttachmentType.builder(() -> 1).serialize(Codec.INT).build());
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BombUpgradeRecipe>> UPGRADE = RECIPES.register("bomb_upgrade",
            () -> new SimpleCraftingRecipeSerializer<>(BombUpgradeRecipe::new));

    public BombsMod(IEventBus modBus, ModContainer container) {
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        RECIPES.register(modBus);
        LOOT.register("bomb_levels", () -> LootLevels.CODEC);
        LOOT.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, Cfg.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.COMMON, Cfg.COMMON_SPEC);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> Blast.tickAll());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e) -> Blast.clear());
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            dev.cld.bombs.client.BombsClient.init(container);
        }
    }
}
