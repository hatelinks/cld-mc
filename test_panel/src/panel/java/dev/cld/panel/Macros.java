package dev.cld.panel;

import dev.cld.panel.ui.Element;
import dev.cld.panel.ui.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Макросы: цепочка команд с паузами, запуск кнопкой или клавишей, повтор через N секунд.
// Шаги через ; или с новой строки. Только одиночная игра и свои серверы.
public final class Macros {
    static final String HELP = "Шаги через ;  команда Baritone (goto 0 70 0, mine iron_ore), wait 20 (тиков) или wait 2s, waitidle (ждать, пока Baritone закончит), slot 1..9, use (ПКМ предметом), on AutoEat и off AutoEat (включить или выключить функцию панели), toggle AutoEat (переключить), /команда сервера, say текст, команды с точкой, end (закончить макрос). stop это команда Baritone: остановить его";
    // Повтор: 0 значит один раз
    static final int[] REPEAT = {0, 5, 10, 30, 60, 300, 600};

    private static final class Run {
        final PanelConfig.Macro m;
        final List<String> steps;
        int i, wait, idle;
        boolean waitIdle;

        Run(PanelConfig.Macro m) {
            this.m = m;
            steps = parse(m.steps);
        }
    }

    private static final List<Run> running = new ArrayList<>();

    // Готовые макросы, выбранные владельцем в анкете 01.11: имя, шаги, повтор в секундах
    private static final String KEEP = "on AutoEat; on AutoReplenish; on Eject; ";
    static final String[][] PRESETS = {
        {"Алмазы", KEEP + "mine diamond_ore deepslate_diamond_ore", "0"},
        {"Железо", KEEP + "mine iron_ore deepslate_iron_ore", "0"},
        {"Уголь", KEEP + "mine coal_ore deepslate_coal_ore", "0"},
        {"Медь", KEEP + "mine copper_ore deepslate_copper_ore", "0"},
        {"Золото", KEEP + "mine gold_ore deepslate_gold_ore", "0"},
        {"Редстоун", KEEP + "mine redstone_ore deepslate_redstone_ore", "0"},
        {"Лазурит", KEEP + "mine lapis_ore deepslate_lapis_ore", "0"},
        {"Изумруды", KEEP + "mine emerald_ore deepslate_emerald_ore", "0"},
        {"Незер: обломки и кварц", "on AutoEat; on AutoReplenish; mine ancient_debris nether_quartz_ore", "0"},
        {"Лес", "on AutoTool; on AutoReplenish; mine oak_log birch_log spruce_log jungle_log acacia_log dark_oak_log mangrove_log cherry_log", "0"},
        {"Туннель 3x3", KEEP + "tunnel 3 3 100", "0"},
        {"Здесь дом", "sethome", "0"},
        {"Домой", "stop; surface; waitidle; home", "0"},
        {"Разведка и домой", "sethome; explore; wait 300s; stop; home", "0"},
        {"Ферма по кругу", "farm 64; waitidle", "300"},
        {"AFK рыбалка", "on AutoFish; use", "0"},
        {"Долгая работа", "on AutoEat; on AutoReplenish; on Eject; on NoFall", "0"},
        {"Осторожно", "set allowBreak false; set allowPlace false; set allowParkour false", "0"},
        {"Обычно", "set allowBreak true; set allowPlace true; set allowParkour false", "0"},
        {"Быстро", "set allowParkour true; set allowParkourPlace true; set allowSprint true", "0"},
        {"День и ясно", "/time set day; /weather clear", "0"},
        {"Квадрат 500 блоков", "goto ~500 ~; waitidle; goto ~ ~500; waitidle; goto ~-500 ~; waitidle; goto ~ ~-500; waitidle", "0"},
    };

    // Добавить готовые макросы, которых нет по имени; свои макросы и клавиши не трогаем
    static int addPresets(PanelConfig c) {
        int n = 0;
        for (String[] p : PRESETS) {
            boolean has = false;
            for (PanelConfig.Macro m : c.macros) has |= m.name.equalsIgnoreCase(p[0]);
            if (has) continue;
            PanelConfig.Macro m = new PanelConfig.Macro();
            m.name = p[0];
            m.steps = p[1];
            m.repeat = Integer.parseInt(p[2]);
            c.macros.add(m);
            n++;
        }
        return n;
    }

    private Macros() {}

