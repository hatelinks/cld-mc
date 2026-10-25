package dev.cld.earlywindow;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.neoforged.fml.earlydisplay.ColourScheme;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.earlydisplay.ElementShader;
import net.neoforged.fml.earlydisplay.QuadHelper;
import net.neoforged.fml.earlydisplay.RenderElement;
import net.neoforged.fml.earlydisplay.SimpleBufferBuilder;
import net.neoforged.fml.earlydisplay.SimpleFont;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;

/**
 * Экран запуска сборки: тёмный фон, две пиксельные полоски (память и прогресс)
 * и вращающийся уроборос вместо лисы. Логотип Mojang, лог и версия убраны.
 * Подключается через config/fml.toml: earlyWindowProvider = "cldwindow".
 */
public class CldWindow extends DisplayWindow {
    private static final int WHITE = 0xFFFFFFFF;
    // packedint у FML — ABGR: #3fbf6a
    private static final int EMERALD = 0xFF6ABF3F;
    private static final int BG_R = 0x12, BG_G = 0x12, BG_B = 0x12;

    private SimpleFont font;
    private Method lineSpacing, descent, textureNumber, perfText, perfMemory;

    @Override
    public String name() {
        return "cldwindow";
    }

    @Override
    public Runnable start(String mcVersion, String forgeVersion) {
        try {
            darkScheme();
        } catch (Throwable t) {
            System.err.println("[cldwindow] colour scheme: " + t);
        }
        Runnable tick = super.start(mcVersion, forgeVersion);
        try {
            // Однопоточный планировщик: задача выполнится после initRender (у него задержка 1 мс)
            ScheduledExecutorService sched = (ScheduledExecutorService) field("renderScheduler").get(this);
            sched.schedule(this::swapElements, 5L, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            System.err.println("[cldwindow] scheduler: " + t);
        }
        return tick;
    }

    /** Без логотипа Mojang. */
    @Override
    public void addMojangTexture(int textureId) {}

    /** Родитель вызывает addReads от имени своего модуля для getClass() — для подкласса это запрещено, делаем сами. */
    @Override
    public void updateModuleReads(ModuleLayer layer) {
        try {
            Module fm = layer.findModule("neoforge").orElseThrow();
            getClass().getModule().addReads(fm);
            Class<?> clz = Class.forName(fm, "net.neoforged.neoforge.client.loading.NeoForgeLoadingOverlay");
            Map<String, Method> methods = Arrays.stream(clz.getMethods())
                    .filter(m -> Modifier.isStatic(m.getModifiers()))
                    .collect(Collectors.toMap(Method::getName, Function.identity(), (a, b) -> a));
            field("loadingOverlay").set(this, methods.get("newInstance"));
        } catch (Throwable t) {
            throw new IllegalStateException("cldwindow: cannot find loading overlay", t);
        }
    }

    // ---------------------------------------------------------------- setup

    private void darkScheme() throws Exception {
        Field bg = ColourScheme.class.getDeclaredField("background");
        bg.setAccessible(true);
        bg.set(ColourScheme.BLACK, new ColourScheme.Colour(BG_R, BG_G, BG_B));
        field("colourScheme").set(this, ColourScheme.BLACK);
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field f = DisplayWindow.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static Method method(Class<?> c, String name) throws NoSuchMethodException {
        Method m = c.getDeclaredMethod(name);
        m.setAccessible(true);
        return m;
    }

    @SuppressWarnings("unchecked")
    private void swapElements() {
        try {
            font = (SimpleFont) field("font").get(this);
            lineSpacing = method(SimpleFont.class, "lineSpacing");
            descent = method(SimpleFont.class, "descent");
            textureNumber = method(SimpleFont.class, "textureNumber");
            Class<?> perf = Class.forName("net.neoforged.fml.earlydisplay.PerformanceInfo");
            perfText = method(perf, "text");
            perfMemory = method(perf, "memory");

            List<RenderElement> elements = (List<RenderElement>) field("elements").get(this);
            List<RenderElement> mine = new ArrayList<>();
            mine.add(element(this::ouroboros));
            mine.add(element(this::memoryBar));
            mine.add(element(this::progressBars));
            elements.clear();
            elements.addAll(mine);
        } catch (Throwable t) {
            // При любой ошибке остаётся стандартный экран NeoForge
            System.err.println("[cldwindow] keeping default screen: " + t);
        }
    }

    @FunctionalInterface
    private interface Draw {
        void draw(SimpleBufferBuilder bb, RenderElement.DisplayContext ctx, int frame) throws Exception;
    }

    /** RenderElement принимает пакетные интерфейсы Initializer/Renderer — создаём их через Proxy. */
    private static RenderElement element(Draw draw) throws Exception {
        ClassLoader cl = RenderElement.class.getClassLoader();
        Class<?> rendererI = Class.forName("net.neoforged.fml.earlydisplay.RenderElement$Renderer", false, cl);
        Class<?> initI = Class.forName("net.neoforged.fml.earlydisplay.RenderElement$Initializer", false, cl);
        InvocationHandler rh = (p, m, a) -> switch (m.getName()) {
            case "accept" -> {
                try {
                    draw.draw((SimpleBufferBuilder) a[0], (RenderElement.DisplayContext) a[1], (Integer) a[2]);
                } catch (Throwable t) {
                    // не роняем поток отрисовки
                }
                yield null;
            }
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p == a[0];
            case "toString" -> "cldwindow renderer";
            default -> InvocationHandler.invokeDefault(p, m, a);
        };
        Object renderer = Proxy.newProxyInstance(cl, new Class<?>[] {rendererI}, rh);
        InvocationHandler ih = (p, m, a) -> switch (m.getName()) {
            case "get" -> renderer;
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p == a[0];
            case "toString" -> "cldwindow initializer";
            default -> null;
        };
        Object init = Proxy.newProxyInstance(cl, new Class<?>[] {initI}, ih);
        Constructor<RenderElement> ctor = RenderElement.class.getConstructor(initI);
        return ctor.newInstance(init);
    }

    // ---------------------------------------------------------------- drawing

    private static int alpha(int colour) {
        Field f;
        int a = 255;
        try {
            f = RenderElement.class.getDeclaredField("globalAlpha");
            f.setAccessible(true);
            a = f.getInt(null);
        } catch (Throwable ignored) {}
        return (colour & 0x00FFFFFF) | ((((colour >>> 24) * a) / 255) << 24);
    }

    private static void quad(SimpleBufferBuilder bb, float x0, float x1, float y0, float y1, int c) {
        QuadHelper.loadQuad(bb, x0, x1, y0, y1, 0f, 0f, 0f, 0f, c);
    }

    /** Произвольный четырёхугольник (для наклонных линий). */
    private static void poly(SimpleBufferBuilder bb, float[] p, int c) {
        for (int i = 0; i < 8; i += 2) bb.pos(p[i], p[i + 1]).tex(0f, 0f).colour(c).endVertex();
    }

    private static void line(SimpleBufferBuilder bb, float x0, float y0, float x1, float y1, float w, int c) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) return;
        float nx = -dy / len * w / 2, ny = dx / len * w / 2;
        poly(bb, new float[] {x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny}, c);
    }

