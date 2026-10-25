package dev.cld.pregen.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Сжатый снимок поверхности чанка для карты: цвет блока, глубина воды, высота, биомы, структуры. */
public final class ChunkSample {
    public final int x, z;
    public final byte[] color = new byte[256];   // id MapColor верхнего блока
    public final byte[] water = new byte[256];   // глубина воды, 0 = суша
    public final short[] height = new short[256];
    @SuppressWarnings("unchecked")
    public final Holder<Biome>[] biomes = new Holder[16]; // сетка 4x4
    public final String[] structures;

    private ChunkSample(int x, int z, String[] structures) {
        this.x = x; this.z = z; this.structures = structures;
    }

    public static long key(int x, int z) { return ChunkPos.asLong(x, z); }

    public static ChunkSample of(ServerLevel level, LevelChunk chunk) {
        ChunkPos cp = chunk.getPos();
        var structReg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var starts = chunk.getAllStarts();
        String[] names = new String[starts.size()];
        int n = 0;
        for (Structure s : starts.keySet()) {
            ResourceLocation id = structReg.getKey(s);
            names[n++] = id == null ? "?" : id.toString();
        }
        ChunkSample out = new ChunkSample(cp.x, cp.z, names);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = level.getMinBuildHeight();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int i = lz * 16 + lx;
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                pos.set(cp.getMinBlockX() + lx, y, cp.getMinBlockZ() + lz);
                BlockState state = chunk.getBlockState(pos);
                int depth = 0;
                if (!state.getFluidState().isEmpty()) {
                    int yy = y;
                    while (depth < 64 && yy > minY) {
                        pos.setY(--yy);
                        if (chunk.getBlockState(pos).getFluidState().isEmpty()) break;
                        depth++;
                    }
                    pos.setY(y);
                }
                out.color[i] = (byte) state.getMapColor(chunk, pos).id;
                out.water[i] = (byte) Math.min(depth, 127);
                out.height[i] = (short) y;
            }
        }
        for (int qz = 0; qz < 4; qz++) {
            for (int qx = 0; qx < 4; qx++) {
                int y = out.height[(qz * 4 + 2) * 16 + qx * 4 + 2];
                out.biomes[qz * 4 + qx] = chunk.getNoiseBiome(
                        QuartPos.fromBlock(cp.getMinBlockX()) + qx, QuartPos.fromBlock(y), QuartPos.fromBlock(cp.getMinBlockZ()) + qz);
            }
        }
        return out;
    }
}
