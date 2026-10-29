package dev.cld.tcw.mixin;

import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Подсыпка под структуры считается для каждого блока чанка, хотя влияет только в 12 блоках по высоте
// от частей структур. Запоминаем общий диапазон Y и вне его сразу отдаём 0, как и ваниль.
// Работает во всех мирах: результат не меняется.
@Mixin(Beardifier.class)
public class BeardifierMixin implements dev.cld.tcw.BeardRange {
    @Shadow @Final protected ObjectListIterator<Beardifier.Rigid> pieceIterator;
    @Shadow @Final protected ObjectListIterator<JigsawJunction> junctionIterator;

    @Unique private int tcw$lo = Integer.MAX_VALUE, tcw$hi = Integer.MIN_VALUE;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void tcw$range(CallbackInfo ci) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        while (pieceIterator.hasNext()) {
            Beardifier.Rigid r = pieceIterator.next();
            if (r.terrainAdjustment() == TerrainAdjustment.NONE) continue;
            BoundingBox b = r.box();
            int ground = b.minY() + r.groundLevelDelta();
            lo = Math.min(lo, Math.min(ground, b.minY()) - 13);
            hi = Math.max(hi, Math.max(ground, b.maxY()) + 13);
        }
        pieceIterator.back(Integer.MAX_VALUE);
        while (junctionIterator.hasNext()) {
            int g = junctionIterator.next().getSourceGroundY();
            lo = Math.min(lo, g - 13);
            hi = Math.max(hi, g + 13);
        }
        junctionIterator.back(Integer.MAX_VALUE);
        tcw$lo = lo;
        tcw$hi = hi;
    }

    @Override
    public int tcw$hi() { return tcw$hi; }

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void tcw$skip(DensityFunction.FunctionContext ctx, CallbackInfoReturnable<Double> cir) {
        int y = ctx.blockY();
        if (y < tcw$lo || y > tcw$hi) cir.setReturnValue(0.0);
    }
}
