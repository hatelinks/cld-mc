package dev.cld.gdn.mixin;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** При создании нового мира сразу выбран тип «Горы до небес 1024». */
@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationUiStateMixin {
    private static final ResourceKey<WorldPreset> GDN_DEFAULT =
            ResourceKey.create(Registries.WORLD_PRESET, ResourceLocation.fromNamespaceAndPath("gdn", "h1024"));

    @Inject(method = "<init>", at = @At("TAIL"))
    private void gdn$defaultPreset(Path saves, WorldCreationContext ctx, Optional<ResourceKey<WorldPreset>> preset,
                                   OptionalLong seed, CallbackInfo ci) {
        if (!preset.map(WorldPresets.NORMAL::equals).orElse(false)) return;
        WorldCreationUiState self = (WorldCreationUiState) (Object) this;
        ctx.worldgenLoadContext().registryOrThrow(Registries.WORLD_PRESET).getHolder(GDN_DEFAULT)
                .ifPresent(h -> self.setWorldType(new WorldCreationUiState.WorldTypeEntry(h)));
    }
}
