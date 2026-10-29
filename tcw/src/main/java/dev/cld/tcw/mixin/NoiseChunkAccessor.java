package dev.cld.tcw.mixin;

import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NoiseChunk.class)
public interface NoiseChunkAccessor {
    @Accessor("beardifier")
    DensityFunctions.BeardifierOrMarker tcw$beardifier();
}
