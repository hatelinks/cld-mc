package dev.cld.structures.mixin;

import dev.cld.structures.Overlap;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** createStructures перебирает наборы структур в лямбде; до генерации набора проверяем соседство с более редкими. */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Inject(method = "lambda$createStructures$14", at = @At("HEAD"), cancellable = true)
    private void cld$skipOverlapping(StructureManager structureManager, SectionPos section, ChunkAccess chunk,
                                     ChunkGeneratorStructureState state, ChunkPos pos, RegistryAccess registries,
                                     RandomState random, StructureTemplateManager templates, Holder<StructureSet> set,
                                     CallbackInfo ci) {
        if (Overlap.blocked(set, state, pos.x, pos.z)) ci.cancel();
    }
}
