package dev.cld.structures.mixin;

import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkGeneratorStructureState.class)
public interface StructureStateAccessor {
    @Accessor("biomeSource")
    BiomeSource cld$biomeSource();
}
