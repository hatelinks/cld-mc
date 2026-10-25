package dev.cld.structures;

import com.mojang.logging.LogUtils;
import dev.cld.structures.mixin.StructureStateAccessor;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Обобщённая «зона исключения» для всех наборов структур сразу.
 * Набор пропускает чанк, если рядом может появиться структура из более редкого набора (больше spacing)
 * того же этапа генерации (поверхность с поверхностью, подземные с подземными) и её биом подходит.
 * Решение зависит только от сида и координат, поэтому не зависит от порядка генерации чанков.
 */
public final class Overlap {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int[] BIOME_Y = {-32, 32, 64, 128, 256};

    private static int maxRadius = 8;
    private static double sizeFactor = 0.6;
    private static Set<String> ignored = Set.of();

    private record Info(String id, long priority, EnumSet<GenerationStep.Decoration> steps, int radius, boolean ignored) {}

    private static final Map<StructureSet, Info> INFO = Collections.synchronizedMap(new IdentityHashMap<>());

    public static boolean blocked(Holder<StructureSet> holder, ChunkGeneratorStructureState state, int cx, int cz) {
        StructureSet self = holder.value();
        Info me = info(holder);
        if (me.ignored || !self.placement().isStructureChunk(state, cx, cz)) return false;
        for (Holder<StructureSet> oh : state.possibleStructureSets()) {
            StructureSet other = oh.value();
            if (other == self) continue;
            Info o = info(oh);
            if (o.ignored || !higher(o, me) || Collections.disjoint(o.steps, me.steps)) continue;
            int r = Math.min(maxRadius, me.radius + o.radius);
            if (hasValidStart(other, state, cx, cz, r)) return true;
        }
        return false;
    }

    private static boolean higher(Info a, Info b) {
        return a.priority != b.priority ? a.priority > b.priority : a.id.compareTo(b.id) > 0;
    }

    private static boolean hasValidStart(StructureSet set, ChunkGeneratorStructureState state, int cx, int cz, int r) {
        StructurePlacement p = set.placement();
        if (p instanceof RandomSpreadStructurePlacement rs) {
            int s = rs.spacing();
            long seed = state.getLevelSeed();
            for (int rx = Math.floorDiv(cx - r, s); rx <= Math.floorDiv(cx + r, s); rx++)
                for (int rz = Math.floorDiv(cz - r, s); rz <= Math.floorDiv(cz + r, s); rz++) {
                    ChunkPos c = rs.getPotentialStructureChunk(seed, rx * s, rz * s);
                    if (Math.abs(c.x - cx) <= r && Math.abs(c.z - cz) <= r && valid(set, state, c.x, c.z)) return true;
                }
            return false;
        }
        if (p instanceof ConcentricRingsStructurePlacement rings) {
            List<ChunkPos> list = state.getRingPositionsFor(rings);
            if (list == null) return false;
            for (ChunkPos c : list)
                if (Math.abs(c.x - cx) <= r && Math.abs(c.z - cz) <= r && valid(set, state, c.x, c.z)) return true;
            return false;
        }
        for (int x = cx - r; x <= cx + r; x++)
            for (int z = cz - r; z <= cz + r; z++)
                if (valid(set, state, x, z)) return true;
        return false;
    }

    /** Чанк — место старта набора и хотя бы одной его структуре подходит биом (примерно: по нескольким высотам). */
    private static boolean valid(StructureSet set, ChunkGeneratorStructureState state, int x, int z) {
        if (!set.placement().isStructureChunk(state, x, z)) return false;
        BiomeSource source = ((StructureStateAccessor) state).cld$biomeSource();
        var sampler = state.randomState().sampler();
        int qx = QuartPos.fromBlock(x * 16 + 8), qz = QuartPos.fromBlock(z * 16 + 8);
        for (int y : BIOME_Y) {
            Holder<Biome> biome = source.getNoiseBiome(qx, QuartPos.fromBlock(y), qz, sampler);
            for (StructureSet.StructureSelectionEntry e : set.structures())
                if (e.structure().value().biomes().contains(biome)) return true;
        }
        return false;
    }

    private static Info info(Holder<StructureSet> holder) {
        StructureSet set = holder.value();
        Info i = INFO.get(set);
        if (i != null) return i;
        String id = holder.unwrapKey().map(k -> k.location().toString()).orElse(String.valueOf(set.hashCode()));
        StructurePlacement p = set.placement();
        long priority = p instanceof RandomSpreadStructurePlacement rs ? rs.spacing()
                : p instanceof ConcentricRingsStructurePlacement ? Integer.MAX_VALUE : 32;
        EnumSet<GenerationStep.Decoration> steps = EnumSet.noneOf(GenerationStep.Decoration.class);
        int radius = 1;
        for (StructureSet.StructureSelectionEntry e : set.structures()) {
            Structure s = e.structure().value();
            steps.add(s.step());
            radius = Math.max(radius, radius(s));
        }
        i = new Info(id, priority, steps, radius, ignored.contains(id));
        INFO.put(set, i);
        return i;
    }

    /** Примерный радиус структуры в чанках: у пазловых (jigsaw) — по max_distance_from_center, иначе 2. */
    private static int radius(Structure s) {
        for (Class<?> c = s.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("maxDistanceFromCenter");
                if (f.getType() != int.class) break;
                f.setAccessible(true);
                int blocks = f.getInt(s);
                return Math.max(1, Math.min(6, (int) Math.round(blocks / 16.0 * sizeFactor)));
            } catch (NoSuchFieldException ignore) {
            } catch (ReflectiveOperationException | RuntimeException e) {
                break;
            }
        }
        return 2;
    }

    static void loadConfig() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("cld_structures.properties");
        Properties p = new Properties();
        p.setProperty("max_radius_chunks", "8");
        p.setProperty("size_factor", "0.6");
        p.setProperty("ignored_sets", "");
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) { p.load(r); }
            } else {
                try (Writer w = Files.newBufferedWriter(file)) {
                    p.store(w, " cld_structures: max_radius_chunks - наибольшее расстояние проверки (чанки);"
                            + " size_factor - доля max_distance_from_center пазловых структур, считаемая их радиусом;"
                            + " ignored_sets - наборы через запятую, которые не трогаем (например minecraft:mineshafts)");
                }
            }
            maxRadius = Integer.parseInt(p.getProperty("max_radius_chunks").trim());
            sizeFactor = Double.parseDouble(p.getProperty("size_factor").trim());
            Set<String> ign = new HashSet<>();
            for (String s : p.getProperty("ignored_sets").split(",")) if (!s.isBlank()) ign.add(s.trim());
            ignored = Set.copyOf(ign);
        } catch (IOException | RuntimeException e) {
            LOG.warn("cld_structures: не удалось прочитать {}, беру значения по умолчанию", file, e);
        }
    }

    private Overlap() {}
}
