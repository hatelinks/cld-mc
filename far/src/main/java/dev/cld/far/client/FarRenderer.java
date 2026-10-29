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
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Тайлы дальнего рельефа: считаются в фоновых потоках по модели генератора, один раз загружаются в видеокарту
// и рисуются после неба своей проекцией с дальней плоскостью за дальностью. Затем буфер глубины очищается,
// и обычные чанки рисуются поверх; внутри радиуса прорисовки игры тайлы отбрасываются в шейдере.
final class FarRenderer {
    private static final Logger LOG = LoggerFactory.getLogger("cld_far");
    // Сколько готовых тайлов загружать в видеокарту за кадр (без рывков).
    private static final int UPLOADS_PER_FRAME = 4;

    // water, полупрозрачная вода отдельным буфером (режим clearWater) или null.
    // x1, z1 и y0, y1: границы тайла для отсечения по полю зрения.
    private record Tile(VertexBuffer vb, VertexBuffer water, double x0, double z0, double x1, double z1, float y0, float y1) {
        boolean visible(FrustumIntersection f, Vec3 cam) {
            return f.testAab((float) (x0 - cam.x) - 1, (float) (y0 - cam.y) - 1, (float) (z0 - cam.z) - 1,
                    (float) (x1 - cam.x) + 1, (float) (y1 - cam.y) + 1, (float) (z1 - cam.z) + 1);
        }

        void close() {
            vb.close();
            if (water != null) water.close();
        }
    }
    private record Part(MeshData mesh, ByteBufferBuilder buf) {
        void free() {
            mesh.close();
            buf.close();
        }

        VertexBuffer upload() {
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind();
            vb.upload(mesh); // закрывает MeshData
            VertexBuffer.unbind();
            buf.close();
            return vb;
        }

