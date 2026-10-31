package dev.cld.panel.mixin;

import dev.cld.panel.Cheats;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Jesus: вода и лава под игроком твёрдые, как целый блок. Игрок стоит над жидкостью, а не в ней,
// поэтому нет плавания и подбрасывания. Работает только для блока, который целиком ниже ног
@Mixin(LiquidBlock.class)
public class LiquidBlockMixin {
    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void cld$solid(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx, CallbackInfoReturnable<VoxelShape> cir) {
        if (ctx instanceof EntityCollisionContext ec && Cheats.jesusStands(ec.getEntity()) && ec.isAbove(Shapes.block(), pos, true)) {
            cir.setReturnValue(Shapes.block());
        }
    }
}
