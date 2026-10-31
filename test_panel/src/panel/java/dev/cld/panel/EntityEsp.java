package dev.cld.panel;

import baritone.api.event.events.RenderEvent;
import baritone.utils.IRenderer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

// ESP и Tracers: рамки и линии к существам сквозь стены, фильтр и цвета как у радара
public final class EntityEsp {
    public static boolean esp, tracers;

    private EntityEsp() {}

    static void render(RenderEvent ev) {
        Minecraft mc = Minecraft.getInstance();
        if (!(esp || tracers) || mc.player == null || mc.level == null || !Panel.full()) return;
        float pt = ev.getPartialTicks();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 eye = cam.getPosition().add(new Vec3(cam.getLookVector()));
        BufferBuilder bb = IRenderer.startLines(Color.WHITE, 0.9f, 2f, true);
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity) || e == mc.player || !e.isAlive() || e.distanceToSqr(mc.player) > 128 * 128) continue;
            if (!ToolWindows.radarMatch(e)) continue;
            int c = ToolWindows.entityColor(e);
            IRenderer.color[0] = (c >> 16 & 255) / 255f;
            IRenderer.color[1] = (c >> 8 & 255) / 255f;
            IRenderer.color[2] = (c & 255) / 255f;
            IRenderer.color[3] = 0.9f;
            // позиция между тиками, чтобы рамка не дрожала
            Vec3 at = e.getPosition(pt);
            AABB box = e.getBoundingBox().move(at.subtract(e.position()));
            if (esp) IRenderer.emitAABB(bb, ev.getModelViewStack(), box);
            if (tracers) IRenderer.emitLine(bb, ev.getModelViewStack(), eye, box.getCenter());
        }
        IRenderer.endLines(bb, true);
    }
}
