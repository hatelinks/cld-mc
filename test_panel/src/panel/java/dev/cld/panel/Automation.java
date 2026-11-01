package dev.cld.panel;

import baritone.Baritone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Set;

// Автоматизация из Impact и Meteor (идеи, код свой): AutoEat, AutoReplenish, Eject.
// Только одиночная игра и свои серверы. Baritone на время еды и перекладки встаёт на паузу.
public final class Automation {
    public static boolean autoEat, replenish, eject;
    // Порог пополнения хотбара, выбирается кнопкой
    static final int[] REFILL = {1, 4, 8, 16, 32};
    // Еда, от которой плохо: AutoEat её не берёт
    private static final Set<String> BAD = Set.of("rotten_flesh", "spider_eye", "poisonous_potato", "pufferfish", "suspicious_stew", "chorus_fruit");

    // Состояние еды: слот до еды, откуда взяли еду из инвентаря (-1 из хотбара), сколько тиков едим
    private static boolean eating;
    private static int prevSlot, swappedFrom = -1, eatTicks, waitTicks, cooldown;
    static String eatingName = "";
    // Что лежало в хотбаре прошлый тик, чтобы заметить кончившийся стак или сломанный инструмент
    private static final ItemStack[] last = new ItemStack[9];
    private static int invTick;

