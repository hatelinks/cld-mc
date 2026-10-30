package dev.cld.tcw.mixin;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockStateMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

public final class RuleTestAccessors {
    @Mixin(TagMatchTest.class)
    public interface Tag {
        @Accessor("tag")
        TagKey<net.minecraft.world.level.block.Block> tcw$tag();
    }

    @Mixin(BlockMatchTest.class)
    public interface Block {
        @Accessor("block")
        net.minecraft.world.level.block.Block tcw$block();
    }

    @Mixin(BlockStateMatchTest.class)
    public interface State {
        @Accessor("blockState")
        BlockState tcw$state();
    }
}
