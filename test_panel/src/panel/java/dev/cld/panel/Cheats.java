package dev.cld.panel;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Abilities;

import java.util.UUID;
import java.util.function.Consumer;

// Функции из Nodus: яркость, полёт, свободная камера. Работают только в одиночной игре и на своих серверах.
public final class Cheats {
    public static boolean fullbright, flight, freecam;
    private static boolean brightNow, flyApplied;
    private static RemotePlayer cam;
    private static ClientLevel camLevel;

    private Cheats() {}

    // Для миксина LightTexture, считается раз в тик
    public static boolean bright() {
        return brightNow;
    }

    // Включить с проверкой, где можно
    static boolean toggle(boolean now, String name) {
        if (!now && !Panel.full()) {
            say(name + ": только в одиночной игре и на своём сервере");
            return false;
        }
        say(name + ": " + (!now ? "вкл" : "выкл"));
        return !now;
    }

    // В консоль панели и над хотбаром, чтобы было видно и при включении клавишей
    private static void say(String s) {
        Panel.log(s);
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(net.minecraft.network.chat.Component.literal(s), true);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        boolean ok = p != null && mc.level != null && Panel.full();
        brightNow = fullbright && ok;
        if (p == null || mc.level == null) {
            cam = null;
            flyApplied = false;
            return;
        }
        fly(p, flight && ok);
        // после смерти или смены мира игра сама ставит камеру на игрока, тогда выключаем свою
        if (cam != null && (!freecam || !ok || camLevel != mc.level || mc.getCameraEntity() != cam)) stopCam(mc, p);
        else if (cam == null && freecam && ok) startCam(mc, p);
        if (cam != null) moveCam(mc);
    }

    private static void fly(LocalPlayer p, boolean on) {
        Abilities a = p.getAbilities();
        if (on) {
            if (!flyApplied) {
                flyApplied = true;
                // в одиночной игре сервер тоже разрешает полёт, тогда нет урона от падения
                server(p, sp -> sp.getAbilities().mayfly = true);
                a.mayfly = true;
                a.flying = true;
                p.onUpdateAbilities();
            }
            a.mayfly = true;
            a.setFlyingSpeed(0.05f * PanelConfig.get().flySpeed);
        } else if (flyApplied) {
            flyApplied = false;
            a.setFlyingSpeed(0.05f);
            if (!p.isCreative() && !p.isSpectator()) {
                a.mayfly = false;
                a.flying = false;
            }
            p.onUpdateAbilities();
            server(p, sp -> {
                if (!sp.isCreative() && !sp.isSpectator()) {
                    sp.getAbilities().mayfly = false;
                    sp.getAbilities().flying = false;
                }
                sp.onUpdateAbilities();
            });
        }
    }

    private static void server(LocalPlayer p, Consumer<ServerPlayer> f) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s == null) return;
        UUID id = p.getUUID();
        s.execute(() -> {
            ServerPlayer sp = s.getPlayerList().getPlayer(id);
            if (sp != null) f.accept(sp);
        });
    }

    // Камера отдельной сущностью вне мира, игрок стоит на месте (или идёт по Baritone)
    private static void startCam(Minecraft mc, LocalPlayer p) {
        cam = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "cld_cam"));
        cam.copyPosition(p);
        cam.setOldPosAndRot();
        cam.noPhysics = true;
        camLevel = mc.level;
        mc.setCameraEntity(cam);
        mc.gameRenderer.setRenderHand(false);
        // пустое управление: клавиши двигают камеру. Baritone, если ведёт игрока, поставит своё
        if (p.input instanceof KeyboardInput) p.input = new Input();
    }

    private static void stopCam(Minecraft mc, LocalPlayer p) {
        RemotePlayer old = cam;
        cam = null;
        if (mc.getCameraEntity() == old || mc.getCameraEntity() == null) mc.setCameraEntity(p);
        mc.gameRenderer.setRenderHand(true);
        if (p.input.getClass() == Input.class) p.input = new KeyboardInput(mc.options);
    }

    private static void moveCam(Minecraft mc) {
        cam.setOldPosAndRot();
        if (mc.screen != null) return;
        Options o = mc.options;
        int f = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        int s = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
        int v = (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0);
        double sp = PanelConfig.get().camSpeed * (o.keySprint.isDown() ? 3 : 1);
        double yaw = Math.toRadians(cam.getYRot());
        double dx = (-Math.sin(yaw) * f + Math.cos(yaw) * s) * sp;
        double dz = (Math.cos(yaw) * f + Math.sin(yaw) * s) * sp;
        cam.setPos(cam.getX() + dx, cam.getY() + v * sp, cam.getZ() + dz);
    }

    // Для миксина Entity.turn: поворот мыши уходит камере
    public static boolean turnCamera(Entity e, double yaw, double pitch) {
        if (cam == null || e != Minecraft.getInstance().player) return false;
        cam.turn(yaw, pitch);
        return true;
    }
}
