package dev.cld.tcw.mixin;

import dev.cld.tcw.Precheck;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.DiskFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Диск (wythers stone_cliffs и похожие) проверяет условие на каждом блоке цилиндра, а условие из датапака
// читает по 4-6 блоков. Сначала смотрим только сам блок: если по нему условие точно ложно, остальное не читаем.
@Mixin(DiskFeature.class)
public abstract class DiskFeatureMixin {
    @Redirect(method = "placeColumn", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/blockpredicates/BlockPredicate;test(Ljava/lang/Object;Ljava/lang/Object;)Z"))
    private boolean tcw$test(BlockPredicate target, Object level, Object pos) {
        return Precheck.test(target, (WorldGenLevel) level, (BlockPos) pos);
    }
}
