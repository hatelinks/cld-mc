package dev.cld.tcw;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.cld.tcw.gen.Caves;
import dev.cld.tcw.gen.Hydro;
import dev.cld.tcw.gen.Macro;
import dev.cld.tcw.gen.Terrain;
import java.util.function.BiPredicate;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /locate tcw <что>: поиск мест по модели рельефа (без генерации чанков).
 * Работает только в Верхнем мире TelipailoCraft.
 */
public final class LocateCommand {
    private static final int RADIUS = 30000;

    private LocateCommand() {}

    static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> tcw = Commands.literal("tcw");
        for (Macro.Style st : Macro.STYLES) {
            String name = switch (st) {
                case ALPINE -> "alpine";
                case PATAGONIA -> "needles";
                case DOLOMITE -> "dolomites";
                case GRANITE -> "granite";
                case VOLCANIC -> "volcanic_range";
                case MESA -> "canyons";
                case PLATEAU -> "plateau";
                case KARST -> "karst";
            };
            tcw.then(Commands.literal(name).executes(c -> style(c.getSource(), st)));
        }
        tcw.then(Commands.literal("peak").executes(c -> peak(c.getSource())));
        tcw.then(Commands.literal("volcano").executes(c -> volcano(c.getSource())));
        tcw.then(Commands.literal("glacier").executes(c -> grid(c.getSource(), "ледник", (t, s) ->
                s.temp < -0.2 && s.mountain > 0.25 && s.height > 0 && s.height > t.snowLine(0, 0, s.temp) + 40)));
        tcw.then(Commands.literal("fjord").executes(c -> grid(c.getSource(), "фьорд", (t, s) ->
                s.temp < -0.25 && s.mountain > 0.3 && s.coast > -150 && s.coast < 250)));
        tcw.then(Commands.literal("ocean").executes(c -> grid(c.getSource(), "открытый океан", (t, s) -> s.coast < -1500)));
        tcw.then(Commands.literal("river").executes(c -> river(c.getSource(), false)));
        tcw.then(Commands.literal("waterfall").executes(c -> river(c.getSource(), true)));
        tcw.then(Commands.literal("lake").executes(c -> lake(c.getSource())));
        tcw.then(Commands.literal("cave_hall").executes(c -> hall(c.getSource())));
        tcw.then(Commands.literal("cenote").executes(c -> cenote(c.getSource())));
        tcw.then(Commands.literal("underground_river").executes(c -> {
            CommandSourceStack s = c.getSource();
            return columns(s, "подземная река", s.getPosition().x, s.getPosition().z, 8, 6000,
                    (t, x, z) -> t.caves.nearGallery(x, z), (t, col, x, z) -> t.caves.riverLevel(x, z, col));
        }));
        tcw.then(Commands.literal("ice_cave").executes(c -> iceCave(c.getSource())));
        d.register(Commands.literal("locate").requires(s -> s.hasPermission(2)).then(tcw));
    }

    private static Terrain terrain(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        boolean ok = level.dimension() == Level.OVERWORLD
                && level.dimensionTypeRegistration().unwrapKey().map(ResourceKey::location).map(Worlds::isTcw).orElse(false);
        if (!ok) {
            src.sendFailure(Component.literal("Этот поиск работает только в Верхнем мире TelipailoCraft"));
            return null;
        }
        return Worlds.terrain(level);
    }

    /** Поиск по кольцам от игрока с шагом step: ближайшая точка, где выполняется условие. */
    private static double[] rings(Terrain t, double x0, double z0, int step, int radius, BiPredicate<Terrain, Macro.Sample> ok) {
        Macro.Sample s = new Macro.Sample();
        for (int r = 0; r <= radius; r += step) {
            double best = Double.MAX_VALUE;
            double[] hit = null;
            int n = Math.max(1, r / step);
            for (int i = -n; i <= n; i++)
                for (int j = -n; j <= n; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != n) continue;
                    double x = x0 + i * step, z = z0 + j * step;
                    t.macro.sample(x, z, s);
                    if (!ok.test(t, s)) continue;
                    double d = (x - x0) * (x - x0) + (z - z0) * (z - z0);
                    if (d < best) { best = d; hit = new double[] { x, z }; }
                }
            if (hit != null) return hit;
        }
        return null;
    }

    private static int grid(CommandSourceStack src, String what, BiPredicate<Terrain, Macro.Sample> ok) {
        Terrain t = terrain(src);
        if (t == null) return 0;
        double x0 = src.getPosition().x, z0 = src.getPosition().z;
        double[] hit = rings(t, x0, z0, 96, RADIUS, (tt, s) -> {
            if (ok == null) return false;
            return ok.test(tt, s);
        });
        return report(src, t, what, hit, Double.NaN);
    }

    private static int style(CommandSourceStack src, Macro.Style st) {
        boolean flat = st == Macro.Style.MESA || st == Macro.Style.PLATEAU || st == Macro.Style.KARST;
        return grid(src, "горы: " + st.name().toLowerCase(), (t, s) -> s.style[st.ordinal()] > 0.75
                && (flat ? s.coast > 600 && s.height > 100 : s.mountain > 0.55));
    }

    private static int peak(CommandSourceStack src) {
        Terrain t = terrain(src);
        if (t == null) return 0;
        double x0 = src.getPosition().x, z0 = src.getPosition().z;
        // ближайший массив выше 520, затем самая высокая точка вокруг него
        double[] hit = rings(t, x0, z0, 64, RADIUS, (tt, s) -> s.height > 520);
        if (hit == null) return report(src, t, "пик", null, Double.NaN);
        Terrain.Column c = new Terrain.Column();
        double bx = hit[0], bz = hit[1], bh = -1e9;
        for (int i = -24; i <= 24; i++)
            for (int j = -24; j <= 24; j++) {
                t.column(hit[0] + i * 12, hit[1] + j * 12, c);
                if (c.height > bh) { bh = c.height; bx = hit[0] + i * 12; bz = hit[1] + j * 12; }
            }
        return report(src, t, "пик", new double[] { bx, bz }, Double.NaN);
    }

    private static int volcano(CommandSourceStack src) {
        Terrain t = terrain(src);
        if (t == null) return 0;
        double x0 = src.getPosition().x, z0 = src.getPosition().z;
        Macro.Sample s = new Macro.Sample();
        int gi0 = (int) Math.floor(x0 / Macro.CONE_CELL), gj0 = (int) Math.floor(z0 / Macro.CONE_CELL);
        int maxR = RADIUS / (int) Macro.CONE_CELL;
        for (int r = 0; r <= maxR; r++) {
            double best = Double.MAX_VALUE;
            double[] hit = null;
            for (int i = gi0 - r; i <= gi0 + r; i++)
                for (int j = gj0 - r; j <= gj0 + r; j++) {
                    if (Math.max(Math.abs(i - gi0), Math.abs(j - gj0)) != r) continue;
                    double[] cr = t.macro.cone(i, j);
                    if (cr == null) continue;
                    t.macro.sample(cr[0], cr[1], s);
                    if (s.style[Macro.Style.VOLCANIC.ordinal()] < 0.3 || s.coast < 0) continue;
                    double d = (cr[0] - x0) * (cr[0] - x0) + (cr[1] - z0) * (cr[1] - z0);
                    if (d < best) { best = d; hit = new double[] { cr[0], cr[1] }; }
                }
            if (hit != null) return report(src, t, "вулкан (кратер)", hit, Double.NaN);
        }
        return report(src, t, "вулкан", null, Double.NaN);
    }

    private static Hydro hydroAt(Terrain t, double x, double z) {
        Macro.Cont c = t.macro.cont(x, z, new Macro.Cont());
        return t.hydro(c.ci, c.cj);
    }

    private static int river(CommandSourceStack src, boolean waterfall) {
        Terrain t = terrain(src);
        if (t == null) return 0;
        double x0 = src.getPosition().x, z0 = src.getPosition().z;
        double[] hit = null;
        Macro.Cont c = t.macro.cont(x0, z0, new Macro.Cont());
        for (int r = 0; r <= 1 && hit == null; r++)
            for (int i = c.ci - r; i <= c.ci + r; i++)
                for (int j = c.cj - r; j <= c.cj + r; j++) {
                    Hydro h = t.hydro(i, j);
                    if (h == null) continue;
                    double[] f = h.nearestSegment(x0, z0, waterfall ? h::step : h::big, waterfall);
                    if (f != null && (hit == null || dist(f, x0, z0) < dist(hit, x0, z0))) hit = f;
                }
        return report(src, t, waterfall ? "водопад" : "большая река", hit, hit == null ? Double.NaN : hit[2]);
    }

    private static int lake(CommandSourceStack src) {
        Terrain t = terrain(src);
        if (t == null) return 0;
        double x0 = src.getPosition().x, z0 = src.getPosition().z;
        double[] hit = null;
        Macro.Cont c = t.macro.cont(x0, z0, new Macro.Cont());
        for (int r = 0; r <= 1 && hit == null; r++)
            for (int i = c.ci - r; i <= c.ci + r; i++)
                for (int j = c.cj - r; j <= c.cj + r; j++) {
                    Hydro h = t.hydro(i, j);
                    if (h == null) continue;
                    double[] f = h.nearestLake(x0, z0);
                    if (f != null && (hit == null || dist(f, x0, z0) < dist(hit, x0, z0))) hit = f;
                }
        return report(src, t, "озеро", hit, hit == null ? Double.NaN : hit[2]);
    }

    private static int hall(CommandSourceStack src) {
        Terrain t = terrain(src);
        if (t == null) return 0;
        double x0 = src.getPosition().x, z0 = src.getPosition().z;
        int gi0 = (int) Math.floor(x0 / Caves.HALL_CELL), gj0 = (int) Math.floor(z0 / Caves.HALL_CELL);
        for (int r = 0; r <= RADIUS / (int) Caves.HALL_CELL; r++) {
            Caves.Hall best = null;
            for (int i = gi0 - r; i <= gi0 + r; i++)
                for (int j = gj0 - r; j <= gj0 + r; j++) {
                    if (Math.max(Math.abs(i - gi0), Math.abs(j - gj0)) != r) continue;
                    Caves.Hall h = t.caves.hall(i, j);
                    if (h != null && (best == null || Math.hypot(h.x - x0, h.z - z0) < Math.hypot(best.x - x0, best.z - z0))) best = h;
                }
            if (best != null) {
                String what = "гигантский зал" + (best.fluid == Caves.LAVA ? " с лавой" : best.fluid == Caves.WATER ? " с озером" : "");
                return report(src, t, what, new double[] { best.x, best.z }, best.fluid != Caves.NONE ? best.level + 1 : best.floor() + 2);
            }
        }
        return report(src, t, "гигантский зал", null, Double.NaN);
    }

    private static int cenote(CommandSourceStack src) {
        Terrain t = terrain(src);
        if (t == null) return 0;
        double x0 = src.getPosition().x, z0 = src.getPosition().z;
        int gi0 = (int) Math.floor(x0 / Caves.SINK_CELL), gj0 = (int) Math.floor(z0 / Caves.SINK_CELL);
        for (int r = 0; r <= RADIUS / (int) Caves.SINK_CELL; r++) {
            Caves.Sink best = null;
            for (int i = gi0 - r; i <= gi0 + r; i++)
                for (int j = gj0 - r; j <= gj0 + r; j++) {
                    if (Math.max(Math.abs(i - gi0), Math.abs(j - gj0)) != r) continue;
                    Caves.Sink s = t.caves.sink(i, j);
                    if (s != null && (best == null || Math.hypot(s.x - x0, s.z - z0) < Math.hypot(best.x - x0, best.z - z0))) best = s;
                }
            if (best != null) return report(src, t, "карстовый провал (сенот)", new double[] { best.x + best.r + 2, best.z }, Double.NaN);
        }
        return report(src, t, "карстовый провал", null, Double.NaN);
    }

    interface ColumnProbe { double at(Terrain t, Terrain.Column c, double x, double z); }
    interface Prefilter { boolean ok(Terrain t, double x, double z); }

    /** Ледник поблизости по крупной модели, затем ледяной ход возле него. */
    private static int iceCave(CommandSourceStack src) {
        Terrain t = terrain(src);
        if (t == null) return 0;
        double x0 = src.getPosition().x, z0 = src.getPosition().z;
        double[] g = rings(t, x0, z0, 96, RADIUS, (tt, s) ->
                s.temp < -0.25 && s.mountain > 0.3 && s.height > tt.snowLine(0, 0, s.temp) + 40);
        if (g == null) return report(src, t, "ледяная пещера", null, Double.NaN);
        return columns(src, "ледяная пещера", g[0], g[1], 8, 700, null, (tt, col, x, z) -> tt.caves.iceLevel(x, z, col));
    }

    /**
     * Поиск по столбцам рельефа кольцами вокруг (cx, cz). Столбец считается только там, где прошёл быстрый фильтр;
     * число столбцов ограничено, чтобы сервер не подвисал.
     */
    private static int columns(CommandSourceStack src, String what, double cx, double cz, int step, int radius, Prefilter pre, ColumnProbe probe) {
        Terrain t = terrain(src);
        if (t == null) return 0;
        Terrain.Column c = new Terrain.Column();
        int budget = 20000;
        for (int r = 0; r <= radius; r += step) {
            int n = Math.max(1, r / step);
            for (int i = -n; i <= n; i++)
                for (int j = -n; j <= n; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != n) continue;
                    double x = cx + i * step, z = cz + j * step;
                    if (pre != null && !pre.ok(t, x, z)) continue;
                    if (--budget < 0) return report(src, t, what, null, Double.NaN);
                    t.column(x, z, c);
                    double y = probe.at(t, c, x, z);
                    if (!Double.isNaN(y)) return report(src, t, what, new double[] { x, z }, y + 1);
                }
        }
        return report(src, t, what, null, Double.NaN);
    }

    private static double dist(double[] p, double x, double z) {
        return Math.hypot(p[0] - x, p[1] - z);
    }

    private static int report(CommandSourceStack src, Terrain t, String what, double[] hit, double y) {
        if (hit == null) {
            src.sendFailure(Component.literal("Не найдено поблизости: " + what));
            return 0;
        }
        int x = (int) Math.floor(hit[0]), z = (int) Math.floor(hit[1]);
        int h = (int) Math.ceil(Double.isNaN(y) ? t.column(x, z, new Terrain.Column()).height : y) + 2;
        int dist = (int) Math.round(Math.hypot(x - src.getPosition().x, z - src.getPosition().z));
        Component coords = ComponentUtils.wrapInSquareBrackets(Component.translatable("chat.coordinates", x, h, z))
                .withStyle(s -> s.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + x + " " + h + " " + z))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.coordinates.tooltip"))));
        src.sendSuccess(() -> Component.literal(what + ": ").append(coords).append(" (" + dist + " блоков)"), false);
        return dist;
    }
}
