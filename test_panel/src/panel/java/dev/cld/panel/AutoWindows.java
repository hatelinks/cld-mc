package dev.cld.panel;

import dev.cld.panel.ui.Element.Button;
import dev.cld.panel.ui.Element.Input;
import dev.cld.panel.ui.Element.Label;
import dev.cld.panel.ui.Element.ListView;
import dev.cld.panel.ui.Element.ListView.Row;
import dev.cld.panel.ui.Theme;
import dev.cld.panel.ui.Window;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

// Окна автоматизации: AutoEat, AutoReplenish, Eject и макросы.
final class AutoWindows {
    private AutoWindows() {}

    static Window automation(PanelConfig c) {
        Window w = new Window("auto", "Automation", 160, c.win("auto", 622, 230, 3));
        w.add(new Button(() -> "AutoEat", () -> Automation.autoEat, b -> Automation.autoEat = Cheats.toggle(Automation.autoEat, "AutoEat"))
                .tip("Ест, когда голод упал до порога: берёт самую сытную еду из хотбара или рюкзака, Baritone на это время стоит. Гнилую плоть, паучий глаз и ядовитую еду не ест"));
        w.add(new Button(() -> "Есть при голоде " + c.eatAt + "/20", () -> false, b -> {
            c.eatAt = Math.max(2, Math.min(19, c.eatAt + (b == 1 ? -1 : 1)));
            c.save();
        }).tip("Полная сытость 20. ЛКМ больше, ПКМ меньше"));
        w.add(new Button(() -> "AutoReplenish", () -> Automation.replenish, b -> Automation.replenish = Cheats.toggle(Automation.replenish, "AutoReplenish"))
                .tip("Пополняет хотбар из рюкзака: кончился стак или сломался инструмент, кладёт такой же"));
        w.add(new Button(() -> "Пополнять при " + c.refill + " и меньше", () -> false, b -> {
            int i = 0;
            while (i < Automation.REFILL.length - 1 && Automation.REFILL[i] < c.refill) i++;
            i = Math.max(0, Math.min(Automation.REFILL.length - 1, i + (b == 1 ? -1 : 1)));
            c.refill = Automation.REFILL[i];
            c.save();
        }).tip("Сколько штук в стаке хотбара считать мало. ЛКМ больше, ПКМ меньше"));
        w.add(new Button(() -> "Eject", () -> Automation.eject, b -> Automation.eject = Cheats.toggle(Automation.eject, "Eject"))
                .tip("Выбрасывает предметы из списка ниже, пока инвентарь закрыт"));
        w.add(new Button(() -> "Оставлять один стак", () -> c.ejectKeep, b -> {
            c.ejectKeep = !c.ejectKeep;
            c.save();
        }).tip("Первый стак каждого предмета из списка остаётся: Baritone строит мосты и столбы из камня и земли"));
        w.add(new Input("предмет или #тег, Enter", false, t -> add(c, t)));
        w.add(new Button("Камень, земля, гравий", () -> {
            for (String s : new String[]{"cobblestone", "cobbled_deepslate", "dirt", "gravel", "granite", "diorite", "andesite", "tuff", "netherrack"}) add(c, s);
        }).tip("Добавить в список то, что копает Baritone по пути"));
        w.add(new Button("Добыча с мобов", () -> {
            for (String s : new String[]{"rotten_flesh", "poisonous_potato", "spider_eye", "bone", "string", "arrow"}) add(c, s);
        }));
        w.add(new ListView(4, () -> {
            List<Row> rows = new ArrayList<>();
            for (String q : c.eject) rows.add(new Row(q, 0xFFE0E0E0, b -> {
                c.eject.remove(q);
                c.save();
            }, () -> "Клик убирает из списка"));
            return rows;
        }));
        w.add(new Label(() -> !Panel.full() ? "Только одиночная игра и свой сервер"
                : Automation.eating() ? "Ем: " + Automation.eatingName : "Наведи на функцию: подсказка"));
        return w;
    }

