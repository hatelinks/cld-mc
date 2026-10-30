package dev.cld.tcw.mixin;

import java.util.List;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.blockpredicates.CombiningPredicate")
public interface CombiningAccessor {
    @Accessor("predicates")
    List<BlockPredicate> tcw$predicates();
}
