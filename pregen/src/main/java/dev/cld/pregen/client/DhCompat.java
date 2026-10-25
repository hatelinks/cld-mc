package dev.cld.pregen.client;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Distant Horizons (необязательно): пока открыта карта, DH не генерирует, не рисует и почти не занимает потоки.
 * Значения ставятся через API DH поверх конфига и не сохраняются в файл; resume() снимает их.
 * Работает через отражение, чтобы мод собирался и запускался без DH.
 */
final class DhCompat {
    private static final Logger LOG = LogUtils.getLogger();
    private static boolean paused;

    /** {группа..., значение, что поставить} */
    private static final Object[][] OVERRIDES = {
            {"worldGenerator", "enableDistantWorldGeneration", Boolean.FALSE},
            {"graphics", "renderingEnabled", Boolean.FALSE},
            {"multiThreading", "threadCount", 1},
    };

    static void pause() {
        if (paused || !ModList.get().isLoaded("distanthorizons")) return;
        for (Object[] o : OVERRIDES) call(o, "setValue", o[2]);
        paused = true;
    }

    static void resume() {
        if (!paused) return;
        for (Object[] o : OVERRIDES) call(o, "clearValue", null);
        paused = false;
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
