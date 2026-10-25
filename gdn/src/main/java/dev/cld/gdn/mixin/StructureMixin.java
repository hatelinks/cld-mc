package dev.cld.gdn.mixin;

import dev.cld.gdn.WorldType;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** В мирах «Горы до небес» ванильные структуры появляются ещё и в биомах из тегов gdn:has_structure/*. */
@Mixin(Structure.class)
public class StructureMixin {
    @Inject(method = "biomes", at = @At("RETURN"), cancellable = true)
    private void gdn$biomes(CallbackInfoReturnable<HolderSet<Biome>> cir) {
        HolderSet<Biome> out = WorldType.structureBiomes((Structure) (Object) this, cir.getReturnValue());
        if (out != cir.getReturnValue()) cir.setReturnValue(out);
    }
}
