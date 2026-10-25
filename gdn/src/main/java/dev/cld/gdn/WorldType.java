package dev.cld.gdn;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Какой тип мира у запущенного сервера: определяется по типу измерения Верхнего мира. */
public final class WorldType {
    /** Структура -> биомы с добавками jjtm (gdn:has_structure/*), только в мирах «Горы до небес». */
    private static volatile Map<Structure, HolderSet<Biome>> structureBiomes = Map.of();

    private WorldType() {}

    public static Optional<ResourceLocation> overworldType(RegistryAccess access) {
        return access.registry(Registries.LEVEL_STEM)
                .map(r -> r.get(LevelStem.OVERWORLD))
                .flatMap(stem -> stem.type().unwrapKey())
                .map(ResourceKey::location);
    }

    public static Optional<ResourceLocation> currentOverworldType() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? Optional.empty() : overworldType(server.registryAccess());
    }

    public static boolean isGdn(ResourceLocation type) {
        return type != null && GdnMod.NS.equals(type.getNamespace());
    }

    public static HolderSet<Biome> structureBiomes(Structure s, HolderSet<Biome> original) {
        Map<Structure, HolderSet<Biome>> m = structureBiomes;
        return m.isEmpty() ? original : m.getOrDefault(s, original);
    }

    static void onServerAboutToStart(ServerAboutToStartEvent e) {
        structureBiomes = Map.of();
        RegistryAccess access = e.getServer().registryAccess();
        if (!overworldType(access).map(WorldType::isGdn).orElse(false)) return;
        Registry<Biome> biomes = access.registryOrThrow(Registries.BIOME);
        Map<Structure, HolderSet<Biome>> map = new IdentityHashMap<>();
        for (Structure s : access.registryOrThrow(Registries.STRUCTURE)) {
            HolderSet<Biome> set = s.biomes();
            Optional<TagKey<Biome>> tag = set.unwrapKey();
            if (tag.isEmpty() || !tag.get().location().getPath().startsWith("has_structure/")) continue;
            TagKey<Biome> extra = TagKey.create(Registries.BIOME,
                    ResourceLocation.fromNamespaceAndPath(GdnMod.NS, tag.get().location().getPath()));
            Optional<HolderSet.Named<Biome>> add = biomes.getTag(extra);
            if (add.isEmpty()) continue;
            LinkedHashSet<Holder<Biome>> all = new LinkedHashSet<>();
            set.forEach(all::add);
            add.get().forEach(all::add);
            if (all.size() == set.size()) continue;
            List<Holder<Biome>> list = new ArrayList<>(all);
            map.put(s, HolderSet.direct(list));
        }
        structureBiomes = map;
    }

    static void onServerStopped(ServerStoppedEvent e) {
        structureBiomes = Map.of();
    }
}