    private void text(SimpleBufferBuilder bb, RenderElement.DisplayContext ctx, int x, int y, String s, int colour) throws Exception {
        if (s == null || s.isEmpty()) return;
        ctx.elementShader().updateTextureUniform((Integer) textureNumber.invoke(font));
        ctx.elementShader().updateRenderTypeUniform(ElementShader.RenderType.FONT);
        bb.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
        font.generateVerticesForTexts(x, y, bb, new SimpleFont.DisplayText(s, colour));
        bb.draw();
    }

    private static void begin(SimpleBufferBuilder bb, RenderElement.DisplayContext ctx) {
        ctx.elementShader().updateTextureUniform(0);
        ctx.elementShader().updateRenderTypeUniform(ElementShader.RenderType.BAR);
        bb.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
    }

    /** Пиксельная полоска: белая рамка и блоки. from..to — заполненная доля. */
    private static void pixelBar(SimpleBufferBuilder bb, RenderElement.DisplayContext ctx, int x, int y, float from, float to) {
        int s = ctx.scale();
        int w = 400 * s, h = 20 * s, b = 2 * s, gap = 2 * s;
        int c = alpha(WHITE);
        begin(bb, ctx);
        quad(bb, x, x + w, y, y + b, c);
        quad(bb, x, x + w, y + h - b, y + h, c);
        quad(bb, x, x + b, y, y + h, c);
        quad(bb, x + w - b, x + w, y, y + h, c);
        int ix = x + b + gap, iw = w - 2 * (b + gap), iy = y + b + gap, ih = h - 2 * (b + gap);
        int block = 8 * s, step = block + gap;
        int n = (iw + gap) / step;
        int first = (int) Math.floor(from * n), last = (int) Math.floor(to * n);
        for (int i = Math.max(0, first); i < Math.min(n, last); i++) {
            int bx = ix + i * step;
            quad(bb, bx, bx + block, iy, iy + ih, c);
        }
        bb.draw();
    }

    private int lineSpacing() throws Exception {
        return (Integer) lineSpacing.invoke(font);
    }

    private int descent() throws Exception {
        return (Integer) descent.invoke(font);
    }