        // null, если вершин нет.
        static Part of(float[] pos, int[] argb, int vertices) {
            if (vertices == 0) return null;
            ByteBufferBuilder buf = new ByteBufferBuilder(vertices * DefaultVertexFormat.POSITION_COLOR.getVertexSize());
            BufferBuilder bb = new BufferBuilder(buf, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int v = 0; v < vertices; v++) bb.addVertex(pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2]).setColor(argb[v]);
            MeshData md = bb.build();
            if (md == null) {
                buf.close();
                return null;
            }
            return new Part(md, buf);
        }
    }
    private record Built(long key, int gen, double x0, double z0, double size, float y0, float y1, Part ground, Part water) {}

    private final Map<Long, Tile> tiles = new HashMap<>();
    private final Map<Long, Integer> lastUsed = new HashMap<>();
    private final Set<Long> pending = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Built> done = new ConcurrentLinkedQueue<>();
    private volatile Set<Long> wanted = Set.of();
    private volatile int gen;
    private volatile Terrain terrain;
    private TileMesh.Palette palette;
    private ThreadPoolExecutor pool;
    private int poolThreads, step, mountains, pixel, frame;
    private double smooth = -1;
    private boolean clearWater;
    private boolean active;
    private volatile boolean failed;

    boolean active() { return active; }

    // Кадр без дальнего рельефа: туман игры остаётся обычным.
    void idle() { active = false; }

    // Новый мир (или выход из мира при null): всё посчитанное сбрасывается. Вызывается в потоке рендера.
    void setWorld(SeedPayload p) {
        terrain = p == null ? null : new Terrain(p.seed(), p.options());
        reset();
    }

    private void reset() {
        gen++;
        tiles.values().forEach(Tile::close);
        tiles.clear();
        lastUsed.clear();
        pending.clear();
        for (Built b; (b = done.poll()) != null; ) free(b);
        active = false;
        failed = false;
        palette = null;
    }

    private static void free(Built b) {
        b.ground.free();
        if (b.water != null) b.water.free();
    }

    void render(Minecraft mc, RenderLevelStageEvent e) {
        Terrain t = terrain;
        active = false;
        if (t == null || !FarConfig.ENABLED.get() || failed) return;
        int st = FarConfig.STEP.get(), mo = FarConfig.MOUNTAINS.get();
        boolean cw = FarConfig.CLEAR_WATER.get();
        int px = FarConfig.PIXEL.get();
        double sm = FarConfig.SMOOTH.get();
        if (st != step || mo != mountains || cw != clearWater || px != pixel || sm != smooth) {
            step = st;
            mountains = mo;
            clearWater = cw;
            pixel = px;
            smooth = sm;
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
        LodTree.select(cam.x, cam.y, cam.z, dist, clip, FarConfig.DETAIL.get(), tile0, new LodTree.Sink() {
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
                if (pending.add(key)) pool.execute(new Job(key, g, t, pal, level, tx, tz, priority, tile0, mo, cw, sm, px));
            }
        });
        // подзорная труба: блочный рельеф в конусе взгляда, ближние тайлы первыми
        List<Tile> scopeDraw = new ArrayList<>();
        double scopeFar = 0;
        boolean scoping = FarConfig.SCOPE.get() && mc.player != null && mc.player.isScoping();
        if (scoping) scopeFar = scope(cam, e.getCamera().getYRot(), clip, FarConfig.SCOPE_RANGE.get(), scopeDraw, want, g, t, pal);
        wanted = want;
        if (frame % 120 == 0) evict();
        if (draw.isEmpty()) return;
        active = true;
        draw(mc, e, cam, draw, dist, clip, scoping ? scopeDraw : null, scopeFar, e.getCamera().getYRot());
    }

    static final int SCOPE_LEVEL = 63, SCOPE_TILE = 32;
    // Полуугол конуса подзорной трубы: с запасом к её полю зрения, чтобы край конуса не попадал в кадр.
    static final double SCOPE_HALF = Math.toRadians(14);

    // Тайлы 32x32 блока в конусе взгляда от clip до range. Возвращает дальность, до которой все тайлы конуса готовы:
    // ближе неё вместо гладкого рельефа рисуются блоки, дальше, как обычно.
    private double scope(Vec3 cam, float yaw, double clip, double range, List<Tile> out, Set<Long> want, int g, Terrain t, TileMesh.Palette pal) {
        double dx = -Math.sin(Math.toRadians(yaw)), dz = Math.cos(Math.toRadians(yaw));
        double half = SCOPE_TILE * 0.71, ready = range;
        int ax = (int) Math.floor((cam.x - range) / SCOPE_TILE), bx = (int) Math.floor((cam.x + range) / SCOPE_TILE);
        int az = (int) Math.floor((cam.z - range) / SCOPE_TILE), bz = (int) Math.floor((cam.z + range) / SCOPE_TILE);
        for (int tz = az; tz <= bz; tz++)
            for (int tx = ax; tx <= bx; tx++) {
                double vx = (tx + 0.5) * SCOPE_TILE - cam.x, vz = (tz + 0.5) * SCOPE_TILE - cam.z, d = Math.sqrt(vx * vx + vz * vz);
                if (d > range + half || d + half < clip || d < 1) continue;
                if ((vx * dx + vz * dz) / d < Math.cos(SCOPE_HALF + Math.atan(half / d))) continue;
                long key = LodTree.key(SCOPE_LEVEL, tx, tz);
                Tile tile = tiles.get(key);
                if (tile != null) {
                    lastUsed.put(key, frame);
                    out.add(tile);
                } else {
                    ready = Math.min(ready, Math.max(clip, d - half));
                    want.add(key);
                    if (pending.add(key)) pool.execute(new Job(key, g, t, pal, SCOPE_LEVEL, tx, tz, -1e9 + d, SCOPE_TILE, 1, false, 0, 0));
                }
            }
        return ready;
    }

    private void draw(Minecraft mc, RenderLevelStageEvent e, Vec3 cam, List<Tile> draw, double dist, double clip, List<Tile> scope, double scopeFar,
            float yaw) {
        ShaderInstance sh = FarClient.shader;
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        float[] b = light(mc, e.getPartialTick().getGameTimeDeltaPartialTick(false));
        RenderSystem.setShaderFogStart((float) (dist * FarConfig.FOG.get()));
        RenderSystem.setShaderFogEnd((float) dist);
        RenderSystem.setShaderColor(b[0], b[1], b[2], 1);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();

        Matrix4f proj = withDepth(e.getProjectionMatrix(), (float) Math.max(4, clip * 0.3), (float) (dist * 1.5 + 1024));
        sh.setDefaultUniforms(VertexFormat.Mode.QUADS, e.getModelViewMatrix(), proj, mc.getWindow());
        Uniform clipU = sh.getUniform("ClipRadius");
        if (clipU != null) clipU.set((float) clip);
        Uniform mode = sh.getUniform("ScopeMode");
        if (mode != null) {
            mode.set(scope != null ? 1f : 0f);
            sh.getUniform("ScopeDir").set((float) -Math.sin(Math.toRadians(yaw)), (float) Math.cos(Math.toRadians(yaw)));
            sh.getUniform("ScopeCos").set((float) Math.cos(SCOPE_HALF));
            sh.getUniform("ScopeFar").set((float) scopeFar);
        }
        sh.apply();
        Uniform off = sh.CHUNK_OFFSET;
        // тайлы за спиной и сбоку не рисуются: раньше в кадр шли все тайлы вокруг игрока
        FrustumIntersection fr = new FrustumIntersection(new Matrix4f(proj).mul(e.getModelViewMatrix()), false);
        draw.removeIf(t -> !t.visible(fr, cam));
        for (Tile t : draw) {
            if (off != null) {
                off.set((float) (t.x0 - cam.x), (float) -cam.y, (float) (t.z0 - cam.z));
                off.upload();
            }
            t.vb.bind();
            t.vb.draw();
        }
        if (scope != null && !scope.isEmpty() && mode != null) {
            mode.set(2f);
            mode.upload();
            for (Tile t : scope) {
                off.set((float) (t.x0 - cam.x), (float) -cam.y, (float) (t.z0 - cam.z));
                off.upload();
                t.vb.bind();
                t.vb.draw();
            }
            mode.set(1f);
            mode.upload();
        }
        // полупрозрачная вода поверх дна, без записи глубины (как вода в игре)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        for (Tile t : draw) {
            if (t.water == null) continue;
            if (off != null) {
                off.set((float) (t.x0 - cam.x), (float) -cam.y, (float) (t.z0 - cam.z));
                off.upload();
            }
            t.water.bind();
            t.water.draw();
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        VertexBuffer.unbind();
        sh.clear();

        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        // чанки игры рисуются поверх: они всегда ближе дальнего рельефа
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    // Свет как у блоков под открытым небом: повторяет карту освещения игры (LightTexture) при небесном свете 15
    // и без света от блоков. Ночью свет тусклый и синеватый, учитываются молнии, ночное зрение и настройка "Яркость".
    // Раньше бралась одна яркость без синего оттенка, и ночью дальний рельеф выходил заметно светлее чанков.
    static float[] light(Minecraft mc, float partial) {
        float sky = mc.level.getSkyDarken(partial);
        float f1 = mc.level.getSkyFlashTime() > 0 ? 1 : sky * 0.95f + 0.05f;
        float[] c = { (sky + (1 - sky) * 0.35f) * f1, (sky + (1 - sky) * 0.35f) * f1, f1 };
        for (int i = 0; i < 3; i++) c[i] += (0.75f - c[i]) * 0.04f;
        if (mc.player != null && mc.player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)) {
            float nv = net.minecraft.client.renderer.GameRenderer.getNightVisionScale(mc.player, partial);
            float max = Math.max(c[0], Math.max(c[1], c[2]));
            if (max < 1) for (int i = 0; i < 3; i++) c[i] += (c[i] / max - c[i]) * nv;
        }
        float gamma = mc.options.gamma().get().floatValue();
        for (int i = 0; i < 3; i++) {
            float x = Math.max(0, Math.min(1, c[i]));
            float ng = 1 - (float) Math.pow(1 - x, 4);
            x += (ng - x) * gamma;
            x += (0.75f - x) * 0.04f;
            c[i] = Math.max(0, Math.min(1, x));
        }
        return c;
    }

    // Та же проекция (поле зрения, покачивание камеры), но с другими ближней и дальней плоскостями.
    // Проекция игры = перспектива x аффинное покачивание B, поэтому строка глубины пересчитывается через строку w.
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
            tiles.put(b.key, new Tile(b.ground.upload(), b.water != null ? b.water.upload() : null, b.x0, b.z0, b.x0 + b.size, b.z0 + b.size, b.y0, b.y1));
            lastUsed.put(b.key, frame);
        }
    }

    // Выгрузить тайлы, которые давно не нужны ни для рисования, ни как запасные.
    private void evict() {
        Set<Long> w = wanted;
        for (Iterator<Map.Entry<Long, Tile>> it = tiles.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Tile> en = it.next();
            Integer used = lastUsed.get(en.getKey());
            if ((used == null || frame - used > 600) && !w.contains(en.getKey())) {
                en.getValue().close();
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
        final int mountains, pixel;
        final boolean clearWater;
        final double smooth;

        Job(long key, int gen, Terrain t, TileMesh.Palette pal, int level, int tx, int tz, double priority, double tile0, int mountains, boolean clearWater, double smooth, int pixel) {
            this.mountains = mountains;
            this.smooth = smooth;
            this.pixel = pixel;
            this.clearWater = clearWater;
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
                double size = level == SCOPE_LEVEL ? SCOPE_TILE : tile0 * (1L << level), x0 = tx * size, z0 = tz * size;
                TileMesh m = level == SCOPE_LEVEL ? TileMesh.buildBlocky(t, pal, x0, z0, SCOPE_TILE, this::stale)
                        : TileMesh.build(t, pal, x0, z0, size, mountains, clearWater, smooth, pixel, this::stale);
                Part ground = m == null ? null : Part.of(m.pos, m.argb, m.vertices());
                if (ground == null) {
                    pending.remove(key);
                    return;
                }
                float y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
                for (int v = 0; v < m.vertices(); v++) {
                    y0 = Math.min(y0, m.pos[v * 3 + 1]);
                    y1 = Math.max(y1, m.pos[v * 3 + 1]);
                }
                for (int v = 0; v < m.waterVertices(); v++) {
                    y0 = Math.min(y0, m.waterPos[v * 3 + 1]);
                    y1 = Math.max(y1, m.waterPos[v * 3 + 1]);
                }
                done.add(new Built(key, gen, x0, z0, size, y0, y1, ground, Part.of(m.waterPos, m.waterArgb, m.waterVertices())));
            } catch (Throwable ex) {
                pending.remove(key);
                if (!failed) LOG.error("Дальний рельеф: ошибка расчёта тайла, отключаюсь до перезахода", ex);
                failed = true;
            }
        }
    }
}
