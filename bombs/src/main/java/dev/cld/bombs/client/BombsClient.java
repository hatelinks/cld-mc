package dev.cld.bombs.client;

import dev.cld.bombs.Cfg;
import dev.cld.bombs.Levels;
import net.mehvahdjukaar.supplementaries.common.items.BombItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

// Клиент: экран настроек (общий экран "Наши моды" откроет его же) и подсказка с радиусом.
public final class BombsClient {
    private BombsClient() {}

    public static void init(ModContainer c) {
        c.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(BombsClient::onTooltip);
    }

    private static void onTooltip(ItemTooltipEvent e) {
        ItemStack s = e.getItemStack();
        if (!(s.getItem() instanceof BombItem bomb) || !Cfg.b(Cfg.ENABLED)) return;
        int l = Levels.of(s);
        double r = Levels.radius(bomb.getType(), l);
        e.getToolTip().add(Component.translatable("tooltip.cld_bombs.radius", String.format("%.1f", r)).withStyle(ChatFormatting.GRAY));
        if (l >= Cfg.i(Cfg.WATER_LEVEL)) e.getToolTip().add(Component.translatable("tooltip.cld_bombs.water").withStyle(ChatFormatting.BLUE));
        if (l >= Cfg.i(Cfg.FLUID_LEVEL)) e.getToolTip().add(Component.translatable("tooltip.cld_bombs.fluids").withStyle(ChatFormatting.GOLD));
        if (Levels.breaksObsidian(l)) e.getToolTip().add(Component.translatable("tooltip.cld_bombs.obsidian").withStyle(ChatFormatting.DARK_PURPLE));
        if (l < Levels.max()) {
            e.getToolTip().add(Component.translatable("tooltip.cld_bombs.upgrade", Levels.cost(l + 1), Cfg.i(Cfg.TNT_POWDER)).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
