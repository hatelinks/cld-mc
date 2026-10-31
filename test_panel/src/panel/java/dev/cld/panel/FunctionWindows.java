package dev.cld.panel;

import dev.cld.panel.ui.Element.Button;
import dev.cld.panel.ui.Element.Input;
import dev.cld.panel.ui.Element.Label;
import dev.cld.panel.ui.Element.ListView;
import dev.cld.panel.ui.Element.ListView.Row;
import dev.cld.panel.ui.Window;

import java.util.ArrayList;
import java.util.List;

// Окна третьего этапа: функции из Nodus (яркость, xray, полёт, камера) и поиск блоков.
final class FunctionWindows {
    private FunctionWindows() {}

    static Window functions(PanelConfig c) {
        Window w = new Window("functions", "Функции", 150, c.win("functions", 4, 230, 5));
        w.add(new Button(() -> "Яркость", () -> Cheats.fullbright, b -> Cheats.fullbright = Cheats.toggle(Cheats.fullbright, "Яркость")));
        w.add(new Button(() -> "Xray: руды сквозь стены", () -> BlockEsp.xray, b -> {
            BlockEsp.xray = Cheats.toggle(BlockEsp.xray, "Xray");
            BlockEsp.changed();
        }));
        w.add(new Button(() -> "Полёт", () -> Cheats.flight, b -> Cheats.flight = Cheats.toggle(Cheats.flight, "Полёт")));
        w.add(new Button(() -> "Скорость полёта x" + c.flySpeed + " (ЛКМ +, ПКМ -)", () -> false, b -> {
            c.flySpeed = Math.max(1, Math.min(10, c.flySpeed + (b == 1 ? -1 : 1)));
        }));
        w.add(new Button(() -> "Свободная камера", () -> Cheats.freecam, b -> Cheats.freecam = Cheats.toggle(Cheats.freecam, "Свободная камера")));
        w.add(new Button(() -> String.format("Скорость камеры %.2f (ЛКМ +, ПКМ -)", c.camSpeed), () -> false, b -> {
            c.camSpeed = Math.max(0.25, Math.min(5, c.camSpeed + (b == 1 ? -0.25 : 0.25)));
        }));
        w.add(new Button(() -> "ESP: рамки существ", () -> EntityEsp.esp, b -> EntityEsp.esp = Cheats.toggle(EntityEsp.esp, "ESP")));
        w.add(new Button(() -> "Tracers: линии к существам", () -> EntityEsp.tracers, b -> EntityEsp.tracers = Cheats.toggle(EntityEsp.tracers, "Tracers")));
        w.add(new Label(() -> Panel.full() ? "Камера: WASD, прыжок, присед, бег x3" : "Только одиночная игра и свой сервер"));
        return w;
    }

    // Движение из Nodus
    static Window movement(PanelConfig c) {
        Window w = new Window("movement", "Движение", 150, c.win("movement", 310, 230, 0));
        w.add(new Button(() -> "NoFall", () -> Cheats.noFall, b -> Cheats.noFall = Cheats.toggle(Cheats.noFall, "NoFall")));
        w.add(new Button(() -> "Step: шаг на блок", () -> Cheats.step, b -> Cheats.step = Cheats.toggle(Cheats.step, "Step")));
        w.add(new Button(() -> "Jesus: по воде и лаве", () -> Cheats.jesus, b -> Cheats.jesus = Cheats.toggle(Cheats.jesus, "Jesus")));
        w.add(new Button(() -> "Spider: по стенам", () -> Cheats.spider, b -> Cheats.spider = Cheats.toggle(Cheats.spider, "Spider")));
        w.add(new Button(() -> "Phase: сквозь блоки", () -> Cheats.phase, b -> Cheats.phase = Cheats.toggle(Cheats.phase, "Phase")));
        w.add(new Button(() -> "Timer: тики x" + Cheats.TIMER[Cheats.timer] + " (ЛКМ +, ПКМ -)", () -> Cheats.timer > 0, b -> {
            if (!Panel.full()) {
                Panel.log("Timer: только в одиночной игре и на своём сервере");
                return;
            }
            Cheats.timer = Math.max(0, Math.min(Cheats.TIMER.length - 1, Cheats.timer + (b == 1 ? -1 : 1)));
            Panel.log("Timer: x" + Cheats.TIMER[Cheats.timer]);
        }));
        w.add(new Label(() -> "Присед опускает в воду, Phase летает"));
        return w;
    }

    static Window world(PanelConfig c) {
        Window w = new Window("world", "Мир", 150, c.win("world", 466, 230, 4));
        w.add(new Button(() -> "FastBreak", () -> Cheats.fastBreak, b -> Cheats.fastBreak = Cheats.toggle(Cheats.fastBreak, "FastBreak")));
        w.add(new Button(() -> "AutoTool", () -> Cheats.autoTool, b -> Cheats.autoTool = Cheats.toggle(Cheats.autoTool, "AutoTool")));
        w.add(new Button(() -> "AutoFish", () -> Cheats.autoFish, b -> Cheats.autoFish = Cheats.toggle(Cheats.autoFish, "AutoFish")));
        return w;
    }

    // Поиск блоков как Search в Nodus: рамки сквозь стены, у каждого запроса свой цвет
    static Window search(PanelConfig c) {
        Window w = new Window("search", "Поиск блоков", 170, c.win("search", 158, 230, 2));
        w.add(new Button(() -> "Показывать найденное", () -> BlockEsp.search, b -> {
            BlockEsp.search = Cheats.toggle(BlockEsp.search, "Поиск блоков");
            BlockEsp.changed();
        }));
        w.add(new Input("блок, часть имени или #тег", false, t -> add(c, t)));
        w.add(new Button("Сундуки, бочки, шалкеры", () -> {
            add(c, "chest");
            add(c, "barrel");
            add(c, "shulker_box");
        }));
        w.add(new Button("Спавнеры и хранилища", () -> {
            add(c, "spawner");
            add(c, "vault");
        }));
        w.add(new ListView(5, () -> {
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < c.search.size(); i++) {
                String q = c.search.get(i);
                rows.add(new Row(q, BlockEsp.SEARCH_COLORS[i % BlockEsp.SEARCH_COLORS.length], b -> {
                    c.search.remove(q);
                    c.save();
                    BlockEsp.changed();
                }, () -> "Блоков под запрос: " + BlockEsp.match(q).size() + ". Клик убирает запрос"));
            }
            return rows;
        }));
        w.add(new Button(() -> "Радиус " + c.espRadius + " блоков (ЛКМ +, ПКМ -)", () -> false, b -> {
            c.espRadius = Math.max(16, Math.min(160, c.espRadius + (b == 1 ? -16 : 16)));
            BlockEsp.changed();
        }));
        w.add(new Label(() -> BlockEsp.xray || BlockEsp.search ? "Найдено: " + BlockEsp.count() : "Поиск и xray выключены"));
        return w;
    }

    private static void add(PanelConfig c, String q) {
        q = q.trim().toLowerCase(java.util.Locale.ROOT);
        if (q.isEmpty() || c.search.contains(q)) return;
        int n = BlockEsp.match(q).size();
        if (n == 0) {
            Panel.log("Нет блоков под запрос " + q);
            return;
        }
        c.search.add(q);
        c.save();
        if (!BlockEsp.search && Panel.full()) BlockEsp.search = true;
        BlockEsp.changed();
        Panel.log("Ищу " + q + ": " + n + " видов блоков");
    }
}
