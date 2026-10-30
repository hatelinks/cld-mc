package dev.cld.far.client;

import com.mojang.blaze3d.platform.GlStateManager;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Маска чанков вокруг игрока, текстура по клетке на чанк: бит 1, игра этот чанк уже нарисовала (дальний рельеф там
// не нужен), бит 2, тут готов блочный тайл (грубый рельеф под ним не рисуется). Раньше дальний рельеф отрезался
// кругом по расстоянию в 3D: у чанков квадратный край, поэтому были щели и наложения, а высокие скалы рядом
// (дальше круга по высоте) рисовались грубыми коробками поверх настоящих.
final class ChunkMask {
    private static final Logger LOG = LoggerFactory.getLogger("cld_far");
    static final int CHUNK = 1, BLOCKY = 2;

    // Sodium рисует секцию, только если она ближе прорисовки и по горизонтали, и по высоте.
    private static final MethodHandle SODIUM_INSTANCE, SODIUM_READY;
    static {
        MethodHandle inst = null, ready = null;
        try {
            Class<?> c = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            inst = l.findStatic(c, "instanceNullable", MethodType.methodType(c));
            ready = l.findVirtual(c, "isSectionReady", MethodType.methodType(boolean.class, int.class, int.class, int.class));
        } catch (Throwable e) {
            // Sodium нет: чанк считается нарисованным, если он загружен и в прорисовке
        }
        SODIUM_INSTANCE = inst;
        SODIUM_READY = ready;
    }

    static boolean sodium() { return SODIUM_READY != null; }

    private int tex = -1, texSize;
    private byte[] cells = new byte[0], uploaded = new byte[0];
    private ByteBuffer buf;
    int size, originX, originZ; // клетки, угол маски в чанках
    private boolean sodiumFailed;

    // Пересобрать маску вокруг камеры: half, полуразмер в чанках, rd, прорисовка игры в блоках.
    void begin(double camX, double camY, double camZ, int half, double rd) {
        size = half * 2 + 1;
        originX = (int) Math.floor(camX / 16) - half;
        originZ = (int) Math.floor(camZ / 16) - half;
        if (cells.length != size * size) cells = new byte[size * size];
        else java.util.Arrays.fill(cells, (byte) 0);
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Object sod = null;
        if (SODIUM_READY != null && !sodiumFailed) {
            try {
                sod = SODIUM_INSTANCE.invoke();
            } catch (Throwable e) {
                sodiumFailed = true;
            }
        }
        int camSec = (int) Math.floor(camY / 16), vr = Math.max(1, (int) (rd / 16) - 1);
        int minSec = level.getMinSection(), maxSec = level.getMaxSection() - 1;
        // только чанки, которые игра рисует наверняка: центр чанка на чанк ближе края прорисовки
        double lim = rd - 8;
        int r = (int) Math.ceil(rd / 16) + 1;
        int ccx = (int) Math.floor(camX / 16), ccz = (int) Math.floor(camZ / 16);
        for (int dz = -r; dz <= r; dz++)
            for (int dx = -r; dx <= r; dx++) {
                int cx = ccx + dx, cz = ccz + dz;
                double px = cx * 16 + 8 - camX, pz = cz * 16 + 8 - camZ;
                if (px * px + pz * pz > lim * lim) continue;
                int ix = cx - originX, iz = cz - originZ;
                if (ix < 0 || iz < 0 || ix >= size || iz >= size) continue;
                if (!level.getChunkSource().hasChunk(cx, cz)) continue;
                if (sod != null) {
                    int sy = (level.getChunk(cx, cz).getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8) - 1) >> 4;
                    sy = Math.max(Math.max(minSec, camSec - vr), Math.min(Math.min(maxSec, camSec + vr), sy));
                    try {
                        if (!(boolean) SODIUM_READY.invoke(sod, cx, sy, cz)) continue;
                    } catch (Throwable e) {
                        if (!sodiumFailed) LOG.warn("Дальний рельеф: Sodium не ответил про секции, маска по загруженным чанкам", e);
                        sodiumFailed = true;
                        sod = null;
                    }
                }
                cells[iz * size + ix] |= CHUNK;
            }
    }

    // Отметить блочный тайл: x0, z0 и сторона в блоках (кратны 16).
    void markBlocky(double x0, double z0, double side) {
        int ax = (int) Math.floor(x0 / 16) - originX, az = (int) Math.floor(z0 / 16) - originZ, n = (int) Math.round(side / 16);
        for (int j = Math.max(0, az); j < Math.min(size, az + n); j++)
            for (int i = Math.max(0, ax); i < Math.min(size, ax + n); i++) cells[j * size + i] |= BLOCKY;
    }

    // Загрузить в видеокарту, если что-то поменялось. Возвращает номер текстуры.
    int upload() {
        boolean resize = tex < 0 || texSize != size;
        if (!resize && java.util.Arrays.equals(cells, uploaded)) return tex;
        if (tex < 0) tex = GlStateManager._genTexture();
        GlStateManager._bindTexture(tex);
        if (buf == null || buf.capacity() < cells.length) {
            if (buf != null) MemoryUtil.memFree(buf);
            buf = MemoryUtil.memAlloc(cells.length);
        }
        buf.clear();
        buf.put(cells).flip();
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 1);
        if (resize) {
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, size, size, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, buf);
            texSize = size;
        } else {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, size, size, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, buf);
        }
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        GlStateManager._bindTexture(0);
        uploaded = cells.clone();
        return tex;
    }

    void close() {
        if (tex >= 0) GlStateManager._deleteTexture(tex);
        tex = -1;
        if (buf != null) MemoryUtil.memFree(buf);
        buf = null;
        uploaded = new byte[0];
    }
}
