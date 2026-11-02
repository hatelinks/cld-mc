package dev.cld.questjei;

import dev.architectury.fluid.FluidStack;
import dev.architectury.hooks.fluid.forge.FluidStackHooksForge;
import dev.ftb.mods.ftblibrary.config.Tristate;
import dev.ftb.mods.ftblibrary.ui.input.Key;
import dev.ftb.mods.ftbquests.integration.RecipeModHelper;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IBookmarkManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

// Помощник для FTB Quests: клик по предмету в квесте открывает JEI.
// Обычный клик показывает рецепт, с Shift показывает где предмет используется.
public class JeiQuestHelper implements RecipeModHelper {

    @Override
    public boolean isRecipeModAvailable() {
        return QuestJeiPlugin.runtime != null;
    }

    @Override
    public void showRecipes(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        show(VanillaTypes.ITEM_STACK, stack.copyWithCount(1));
    }

    @Override
    public void showRecipes(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return;
        show(NeoForgeTypes.FLUID_STACK, FluidStackHooksForge.toForge(stack));
    }

    private <T> void show(IIngredientType<T> type, T value) {
        IJeiRuntime rt = QuestJeiPlugin.runtime;
        if (rt == null) return;
        RecipeIngredientRole role = Screen.hasShiftDown() ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT;
        try {
            rt.getRecipesGui().show(rt.getJeiHelpers().getFocusFactory().createFocus(role, type, value));
        } catch (RuntimeException e) {
            QuestJeiPlugin.LOG.warn("JEI could not open recipes", e);
        }
    }

    @Override
    public Tristate toggleBookmark(ItemStack stack) {
        IJeiRuntime rt = QuestJeiPlugin.runtime;
        if (rt == null || stack.isEmpty()) return Tristate.DEFAULT;
        Optional<ITypedIngredient<ItemStack>> ing = rt.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, stack.copyWithCount(1), true);
        if (ing.isEmpty()) return Tristate.DEFAULT;
        IBookmarkManager bm = rt.getBookmarkManager();
        if (bm.contains(ing.get())) {
            bm.remove(ing.get());
            return Tristate.FALSE;
        }
        bm.add(ing.get());
        return Tristate.TRUE;
    }

    @Override
    public boolean isBookmarkKey(Key key) {
        IJeiRuntime rt = QuestJeiPlugin.runtime;
        return rt != null && rt.getKeyMappings().getBookmark().isActiveAndMatches(key.getInputMapping());
    }

    @Override
    public String getHelperName() {
        return "JEI";
    }

    // Своих категорий квестов в JEI не делаем, обновлять нечего
    @Override
    public void refreshAll(Components components) {
    }

    @Override
    public void refreshRecipes(QuestObjectBase object) {
    }
}
