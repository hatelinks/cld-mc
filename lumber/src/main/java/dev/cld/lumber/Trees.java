package dev.cld.lumber;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllTags;
import com.simibubi.create.content.kinetics.saw.TreeCutter;
import dev.cld.lumber.core.Fell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

// Связка поиска дерева (core/Fell) с блоками игры и с TreeCutter из Create
public final class Trees {
    private static final Logger LOG = LogUtils.getLogger();
    private static final TagKey<Block> STRIPPED_LOGS = tag("c", "stripped_logs");
    private static final TagKey<Block> STRIPPED_WOODS = tag("c", "stripped_woods");
    private static final TagKey<Block> C_LOGS = tag("c", "logs");
    private static final TagKey<Block> CRIMSON_STEMS = BlockTags.CRIMSON_STEMS;
    private static final TagKey<Block> WARPED_STEMS = BlockTags.WARPED_STEMS;
    // брёвна модов, которые забыли положить в теги: столб с именем ..._log, ..._wood, ..._stem, ..._hyphae
    private static final Pattern LOG_NAME = Pattern.compile("(^|.*_)(log|wood|stem|hyphae)$");
    private static final ConcurrentHashMap<Block, Boolean> NAMED = new ConcurrentHashMap<>();
    private static boolean warned;
    private static final boolean DEBUG = Boolean.getBoolean("cld_lumber.debug");

    private Trees() {
    }

    private static TagKey<Block> tag(String ns, String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ns, path));
    }

    // Дополнение к TreeCutter.isLog: обтёсанные брёвна и древесина из тегов c:, брёвна модов без тегов
    public static boolean extraLog(BlockState s) {
        if (s.isAir()) return false;
        if (s.is(STRIPPED_LOGS) || s.is(STRIPPED_WOODS) || s.is(C_LOGS)) return true;
        return NAMED.computeIfAbsent(s.getBlock(), Trees::looksLikeLog);
    }

    private static boolean looksLikeLog(Block b) {
        if (!(b instanceof RotatedPillarBlock)) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
        return id != null && LOG_NAME.matcher(id.getPath()).matches();
    }

    // Своё дерево вместо поиска Create. null значит отдать обычному TreeCutter
    public static TreeCutter.Tree find(BlockGetter reader, BlockPos pos, BlockState broken) {
        if (reader == null || pos == null || broken == null) return null;
        if (DEBUG) LOG.info("cld_lumber: спил {} у {}", BuiltInRegistries.BLOCK.getKey(broken.getBlock()), pos);
        try {
            // корни мангра тоже: пила у земли часто пилит именно их
            if (!(TreeCutter.isLog(broken) || TreeCutter.isRoot(broken)) || TreeCutter.isVerticalPlant(broken)) return null;
            Fell.Result r = Fell.find(new View(reader), pos.getX(), pos.getY(), pos.getZ());
            if (DEBUG) {
                if (r == null) LOG.info("cld_lumber: {} у {}: рубит Create, {}", BuiltInRegistries.BLOCK.getKey(broken.getBlock()), pos, Fell.lastReason);
                else LOG.info("cld_lumber: {} у {}: брёвен {}, листвы {}, прочего {}", BuiltInRegistries.BLOCK.getKey(broken.getBlock()), pos, r.logs.size(), r.leaves.size(), r.attachments.size());
            }
            if (r == null) return null;
            return new TreeCutter.Tree(toPos(r.logs), toPos(r.leaves), toPos(r.attachments));
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                LOG.error("cld_lumber: ошибка поиска дерева у {}, рубит обычный Create", pos, e);
            }
            return null;
        }
    }

    private static List<BlockPos> toPos(List<Long> list) {
        List<BlockPos> out = new ArrayList<>(list.size());
        for (long p : list) out.add(new BlockPos(Fell.x(p), Fell.y(p), Fell.z(p)));
        return out;
    }

    static int kind(BlockState s) {
        if (s.isAir()) return Fell.AIR;
        if (TreeCutter.isLog(s)) {
            if (s.is(Blocks.MUSHROOM_STEM) || s.is(CRIMSON_STEMS) || s.is(WARPED_STEMS)) return Fell.STEM;
            return Fell.LOG;
        }
        if (TreeCutter.isRoot(s)) return Fell.ROOT;
        if (isLeaf(s)) return persistent(s) ? Fell.KEPT_LEAF : Fell.LEAF;
        if (s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(BlockTags.WART_BLOCKS)) return Fell.CANOPY;
        if (AllTags.AllBlockTags.TREE_ATTACHMENTS.matches(s) || s.is(Blocks.WEEPING_VINES) || s.is(Blocks.WEEPING_VINES_PLANT)) return Fell.ATTACH;
        if (s.canBeReplaced()) return Fell.AIR;
        return Fell.GROUND;
    }

    // как в Create: листва это блок с числовым свойством distance (кроме лесов)
    private static boolean isLeaf(BlockState s) {
        if (s.is(BlockTags.LEAVES)) return true;
        for (Property<?> p : s.getProperties())
            if (p instanceof IntegerProperty && p != BlockStateProperties.STABILITY_DISTANCE && p.getName().equals("distance")) return true;
        return false;
    }

    private static boolean persistent(BlockState s) {
        for (Property<?> p : s.getProperties())
            if (p instanceof BooleanProperty b && p.getName().equals("persistent")) return s.getValue(b);
        return false;
    }

    // Чтение мира без загрузки чанков: незагруженное считается землёй, туда поиск не идёт
    private static final class View implements Fell.World {
        private final BlockGetter getter;
        private final LevelReader reader;
        private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        View(BlockGetter getter) {
            this.getter = getter;
            this.reader = getter instanceof LevelReader r ? r : null;
        }

        @Override
        public int kind(int x, int y, int z) {
            if (reader != null && !reader.hasChunkAt(x, z)) return Fell.GROUND;
            return Trees.kind(getter.getBlockState(m.set(x, y, z)));
        }
    }
}
