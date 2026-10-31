package dev.cld.panel;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.pathing.goals.Goal;
import dev.cld.panel.ui.Element;
import dev.cld.panel.ui.Element.Button;
import dev.cld.panel.ui.Element.Input;
import dev.cld.panel.ui.Element.Label;
import dev.cld.panel.ui.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// Набор окон панели. Новое окно: собрать здесь и добавить в список.
final class Windows {
    private static boolean paused;

    private Windows() {}

    static List<Window> build(PanelConfig c) {
        List<Window> list = new ArrayList<>();
        list.add(baritone(c));
        list.add(info(c));
        list.add(panel(c));
        list.add(ToolWindows.settings(c));
        list.add(ToolWindows.waypoints(c));
        list.add(ToolWindows.radar(c));
        list.add(FunctionWindows.functions(c));
        list.add(FunctionWindows.search(c));
        list.add(FunctionWindows.movement(c));
        list.add(FunctionWindows.world(c));
        return list;
    }

    private static Window baritone(PanelConfig c) {
        Window w = new Window("baritone", "Baritone", 130, c.win("baritone", 4, 4, 0));
        Settings s = BaritoneAPI.getSettings();
        w.add(new Label(() -> "Сейчас: " + inWorld(() -> Panel.baritone().getPathingControlManager().mostRecentInControl()
                .map(p -> p.displayName()).orElse("ничего"))));
        w.add(new Label(() -> "Цель: " + inWorld(() -> {
            Goal g = Panel.baritone().getCustomGoalProcess().getGoal();
            return g == null ? "нет" : g.toString();
        })));
        w.add(new Label(() -> "Осталось: " + inWorld(() -> Panel.baritone().getPathingBehavior().ticksRemainingInSegment()
                .map(t -> String.format("%.0f с", t / 20)).orElse("-"))));
        w.add(new Input("x y z или x z, Enter: идти", true, t -> Panel.run("goto " + t)));
        w.add(new Input("блок, Enter: копать", true, t -> Panel.run("mine " + t)));
        w.add(new Button("Исследовать", () -> Panel.run("explore")));
        w.add(new Button("Подбирать предметы", () -> Panel.run("pickup")));
        w.add(new Button("Ферма рядом", () -> Panel.run("farm")));
        w.add(new Button("На поверхность", () -> Panel.run("surface")));
        w.add(new Button(() -> paused ? "Продолжить" : "Пауза", () -> paused, b -> {
            paused = !paused;
            Panel.run(paused ? "pause" : "resume");
        }));
        w.add(new Button("Стоп", () -> {
            paused = false;
            Panel.run("stop");
        }));
        w.add(toggle("Ломать блоки", s.allowBreak));
        w.add(toggle("Ставить блоки", s.allowPlace));
        w.add(toggle("Паркур", s.allowParkour));
        w.add(toggle("Бег", s.allowSprint));
        return w;
    }

    private static Window info(PanelConfig c) {
        Window w = new Window("info", "Инфо", 150, c.win("info", 140, 4, 1));
        Minecraft mc = Minecraft.getInstance();
        w.add(new Label(() -> player(p -> String.format("XYZ %.1f / %.1f / %.1f", p.getX(), p.getY(), p.getZ()))));
        w.add(new Label(() -> player(p -> {
            BlockPos b = p.blockPosition();
            return "Блок " + b.getX() + " " + b.getY() + " " + b.getZ() + "  чанк " + (b.getX() >> 4) + " " + (b.getZ() >> 4);
        })));
        w.add(new Label(() -> player(p -> "Биом " + p.level().getBiome(p.blockPosition()).unwrapKey().map(k -> k.location().toString()).orElse("?"))));
        w.add(new Label(() -> player(p -> "Смотрит " + p.getDirection().getName() + String.format("  %.0f / %.0f", p.getYRot() % 360, p.getXRot()))));
        w.add(new Label(() -> player(p -> "Свет: блоки " + p.level().getBrightness(LightLayer.BLOCK, p.blockPosition())
                + ", небо " + p.level().getBrightness(LightLayer.SKY, p.blockPosition()))));
        w.add(new Label(() -> player(p -> "Мир " + p.level().dimension().location() + ", время " + p.level().getDayTime() % 24000)));
        w.add(new Label(() -> "FPS " + mc.getFps()));
        return w;
    }

    private static Window panel(PanelConfig c) {
        Window w = new Window("panel", "Панель", 140, c.win("panel", 296, 4, 6));
        w.add(new Label(() -> Minecraft.getInstance().level == null ? "Не в мире"
                : Panel.full() ? "Режим: всё доступно" : "Чужой сервер: Baritone и инфо"));
        w.add(new Button(() -> String.format("Размер x%.2f (ЛКМ +, ПКМ -)", c.size), () -> false, b -> {
            c.size = Math.max(0.5, Math.min(2, c.size + (b == 1 ? -0.25 : 0.25)));
        }));
        w.add(new Button(() -> {
            String a = Panel.serverAddress();
            if (a == null) return Minecraft.getInstance().hasSingleplayerServer() ? "Одиночная игра" : "Свой сервер: нет связи";
            return "Свой сервер " + a + ": " + (c.servers.contains(a) ? "да" : "нет");
        }, () -> {
            String a = Panel.serverAddress();
            return a != null && c.servers.contains(a);
        }, b -> {
            String a = Panel.serverAddress();
            if (a == null) return;
            if (!c.servers.remove(a)) c.servers.add(a);
            c.save();
        }));
        w.add(new Button("Консоль (U)", () -> Minecraft.getInstance().setScreen(new ConsoleScreen(Minecraft.getInstance().screen))));
        w.add(new Button("Сбросить раскладку", () -> {
            c.windows.clear();
            Panel.resetWindows();
        }));
        return w;
    }

    private static Element toggle(String name, Settings.Setting<Boolean> st) {
        return new Button(() -> name, () -> st.value, b -> {
            st.value = !st.value;
            Panel.log(name + ": " + (st.value ? "вкл" : "выкл"));
        });
    }

    private static String player(java.util.function.Function<Player, String> f) {
        Player p = Minecraft.getInstance().player;
        return p == null ? "-" : f.apply(p);
    }

    private static String inWorld(Supplier<String> f) {
        if (Minecraft.getInstance().player == null) return "-";
        try {
            return f.get();
        } catch (Exception e) {
            return "?";
        }
    }
}
