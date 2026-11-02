package dev.cld.tcw.mixin;

import com.mojang.datafixers.util.Pair;
import dev.cld.tcw.EndSeaStructures;
import dev.cld.tcw.TcwBiomeSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// End Sea: поиск ближайшей структуры (/locate, карты, глаз Края) не дальше EndSeaStructures.SEARCH блоков.
// Многих структур там нет вовсе, и ванильный поиск на сотню клеток перебирает десятки километров, сервер зависает
@Mixin(ChunkGenerator.class)
public class NearestStructureMixin {
    @Unique private static final ThreadLocal<Boolean> TCW_INNER = ThreadLocal.withInitial(() -> false);

    @Inject(method = "findNearestMapStructure", at = @At("HEAD"), cancellable = true)
    private void tcw$radius(ServerLevel level, HolderSet<Structure> set, BlockPos pos, int radius, boolean skip,
                            CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir) {
        ChunkGenerator self = (ChunkGenerator) (Object) this;
        if (TCW_INNER.get() || !(self.getBiomeSource() instanceof TcwBiomeSource b) || !b.options().endSea()) return;
        int spacing = 1;
        for (Holder<Structure> h : set)
            for (StructurePlacement p : level.getChunkSource().getGeneratorState().getPlacementsForStructure(h))
                if (p instanceof RandomSpreadStructurePlacement r) spacing = Math.max(spacing, r.spacing());
        int want = Math.max(3, EndSeaStructures.SEARCH / (spacing * 16));
        if (radius <= want) return;
        TCW_INNER.set(true);
        try {
            cir.setReturnValue(self.findNearestMapStructure(level, set, pos, want, skip));
        } finally {
            TCW_INNER.set(false);
        }
    }
}
