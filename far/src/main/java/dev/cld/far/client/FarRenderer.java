package dev.cld.far.client;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.cld.far.FarConfig;
import dev.cld.far.SeedPayload;
import dev.cld.far.gen.LodTree;
import dev.cld.far.gen.TileMesh;
import dev.cld.tcw.gen.Terrain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Тайлы дальнего рельефа: считаются в фоновых потоках по модели генератора, один раз загружаются в видеокарту
 * и рисуются после неба своей проекцией с дальней плоскостью за дальностью. Затем буфер глубины очищается,
 * и обычные чанки рисуются поверх; внутри радиуса прорисовки игры тайлы отбрасываются в шейдере.
 */
final class FarRenderer {
    private static final Logger LOG = LoggerFactory.getLogger("cld_far");
    /** Сколько готовых тайлов загружать в видеокарту за кадр (без рывков). */
    private static final int UPLOADS_PER_FRAME = 4;

    private record Tile(VertexBuffer vb, double x0, double z0) {}
    private record Built(long key, int gen, double x0, double z0, MeshData mesh, ByteBufferBuilder buf) {}

    private final Map<Long, Tile> tiles = new HashMap<>();
    private final Map<Long, Integer> lastUsed = new HashMap<>();
    private final Set<Long> pending = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Built> done = new ConcurrentLinkedQueue<>();
    private volatile Set<Long> wanted = Set.of();
    private volatile int gen;
    private volatile Terrain terrain;
    private TileMesh.Palette palette;
    private ThreadPoolExecutor pool;
    private int poolThreads, step, frame;
    private boolean active;
    private volatile boolean failed;

    boolean active() { return active; }

    /** Новый мир (или выход из мира при null): всё посчитанное сбрасывается. Вызывается в потоке рендера. */
    void setWorld(SeedPayload p) {
        terrain = p == null ? null : new Terrain(p.seed(), p.options());
        reset();
    }

    private void reset() {
        gen++;
        tiles.values().forEach(t -> t.vb.close());
        tiles.clear();
        lastUsed.clear();
        pending.clear();
        for (Built b; (b = done.poll()) != null; ) free(b);
        active = false;
        failed = false;
        palette = null;
    }

    private static void free(Built b) {
        b.mesh.close();
        b.buf.close();
    }

    void render(Minecraft mc, RenderLevelStageEvent e) {
        Terrain t = terrain;
        active = false;
        if (t == null || !FarConfig.ENABLED.get() || failed) return;
        int st = FarConfig.STEP.get();
        if (st != step) {
            step = st;
            reset();
        }
        ensurePool(FarConfig.THREADS.get());
        if (palette == null) {
            TileMesh.Palette p = BiomePalette.create(mc.level.registryAccess());
            palette = p != null ? p : TileMesh.Palette.MODEL;
        }
        TileMesh.Palette pal = palette;
        frame++;
        upload();

        Vec3 cam = e.getCamera().getPosition();
        double dist = FarConfig.DISTANCE.get();
        // игра рисует чанки до getRenderDistance(); край на чанк ближе, чтобы не было щели, пока крайние чанки не загружены
        double clip = Math.max(0, mc.gameRenderer.getRenderDistance() - 16);
        double tile0 = TileMesh.N * step;
        List<Tile> draw = new ArrayList<>();
        Set<Long> want = new HashSet<>();
        int g = gen;
        LodTree.select(cam.x, cam.z, dist, clip, FarConfig.DETAIL.get(), tile0, new LodTree.Sink() {
            @Override
            public boolean ready(long key) {
                if (!tiles.containsKey(key)) return false;
                lastUsed.put(key, frame);
                return true;
            }

            @Override
            public void draw(long key, int level, int tx, int tz) { draw.add(tiles.get(key)); }

            @Override
            public void want(long key, int level, int tx, int tz, double priority) {
                want.add(key);
                if (pending.add(key)) pool.execute(new Job(key, g, t, pal, level, tx, tz, priority, tile0));
            }
        });
        wanted = want;
        if (frame % 120 == 0) evict();
        if (draw.isEmpty()) return;
        active = true;
        draw(mc, e, cam, draw, dist, clip);
    }

