package dev.cld.tcw.mixin;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.StateTestingPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(StateTestingPredicate.class)
public interface StateTestingAccessor {
    @Accessor("offset")
    Vec3i tcw$offset();

    @Invoker("test")
    boolean tcw$testState(BlockState state);
}
