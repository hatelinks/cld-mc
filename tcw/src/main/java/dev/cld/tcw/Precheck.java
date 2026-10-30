package dev.cld.tcw;

import dev.cld.tcw.mixin.CombiningAccessor;
import dev.cld.tcw.mixin.NotAccessor;
import dev.cld.tcw.mixin.StateTestingAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicateType;
import net.minecraft.world.level.levelgen.blockpredicates.StateTestingPredicate;

// Быстрая проверка условия по одному блоку в самой точке. Из дерева условия берём только то,
// что зависит от этого блока: exact значит, что ответ полный, иначе это необходимое условие.
public final class Precheck {
    private final List<Precheck> parts;
    private final StateTestingAccessor leaf;
    private final int kind; // 0 лист, 1 и, 2 или, 3 не
    final boolean exact;

    private Precheck(int kind, StateTestingAccessor leaf, List<Precheck> parts, boolean exact) {
        this.kind = kind; this.leaf = leaf; this.parts = parts; this.exact = exact;
    }

    private static final Precheck NONE = new Precheck(-1, null, List.of(), false);
    private static final Map<BlockPredicate, Precheck> CACHE = new ConcurrentHashMap<>();

    public static boolean test(BlockPredicate p, WorldGenLevel level, BlockPos pos) {
        Precheck c = CACHE.computeIfAbsent(p, Precheck::of);
        if (c == NONE) return p.test(level, pos);
        boolean ok = c.on(level.getBlockState(pos));
        if (!ok || c.exact) return ok;
        return p.test(level, pos);
    }

    private boolean on(BlockState s) {
        switch (kind) {
            case 0: return leaf.tcw$testState(s);
            case 1: for (Precheck q : parts) if (!q.on(s)) return false; return true;
            case 2: for (Precheck q : parts) if (q.on(s)) return true; return false;
            default: return !parts.get(0).on(s);
        }
    }

    private static Precheck of(BlockPredicate p) {
        try {
            return build(p, 0);
        } catch (Throwable t) {
            return NONE;
        }
    }

    private static Precheck build(BlockPredicate p, int depth) {
        if (depth > 16) return NONE;
        BlockPredicateType<?> t = p.type();
        if (p instanceof StateTestingPredicate) {
            StateTestingAccessor a = (StateTestingAccessor) p;
            return a.tcw$offset().equals(Vec3i.ZERO) ? new Precheck(0, a, List.of(), true) : NONE;
        }
        if (t == BlockPredicateType.ALL_OF || t == BlockPredicateType.ANY_OF) {
            List<Precheck> got = new ArrayList<>();
            boolean all = true;
            for (BlockPredicate q : ((CombiningAccessor) p).tcw$predicates()) {
                Precheck c = build(q, depth + 1);
                if (c == NONE) all = false; else got.add(c);
                all &= c.exact;
            }
            if (t == BlockPredicateType.ALL_OF) return got.isEmpty() ? NONE : new Precheck(1, null, got, all);
            return all && !got.isEmpty() ? new Precheck(2, null, got, true)
                    : allNecessary(got, ((CombiningAccessor) p).tcw$predicates().size()) ? new Precheck(2, null, got, false) : NONE;
        }
        if (t == BlockPredicateType.NOT) {
            Precheck c = build(((NotAccessor) p).tcw$predicate(), depth + 1);
            return c != NONE && c.exact ? new Precheck(3, null, List.of(c), true) : NONE;
        }
        return NONE;
    }

    // Для ветвления или необходимое условие есть, только если оно есть у каждой ветки.
    private static boolean allNecessary(List<Precheck> got, int total) {
        return !got.isEmpty() && got.size() == total;
    }
}