    private void draw(Minecraft mc, RenderLevelStageEvent e, Vec3 cam, List<Tile> draw, double dist, double clip) {
        ShaderInstance sh = FarClient.shader;
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        float b = mc.level.getSkyDarken(e.getPartialTick().getGameTimeDeltaPartialTick(false));
        RenderSystem.setShaderFogStart((float) (dist * FarConfig.FOG.get()));
        RenderSystem.setShaderFogEnd((float) dist);
        RenderSystem.setShaderColor(b, b, b, 1);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();

        Matrix4f proj = withDepth(e.getProjectionMatrix(), (float) Math.max(4, clip * 0.3), (float) (dist * 1.5 + 1024));
        sh.setDefaultUniforms(VertexFormat.Mode.QUADS, e.getModelViewMatrix(), proj, mc.getWindow());
        Uniform clipU = sh.getUniform("ClipRadius");
        if (clipU != null) clipU.set((float) clip);
        sh.apply();
        Uniform off = sh.CHUNK_OFFSET;
        for (Tile t : draw) {
            if (off != null) {
                off.set((float) (t.x0 - cam.x), (float) -cam.y, (float) (t.z0 - cam.z));
                off.upload();
            }
            t.vb.bind();
            t.vb.draw();
        }
        VertexBuffer.unbind();
        sh.clear();

        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        // чанки игры рисуются поверх: они всегда ближе дальнего рельефа
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    /**
     * Та же проекция (поле зрения, покачивание камеры), но с другими ближней и дальней плоскостями.
     * Проекция игры = перспектива × аффинное покачивание B, поэтому строка глубины пересчитывается через строку w.
     */
    static Matrix4f withDepth(Matrix4f p, float n, float f) {
        Matrix4f m = new Matrix4f(p);
        float a = -(f + n) / (f - n), b = -2 * f * n / (f - n);
        m.m02(-a * p.m03());
        m.m12(-a * p.m13());
        m.m22(-a * p.m23());
        m.m32(-a * p.m33() + b);
        return m;
    }

    private void upload() {
        for (int i = 0; i < UPLOADS_PER_FRAME; i++) {
            Built b = done.poll();
            if (b == null) return;
            pending.remove(b.key);
            if (b.gen != gen || tiles.containsKey(b.key)) {
                free(b);
                continue;
            }
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind();
            vb.upload(b.mesh); // закрывает MeshData
            VertexBuffer.unbind();
            b.buf.close();
            tiles.put(b.key, new Tile(vb, b.x0, b.z0));
            lastUsed.put(b.key, frame);
        }
    }

    /** Выгрузить тайлы, которые давно не нужны ни для рисования, ни как запасные. */
    private void evict() {
        Set<Long> w = wanted;
        for (Iterator<Map.Entry<Long, Tile>> it = tiles.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Tile> en = it.next();
            Integer used = lastUsed.get(en.getKey());
            if ((used == null || frame - used > 600) && !w.contains(en.getKey())) {
                en.getValue().vb.close();
                lastUsed.remove(en.getKey());
                it.remove();
            }
        }
    }

    private void ensurePool(int threads) {
        if (pool != null && poolThreads == threads) return;
        if (pool != null) pool.shutdownNow();
        pending.clear();
        poolThreads = threads;
        int[] n = { 0 };
        pool = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, new PriorityBlockingQueue<>(), r -> {
            Thread th = new Thread(r, "cld_far-" + n[0]++);
            th.setDaemon(true);
            th.setPriority(Thread.MIN_PRIORITY);
            return th;
        });
        pool.allowCoreThreadTimeOut(true);
    }

    private final class Job implements Runnable, Comparable<Job> {
        final long key;
        final int gen, level, tx, tz;
        final Terrain t;
        final TileMesh.Palette pal;
        final double priority, tile0;

        Job(long key, int gen, Terrain t, TileMesh.Palette pal, int level, int tx, int tz, double priority, double tile0) {
            this.key = key; this.gen = gen; this.t = t; this.pal = pal; this.level = level; this.tx = tx; this.tz = tz; this.priority = priority; this.tile0 = tile0;
        }

        @Override
        public int compareTo(Job o) { return Double.compare(priority, o.priority); }

        private boolean stale() { return gen != FarRenderer.this.gen || !wanted.contains(key) && !wanted.isEmpty(); }

        @Override
        public void run() {
            if (stale()) {
                pending.remove(key);
                return;
            }
            try {
                double size = tile0 * (1L << level), x0 = tx * size, z0 = tz * size;
                TileMesh m = TileMesh.build(t, pal, x0, z0, size / TileMesh.N, this::stale);
                if (m == null) {
                    pending.remove(key);
                    return;
                }
                ByteBufferBuilder buf = new ByteBufferBuilder(m.vertices() * DefaultVertexFormat.POSITION_COLOR.getVertexSize());
                BufferBuilder bb = new BufferBuilder(buf, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                for (int v = 0, nv = m.vertices(); v < nv; v++)
                    bb.addVertex(m.pos[v * 3], m.pos[v * 3 + 1], m.pos[v * 3 + 2]).setColor(m.argb[v]);
                MeshData md = bb.build();
                if (md == null) {
                    buf.close();
                    pending.remove(key);
                    return;
                }
                done.add(new Built(key, gen, x0, z0, md, buf));
            } catch (Throwable ex) {
                pending.remove(key);
                if (!failed) LOG.error("Дальний рельеф: ошибка расчёта тайла, отключаюсь до перезахода", ex);
                failed = true;
            }
        }
    }
}