    static List<String> parse(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String s : text.split("[;\n]")) {
            s = s.trim();
            if (!s.isEmpty() && !s.startsWith("//")) out.add(s);
        }
        return out;
    }

    static PanelConfig.Macro find(String name) {
        for (PanelConfig.Macro m : PanelConfig.get().macros) if (m.name.equalsIgnoreCase(name.trim())) return m;
        return null;
    }

    static boolean running(PanelConfig.Macro m) {
        for (Run r : running) if (r.m == m) return true;
        return false;
    }

    // Запуск или остановка, если уже идёт
    static void toggle(PanelConfig.Macro m) {
        if (running(m)) {
            running.removeIf(r -> r.m == m);
            Panel.log("Макрос " + m.name + " остановлен");
            return;
        }
        if (!Panel.full()) {
            Panel.log("Макросы: только в одиночной игре и на своём сервере");
            return;
        }
        Run r = new Run(m);
        if (r.steps.isEmpty()) {
            Panel.log("Макрос " + m.name + " пустой");
            return;
        }
        running.add(r);
        Panel.log("Макрос " + m.name + ": " + r.steps.size() + " шагов" + (m.repeat > 0 ? ", повтор через " + m.repeat + " с" : ""));
    }

    static void stopAll() {
        if (!running.isEmpty()) Panel.log("Макросы остановлены: " + running.size());
        running.clear();
    }

    static int count() {
        return running.size();
    }

    static void tick() {
        if (running.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Panel.full()) {
            running.clear();
            return;
        }
        for (Run r : new ArrayList<>(running)) {
            if (!running.contains(r)) continue;
            if (step(r)) running.remove(r);
        }
    }

    // true, когда макрос закончился
    private static boolean step(Run r) {
        if (r.wait > 0) {
            r.wait--;
            return false;
        }
        if (r.waitIdle) {
            // первые тики даём Baritone взяться за дело, потом ждём, пока он освободится (не дольше 10 минут)
            if (++r.idle < 5) return false;
            if (busy() && r.idle < 12000) return false;
            r.waitIdle = false;
        }
        // за тик не больше 20 шагов, чтобы цикл без пауз не повесил игру
        for (int n = 0; n < 20; n++) {
            if (r.i >= r.steps.size()) {
                if (r.m.repeat <= 0) return true;
                r.i = 0;
                r.wait = r.m.repeat * 20;
                return false;
            }
            String s = r.steps.get(r.i++);
            if (!exec(r, s)) return true;
            if (r.wait > 0 || r.waitIdle) return false;
        }
        return false;
    }

    private static boolean busy() {
        try {
            var b = Panel.baritone();
            return b.getPathingBehavior().isPathing() || b.getPathingControlManager().mostRecentInControl().isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    // Один шаг; false значит остановить макрос
    private static boolean exec(Run r, String s) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        String[] w = s.split("\\s+", 2);
        String cmd = w[0].toLowerCase(Locale.ROOT);
        String arg = w.length > 1 ? w[1].trim() : "";
        switch (cmd) {
            case "wait" -> {
                try {
                    String a = arg.toLowerCase(Locale.ROOT);
                    r.wait = a.endsWith("s") ? (int) Math.round(Double.parseDouble(a.substring(0, a.length() - 1)) * 20) : Integer.parseInt(a);
                } catch (Exception e) {
                    Panel.log("Макрос " + r.m.name + ": не понял " + s);
                    return false;
                }
            }
            case "waitidle" -> {
                r.waitIdle = true;
                r.idle = 0;
            }
            case "slot" -> {
                try {
                    int n = Integer.parseInt(arg);
                    if (n >= 1 && n <= 9) p.getInventory().selected = n - 1;
                } catch (Exception e) {
                    Panel.log("Макрос " + r.m.name + ": slot 1..9");
                }
            }
            case "use" -> mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
            case "toggle", "on", "off" -> {
                Boolean want = cmd.equals("on") ? Boolean.TRUE : cmd.equals("off") ? Boolean.FALSE : null;
                if (!pressButton(arg, want)) Panel.log("Макрос " + r.m.name + ": нет кнопки " + arg);
            }
            case "say" -> p.connection.sendChat(arg);
            case "end" -> {
                return false;
            }
            default -> {
                if (s.startsWith("/")) p.connection.sendCommand(s.substring(1));
                else if (s.startsWith(".")) Keys.dotCommand(s);
                else Panel.run(s.startsWith("#") ? s.substring(1) : s);
            }
        }
        return true;
    }

    // Нажать кнопку панели по надписи: сначала точное совпадение, потом начало (Timer x1 по слову timer)
    // want: null переключить, иначе нажать только если состояние другое
    static boolean pressButton(String name, Boolean want) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (n.isEmpty()) return false;
        for (int pass = 0; pass < 2; pass++)
            for (Window w : Panel.windows())
                for (Element e : w.rows) {
                    if (!(e instanceof Element.Button b)) continue;
                    String t = b.text().toLowerCase(Locale.ROOT);
                    if (pass == 0 ? t.equals(n) : t.startsWith(n)) {
                        if (want == null || b.isOn() != want) e.click(0, 0, 0, 0, w.width, 0);
                        return true;
                    }
                }
        return false;
    }
}
