package dev.cld.bombs;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

// Прокачка на верстаке: одна бомба и сколько угодно пороха и TNT в любых клетках (TNT считается за 5 пороха).
// Уровень растёт, пока хватает пороха (уровень N стоит N), лишний порох возвращается.
public final class BombUpgradeRecipe extends CustomRecipe {
    public BombUpgradeRecipe(CraftingBookCategory category) {
        super(category);
    }

    // бомба, порох всего и уровень после прокачки; null, если набор не подходит
    private record Plan(ItemStack bomb, int powder, int level, int left) {}

    private static Plan plan(CraftingInput in) {
        if (!Cfg.b(Cfg.ENABLED)) return null;
        ItemStack bomb = ItemStack.EMPTY;
        int powder = 0;
        for (int i = 0; i < in.size(); i++) {
            ItemStack s = in.getItem(i);
            if (s.isEmpty()) continue;
            if (Levels.isBomb(s)) {
                if (!bomb.isEmpty()) return null;
                bomb = s;
            } else if (s.is(Items.GUNPOWDER)) {
                powder += 1;
            } else if (s.is(Items.TNT)) {
                powder += Cfg.i(Cfg.TNT_POWDER);
            } else {
                return null;
            }
        }
        if (bomb.isEmpty() || powder == 0) return null;
        int level = Levels.of(bomb), left = powder;
        while (level < Levels.max() && left >= Levels.cost(level + 1)) {
            level++;
            left -= Levels.cost(level);
        }
        if (level == Levels.of(bomb)) return null;
        return new Plan(bomb, powder, level, left);
    }

    @Override
    public boolean matches(CraftingInput in, Level level) {
        return plan(in) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput in, HolderLookup.Provider registries) {
        Plan p = plan(in);
        if (p == null) return ItemStack.EMPTY;
        ItemStack out = p.bomb().copyWithCount(1);
        Levels.set(out, p.level());
        return out;
    }

    // Остаток пороха кладётся обратно в клетку с порохом, а если его нет, в клетку с TNT (если места нет, игра отдаст его в инвентарь).
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput in) {
        NonNullList<ItemStack> rest = super.getRemainingItems(in);
        Plan p = plan(in);
        if (p == null || p.left() <= 0) return rest;
        int slot = -1;
        for (int i = 0; i < in.size(); i++) {
            ItemStack s = in.getItem(i);
            if (s.is(Items.GUNPOWDER)) { slot = i; break; }
            if (slot < 0 && s.is(Items.TNT)) slot = i;
        }
        if (slot >= 0) rest.set(slot, new ItemStack(Items.GUNPOWDER, p.left()));
        return rest;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return w * h >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BombsMod.UPGRADE.get();
    }
}
