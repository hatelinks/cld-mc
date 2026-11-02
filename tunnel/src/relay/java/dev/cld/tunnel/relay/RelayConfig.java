package dev.cld.tunnel.relay;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

// Настройки relay из relay.properties. Пустое значение выключает свою часть.
public final class RelayConfig {
    public String controlHost = "0.0.0.0";
    public int controlPort = 25580;
    // Общий порт для игроков, мир выбирается по имени из адреса (нужен домен *.domain)
    public String publicHost = "0.0.0.0";
    public int publicPort = 25565;
    public String domain = "";
    // Порты для игроков без домена: у каждого мира свой порт
    public String address = "";
    public int portMin = 0;
    public int portMax = -1;
    public String key = "";
    public int maxHosts = 50;
    public int maxPlayersPerHost = 20;
    public Path state = Path.of("relay-state.txt");

    public boolean namesOn() { return !domain.isEmpty() && publicPort > 0; }
    public boolean portsOn() { return !address.isEmpty() && portMax >= portMin && portMin > 0; }

    public static RelayConfig load(Path file) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        RelayConfig c = new RelayConfig();
        String[] ctl = hostPort(p.getProperty("control", "0.0.0.0:25580"));
        c.controlHost = ctl[0];
        c.controlPort = Integer.parseInt(ctl[1]);
        String pub = p.getProperty("public", "0.0.0.0:25565").trim();
        if (pub.isEmpty()) {
            c.publicPort = 0;
        } else {
            String[] hp = hostPort(pub);
            c.publicHost = hp[0];
            c.publicPort = Integer.parseInt(hp[1]);
        }
        c.domain = p.getProperty("domain", "").trim().toLowerCase(java.util.Locale.ROOT);
        while (c.domain.startsWith(".")) c.domain = c.domain.substring(1);
        c.address = p.getProperty("address", "").trim();
        String ports = p.getProperty("ports", "").trim();
        if (!ports.isEmpty()) {
            String[] r = ports.split("-");
            c.portMin = Integer.parseInt(r[0].trim());
            c.portMax = Integer.parseInt(r[r.length - 1].trim());
        }
        c.key = p.getProperty("key", "").trim();
        c.maxHosts = Integer.parseInt(p.getProperty("maxHosts", "50").trim());
        c.maxPlayersPerHost = Integer.parseInt(p.getProperty("maxPlayersPerHost", "20").trim());
        c.state = Path.of(p.getProperty("state", "relay-state.txt").trim());
        // относительный путь считаем от папки с relay.properties
        Path dir = file.toAbsolutePath().getParent();
        if (!c.state.isAbsolute() && dir != null) c.state = dir.resolve(c.state);
        c.check();
        return c;
    }

    public void check() {
        if (!namesOn() && !portsOn())
            throw new IllegalArgumentException("нужен domain (и public) или address с ports, иначе игрокам некуда заходить");
        if (portsOn() && (portMin < 1 || portMax > 65535)) throw new IllegalArgumentException("ports вне 1-65535");
        if (key.isEmpty()) System.out.println("[relay] key пустой: открыть мир сможет любой с модом");
    }

    private static String[] hostPort(String s) {
        s = s.trim();
        int i = s.lastIndexOf(':');
        if (i < 0) return new String[] {"0.0.0.0", s};
        return new String[] {s.substring(0, i), s.substring(i + 1)};
    }
}
