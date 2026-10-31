package dev.cld.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Abilities;

import java.util.UUID;
import java.util.function.Consumer;

// Функции из Nodus: яркость, полёт, свободная камера. Работают только в одиночной игре и на своих серверах.
public final class Cheats {
    public static boolean fullbright, flight, freecam;
    public static boolean noFall, fastBreak, step, jesus, spider, phase, autoTool, autoFish;
    // Скорость тиков мира: 1 обычная
    static final int[] TIMER = {1, 2, 3, 5, 10};
    static int timer;
    private static int timerApplied;
    private static boolean brightNow, flyApplied, ok;
    // Phase на встроенном сервере: тот же игрок по UUID
    private static volatile boolean phaseServer;
    private static volatile UUID phaseId;
    private static int fishWait;
    private static final net.minecraft.resources.ResourceLocation STEP_ID = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cld_test_panel", "step");
    private static Entity cam;
    private static ClientLevel camLevel;

    private Cheats() {}

    // Для миксина LightTexture, считается раз в тик
    public static boolean bright() {
        return brightNow;
    }

    // Jesus держит над жидкостью: присед опускает в воду, падение с высоты уходит в воду (без урона)
    public static boolean jesusStands(Entity e) {
        return jesus && ok && e != null && e == Minecraft.getInstance().player && !e.isShiftKeyDown() && e.fallDistance <= 3;
    }

    public static boolean spider() {
        return spider && ok;
    }

    public static boolean fastBreak() {
        return fastBreak && ok;
    }

    // Для пакета движения: падаем больше 2 блоков, полёт выключен
    public static boolean noFallNow() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        return noFall && ok && p != null && mc.isSameThread() && p.fallDistance > 2 && !p.getAbilities().flying;
    }

    // Игрок с Phase: на клиенте наш LocalPlayer, на встроенном сервере игрок с тем же UUID
    public static boolean phased(Entity e) {
        if (e == null) return false;
        if (e instanceof LocalPlayer) return phase && ok && e == Minecraft.getInstance().player;
        return phaseServer && e instanceof ServerPlayer && e.getUUID().equals(phaseId);
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

    // Только в консоль панели, без надписей на экране (так хочет владелец)
    private static void say(String s) {
        Panel.log(s);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ok = p != null && mc.level != null && Panel.full();
        brightNow = fullbright && ok;
        phaseServer = phase && ok && mc.hasSingleplayerServer();
        if (p == null || mc.level == null) {
            cam = null;
            flyApplied = false;
            timerApplied = 0;
            return;
        }
        phaseId = p.getUUID();
        // Phase вместе с полётом, иначе игрок провалится сквозь пол
        fly(p, (flight || phase) && ok);
        step(p, step && ok);
        tickRate(mc, p);
        if (autoTool && ok) autoTool(mc, p);
        if (autoFish && ok) autoFish(mc, p);
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

    // Step: шаг на целый блок, атрибут и на клиенте, и на встроенном сервере
    private static void step(LocalPlayer p, boolean on) {
        var a = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT);
        if (a == null || a.hasModifier(STEP_ID) == on) return;
        if (on) a.addTransientModifier(stepMod());
        else a.removeModifier(STEP_ID);
        server(p, sp -> {
            var sa = sp.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT);
            if (sa == null) return;
            sa.removeModifier(STEP_ID);
            if (on) sa.addTransientModifier(stepMod());
        });
    }

    private static net.minecraft.world.entity.ai.attributes.AttributeModifier stepMod() {
        return new net.minecraft.world.entity.ai.attributes.AttributeModifier(STEP_ID, 0.4, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
    }

    // Timer: скорость тиков всего мира (как /tick rate), фермы и машины работают быстрее
    private static void tickRate(Minecraft mc, LocalPlayer p) {
        int want = ok ? TIMER[timer] : 1;
        if (timerApplied == 0) timerApplied = 1;
        if (want == timerApplied || !ok) return;
        timerApplied = want;
        float rate = 20f * want;
        var s = mc.getSingleplayerServer();
        if (s != null) s.execute(() -> s.tickRateManager().setTickRate(rate));
        else p.connection.sendCommand("tick rate " + (int) rate);
    }

    // AutoTool: пока держишь атаку по блоку, в руке лучший инструмент из хотбара
    private static void autoTool(Minecraft mc, LocalPlayer p) {
        if (mc.screen != null || !mc.options.keyAttack.isDown()) return;
        if (!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return;
        var state = mc.level.getBlockState(hit.getBlockPos());
        var inv = p.getInventory();
        int best = inv.selected;
        float bestSpeed = inv.getItem(best).getDestroySpeed(state);
        for (int i = 0; i < 9; i++) {
            float sp = inv.getItem(i).getDestroySpeed(state);
            if (sp > bestSpeed) {
                bestSpeed = sp;
                best = i;
            }
        }
        inv.selected = best;
    }

    // AutoFish: клюёт, вытаскиваем, через полсекунды снова забрасываем
    private static void autoFish(Minecraft mc, LocalPlayer p) {
        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        if (!(p.getItemInHand(hand).getItem() instanceof net.minecraft.world.item.FishingRodItem)) return;
        if (fishWait > 0) {
            if (--fishWait == 0 && p.fishing == null) mc.gameMode.useItem(p, hand);
            return;
        }
        if (p.fishing != null && ((dev.cld.panel.mixin.FishingHookAccessor) p.fishing).cld$biting()) {
            mc.gameMode.useItem(p, hand);
            fishWait = 10;
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

    // Камера отдельной сущностью вне мира, игрок стоит на месте (или идёт по Baritone).
    // Marker, а не игрок: у живых сущностей камера берёт поворот головы (yHeadRot), а turn его не меняет
    private static void startCam(Minecraft mc, LocalPlayer p) {
        cam = new Marker(EntityType.MARKER, mc.level);
        Vec3 eye = p.getEyePosition();
        cam.setPos(eye.x, eye.y, eye.z);
        cam.setYRot(p.getYRot());
        cam.setXRot(p.getXRot());
        cam.setOldPosAndRot();
        cam.noPhysics = true;
        camLevel = mc.level;
        mc.setCameraEntity(cam);
        mc.gameRenderer.setRenderHand(false);
        // пустое управление: клавиши двигают камеру. Baritone, если ведёт игрока, поставит своё
        if (p.input instanceof KeyboardInput) p.input = new Input();
    }

    private static void stopCam(Minecraft mc, LocalPlayer p) {
        Entity old = cam;
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
