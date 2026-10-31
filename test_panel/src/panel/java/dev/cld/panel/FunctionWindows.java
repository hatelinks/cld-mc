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
        w.add(new Button(() -> "Fullbright", () -> Cheats.fullbright, b -> Cheats.fullbright = Cheats.toggle(Cheats.fullbright, "Fullbright")).tip("Полная яркость: светло в пещерах и ночью"));
        w.add(new Button(() -> "Xray", () -> BlockEsp.xray, b -> {
            BlockEsp.xray = Cheats.toggle(BlockEsp.xray, "Xray");
            BlockEsp.changed();
        }).tip("Рамки вокруг руд сквозь стены, радиус как у Search"));
        w.add(new Button(() -> "Fly", () -> Cheats.flight, b -> Cheats.flight = Cheats.toggle(Cheats.flight, "Fly")).tip("Полёт как в творческом режиме"));
        w.add(new Button(() -> "Скорость Fly x" + c.flySpeed + " (ЛКМ +, ПКМ -)", () -> false, b -> {
            c.flySpeed = Math.max(1, Math.min(10, c.flySpeed + (b == 1 ? -1 : 1)));
        }));
        w.add(new Button(() -> "Freecam", () -> Cheats.freecam, b -> Cheats.freecam = Cheats.toggle(Cheats.freecam, "Freecam")).tip("Камера летает отдельно от игрока: WASD, прыжок вверх, присед вниз, бег x3"));
        w.add(new Button(() -> String.format("Скорость Freecam %.2f (ЛКМ +, ПКМ -)", c.camSpeed), () -> false, b -> {
            c.camSpeed = Math.max(0.25, Math.min(5, c.camSpeed + (b == 1 ? -0.25 : 0.25)));
        }));
        w.add(new Button(() -> "ESP", () -> EntityEsp.esp, b -> EntityEsp.esp = Cheats.toggle(EntityEsp.esp, "ESP")).tip("Рамки вокруг существ сквозь стены, кого показывать выбирается в Radar"));
        w.add(new Button(() -> "Tracers", () -> EntityEsp.tracers, b -> EntityEsp.tracers = Cheats.toggle(EntityEsp.tracers, "Tracers")).tip("Линии от прицела к существам, кого показывать выбирается в Radar"));
        w.add(new Label(() -> Panel.full() ? "Наведи на функцию: подсказка" : "Только одиночная игра и свой сервер"));
        return w;
    }

    // Движение из Nodus
    static Window movement(PanelConfig c) {
        Window w = new Window("movement", "Движение", 150, c.win("movement", 310, 230, 0));
        w.add(new Button(() -> "NoFall", () -> Cheats.noFall, b -> Cheats.noFall = Cheats.toggle(Cheats.noFall, "NoFall")).tip("Без урона от падения: сервер считает, что ты на земле"));
        w.add(new Button(() -> "Step", () -> Cheats.step, b -> Cheats.step = Cheats.toggle(Cheats.step, "Step")).tip("Заходить на блок без прыжка"));
        w.add(new Button(() -> "Jesus", () -> Cheats.jesus, b -> Cheats.jesus = Cheats.toggle(Cheats.jesus, "Jesus")).tip("Ходить по воде и лаве, присед опускает вниз"));
        w.add(new Button(() -> "Spider", () -> Cheats.spider, b -> Cheats.spider = Cheats.toggle(Cheats.spider, "Spider")).tip("Забираться по любой стене, как по лестнице"));
        w.add(new Button(() -> "Phase", () -> Cheats.phase, b -> Cheats.phase = Cheats.toggle(Cheats.phase, "Phase")).tip("Проходить сквозь блоки, вместе с Fly"));
        w.add(new Button(() -> "Timer x" + Cheats.TIMER[Cheats.timer] + " (ЛКМ +, ПКМ -)", () -> Cheats.timer > 0, b -> {
            if (!Panel.full()) {
                Panel.log("Timer: только в одиночной игре и на своём сервере");
                return;
            }
            Cheats.timer = Math.max(0, Math.min(Cheats.TIMER.length - 1, Cheats.timer + (b == 1 ? -1 : 1)));
            Panel.log("Timer: x" + Cheats.TIMER[Cheats.timer]);
        }).tip("Скорость тиков всего мира: фермы, машины и мобы быстрее"));
        w.add(new Label(() -> "Наведи на функцию: подсказка"));
        return w;
    }

    static Window world(PanelConfig c) {
        Window w = new Window("world", "Мир", 150, c.win("world", 466, 230, 4));
        w.add(new Button(() -> "FastBreak", () -> Cheats.fastBreak, b -> Cheats.fastBreak = Cheats.toggle(Cheats.fastBreak, "FastBreak")).tip("Ломать быстрее и без паузы между блоками"));
        w.add(new Button(() -> "AutoTool", () -> Cheats.autoTool, b -> Cheats.autoTool = Cheats.toggle(Cheats.autoTool, "AutoTool")).tip("Сам берёт лучший инструмент из хотбара, пока ломаешь"));
        w.add(new Button(() -> "AutoFish", () -> Cheats.autoFish, b -> Cheats.autoFish = Cheats.toggle(Cheats.autoFish, "AutoFish")).tip("Сам вытаскивает рыбу при поклёвке и забрасывает снова"));
        return w;
    }

    // Поиск блоков как Search в Nodus: рамки сквозь стены, у каждого запроса свой цвет
    static Window search(PanelConfig c) {
        Window w = new Window("search", "Search", 170, c.win("search", 158, 230, 2));
        w.add(new Button(() -> "Search", () -> BlockEsp.search, b -> {
            BlockEsp.search = Cheats.toggle(BlockEsp.search, "Search");
            BlockEsp.changed();
        }).tip("Рамки вокруг блоков из списка ниже сквозь стены"));
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
        w.add(new Label(() -> BlockEsp.xray || BlockEsp.search ? "Найдено: " + BlockEsp.count() : "Search и Xray выключены"));
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
