package dev.cld.tcw.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Водоносный слой (ваниль 1.21.1) помечает на дообработку почти всю воду океана: при загрузке чанка
// каждая такая вода тикает на главном потоке сервера, а в глубоком океане tcw это ~15 тысяч блоков на чанк.
// Источник, у которого снизу и по бокам такие же источники или сплошные блоки, никуда не течёт и не
// меняется, поэтому его тик пропускаем. Остальные тикают как в ванили. Работает во всех мирах.
@Mixin(LevelChunk.class)
public abstract class FluidPostProcessMixin {
    @Redirect(method = "postProcessGeneration", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void tcw$skipStillSource(FluidState fs, Level level, BlockPos pos) {
        if (fs.isSource() && isStill((LevelChunk) (Object) this, fs, pos)) return;
        fs.tick(level, pos);
    }

    @Unique
    private static boolean isStill(LevelChunk chunk, FluidState fs, BlockPos pos) {
        if (!(chunk.getBlockState(pos).getBlock() instanceof LiquidBlock)) return false;
        if (pos.getY() <= chunk.getMinBuildHeight()) return false;
        return calm(chunk, fs, pos.below()) && calm(chunk, fs, pos.north()) && calm(chunk, fs, pos.south())
                && calm(chunk, fs, pos.east()) && calm(chunk, fs, pos.west());
    }

    // Сосед, куда вода не потечёт: такой же источник или сплошной блок без места для жидкости.
    // Сосед из другого чанка смотрим, только если тот уже загружен, иначе считаем, что тикать надо.
    @Unique
    private static boolean calm(LevelChunk chunk, FluidState fs, BlockPos p) {
        LevelChunk c = chunk;
        int cx = p.getX() >> 4, cz = p.getZ() >> 4;
        if (cx != chunk.getPos().x || cz != chunk.getPos().z) {
            c = chunk.getLevel().getChunkSource().getChunkNow(cx, cz);
            if (c == null) return false;
        }
        BlockState s = c.getBlockState(p);
        FluidState n = s.getFluidState();
        if (n.isEmpty()) return !(s.getBlock() instanceof LiquidBlockContainer) && s.blocksMotion();
        return n.isSource() && n.getType().isSame(fs.getType());
    }
}
