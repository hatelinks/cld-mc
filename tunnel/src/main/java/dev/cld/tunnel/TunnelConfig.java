package dev.cld.tunnel;

import net.neoforged.neoforge.common.ModConfigSpec;

// Настройки мода: config/cld_tunnel-client.toml, в игре TC Моды или список модов.
public final class TunnelConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue AUTO;
    public static final ModConfigSpec.ConfigValue<String> RELAY;
    public static final ModConfigSpec.ConfigValue<String> KEY;
    public static final ModConfigSpec.BooleanValue OFFLINE_PLAYERS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        AUTO = b.comment("Открывать мир по интернету вместе с Открыть для сети")
            .define("auto", true);
        RELAY = b.comment("Адрес relay-сервера: имя или IP, можно с портом (по умолчанию 25580)")
            .define("relay", "");
        KEY = b.comment("Ключ relay, такой же, как key в relay.properties")
            .define("key", "");
        OFFLINE_PLAYERS = b.comment("Пускать игроков без лицензии. Осторожно: зайти можно под любым ником, даже вашим")
            .define("offlinePlayers", false);
        SPEC = b.build();
    }

    private TunnelConfig() {}

    // host и порт из строки relay
    static String[] relay() {
        String s = RELAY.get().trim();
        if (s.isEmpty()) return null;
        int port = dev.cld.tunnel.core.Proto.DEFAULT_PORT;
        int i = s.lastIndexOf(':');
        if (i > 0 && s.indexOf(':') == i) {
            try {
                port = Integer.parseInt(s.substring(i + 1).trim());
                s = s.substring(0, i).trim();
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (port < 1 || port > 65535 || s.isEmpty()) return null;
        return new String[] {s, String.valueOf(port)};
    }
}
