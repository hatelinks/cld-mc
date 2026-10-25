package dev.cld.tcw;

import dev.cld.tcw.gen.Terrain;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Генераторы по сиду и общий кэш столбцов. */
public final class Worlds {
    private static volatile Terrain last;

    private Worlds() {}

    public static Terrain terrain(long seed) {
        Terrain t = last;
        if (t != null && t.seed() == seed) return t;
        synchronized (Worlds.class) {
            t = last;
            if (t == null || t.seed() != seed) last = t = new Terrain(seed);
            return t;
        }
    }

    public static final ResourceKey<net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters> SEED_NOISE =
            ResourceKey.create(Registries.NOISE, ResourceLocation.fromNamespaceAndPath(TcwMod.NS, "seed"));

    /** Сид генератора из шума tcw:seed, созданного RandomState от сида мира. */
    public static long seedFrom(net.minecraft.world.level.levelgen.synth.NormalNoise n) {
        long a = Double.doubleToLongBits(n.getValue(1234.5, 17.25, -4321.75));
        long b = Double.doubleToLongBits(n.getValue(-987.125, 3.5, 6543.25));
        return a * 0x9E3779B97F4A7C15L ^ Long.rotateLeft(b, 29);
    }

    /** Генератор для мира, в котором идёт декорация (тот же, что у функций плотности). */
    public static Terrain terrain(net.minecraft.world.level.WorldGenLevel level) {
        var rs = level.getLevel().getChunkSource().randomState();
        return terrain(seedFrom(rs.getOrCreateNoise(SEED_NOISE)));
    }

    public static Terrain terrain(net.minecraft.server.level.ServerLevel level) {
        return terrain(seedFrom(level.getChunkSource().randomState().getOrCreateNoise(SEED_NOISE)));
    }

    private static final int CACHE = 1024;

    private static final class Cache {
        final long[] keys = new long[CACHE];
        final Terrain[] owners = new Terrain[CACHE];
        final Terrain.Column[] cols = new Terrain.Column[CACHE];
        { java.util.Arrays.fill(keys, Long.MIN_VALUE); }
    }

    private static final ThreadLocal<Cache> CACHES = ThreadLocal.withInitial(Cache::new);

    /** Столбец рельефа (кэш на поток, прямое отображение). */
    public static Terrain.Column column(Terrain t, int x, int z) {
        Cache c = CACHES.get();
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        int slot = (int) (mix(key) & (CACHE - 1));
        if (c.keys[slot] == key && c.owners[slot] == t) return c.cols[slot];
        Terrain.Column col = c.cols[slot];
        if (col == null) c.cols[slot] = col = new Terrain.Column();
        t.column(x, z, col);
        c.keys[slot] = key;
        c.owners[slot] = t;
        return col;
    }

    private static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h;
    }

    public static Optional<ResourceLocation> overworldType(RegistryAccess access) {
        return access.registry(Registries.LEVEL_STEM)
                .map(r -> r.get(LevelStem.OVERWORLD))
                .flatMap(stem -> stem.type().unwrapKey())
                .map(ResourceKey::location);
    }

    public static boolean isTcw(ResourceLocation type) {
        return type != null && TcwMod.NS.equals(type.getNamespace());
    }

    public static boolean currentIsTcw() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && overworldType(server.registryAccess()).map(Worlds::isTcw).orElse(false);
    }
}
