package dev.cld.pregen.client;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.slf4j.Logger;

/**
 * Distant Horizons (необязательно):
 * - пока открыта карта, DH не генерирует, не рисует и почти не занимает потоки;
 * - дальняя генерация работает только в мирах «Горы до небес» (тип измерения gdn:*),
 *   в стандартном мире DH лишь дорисовывает уже сгенерированное.
 * Значения ставятся через API DH поверх конфига и не сохраняются в файл; снятое значение возвращает конфиг.
 * Работает через отражение, чтобы мод собирался и запускался без DH.
 */
final class DhCompat {
    private static final Logger LOG = LogUtils.getLogger();
    private static boolean paused;
    /** null — тип мира ещё не известен (меню). */
    private static Boolean gdnWorld;

    /** {группа, значение, что поставить} */
    private static final Object[] GENERATION = {"worldGenerator", "enableDistantWorldGeneration", Boolean.FALSE};
    private static final Object[] RENDERING = {"graphics", "renderingEnabled", Boolean.FALSE};
    private static final Object[] THREADS = {"multiThreading", "threadCount", 1};

    static void pause() {
        if (paused) return;
        paused = true;
        apply();
    }

    static void resume() {
        if (!paused) return;
        paused = false;
        apply();
    }

    /** Вызывается при загрузке клиентского мира: определяем тип по Верхнему миру. */
    static void onLevelLoad(LevelEvent.Load e) {
        if (!(e.getLevel() instanceof ClientLevel level)) return;
        Boolean gdn = null;
        if (level.dimension() == Level.OVERWORLD) {
            gdn = isGdn(level);
        } else {
            var server = Minecraft.getInstance().getSingleplayerServer();
            ServerLevel ow = server == null ? null : server.overworld();
            if (ow != null) gdn = isGdn(ow);
        }
        if (gdn != null && !gdn.equals(gdnWorld)) {
            gdnWorld = gdn;
            LOG.info("cld_pregen: мир {}, дальняя генерация DH {}", gdn ? "«Горы до небес»" : "стандартный", gdn ? "по конфигу" : "выключена");
            apply();
        }
    }

    static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut e) {
        gdnWorld = null;
        apply();
    }

    private static boolean isGdn(Level level) {
        return level.dimensionTypeRegistration().unwrapKey().map(ResourceKey::location)
                .map(id -> id.getNamespace().equals("gdn")).orElse(false);
    }

    private static void apply() {
        if (!ModList.get().isLoaded("distanthorizons")) return;
        boolean noGeneration = paused || Boolean.FALSE.equals(gdnWorld);
        set(GENERATION, noGeneration);
        set(RENDERING, paused);
        set(THREADS, paused);
    }

    private static void set(Object[] o, boolean on) {
        if (on) call(o, "setValue", o[2]);
        else call(o, "clearValue", null);
    }

    private static void call(Object[] o, String method, Object arg) {
        try {
            String api = "com.seibel.distanthorizons.api.";
            Object configs = Class.forName(api + "DhApi$Delayed").getField("configs").get(null);
            if (configs == null) return; // DH ещё не готов
            // методы берём у публичных интерфейсов API, а не у внутренних классов реализации
            Method groupGetter = Class.forName(api + "interfaces.config.IDhApiConfig").getMethod((String) o[0]);
            Object group = groupGetter.invoke(configs);
            Object value = groupGetter.getReturnType().getMethod((String) o[1]).invoke(group);
            Class<?> valueApi = Class.forName(api + "interfaces.config.IDhApiConfigValue");
            if (arg == null) valueApi.getMethod(method).invoke(value);
            else valueApi.getMethod(method, Object.class).invoke(value, arg);
        } catch (Throwable t) {
            LOG.warn("cld_pregen: не удалось изменить настройку DH {}.{}", o[0], o[1], t);
        }
    }

    private DhCompat() {}
}
