package dev.cld.lumber.mixin;

import com.simibubi.create.content.kinetics.saw.TreeCutter;
import dev.cld.lumber.Trees;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Пила (и на месте, и на контрапции) зовёт TreeCutter.findTree после спила блока
@Mixin(value = TreeCutter.class, remap = false)
public abstract class TreeCutterMixin {
    @Inject(method = "findTree", at = @At("HEAD"), cancellable = true)
    private static void cld$findTree(BlockGetter reader, BlockPos pos, BlockState broken, CallbackInfoReturnable<TreeCutter.Tree> cir) {
        TreeCutter.Tree tree = Trees.find(reader, pos, broken);
        if (tree != null) cir.setReturnValue(tree);
    }

    // пила должна брать и брёвна модов без тега minecraft:logs
    @Inject(method = "isLog", at = @At("RETURN"), cancellable = true)
    private static void cld$isLog(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && Trees.extraLog(state)) cir.setReturnValue(true);
    }
}
