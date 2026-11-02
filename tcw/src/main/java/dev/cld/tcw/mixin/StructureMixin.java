package dev.cld.tcw.mixin;

import dev.cld.tcw.EndSeaStructures;
import dev.cld.tcw.EndStructures;
import dev.cld.tcw.gen.EndLands;
import dev.cld.tcw.TcwBiomeSource;
import dev.cld.tcw.Worlds;
import dev.cld.tcw.gen.Macro;
import dev.cld.tcw.gen.Terrain;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// TC Islands и наш Край: старт любой структуры проверяется по модели островов (EndSeaStructures, EndStructures). Другие миры не трогает.
@Mixin(Structure.class)
public class StructureMixin {
    // быстрый отказ, пока не считали биомы и части: середина чанка старта над пустотой.
    // Без него /locate морских структур (их в End Sea нет) перебирает всё и вешает сервер
    @Inject(method = "findValidGenerationPoint", at = @At("HEAD"), cancellable = true)
    private void tcw$quickVoid(Structure.GenerationContext ctx, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        if (EndStructures.ours(ctx.chunkGenerator())) {
            int x = ctx.chunkPos().getMiddleBlockX(), z = ctx.chunkPos().getMiddleBlockZ();
            if (!EndLands.vanilla(x, z) && !Worlds.terrain(ctx.randomState()).end.at(x, z).island()) cir.setReturnValue(Optional.empty());
            return;
        }
        if (!(ctx.chunkGenerator().getBiomeSource() instanceof TcwBiomeSource b) || !b.options().endSea()) return;
        // только морские биомы: в End Sea их нет на островах
        if (EndSeaStructures.oceanOnly((Structure) (Object) this)) {
            cir.setReturnValue(Optional.empty());
            return;
        }
        Terrain t = Worlds.terrain(Worlds.seedFrom(ctx.randomState().getOrCreateNoise(Worlds.SEED_NOISE)), b.options());
        int x = ctx.chunkPos().getMiddleBlockX(), z = ctx.chunkPos().getMiddleBlockZ();
        // по крупному рельефу, без рек: полный столбец считает сток всего континента, при поиске это очень долго
        if (t.arch.edge(x, z, t.macro.sample(x, z, new Macro.Sample()).height + 40) <= 0) cir.setReturnValue(Optional.empty());
    }

    @Inject(method = "generate", at = @At("RETURN"), cancellable = true)
    private void tcw$onIsland(RegistryAccess ra, ChunkGenerator gen, BiomeSource bs, RandomState rs, StructureTemplateManager tm, long seed,
                              ChunkPos pos, int refs, LevelHeightAccessor h, Predicate<Holder<Biome>> valid, CallbackInfoReturnable<StructureStart> cir) {
        StructureStart s = cir.getReturnValue();
        if (s != null && s.isValid() && EndStructures.ours(gen)) {
            if (!EndStructures.check(Worlds.terrain(rs).end, s)) cir.setReturnValue(StructureStart.INVALID_START);
            return;
        }
        if (s == null || !s.isValid() || !(gen.getBiomeSource() instanceof TcwBiomeSource b) || !b.options().endSea()) return;
        StructureStart r = EndSeaStructures.check(Worlds.terrain(Worlds.seedFrom(rs.getOrCreateNoise(Worlds.SEED_NOISE)), b.options()), s);
        if (r != s) cir.setReturnValue(r);
    }
}