    private static void add(PanelConfig c, String q) {
        q = Automation.norm(q);
        if (q.isEmpty() || c.eject.contains(q)) return;
        if (!Automation.known(q)) {
            Panel.log("Нет предмета " + q);
            return;
        }
        c.eject.add(q);
        c.save();
    }

    // Повтор в редакторе макроса, сохраняется вместе с макросом
    private static int editRepeat;

    static Window macros(PanelConfig c) {
        Window w = new Window("macros", "Macros", 170, c.win("macros", 622, 400, 1));
        Input name = new Input("имя макроса", true, t -> {});
        Input steps = new Input("шаги через ;", true, t -> {});
        Input key = new Input("клавиша, например m или f6", true, t -> {});
        steps.limit(1000);
        w.add(new ListView(5, () -> {
            List<Row> rows = new ArrayList<>();
            for (PanelConfig.Macro m : c.macros) {
                boolean on = Macros.running(m);
                String k = m.key >= 0 ? " [" + Keys.name(m.key) + "]" : "";
                rows.add(new Row(m.name + k + (on ? "  идёт" : ""), on ? Theme.accent(0xFF) : 0xFFE0E0E0, b -> {
                    if (b == 0) Macros.toggle(m);
                    else if (Screen.hasShiftDown()) {
                        if (on) Macros.toggle(m);
                        c.macros.remove(m);
                        c.save();
                        Panel.log("Макрос " + m.name + " удалён");
                    } else {
                        name.set(m.name);
                        steps.set(m.steps);
                        key.set(m.key >= 0 ? Keys.name(m.key).toLowerCase() : "");
                        editRepeat = m.repeat;
                    }
                }, () -> m.steps + (m.repeat > 0 ? "\nПовтор через " + m.repeat + " с" : "") + "\nЛКМ запустить или остановить, ПКМ править, Shift+ПКМ удалить"));
            }
            return rows;
        }));
        w.add(name.tip("Новое имя создаёт макрос, старое заменяет"));
        w.add(steps.tip(Macros.HELP));
        w.add(key.tip("Клавиша запуска в игре, пусто без клавиши. Вторым нажатием макрос останавливается"));
        w.add(new Button(() -> editRepeat > 0 ? "Повтор через " + editRepeat + " с" : "Повтор: нет", () -> editRepeat > 0, b -> {
            int i = 0;
            while (i < Macros.REPEAT.length - 1 && Macros.REPEAT[i] < editRepeat) i++;
            editRepeat = Macros.REPEAT[Math.max(0, Math.min(Macros.REPEAT.length - 1, i + (b == 1 ? -1 : 1)))];
        }).tip("Макрос запускается снова через столько секунд после конца. ЛКМ больше, ПКМ меньше"));
        w.add(new Button("Сохранить", () -> save(c, name, steps, key)));
        w.add(new Button(() -> Macros.count() > 0 ? "Остановить все (" + Macros.count() + ")" : "Остановить все", () -> false, b -> Macros.stopAll()));
        w.add(new Button("Вернуть готовые макросы", () -> {
            int n = Macros.addPresets(c);
            c.save();
            Panel.log(n == 0 ? "Все готовые макросы уже есть" : "Добавлено готовых макросов: " + n);
        }).tip("Добавляет удалённые готовые макросы обратно, свои и изменённые не трогает"));
        return w;
    }

    private static void save(PanelConfig c, Input name, Input steps, Input key) {
        String n = name.value().trim();
        if (n.isEmpty() || Macros.parse(steps.value()).isEmpty()) {
            Panel.log("Макрос: нужны имя и хотя бы один шаг");
            return;
        }
        int k = key.value().isBlank() ? -1 : Keys.parse(key.value());
        if (!key.value().isBlank() && k < 0) {
            Panel.log("Не знаю клавишу " + key.value());
            return;
        }
        PanelConfig.Macro m = Macros.find(n);
        if (m == null) {
            m = new PanelConfig.Macro();
            c.macros.add(m);
        }
        m.name = n;
        m.steps = steps.value().trim();
        m.key = k;
        m.repeat = editRepeat;
        c.save();
        Panel.log("Макрос " + n + " сохранён: " + Macros.parse(m.steps).size() + " шагов");
    }
}
