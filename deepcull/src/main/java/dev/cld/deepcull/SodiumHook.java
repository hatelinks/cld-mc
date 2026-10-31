package dev.cld.deepcull;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;

// Отдельный класс, чтобы без Sodium его классы не грузились.
final class SodiumHook {
    private SodiumHook() {}

    static void scheduleUpdate() {
        SodiumWorldRenderer r = SodiumWorldRenderer.instanceNullable();
        if (r != null) r.scheduleTerrainUpdate();
    }
}