    private void memoryBar(SimpleBufferBuilder bb, RenderElement.DisplayContext ctx, int frame) throws Exception {
        int s = ctx.scale();
        int y = 10 * s;
        int x = (ctx.scaledWidth() - 400 * s) / 2;
        float mem = (Float) perfMemory.invoke(ctx.performance());
        pixelBar(bb, ctx, x, y, 0f, mem);
        String t = (String) perfText.invoke(ctx.performance());
        if (t != null) text(bb, ctx, ctx.scaledWidth() / 2 - font.stringWidth(t) / 2, y + 22 * s, t, alpha(WHITE));
    }

    private void progressBars(SimpleBufferBuilder bb, RenderElement.DisplayContext ctx, int frame) throws Exception {
        int s = ctx.scale();
        List<ProgressMeter> list = StartupNotificationManager.getCurrentProgress();
        int spacing = lineSpacing() - descent() + 20 * s + 4 * s;
        int x = (ctx.scaledWidth() - 400 * s) / 2;
        for (int i = 0; i < 2 && i < list.size(); i++) {
            ProgressMeter pm = list.get(i);
            int y = 250 * s + i * spacing;
            int barY = y + lineSpacing() - descent();
            if (pm.steps() == 0) {
                // неопределённый прогресс: бегущее окно из трёх блоков
                float p = (frame % 40) / 40f;
                pixelBar(bb, ctx, x, barY, p, Math.min(1f, p + 0.08f));
            } else {
                pixelBar(bb, ctx, x, barY, 0f, pm.progress());
            }
            String label = pm.label().getText();
            if (pm.steps() > 0) label = label + "  " + Math.round(pm.progress() * 100) + "%";
            text(bb, ctx, x, y, label, alpha(WHITE));
        }
    }

    /**
     * Контурный уроборос в пиксельной сетке: змея, кусающая хвост, медленно вращается.
     * Фигура растеризуется каждый кадр в клетки по 2 пикселя, рисуется только граница.
     */
    private void ouroboros(SimpleBufferBuilder bb, RenderElement.DisplayContext ctx, int frame) {
        int s = ctx.scale();
        int cell = 2 * s;
        float r = 32, w = 10; // в виртуальных пикселях 854x480
        double rot = frame * 0.03; // кадры идут 20 раз в секунду
        double len = Math.PI * 1.82;
        double ha = rot + len + 0.16;
        float hx = (float) Math.cos(ha) * r, hy = (float) Math.sin(ha) * r;
        float tx = (float) -Math.sin(ha), ty = (float) Math.cos(ha);
        float nx = (float) Math.cos(ha), ny = (float) Math.sin(ha);
        float hl = w * 1.15f, hw = w * 0.85f;
        int half = (int) Math.ceil((r + w * 1.6f) / 2) + 1;
        int size = half * 2 + 1;
        boolean[] in = new boolean[size * size];
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                float x = (i - half) * 2, y = (j - half) * 2;
                in[j * size + i] = inside(x, y, r, w, rot, len, hx, hy, tx, ty, nx, ny, hl, hw);
            }
        }
        int c = alpha(EMERALD);
        int ox = ctx.scaledWidth() / 2 - half * cell - cell / 2, oy = 160 * s - half * cell - cell / 2;
        begin(bb, ctx);
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                if (!in[j * size + i]) continue;
                boolean edge = i == 0 || j == 0 || i == size - 1 || j == size - 1
                        || !in[j * size + i - 1] || !in[j * size + i + 1] || !in[(j - 1) * size + i] || !in[(j + 1) * size + i];
                if (edge) quad(bb, ox + i * cell, ox + (i + 1) * cell, oy + j * cell, oy + (j + 1) * cell, c);
            }
        }
        // глаз
        float ex = hx + tx * hl * 0.2f + nx * hw * 0.3f, ey = hy + ty * hl * 0.2f + ny * hw * 0.3f;
        int ei = Math.round(ex / 2) + half, ej = Math.round(ey / 2) + half;
        quad(bb, ox + ei * cell, ox + (ei + 1) * cell, oy + ej * cell, oy + (ej + 1) * cell, c);
        bb.draw();
    }

    private static boolean inside(float x, float y, float r, float w, double rot, double len,
                                  float hx, float hy, float tx, float ty, float nx, float ny, float hl, float hw) {
        // голова: эллипс, вытянутый вдоль тела
        float dx = x - hx, dy = y - hy;
        float u = dx * tx + dy * ty, v = dx * nx + dy * ny;
        if ((u * u) / (hl * hl) + (v * v) / (hw * hw) <= 1f) return true;
        // тело: дуга кольца, сужается к хвосту
        double a = Math.atan2(y, x) - rot;
        a = ((a % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
        if (a > len) return false;
        double f = a / len;
        double ww = w * (0.3 + 0.7 * f);
        double d = Math.sqrt(x * x + y * y);
        return Math.abs(d - r) <= ww / 2;
    }
}
