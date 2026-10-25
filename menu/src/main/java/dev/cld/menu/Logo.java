package dev.cld.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Название сборки шрифтом Minecraft: объёмные изумрудные «бока» и тёмная обводка. */
public final class Logo {
    private static final int[] DEPTH = {0x173D26, 0x1C4A2E, 0x215736, 0x27653F};
    private static final int OUTLINE = 0x0B0F0C;
    private static final int FACE = 0xECE7DA;

    private Logo() {}

    public static void render(GuiGraphics g, int width, float alpha, int y) {
        if (alpha < 0.02f) return;
        Font font = Minecraft.getInstance().font;
        String name = Component.translatable("cld_menu.title").getString();
        int tw = font.width(name);
        float scale = Math.max(1f, Math.min(4f, (width - 40f) / tw));
        int a = Math.round(alpha * 255) << 24;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2f, y, 0);
        pose.scale(scale, scale, 1f);
        float px = 1f / scale; // один экранный пиксель в единицах шрифта
        float x = -tw / 2f;
        for (int d = DEPTH.length; d >= 1; d--) {
            draw(g, font, name, x + d * px, d * px, DEPTH[DEPTH.length - d] | a);
        }
        for (int[] o : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            draw(g, font, name, x + o[0] * px, o[1] * px, OUTLINE | a);
        }
        draw(g, font, name, x, 0, FACE | a);
        pose.popPose();
    }

    private static void draw(GuiGraphics g, Font font, String s, float x, float y, int colour) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        g.drawString(font, s, 0, 0, colour, false);
        pose.popPose();
    }
}
