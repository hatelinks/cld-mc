package dev.cld.tcw.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.ReplaceBlobsFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Замена пятнами (wythers кладёт по 400 штук на чанк) ищет нужный блок, спускаясь от поверхности до дна мира
// по одному блоку. Секции, в палитре которых этого блока нет, пропускаем целиком. Ответ тот же, что у ванили.
@Mixin(ReplaceBlobsFeature.class)
public abstract class ReplaceBlobsMixin {
    @Inject(method = "findTarget", at = @At("HEAD"), cancellable = true, require = 0)
    private static void tcw$fastFind(LevelAccessor level, BlockPos.MutableBlockPos pos, Block block,
                                     CallbackInfoReturnable<BlockPos> cir) {
        int stop = level.getMinBuildHeight() + 1;
        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return;
        ChunkAccess chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        while (pos.getY() > stop) {
            int sy = SectionPos.blockToSectionCoord(pos.getY());
            int idx = chunk.getSectionIndexFromSectionY(sy);
            if (idx >= 0 && idx < chunk.getSectionsCount()) {
                LevelChunkSection s = chunk.getSection(idx);
                if (s.hasOnlyAir() ? !block.defaultBlockState().isAir() : !s.maybeHas(st -> st.is(block))) {
                    pos.setY(Math.max(SectionPos.sectionToBlockCoord(sy) - 1, stop));
                    continue;
                }
            }
            if (level.getBlockState(pos).is(block)) {
                cir.setReturnValue(pos);
                return;
            }
            pos.move(0, -1, 0);
        }
        cir.setReturnValue(null);
    }
}
