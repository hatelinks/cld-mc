package dev.cld.tcw;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * Глушит отладочный спам чужих модов в debug.log. Create Structures Arise пишет «CSA config read» на каждое
 * чтение своего конфига — при генерации это десятки тысяч строк в секунду и лишняя нагрузка на память и диск.
 */
final class QuietLogs {
    private static final String[] LOGGERS = { "createstructuresarise" };

    static void apply() {
        for (String name : LOGGERS) {
            try { Configurator.setLevel(name, Level.INFO); }
            catch (Throwable ignored) { } // нет log4j-core: оставляем как есть
        }
    }

    private QuietLogs() { }
}
