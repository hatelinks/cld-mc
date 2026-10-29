package dev.cld.tcw.mixin;

import dev.cld.tcw.SkyBound;
import dev.cld.tcw.TcwMod;
import dev.cld.tcw.Worlds;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Мир tcw высотой 768, а рельеф обычно ниже 200: заполняем шумом только ячейки до верха рельефа
// (SkyBound), выше и так был бы воздух. Если другой мод заменил doFill, просто не срабатывает.
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseFillMixin {
    @Shadow @Final private Holder<NoiseGeneratorSettings> settings;

    @Shadow
    protected abstract ChunkAccess doFill(Blender blender, StructureManager sm, RandomState rs, ChunkAccess chunk, int minCellY, int cellCountY);

    @Shadow
    protected abstract NoiseChunk createNoiseChunk(ChunkAccess chunk, StructureManager sm, Blender blender, RandomState rs);

    @Unique private static final ResourceLocation TCW_SETTINGS = ResourceLocation.fromNamespaceAndPath(TcwMod.NS, "telipailo");
    @Unique private static final ThreadLocal<Boolean> TCW_INNER = ThreadLocal.withInitial(() -> false);
    @Unique private static final boolean TCW_OFF = Boolean.getBoolean("tcw.fullFill");

    @Inject(method = "doFill", at = @At("HEAD"), cancellable = true, require = 0)
    private void tcw$skipSky(Blender blender, StructureManager sm, RandomState rs, ChunkAccess chunk, int minCellY, int cellCountY,
                             CallbackInfoReturnable<ChunkAccess> cir) {
        if (TCW_OFF || blender != Blender.empty() || !settings.is(TCW_SETTINGS) || TCW_INNER.get()) return;
        NoiseGeneratorSettings s = settings.value();
        int cellH = s.noiseSettings().getCellHeight();
        NoiseChunk nc = chunk.getOrCreateNoiseChunk(c -> createNoiseChunk(c, sm, blender, rs));
        int n = SkyBound.cells(Worlds.terrain(rs), nc, chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ(),
                minCellY * cellH, cellH, s.seaLevel(), cellCountY);
        if (n >= cellCountY) return;
        TCW_INNER.set(true);
        try {
            cir.setReturnValue(doFill(blender, sm, rs, chunk, minCellY, n));
        } finally {
            TCW_INNER.set(false);
        }
    }
}
