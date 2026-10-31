package dev.cld.panel;

import baritone.api.event.events.RenderEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.utils.IRenderer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

// Xray и поиск блоков: рамки сквозь стены вокруг нужных блоков в загруженных чанках.
// С Sodium обычный xray (прятать камень) не работает, поэтому рамки, как в Nodus Search.
public final class BlockEsp implements AbstractGameEventListener {
    public static boolean xray, search;
    private static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores"));
    private static final int MAX_PER_CHUNK = 1500;
    static final int[] SEARCH_COLORS = {0xFFFF60FF, 0xFFFFA040, 0xFF60FFFF, 0xFFFFFF60, 0xFF80FF80, 0xFF8080FF};

    private record Found(long[] pos, int[] color) {}

    private static final Map<Long, Found> found = new HashMap<>();
    private static final ArrayDeque<Long> queue = new ArrayDeque<>();
    private static Map<Block, Integer> targets = Map.of();
    private static boolean dirty = true;
    private static ClientLevel lastLevel;
    private static int count;

    private BlockEsp() {}

    static void register() {
        Panel.baritone().getGameEventHandler().registerEventListener(new BlockEsp());
    }

    // Поменялся список: пересобрать цели и пройти чанки заново
    static void changed() {
        dirty = true;
    }

    static int count() {
        return count;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(xray || search) || mc.player == null || mc.level == null || !Panel.full()) {
            clear();
            return;
        }
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            dirty = true;
        }
        if (dirty) {
            dirty = false;
            targets = build();
            clear();
        }
        if (targets.isEmpty()) return;
        int r = PanelConfig.get().espRadius;
        ChunkPos pc = mc.player.chunkPosition();
        int rc = (r >> 4) + 1;
        if (queue.isEmpty()) {
            // новый проход: ближние чанки первыми, дальние за краем убираем
            List<Long> list = new ArrayList<>();
            for (int dx = -rc; dx <= rc; dx++) for (int dz = -rc; dz <= rc; dz++) list.add(ChunkPos.asLong(pc.x + dx, pc.z + dz));
            list.sort((a, b) -> Integer.compare(dist(a, pc), dist(b, pc)));
            queue.addAll(list);
            found.keySet().removeIf(k -> dist(k, pc) > rc * rc * 2);
        }
        for (int n = 0; n < 4 && !queue.isEmpty(); n++) scan(mc.level, queue.poll(), mc.player.getBlockY(), r);
        int c = 0;
        for (Found f : found.values()) c += f.pos.length;
        count = c;
    }

    private static int dist(long k, ChunkPos pc) {
        int dx = ChunkPos.getX(k) - pc.x, dz = ChunkPos.getZ(k) - pc.z;
        return dx * dx + dz * dz;
    }

    private static void clear() {
        found.clear();
        queue.clear();
        count = 0;
    }

    private static void scan(ClientLevel level, long key, int py, int r) {
        int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
        if (!level.hasChunk(cx, cz)) {
            found.remove(key);
            return;
        }
        LevelChunk ch = level.getChunk(cx, cz);
        Map<Block, Integer> t = targets;
        Predicate<BlockState> want = s -> t.containsKey(s.getBlock());
        LevelChunkSection[] secs = ch.getSections();
        long[] pos = new long[MAX_PER_CHUNK];
        int[] col = new int[MAX_PER_CHUNK];
        int n = 0;
        for (int i = 0; i < secs.length && n < MAX_PER_CHUNK; i++) {
            int sy = ch.getSectionYFromSectionIndex(i);
            if (sy * 16 + 15 < py - r || sy * 16 > py + r) continue;
            LevelChunkSection s = secs[i];
            // палитра секции: если нужного блока в ней нет, 4096 блоков не перебираем
            if (s == null || s.hasOnlyAir() || !s.maybeHas(want)) continue;
            for (int y = 0; y < 16 && n < MAX_PER_CHUNK; y++)
                for (int z = 0; z < 16 && n < MAX_PER_CHUNK; z++)
                    for (int x = 0; x < 16 && n < MAX_PER_CHUNK; x++) {
                        Integer c = t.get(s.getBlockState(x, y, z).getBlock());
                        if (c == null) continue;
                        pos[n] = BlockPos.asLong(cx * 16 + x, sy * 16 + y, cz * 16 + z);
                        col[n++] = c;
                    }
        }
        if (n == 0) found.remove(key);
        else found.put(key, new Found(java.util.Arrays.copyOf(pos, n), java.util.Arrays.copyOf(col, n)));
    }

    private static Map<Block, Integer> build() {
        Map<Block, Integer> m = new HashMap<>();
        if (search) {
            List<String> qs = PanelConfig.get().search;
            for (int i = 0; i < qs.size(); i++) for (Block b : match(qs.get(i))) m.putIfAbsent(b, SEARCH_COLORS[i % SEARCH_COLORS.length]);
        }
        if (xray) for (Block b : BuiltInRegistries.BLOCK) {
            if (!b.defaultBlockState().is(ORES)) continue;
            m.putIfAbsent(b, oreColor(BuiltInRegistries.BLOCK.getKey(b).getPath()));
        }
        return m;
    }

    // Блоки по запросу: полный id, #тег или часть имени (chest найдёт и сундуки других модов)
    static List<Block> match(String q) {
        q = q.trim().toLowerCase(Locale.ROOT);
        List<Block> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        if (q.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(q.substring(1));
            if (id == null) return out;
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, id);
            for (Block b : BuiltInRegistries.BLOCK) if (b.defaultBlockState().is(tag)) out.add(b);
            return out;
        }
        for (Block b : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
            if (q.contains(":") ? id.toString().equals(q) : id.getPath().contains(q)) out.add(b);
        }
        return out;
    }

    private static int oreColor(String n) {
        if (n.contains("diamond")) return 0xFF40FFFF;
        if (n.contains("emerald")) return 0xFF40FF60;
        if (n.contains("gold")) return 0xFFFFD020;
        if (n.contains("iron")) return 0xFFE0B090;
        if (n.contains("redstone")) return 0xFFFF3030;
        if (n.contains("lapis")) return 0xFF3050FF;
        if (n.contains("copper")) return 0xFFFF8040;
        if (n.contains("coal")) return 0xFF505050;
        if (n.contains("debris")) return 0xFFA05030;
        if (n.contains("quartz")) return 0xFFF0F0F0;
        return 0xFFFF60FF;
    }

    @Override
    public void onRenderPass(RenderEvent e) {
        if (found.isEmpty()) return;
        BufferBuilder bb = IRenderer.startLines(Color.WHITE, 0.9f, 2f, true);
        for (Found f : found.values()) {
            for (int i = 0; i < f.pos.length; i++) {
                int c = f.color[i];
                IRenderer.color[0] = (c >> 16 & 255) / 255f;
                IRenderer.color[1] = (c >> 8 & 255) / 255f;
                IRenderer.color[2] = (c & 255) / 255f;
                IRenderer.color[3] = 0.9f;
                BlockPos p = BlockPos.of(f.pos[i]);
                IRenderer.emitAABB(bb, e.getModelViewStack(), new AABB(p), 0.002);
            }
        }
        IRenderer.endLines(bb, true);
    }
}
