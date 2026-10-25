package dev.cld.tcw;

import dev.cld.tcw.gen.Macro;
import dev.cld.tcw.gen.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Точка появления в новом мире TelipailoCraft: зелёная долина среди гор, у реки или озера, не на склоне. */
public final class Spawn {
    private Spawn() {}

    static void onCreateSpawn(LevelEvent.CreateSpawnPosition e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (!level.dimensionTypeRegistration().unwrapKey().map(k -> Worlds.isTcw(k.location())).orElse(false)) return;
        Terrain t = Worlds.terrain(level);
        BlockPos p = find(t);
        if (p == null) return;
        // чанк точки появления сгенерируется при загрузке; высоту берём из модели рельефа
        e.getSettings().setSpawn(p, 0.0f);
        e.setCanceled(true);
    }

    /** Лучшая долина в радиусе 6000 блоков от центра мира. */
    public static BlockPos find(Terrain t) {
        Terrain.Column c = new Terrain.Column(), q = new Terrain.Column();
        double best = -1e9;
        int bx = 0, bz = 0, by = 0;
        for (int r = 0; r <= 6000; r += 128) {
            int n = Math.max(1, r / 128);
            for (int i = -n; i <= n; i++)
                for (int j = -n; j <= n; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != n) continue;
                    int x = i * 128, z = j * 128;
                    t.column(x, z, c);
                    double elev = c.height - Macro.SEA;
                    if (c.waterKind != Terrain.WATER_NONE || elev < 4 || elev > 140 || c.slope > 0.18 || c.temp < -0.35) continue;
                    // горы вокруг: самая высокая точка кольца 400–900 блоков
                    double around = 0;
                    for (int a = 0; a < 8; a++) {
                        double ang = a * Math.PI / 4;
                        for (int d = 400; d <= 900; d += 250) {
                            t.column(x + Math.cos(ang) * d, z + Math.sin(ang) * d, q);
                            around = Math.max(around, q.height - c.height);
                        }
                    }
                    double score = Math.min(around, 300) - 0.02 * Math.hypot(x, z) - 300 * c.slope
                            + (c.riverDist < 80 ? 40 : 0) - Math.abs(c.temp - 0.1) * 60;
                    if (score > best) { best = score; bx = x; bz = z; by = (int) Math.ceil(c.height) + 1; }
                }
            if (best > 220 && r > 1500) break;
        }
        if (best == -1e9) return null;
        // ровное место поблизости с точностью до блока
        double bs = 1e9;
        int fx = bx, fz = bz, fy = by;
        for (int i = -6; i <= 6; i++)
            for (int j = -6; j <= 6; j++) {
                t.column(bx + i * 8, bz + j * 8, c);
                if (c.waterKind != Terrain.WATER_NONE || c.riverDist < 8) continue;
                double s = c.slope * 100 + Math.hypot(i, j);
                if (s < bs) { bs = s; fx = bx + i * 8; fz = bz + j * 8; fy = (int) Math.ceil(c.height) + 1; }
            }
        return new BlockPos(fx, fy, fz);
    }
}
