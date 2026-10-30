package dev.cld.tcw.mixin;

import dev.cld.tcw.OreSkip;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Форма облака руды уже посчитана (все случайные числа взяты), дальше только проверка блоков.
// Если заменить нечего, сразу возвращаем false, как и вернула бы ваниль.
@Mixin(OreFeature.class)
public abstract class OreFeatureMixin {
    @Inject(method = "doPlace", require = 0, cancellable = true,
            at = @At(value = "NEW", target = "net/minecraft/world/level/chunk/BulkSectionAccess"))
    private void tcw$skipEmpty(WorldGenLevel level, RandomSource random, OreConfiguration cfg,
                               double x1, double x2, double z1, double z2, double y1, double y2,
                               int x, int y, int z, int w, int h, CallbackInfoReturnable<Boolean> cir) {
        if (OreSkip.nothingToReplace(level, cfg, x1, x2, z1, z2, y1, y2)) cir.setReturnValue(false);
    }
}
