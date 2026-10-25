package dev.cld.menu;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "cld_menu", dist = Dist.CLIENT)
public final class MenuMod {
    public MenuMod() {
        NeoForge.EVENT_BUS.addListener(TitleButtons::onInit);
    }
}