    private Automation() {}

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null || !Panel.full()) {
            if (eating) stopEating(mc, p);
            return;
        }
        if (cooldown > 0) cooldown--;
        if (eating || (autoEat && cooldown == 0 && hungry(p))) eat(mc, p);
        else waitTicks = 0;
        // в открытом инвентаре игрок раскладывает сам, не мешаем
        boolean free = !eating && (mc.screen == null || mc.screen instanceof PanelScreen) && p.containerMenu == p.inventoryMenu && p.containerMenu.getCarried().isEmpty() && !p.isUsingItem();
        if (!free) {
            snapshot(p);
            return;
        }
        if (++invTick % 2 != 0) return;
        if (eject && ejectOne(mc, p)) return;
        if (replenish && invTick % 4 == 0) refill(mc, p);
        snapshot(p);
    }

    // Пауза Baritone: true, когда он стоит или ничем не занят
    private static boolean baritoneStopped() {
        try {
            Baritone b = (Baritone) Panel.baritone();
            return b.getInventoryPauserProcess().stationaryForInventoryMove() || !b.getPathingBehavior().isPathing();
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean hungry(LocalPlayer p) {
        return !p.getAbilities().invulnerable && p.getFoodData().getFoodLevel() <= PanelConfig.get().eatAt && p.canEat(false);
    }

    private static boolean food(ItemStack s) {
        if (s.isEmpty()) return false;
        FoodProperties f = s.get(DataComponents.FOOD);
        return f != null && !BAD.contains(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath());
    }

    private static int score(ItemStack s) {
        FoodProperties f = s.get(DataComponents.FOOD);
        return f == null ? -1 : f.nutrition() * 10 + (int) (f.saturation() * 4);
    }

    // Лучшая еда: индекс в инвентаре 0..35, хотбар 0..8
    private static int bestFood(Inventory inv) {
        int best = -1, bs = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.items.get(i);
            if (!food(s)) continue;
            // из хотбара удобнее, ему немного больше очков
            int sc = score(s) + (i < 9 ? 1 : 0);
            if (sc > bs) {
                bs = sc;
                best = i;
            }
        }
        return best;
    }

    private static void eat(Minecraft mc, LocalPlayer p) {
        // ждём, пока Baritone остановится, но не дольше 2 секунд
        if (!baritoneStopped() && waitTicks++ < 40) return;
        Inventory inv = p.getInventory();
        if (!eating) {
            int i = bestFood(inv);
            if (i < 0) {
                cooldown = 200;
                waitTicks = 0;
                Panel.log("AutoEat: нет еды в инвентаре");
                return;
            }
            prevSlot = inv.selected;
            swappedFrom = -1;
            if (i < 9) inv.selected = i;
            else {
                // еда из рюкзака в текущий слот хотбара, после еды вернём обратно
                mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, i, inv.selected, ClickType.SWAP, p);
                swappedFrom = i;
            }
            eating = true;
            eatTicks = 0;
            eatingName = inv.getSelected().getHoverName().getString();
            mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        }
        eatTicks++;
        mc.options.keyUse.setDown(true);
        if (p.isUsingItem()) return;
        // доели один кусок: если ещё голодны и в руке еда, едим дальше
        if (eatTicks > 2 && eatTicks < 400 && hungry(p) && food(inv.getSelected())) {
            mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
            return;
        }
        if (eatTicks > 2 || eatTicks >= 400) stopEating(mc, p);
    }

    private static void stopEating(Minecraft mc, LocalPlayer p) {
        eating = false;
        waitTicks = 0;
        cooldown = 20;
        eatingName = "";
        mc.options.keyUse.setDown(false);
        if (p == null || mc.gameMode == null) return;
        if (p.isUsingItem()) mc.gameMode.releaseUsingItem(p);
        Inventory inv = p.getInventory();
        if (swappedFrom >= 0) mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, swappedFrom, inv.selected, ClickType.SWAP, p);
        else inv.selected = prevSlot;
        swappedFrom = -1;
        snapshot(p);
    }

    static boolean eating() {
        return eating;
    }

    // Предмет под запрос: полный id, имя без мода или #тег
    static boolean matches(ItemStack s, String q) {
        if (s.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
        if (q.startsWith("#")) {
            ResourceLocation t = ResourceLocation.tryParse(q.substring(1));
            return t != null && s.is(TagKey.create(Registries.ITEM, t));
        }
        return q.contains(":") ? id.toString().equals(q) : id.getPath().equals(q);
    }

    static boolean known(String q) {
        if (q.startsWith("#")) return ResourceLocation.tryParse(q.substring(1)) != null;
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet())
            if (q.contains(":") ? id.toString().equals(q) : id.getPath().equals(q)) return true;
        return false;
    }

    // Выбросить один стак мусора; с "оставлять стак" первый стак каждого вида остаётся (Baritone строит из камня)
    private static boolean ejectOne(Minecraft mc, LocalPlayer p) {
        PanelConfig c = PanelConfig.get();
        if (c.eject.isEmpty()) return false;
        Inventory inv = p.getInventory();
        for (String q : c.eject) {
            boolean kept = !c.ejectKeep;
            // хотбар в конце, чтобы оставался стак под рукой
            for (int k = 0; k < 36; k++) {
                int i = (k + 9) % 36;
                ItemStack s = inv.items.get(i);
                if (!matches(s, q)) continue;
                if (!kept) {
                    kept = true;
                    continue;
                }
                if (!baritoneStopped()) return true;
                // в меню инвентаря хотбар это слоты 36..44, рюкзак 9..35
                int slot = i < 9 ? 36 + i : i;
                mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, slot, 1, ClickType.THROW, p);
                return true;
            }
        }
        return false;
    }

    // Пополнение хотбара из рюкзака: кончился стак или сломался инструмент, кладём такой же
    private static void refill(Minecraft mc, LocalPlayer p) {
        Inventory inv = p.getInventory();
        int at = PanelConfig.get().refill;
        for (int h = 0; h < 9; h++) {
            ItemStack was = last[h], now = inv.items.get(h);
            if (was == null || was.isEmpty()) continue;
            boolean emptied = now.isEmpty();
            boolean low = !emptied && now.isStackable() && ItemStack.isSameItemSameComponents(now, was) && now.getCount() <= at && now.getCount() < now.getMaxStackSize();
            if (!emptied && !low) continue;
            int from = -1;
            for (int i = 9; i < 36; i++) {
                ItemStack s = inv.items.get(i);
                if (s.isEmpty()) continue;
                boolean same = was.isStackable() ? ItemStack.isSameItemSameComponents(s, was) : s.getItem() == was.getItem();
                if (same) {
                    from = i;
                    break;
                }
            }
            if (from < 0) continue;
            if (!baritoneStopped()) return;
            if (emptied) mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, from, h, ClickType.SWAP, p);
            // мало осталось: shift-клик кладёт стак из рюкзака в хотбар к такому же
            else mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, from, 0, ClickType.QUICK_MOVE, p);
            return;
        }
    }

    private static void snapshot(LocalPlayer p) {
        if (p == null) return;
        List<ItemStack> items = p.getInventory().items;
        for (int h = 0; h < 9; h++) last[h] = items.get(h).copy();
    }

    static String norm(String q) {
        return q.trim().toLowerCase(Locale.ROOT);
    }
}
