package dev.cld.bombs;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

// Прокачка на верстаке: одна бомба уровня N и ровно N+1 пороха (TNT считается за 5) дают бомбу уровня N+1.
public final class BombUpgradeRecipe extends CustomRecipe {
    public BombUpgradeRecipe(CraftingBookCategory category) {
        super(category);
    }

    // бомба из сетки или EMPTY, если набор не подходит
    private static ItemStack find(CraftingInput in) {
        if (!Cfg.b(Cfg.ENABLED)) return ItemStack.EMPTY;
        ItemStack bomb = ItemStack.EMPTY;
        int powder = 0;
        for (int i = 0; i < in.size(); i++) {
            ItemStack s = in.getItem(i);
            if (s.isEmpty()) continue;
            if (Levels.isBomb(s)) {
                if (!bomb.isEmpty()) return ItemStack.EMPTY;
                bomb = s;
            } else if (s.is(Items.GUNPOWDER)) {
                powder += 1;
            } else if (s.is(Items.TNT)) {
                powder += Cfg.i(Cfg.TNT_POWDER);
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (bomb.isEmpty() || powder == 0) return ItemStack.EMPTY;
        int next = Levels.of(bomb) + 1;
        if (next > Levels.max() || powder != Levels.cost(next)) return ItemStack.EMPTY;
        return bomb;
    }

    @Override
    public boolean matches(CraftingInput in, Level level) {
        return !find(in).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput in, HolderLookup.Provider registries) {
        ItemStack bomb = find(in);
        if (bomb.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = bomb.copyWithCount(1);
        Levels.set(out, Levels.of(bomb) + 1);
        return out;
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
