package dev.cld.questjei;

import dev.ftb.mods.ftbquests.FTBQuests;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// JEI сам находит этот класс по аннотации и отдаёт нам свой runtime
@JeiPlugin
public class QuestJeiPlugin implements IModPlugin {
    static final Logger LOG = LoggerFactory.getLogger("cld_questjei");
    static volatile IJeiRuntime runtime;
    private static boolean registered;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("cld_questjei", "main");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime rt) {
        runtime = rt;
        if (registered) return;
        registered = true;
        // FTB XMod Compat делает то же самое, второй помощник квесты не принимают
        if (ModList.get().isLoaded("ftbxmodcompat")) {
            LOG.info("ftbxmodcompat installed, quest JEI buttons come from it");
            return;
        }
        try {
            FTBQuests.setRecipeModHelper(new JeiQuestHelper());
            LOG.info("quest items now open JEI recipes");
        } catch (IllegalStateException e) {
            LOG.info("other recipe helper already set for FTB Quests, skipping");
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }
}
