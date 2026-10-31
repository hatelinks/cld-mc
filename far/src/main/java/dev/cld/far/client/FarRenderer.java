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
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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

    // water, полупрозрачная вода отдельным буфером (режим clearWater) или null.
    // x1, z1 и y0, y1: границы тайла для отсечения по полю зрения.
    // lo, hi: высоты по сетке 8x8 частей тайла (для выбора блочных тайлов рядом).
    // look: версия настроек вида, с которой тайл собран (при смене вида тайл рисуется, пока не готов новый). bytes: видеопамять.
    private record Tile(long key, VertexBuffer vb, VertexBuffer water, double x0, double z0, double x1, double z1, float y0, float y1, float[] lo, float[] hi,
            int look, long bytes) {
        // drop: насколько тайл может уйти вниз из-за кривизны земли
        boolean visible(FrustumIntersection f, Vec3 cam, float drop) {
            return f.testAab((float) (x0 - cam.x) - 1, (float) (y0 - cam.y) - 1 - drop, (float) (z0 - cam.z) - 1,
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
    private record Built(long key, int gen, int look, double x0, double z0, double size, float y0, float y1, float[] lo, float[] hi, Part ground, Part water, long bytes) {}
    static final int SUB = 8;

    // Ключи тайлов long. В HashMap<Long> они давали много коллизий (Long.hashCode склеивает половины ключа),
    // корзины превращались в деревья, и выбор тайлов съедал до 13% потока рендера. У fastutil хеш перемешивается.
    private final Long2ObjectOpenHashMap<Tile> tiles = new Long2ObjectOpenHashMap<>();
    private final Long2IntOpenHashMap lastUsed = new Long2IntOpenHashMap();
    // только поток рендера: фоновые потоки сообщают о снятых задачах через done
    private final LongOpenHashSet pending = new LongOpenHashSet();
    private final ConcurrentLinkedQueue<Built> done = new ConcurrentLinkedQueue<>();
    // после публикации не меняется, фоновые потоки только читают
    private volatile LongOpenHashSet wanted = new LongOpenHashSet();
    // Прошлый выбор тайлов: пока камера почти на месте и набор тайлов не менялся, дерево не обходится заново.
    private List<Tile> lastDraw = List.of(), lastBlocky = List.of();
    private final ChunkMask mask = new ChunkMask();
    private double selX = Double.NaN, selY, selZ, selClip, selDist, selDetail;
    private int tilesVer, selVer;
    private volatile int gen;
    private volatile Terrain terrain;
    private TileMesh.Palette palette;
    private ThreadPoolExecutor pool;
    private int poolThreads, step, frame, blockSize;
    private TileMesh.Style style;
    // версия вида: растёт при смене настроек, которые меняют только сетку (рельеф берётся из кэша выборок)
    private volatile int look;
    private final TileCache cache = new TileCache();
    private String worldId;
    private long vram;
    private long lastFrameNs;
    private double frameMs;
    // фоновые потоки ждут, пока игра на паузе
    static volatile boolean paused;
    private boolean active;
    private volatile boolean failed;

    boolean active() { return active; }

    boolean hasWorld() { return terrain != null; }

    // Пересчитать всё (команда); disk: заодно удалить кэш мира на диске.
    void reload(boolean disk) {
        cache.clear();
        if (disk) cache.clearDisk();
        reset();
    }

    // Кадр без дальнего рельефа: туман игры остаётся обычным.
    void idle() { active = false; }

    // Новый мир (или выход из мира при null): всё посчитанное сбрасывается. Вызывается в потоке рендера.
    void setWorld(SeedPayload p) {
        terrain = p == null ? null : new Terrain(p.seed(), p.options());
        // рельеф задают сид, настройки мира и версия генератора
        String tcw = net.neoforged.fml.ModList.get().getModContainerById("tcw").map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        worldId = p == null ? null : Long.toHexString(p.seed()) + "/" + p.options() + "/" + tcw;
        cache.clear();
        reset();
        if (p == null) mask.close();
    }

    private void reset() {
        gen++;
        tiles.values().forEach(Tile::close);
        tiles.clear();
        vram = 0;
        lastUsed.clear();
        pending.clear();
        lastDraw = List.of();
        lastBlocky = List.of();
        selX = Double.NaN;
        tilesVer++;
        for (Built b; (b = done.poll()) != null; ) free(b);
        active = false;
        failed = false;
        palette = null;
    }

    private static void free(Built b) {
        if (b.ground == null) return;
        b.ground.free();
        if (b.water != null) b.water.free();
    }

    void render(Minecraft mc, RenderLevelStageEvent e) {
        Terrain t = terrain;
        active = false;
        if (t == null || !FarConfig.ENABLED.get() || failed) return;
        int st = FarConfig.STEP.get(), bs = FarConfig.BLOCK_SIZE.get();
        TileMesh.Style sty = new TileMesh.Style(FarConfig.MOUNTAINS.get(), FarConfig.CLEAR_WATER.get(), FarConfig.SMOOTH.get(), FarConfig.PIXEL.get(),
                FarConfig.PILLARS.get(), FarConfig.PEAKS.get(), FarConfig.VALLEY_SHADE.get(), FarConfig.COLOR_NOISE.get(),
                FarConfig.BLOCK_SIDES.get(), FarConfig.MESA_BANDS.get(), FarConfig.TREES.get(), FarConfig.ICE.get(), FarConfig.SHORE.get(),
                FarConfig.RIVERS.get());
        if (st != step || bs != blockSize || style == null || sty.mountains() != style.mountains()) {
            step = st;
            style = sty;
            blockSize = bs;
            reset();
        } else if (!sty.equals(style)) {
            // только вид: старые тайлы остаются на экране, новые собираются из кэша выборок без расчёта рельефа
            style = sty;
            look++;
            selX = Double.NaN;
        }
        cache.configure(worldId, FarConfig.SAMPLE_CACHE.get(), FarConfig.DISK_CACHE.get(), FarConfig.DISK_CACHE_MB.get());
        long now = System.nanoTime();
        if (lastFrameNs != 0) frameMs = frameMs * 0.9 + (now - lastFrameNs) / 1e6 * 0.1;
        lastFrameNs = now;
        ensurePool(threads());
        if (palette == null) {
            TileMesh.Palette p = BiomePalette.create(mc.level.registryAccess());
            palette = p != null ? p : TileMesh.Palette.MODEL;
        }
        TileMesh.Palette pal = palette;
        frame++;
        // при просадке кадров (дольше 25 мс) в видеокарту по одному тайлу за кадр
        int up = FarConfig.UPLOADS.get();
        if (FarConfig.ADAPTIVE_UPLOADS.get() && frameMs > 25) up = 1;
        upload(up);

        Vec3 cam = e.getCamera().getPosition();
        double dist = FarConfig.DISTANCE.get();
        double rd = mc.gameRenderer.getRenderDistance();
        // внутри прорисовки игры тайлы тоже выбираются (рисуется только то, что игра не нарисовала: см. ChunkMask),
        // но считаются после всех остальных
        double clip = Math.max(0, rd - 16);
        double tile0 = TileMesh.N * step;
        int g = gen;
        double detail = FarConfig.DETAIL.get();
        boolean scoping = FarConfig.SCOPE.get() && mc.player != null && mc.player.isScoping();
        // Sodium не рисует секции дальше прорисовки и по высоте: там нужен свой рельеф и над чанками
        double vert = ChunkMask.sodium() ? Math.max(16, rd - 16) : 1e9;
        boolean near = FarConfig.NEAR_BLOCKS.get() && vert < 1e8;
        Matrix4f proj = withDepth(e.getProjectionMatrix(), 4, (float) (dist * 1.5 + 1024));
        FrustumIntersection fr = new FrustumIntersection(new Matrix4f(proj).mul(e.getModelViewMatrix()), false);
        double mx = cam.x - selX, my = cam.y - selY, mz = cam.z - selZ;
        if (!scoping && selVer == tilesVer && clip == selClip && dist == selDist && detail == selDetail && mx * mx + my * my + mz * mz < 16) {
            if (frame % 120 == 0) evict();
            if (lastDraw.isEmpty() && lastBlocky.isEmpty()) return;
            active = true;
            draw(mc, e, cam, proj, fr, new ArrayList<>(lastDraw), lastBlocky, dist, clip, rd, vert);
            return;
        }
        List<Tile> draw = new ArrayList<>();
        LongOpenHashSet want = new LongOpenHashSet();
        boolean lookAhead = FarConfig.LOOK_AHEAD.get();
        double fx = -Math.sin(Math.toRadians(e.getCamera().getYRot())), fz = Math.cos(Math.toRadians(e.getCamera().getYRot()));
        LodTree.select(cam.x, cam.y, cam.z, dist, clip, detail, tile0, new LodTree.Sink() {
            @Override
            public boolean ready(long key) {
                Tile tile = tiles.get(key);
                if (tile == null) return false;
                lastUsed.put(key, frame);
                // собран со старым видом: рисуется, пока не готов новый
                if (tile.look != look) {
                    want.add(key);
                    int l = LodTree.level(key);
                    if (pending.add(key)) pool.execute(new Job(key, g, t, pal, l, LodTree.tx(key), LodTree.tz(key), 1e8 + tileDist(tile, cam), tile0, sty, bs));
                }
                return true;
            }

            @Override
            public void draw(long key, int level, int tx, int tz) { draw.add(tiles.get(key)); }

            @Override
            public void want(long key, int level, int tx, int tz, double priority) {
                want.add(key);
                // сначала то, что впереди: тайлы за спиной в конце своего уровня
                if (lookAhead) {
                    double s = tile0 * (1L << level), vx = (tx + 0.5) * s - cam.x, vz = (tz + 0.5) * s - cam.z;
                    if (vx * fx + vz * fz < -s) priority += 4e6;
                }
                if (pending.add(key)) pool.execute(new Job(key, g, t, pal, level, tx, tz, priority, tile0, sty, bs));
            }
        });
        // блочные тайлы: в подзорную трубу всё, что в поле зрения до её дальности, и рядом высокие скалы,
        // которые игра не рисует по высоте
        List<Tile> blocky = new ArrayList<>();
        LongOpenHashSet bset = new LongOpenHashSet();
        double side = SCOPE_TILE * bs;
        // в трубу блоки нужны сразу, скалы рядом после грубого горизонта
        if (scoping) blocky(cam, fr, FarConfig.SCOPE_RANGE.get(), side, Double.NaN, -1e9, blocky, bset, want, g, t, pal, bs);
        if (near) blocky(cam, null, rd + side, side, vert, 2e8, blocky, bset, want, g, t, pal, bs);
        wanted = want;
        lastDraw = draw;
        lastBlocky = blocky;
        selX = cam.x;
        selY = cam.y;
        selZ = cam.z;
        selClip = clip;
        selDist = dist;
        selDetail = detail;
        selVer = tilesVer;
        if (scoping) selX = Double.NaN; // с трубой выбор каждый кадр, поле зрения зависит от взгляда
        if (frame % 120 == 0) evict();
        if (draw.isEmpty() && blocky.isEmpty()) return;
        active = true;
        draw(mc, e, cam, proj, fr, new ArrayList<>(draw), blocky, dist, clip, rd, vert);
    }

    static final int SCOPE_LEVEL = 63, SCOPE_TILE = 32;
    // Больше блочных тайлов за раз не берём: дальше по расстоянию остаётся грубый рельеф.
    static final int BLOCKY_MAX = 900;

    // Блочные тайлы по side блоков до range. fr != null: только те, что в поле зрения (с наклоном взгляда, раньше был
    // конус по направлению, и при взгляде сверху вниз по бокам оставался грубый рельеф). vert: только там, где рельеф
    // выходит за прорисовку игры по высоте (vert от камеры вверх или вниз), NaN, везде.
    // Каждый готовый тайл сам заменяет грубый рельеф под собой (через маску), поэтому полосы и разреза по дуге нет.
    private void blocky(Vec3 cam, FrustumIntersection fr, double range, double side, double vert, double prio, List<Tile> out, LongOpenHashSet seen,
            LongOpenHashSet want, int g, Terrain t, TileMesh.Palette pal, int bs) {
        int ax = (int) Math.floor((cam.x - range) / side), bx = (int) Math.floor((cam.x + range) / side);
        int az = (int) Math.floor((cam.z - range) / side), bz = (int) Math.floor((cam.z + range) / side);
        List<double[]> cand = new ArrayList<>();
        float[] yr = new float[2];
        for (int tz = az; tz <= bz; tz++)
            for (int tx = ax; tx <= bx; tx++) {
                double x0 = tx * side, z0 = tz * side;
                double dx = Math.max(0, Math.max(x0 - cam.x, cam.x - x0 - side)), dz = Math.max(0, Math.max(z0 - cam.z, cam.z - z0 - side));
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > range) continue;
                // сначала грубо по всей высоте мира, чтобы не искать высоты у тайлов за спиной
                if (fr != null && !fr.testAab((float) (x0 - cam.x), (float) (-128 - cam.y), (float) (z0 - cam.z),
                        (float) (x0 + side - cam.x), (float) (640 - cam.y), (float) (z0 + side - cam.z))) continue;
                boolean known = heightRange(x0 + side / 2, z0 + side / 2, yr);
                float y0 = known ? yr[0] : -128, y1 = known ? yr[1] : 640;
                if (!Double.isNaN(vert) && (!known || y1 < cam.y + vert - 8 && y0 > cam.y - vert + 8)) continue;
                if (fr != null && !fr.testAab((float) (x0 - cam.x), (float) (y0 - cam.y - 2), (float) (z0 - cam.z),
                        (float) (x0 + side - cam.x), (float) (y1 - cam.y + 2), (float) (z0 + side - cam.z))) continue;
                cand.add(new double[] { d, tx, tz });
            }
        cand.sort((p, q) -> Double.compare(p[0], q[0]));
        for (int i = 0; i < Math.min(BLOCKY_MAX, cand.size()); i++) {
            double[] c = cand.get(i);
            int tx = (int) c[1], tz = (int) c[2];
            long key = LodTree.key(SCOPE_LEVEL, tx, tz);
            if (!seen.add(key)) continue;
            Tile tile = tiles.get(key);
            if (tile != null) {
                lastUsed.put(key, frame);
                out.add(tile);
                if (tile.look != look) {
                    want.add(key);
                    if (pending.add(key)) pool.execute(new Job(key, g, t, pal, SCOPE_LEVEL, tx, tz, prio + c[0], side, style, bs));
                }
            } else {
                want.add(key);
                if (pending.add(key)) pool.execute(new Job(key, g, t, pal, SCOPE_LEVEL, tx, tz, prio + c[0], side, style, bs));
            }
        }
    }

    // Высоты рельефа в точке по самому мелкому готовому тайлу (весь его диапазон, с запасом); false, если не посчитан.
    private boolean heightRange(double x, double z, float[] out) {
        double tile0 = TileMesh.N * step;
        for (int l = 0; l < 20; l++) {
            double s = tile0 * (1L << l);
            Tile tile = tiles.get(LodTree.key(l, (int) Math.floor(x / s), (int) Math.floor(z / s)));
            if (tile == null) continue;
            int si = (int) Math.max(0, Math.min(SUB - 1, (x - tile.x0) / s * SUB)), sj = (int) Math.max(0, Math.min(SUB - 1, (z - tile.z0) / s * SUB));
            out[0] = tile.lo[sj * SUB + si];
            out[1] = tile.hi[sj * SUB + si];
            if (out[0] > out[1]) { out[0] = tile.y0; out[1] = tile.y1; }
            return true;
        }
        return false;
    }

    private void draw(Minecraft mc, RenderLevelStageEvent e, Vec3 cam, Matrix4f proj, FrustumIntersection fr, List<Tile> draw, List<Tile> blocky,
            double dist, double clip, double rd, double vert) {
        ShaderInstance sh = FarClient.shader;
        // маска чанков: на полразмера с запасом под подзорную трубу
        int half = (int) Math.ceil(Math.max(rd + 32, blocky.isEmpty() ? 0 : FarConfig.SCOPE_RANGE.get() + 64.0 * blockSize) / 16) + 1;
        mask.begin(cam.x, cam.y, cam.z, half, rd);
        for (Tile t : blocky) mask.markBlocky(t.x0, t.z0, t.x1 - t.x0);
        int maskTex = mask.upload();

        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        float[] b = light(mc, partial);
        // погода: в дождь и грозу темнее, туман и дымка ближе
        float rain = FarConfig.WEATHER.get() ? mc.level.getRainLevel(partial) : 0, thunder = FarConfig.WEATHER.get() ? mc.level.getThunderLevel(partial) : 0;
        float dim = (float) (FarConfig.BRIGHTNESS.get() * (1 - 0.18 * rain - 0.15 * thunder));
        for (int i = 0; i < 3; i++) b[i] *= dim;
        RenderSystem.setShaderFogStart((float) (dist * FarConfig.FOG.get() * (1 - 0.35 * rain)));
        RenderSystem.setShaderFogEnd((float) dist);
        RenderSystem.setShaderColor(b[0], b[1], b[2], 1);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();

        sh.setSampler("Mask", maskTex);
        sh.setDefaultUniforms(VertexFormat.Mode.QUADS, e.getModelViewMatrix(), proj, mc.getWindow());
        set(sh, "ClipRadius", (float) clip);
        set(sh, "MaskSize", mask.size);
        Uniform mo = sh.getUniform("MaskOrigin");
        if (mo != null) mo.set((float) (mask.originX * 16 - cam.x), (float) (mask.originZ * 16 - cam.z));
        set(sh, "VertRange", (float) vert);
        set(sh, "Pass", 0);
        lighting(mc, sh, cam, partial, rain);
        atmosphere(mc, sh, cam, partial, dist);
        sh.apply();
        Uniform off = sh.CHUNK_OFFSET;
        Uniform pass = sh.getUniform("Pass");
        lodTint = FarConfig.DEBUG_LOD.get() ? sh.getUniform("LodTint") : null;
        if (lodTint == null && sh.getUniform("LodTint") != null) {
            sh.getUniform("LodTint").set(-1f, -1f, -1f);
            sh.getUniform("LodTint").upload();
        }
        // тайлы за спиной и сбоку не рисуются
        draw.removeIf(t -> !t.visible(fr, cam, curvDrop(dist)));
        for (Tile t : draw) drawTile(off, t, t.vb, cam);
        if (!blocky.isEmpty() && pass != null) {
            pass.set(1f);
            pass.upload();
            for (Tile t : blocky) if (t.visible(fr, cam, 0)) drawTile(off, t, t.vb, cam);
            // грубые тайлы ещё раз под блочными, на дальней плоскости и без записи глубины: заполняют только пиксели,
            // где блочный тайл ничего не нарисовал (на его краю грубый рельеф снаружи бывает выше, стенки нет, видно небо)
            pass.set(3f);
            pass.upload();
            RenderSystem.depthMask(false);
            for (Tile t : draw) {
                boolean under = false;
                for (Tile bt : blocky) if (t.x0 < bt.x1 && bt.x0 < t.x1 && t.z0 < bt.z1 && bt.z0 < t.z1) { under = true; break; }
                if (under) drawTile(off, t, t.vb, cam);
            }
            RenderSystem.depthMask(true);
            pass.set(0f);
            pass.upload();
        }
        // полупрозрачная вода поверх дна, без записи глубины (как вода в игре)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        if (pass != null) {
            pass.set(2f);
            pass.upload();
        }
        for (Tile t : draw) if (t.water != null) drawTile(off, t, t.water, cam);
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

    // Солнце, закат, дымка и насыщенность для шейдера.
    private static void lighting(Minecraft mc, ShaderInstance sh, Vec3 cam, float partial, float rain) {
        double a = mc.level.getSunAngle(partial);
        double sx = -Math.sin(a), sy = Math.cos(a), sz = 0.25;
        // ночью светит луна с другой стороны
        if (sy < 0) { sx = -sx; sy = -sy; }
        double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
        Uniform sun = sh.getUniform("SunDir");
        if (sun != null) sun.set((float) (sx / l), (float) (sy / l), (float) (sz / l));
        set(sh, "SunShade", FarConfig.SUN_SHADE.get().floatValue());
        set(sh, "Saturation", FarConfig.SATURATION.get().floatValue());
        set(sh, "Haze", (float) Math.min(1, FarConfig.HAZE.get() + 0.3 * rain));
        // тёплый свет, когда солнце у горизонта (только днём)
        float[] tint = { 1, 1, 1 };
        double cy = Math.cos(a);
        if (FarConfig.SUNSET.get() && cy > -0.2) {
            double k = 0.45 * (1 - Math.min(1, Math.abs(cy) / 0.35));
            tint = new float[] { 1, (float) (1 - 0.18 * k), (float) (1 - 0.38 * k) };
        }
        Uniform tu = sh.getUniform("Tint");
        if (tu != null) tu.set(tint[0], tint[1], tint[2]);
        float[] fog = RenderSystem.getShaderFogColor();
        float[] hz = { fog[0], fog[1], fog[2] };
        if (FarConfig.SKY_HAZE.get()) {
            Vec3 sky = mc.level.getSkyColor(cam, partial);
            hz = new float[] { (float) (fog[0] + sky.x) / 2, (float) (fog[1] + sky.y) / 2, (float) (fog[2] + sky.z) / 2 };
        }
        Uniform hu = sh.getUniform("HazeColor");
        if (hu != null) hu.set(hz[0], hz[1], hz[2]);
    }

    private static float curvDrop(double dist) { return (float) (FarConfig.CURVATURE.get() * 100); }

    // Вода, тени облаков, туман в низинах и кривизна земли.
    private static void atmosphere(Minecraft mc, ShaderInstance sh, Vec3 cam, float partial, double dist) {
        set(sh, "Curv", (float) (FarConfig.CURVATURE.get() * 100 / (dist * dist)));
        double secs = ((mc.level.getGameTime() % 72000) + partial) / 20.0;
        set(sh, "GameTime", (float) secs);
        Uniform w = sh.getUniform("WorldXZ");
        if (w != null) w.set((float) Math.floorMod((long) Math.floor(cam.x), 16384) + (float) (cam.x - Math.floor(cam.x)),
                (float) Math.floorMod((long) Math.floor(cam.z), 16384) + (float) (cam.z - Math.floor(cam.z)));
        set(sh, "Waves", FarConfig.WAVES.get() ? 1 : 0);
        boolean day = Math.cos(mc.level.getSunAngle(partial)) > 0;
        set(sh, "Glint", (float) (FarConfig.GLINT.get() * (day ? 1 : 0.3)));
        set(sh, "Reflect", FarConfig.REFLECT.get().floatValue());
        set(sh, "CloudShadows", (float) (day ? FarConfig.CLOUD_SHADOWS.get() : 0));
        Uniform co = sh.getUniform("CloudOffset");
        if (co != null) co.set((float) (secs * 2.0 % 16384), (float) (secs * 0.7 % 16384));
        // утром туман гуще: время суток 0, рассвет (в тиках 0 это утро, 6000 полдень)
        long tod = mc.level.getDayTime() % 24000;
        double morning = tod > 22500 || tod < 2500 ? 1 : 0.5;
        set(sh, "Mist", (float) (FarConfig.MIST.get() * morning));
        set(sh, "MistTop", (float) (dev.cld.tcw.gen.Macro.SEA + 22 - cam.y));
    }

    private static Uniform lodTint;

    private static void drawTile(Uniform off, Tile t, VertexBuffer vb, Vec3 cam) {
        if (lodTint != null) {
            // отладка: цвет по уровню, от зелёного (мелкие) к красному (крупные), блочные синие
            int l = LodTree.level(t.key);
            if (l == SCOPE_LEVEL) lodTint.set(0.2f, 0.4f, 1f);
            else lodTint.set(Math.min(1, l / 5f), Math.max(0, 1 - l / 5f), 0.1f);
            lodTint.upload();
        }
        if (off != null) {
            off.set((float) (t.x0 - cam.x), (float) -cam.y, (float) (t.z0 - cam.z));
            off.upload();
        }
        vb.bind();
        vb.draw();
    }

    private static void set(ShaderInstance sh, String name, float v) {
        Uniform u = sh.getUniform(name);
        if (u != null) u.set(v);
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

    private void upload(int perFrame) {
        for (int i = 0; i < perFrame; ) {
            Built b = done.poll();
            if (b == null) return;
            if (b.gen == gen) pending.remove(b.key);
            if (b.ground == null) continue; // задача снята или тайл пустой
            i++;
            Tile old = tiles.get(b.key);
            if (b.gen != gen || old != null && old.look >= b.look) {
                free(b);
                continue;
            }
            if (old != null) {
                old.close();
                vram -= old.bytes;
            }
            tilesVer++;
            tiles.put(b.key, new Tile(b.key, b.ground.upload(), b.water != null ? b.water.upload() : null, b.x0, b.z0, b.x0 + b.size, b.z0 + b.size, b.y0, b.y1, b.lo, b.hi,
                    b.look, b.bytes));
            vram += b.bytes;
            lastUsed.put(b.key, frame);
        }
    }

    // Выгрузить тайлы, которые давно не нужны ни для рисования, ни как запасные.
    private void evict() {
        LongOpenHashSet w = wanted;
        long budget = (long) FarConfig.MAX_VRAM.get() << 20;
        for (Tile t : lastDraw) lastUsed.put(t.key, frame);
        for (var it = tiles.long2ObjectEntrySet().fastIterator(); it.hasNext(); ) {
            var en = it.next();
            long k = en.getLongKey();
            boolean old = !lastUsed.containsKey(k) || frame - lastUsed.get(k) > 600;
            // сверх лимита видеопамяти выгружается всё, что не рисовалось хотя бы пару секунд
            boolean over = vram > budget && (!lastUsed.containsKey(k) || frame - lastUsed.get(k) > 120);
            if ((old || over) && !w.contains(k)) {
                en.getValue().close();
                vram -= en.getValue().bytes;
                lastUsed.remove(k);
                it.remove();
                tilesVer++;
            }
        }
    }

    // 0 в настройке: по числу ядер (треть, от 1 до 4)
    private static int threads() {
        int t = FarConfig.THREADS.get();
        return t > 0 ? t : Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 3));
    }

    private static double tileDist(Tile t, Vec3 cam) {
        double dx = Math.max(0, Math.max(t.x0 - cam.x, cam.x - t.x1)), dz = Math.max(0, Math.max(t.z0 - cam.z, cam.z - t.z1));
        return Math.sqrt(dx * dx + dz * dz);
    }

    // Строки для экрана F3.
    void debug(List<String> out) {
        if (terrain == null) return;
        out.add(String.format("cld_far: тайлов %d, в очереди %d, видеопамять %d МБ", tiles.size(), pending.size(), vram >> 20));
        int h = cache.hits, m = cache.misses;
        out.add(String.format("cld_far: кэш рельефа %d МБ, попаданий %d%%, потоков %d", cache.memBytes() >> 20, h + m == 0 ? 0 : h * 100 / (h + m), poolThreads));
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
        final TileMesh.Style style;
        final int blockSize, myLook = look;

        // tile0: сторона тайла нижнего уровня (у блочного тайла, его сторона), blockSize: размер блока блочного тайла
        Job(long key, int gen, Terrain t, TileMesh.Palette pal, int level, int tx, int tz, double priority, double tile0, TileMesh.Style style, int blockSize) {
            this.style = style;
            this.blockSize = blockSize;
            this.key = key; this.gen = gen; this.t = t; this.pal = pal; this.level = level; this.tx = tx; this.tz = tz; this.priority = priority; this.tile0 = tile0;
        }

        @Override
        public int compareTo(Job o) { return Double.compare(priority, o.priority); }

        private void drop() { done.add(new Built(key, gen, look, 0, 0, 0, 0, 0, null, null, null, null, 0)); }

        private boolean stale() { return gen != FarRenderer.this.gen || !wanted.contains(key) && !wanted.isEmpty(); }

        @Override
        public void run() {
            // на паузе ждём (меню паузы, свернутое окно): кадры и батарея важнее
            while (paused && !stale()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    return;
                }
            }
            if (stale()) {
                drop();
                return;
            }
            try {
                double size = level == SCOPE_LEVEL ? tile0 : tile0 * (1L << level), x0 = tx * size, z0 = tz * size;
                TileMesh m;
                if (level == SCOPE_LEVEL) m = TileMesh.buildBlocky(t, pal, x0, z0, SCOPE_TILE, blockSize, style, this::stale);
                else {
                    // выборка рельефа из кэша (память или диск), иначе считается и кладётся в кэш
                    long ck = TileCache.key(key, tile0, style.mountains());
                    String name = (long) tile0 + "_" + style.mountains() + "_" + level + "_" + tx + "_" + tz + ".bin";
                    TileMesh.Samples sm = cache.get(ck, name);
                    if (sm == null) {
                        sm = TileMesh.sample(t, pal, x0, z0, size, style.mountains(), this::stale);
                        if (sm != null) cache.put(ck, sm, name);
                    }
                    m = sm == null ? null : TileMesh.mesh(t, sm, style);
                }
                Part ground = m == null ? null : Part.of(m.pos, m.argb, m.vertices());
                if (ground == null) {
                    drop();
                    return;
                }
                float y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
                float[] lo = new float[SUB * SUB], hi = new float[SUB * SUB];
                java.util.Arrays.fill(lo, Float.MAX_VALUE);
                java.util.Arrays.fill(hi, -Float.MAX_VALUE);
                for (int v = 0; v < m.vertices(); v++) {
                    float y = m.pos[v * 3 + 1];
                    y0 = Math.min(y0, y);
                    y1 = Math.max(y1, y);
                    int si = (int) Math.max(0, Math.min(SUB - 1, m.pos[v * 3] / size * SUB)), sj = (int) Math.max(0, Math.min(SUB - 1, m.pos[v * 3 + 2] / size * SUB));
                    lo[sj * SUB + si] = Math.min(lo[sj * SUB + si], y);
                    hi[sj * SUB + si] = Math.max(hi[sj * SUB + si], y);
                }
                for (int v = 0; v < m.waterVertices(); v++) {
                    y0 = Math.min(y0, m.waterPos[v * 3 + 1]);
                    y1 = Math.max(y1, m.waterPos[v * 3 + 1]);
                }
                done.add(new Built(key, gen, myLook, x0, z0, size, y0, y1, lo, hi, ground, Part.of(m.waterPos, m.waterArgb, m.waterVertices()),
                        (long) (m.vertices() + m.waterVertices()) * DefaultVertexFormat.POSITION_COLOR.getVertexSize()));
            } catch (Throwable ex) {
                drop();
                if (!failed) LOG.error("Дальний рельеф: ошибка расчёта тайла, отключаюсь до перезахода", ex);
                failed = true;
            }
        }
    }
}
