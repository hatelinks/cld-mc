import dev.cld.tunnel.core.Proto;
import dev.cld.tunnel.core.TunnelClient;
import dev.cld.tunnel.relay.McWire;
import dev.cld.tunnel.relay.Relay;
import dev.cld.tunnel.relay.RelayConfig;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// Проверка relay и туннеля без Minecraft: поддельный сервер мира отвечает эхом,
// поддельные игроки шлют приветствие и данные и сверяют, что вернулось то же самое.
public class TunnelTest {
    static int failed = 0, passed = 0;
    static final String KEY = "test-key-123";

    public static void main(String[] a) throws Exception {
        Path dir = Files.createTempDirectory("cldt");
        EchoServer mc = new EchoServer();
        int portMin = freePortBlock();
        RelayConfig cfg = config(dir, portMin);
        Relay relay = new Relay(cfg);
        relay.start();
        int ctl = relay.controlPort();
        int pub = relay.publicPort();

        // 1. хост подключается и получает оба адреса
        Watcher w = new Watcher();
        String world = Proto.randomHex(16);
        TunnelClient c = new TunnelClient("127.0.0.1", ctl, KEY, world, "127.0.0.1", mc.port(), w);
        c.start();
        String addr = w.await(TunnelClient.State.ONLINE, 5);
        check("адрес выдан", addr != null && addr.contains(".mc.test") && addr.contains("127.0.0.1:"));
        String name = addr.split(" ")[0].replace(".mc.test", "").replaceAll(":\\d+$", "");
        int worldPort = Integer.parseInt(addr.split(" ")[1].split(":")[1]);
        check("имя вида слово-слово-число", name.matches("[a-z]+-[a-z]+-\\d+"));
        check("порт мира в диапазоне", worldPort >= portMin && worldPort <= portMin + 9);

        // 2. игрок по имени: приветствие доходит до сервера без изменений, данные туда и обратно целы
        check("по имени, 1 МБ эхом", player(pub, name + ".mc.test", 2, 1 << 20, mc));
        // адрес с точкой на конце и большими буквами, и метка мода после \0
        check("по имени с точкой и \\0", player(pub, name.toUpperCase() + ".MC.test.\0FML3\0", 2, 1000, mc));
        // 3. игрок по порту мира
        check("по порту, 1 МБ эхом", player(worldPort, "127.0.0.1", 2, 1 << 20, mc));

        // 4. много игроков сразу
        List<Thread> ts = new ArrayList<>();
        AtomicInteger ok = new AtomicInteger();
        for (int i = 0; i < 12; i++) {
            final int n = i;
            Thread t = new Thread(() -> {
                try {
                    if (player(n % 2 == 0 ? pub : worldPort, name + ".mc.test", 2, 200_000 + n * 1000, mc)) ok.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            t.start();
            ts.add(t);
        }
        for (Thread t : ts) t.join(30_000);
        // лимит 20 игроков на мир, 12 влезают
        check("12 игроков параллельно", ok.get() == 12);

        // 5. неизвестный мир: в списке серверов наш текст, при входе экран отказа
        String motd = statusPing(pub, "net-takogo-12.mc.test");
        check("пинг неизвестного мира даёт текст", motd != null && motd.contains("не открыт"));
        String kick = loginKick(pub, "net-takogo-12.mc.test");
        check("вход в неизвестный мир даёт отказ", kick != null && kick.contains("не открыт"));
        // 6. старый пинг 0xFE просто закрывается
        check("старый пинг закрыт", legacyClosed(pub));

        // 7. неверный ключ: хост получает FAILED и больше не пробует
        Watcher bad = new Watcher();
        TunnelClient cb = new TunnelClient("127.0.0.1", ctl, "wrong", Proto.randomHex(16), "127.0.0.1", mc.port(), bad);
        cb.start();
        String why = bad.await(TunnelClient.State.FAILED, 5);
        check("неверный ключ отклонён", why != null && why.contains("ключ") && !cb.isRunning());

        // 8. сервер мира не запущен: игрок отключается, relay не виснет
        Watcher w2 = new Watcher();
        String world2 = Proto.randomHex(16);
        TunnelClient c2 = new TunnelClient("127.0.0.1", ctl, KEY, world2, "127.0.0.1", closedPort(), w2);
        c2.start();
        String addr2 = w2.await(TunnelClient.State.ONLINE, 5);
        String name2 = addr2.split(" ")[0].replace(".mc.test", "");
        check("второй мир получил другое имя", !name2.equals(name));
        long t0 = System.currentTimeMillis();
        boolean dropped = !player(pub, name2 + ".mc.test", 2, 1000, mc);
        check("нет сервера мира: игрок отключён быстро", dropped && System.currentTimeMillis() - t0 < 12_000);
        c2.stop();

        // 9. хост закрыл мир: имя больше не работает
        c.stop();
        waitHosts(relay, 0, 5);
        check("после закрытия мир не найден", statusPing(pub, name + ".mc.test").contains("не открыт"));

        // 10. мир открыт снова: адрес тот же (и после перезапуска relay тоже)
        Watcher w3 = new Watcher();
        TunnelClient c3 = new TunnelClient("127.0.0.1", ctl, KEY, world, "127.0.0.1", mc.port(), w3);
        c3.start();
        check("тот же адрес при повторном открытии", addr.equals(w3.await(TunnelClient.State.ONLINE, 5)));
        relay.stop();
        String retry = w3.await(TunnelClient.State.RETRY, 5);
        check("обрыв relay замечен", retry != null);
        Relay relay2 = new Relay(config(dir, portMin, ctl, pub));
        relay2.start();
        String again = w3.await(TunnelClient.State.ONLINE, 15);
        check("после перезапуска relay тот же адрес", addr.equals(again));
        check("и игроки снова заходят", player(pub, name + ".mc.test", 2, 50_000, mc));

        // 11. тот же мир зашёл второй раз (первое соединение повисло): новый занимает адрес
        Watcher w4 = new Watcher();
        TunnelClient c4 = new TunnelClient("127.0.0.1", ctl, KEY, world, "127.0.0.1", mc.port(), w4);
        c4.start();
        check("повторный вход того же мира", addr.equals(w4.await(TunnelClient.State.ONLINE, 5)));
        check("игрок идёт в новое соединение", player(worldPort, "x", 2, 50_000, mc));
        c3.stop();
        c4.stop();

        // 12. открытый relay без ключа и только по портам
        Path dir2 = Files.createTempDirectory("cldt2");
        RelayConfig open = new RelayConfig();
        open.controlHost = "127.0.0.1";
        open.controlPort = 0;
        open.publicPort = 0;
        open.address = "127.0.0.1";
        open.portMin = portMin + 20;
        open.portMax = portMin + 21;
        open.state = dir2.resolve("s.txt");
        Relay r3 = new Relay(open);
        r3.start();
        Watcher w5 = new Watcher();
        TunnelClient c5 = new TunnelClient("127.0.0.1", r3.controlPort(), "", Proto.randomHex(16), "127.0.0.1", mc.port(), w5);
        c5.start();
        String a5 = w5.await(TunnelClient.State.ONLINE, 5);
        check("без ключа и домена: только порт", a5 != null && a5.matches("127\\.0\\.0\\.1:\\d+"));
        check("по порту работает", player(Integer.parseInt(a5.split(":")[1]), "y", 1, 100_000, mc));
        c5.stop();
        r3.stop();
        relay2.stop();

        System.out.println();
        System.out.println("Итого: прошло " + passed + ", упало " + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    static RelayConfig config(Path dir, int portMin) {
        return config(dir, portMin, 0, 0);
    }

    static RelayConfig config(Path dir, int portMin, int ctl, int pub) {
        RelayConfig c = new RelayConfig();
        c.controlHost = "127.0.0.1";
        c.controlPort = ctl;
        c.publicHost = "127.0.0.1";
        c.publicPort = pub;
        c.domain = "mc.test";
        c.address = "127.0.0.1";
        c.portMin = portMin;
        c.portMax = portMin + 9;
        c.key = KEY;
        c.state = dir.resolve("state.txt");
        // publicPort 0 значит любой свободный, а namesOn требует > 0, поэтому берём свободный заранее
        if (pub == 0) c.publicPort = freePort();
        return c;
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "OK   " : "FAIL ") + what);
        if (ok) passed++;
        else failed++;
    }

    static byte[] handshake(String host, int port, int next) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        McWire.writeVarInt(b, 0);
        McWire.writeVarInt(b, 767);
        McWire.writeString(b, host);
        b.write(port >> 8);
        b.write(port & 0xFF);
        McWire.writeVarInt(b, next);
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        McWire.writeVarInt(p, b.size());
        p.write(b.toByteArray());
        return p.toByteArray();
    }

    // true если сервер получил то же приветствие и вернул все данные без искажений
    static boolean player(int port, String host, int next, int size, EchoServer mc) throws Exception {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 3000);
            s.setSoTimeout(15_000);
            byte[] hs = handshake(host, 25565, next);
            byte[] data = new byte[size];
            new Random(size).nextBytes(data);
            OutputStream out = s.getOutputStream();
            Thread wr = new Thread(() -> {
                try {
                    out.write(hs);
                    for (int i = 0; i < data.length; i += 7000) out.write(data, i, Math.min(7000, data.length - i));
                    out.flush();
                } catch (IOException ignored) {
                }
            });
            wr.start();
            InputStream in = s.getInputStream();
            byte[] back = new byte[hs.length + size];
            int got = 0;
            try {
                while (got < back.length) {
                    int n = in.read(back, got, back.length - got);
                    if (n < 0) break;
                    got += n;
                }
            } catch (IOException e) {
                return false;
            }
            wr.join(5000);
            if (got != back.length) return false;
            return Arrays.equals(Arrays.copyOf(back, hs.length), hs)
                && Arrays.equals(Arrays.copyOfRange(back, hs.length, back.length), data);
        }
    }

    static String statusPing(int port, String host) throws IOException {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(5000);
            OutputStream out = s.getOutputStream();
            out.write(handshake(host, 25565, 1));
            out.write(new byte[] {1, 0});
            out.write(new byte[] {9, 1, 0, 0, 0, 0, 0, 0, 0, 42});
            DataInputStream in = new DataInputStream(s.getInputStream());
            int len = varInt(in);
            byte[] b = new byte[len];
            in.readFully(b);
            DataInputStream p = new DataInputStream(new java.io.ByteArrayInputStream(b));
            if (varInt(p) != 0) return null;
            byte[] js = new byte[varInt(p)];
            p.readFully(js);
            int plen = varInt(in);
            byte[] pong = new byte[plen];
            in.readFully(pong);
            if (pong[0] != 1 || pong[8] != 42) return null;
            return new String(js, StandardCharsets.UTF_8);
        }
    }

    static String loginKick(int port, String host) throws IOException {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(5000);
            s.getOutputStream().write(handshake(host, 25565, 2));
            DataInputStream in = new DataInputStream(s.getInputStream());
            int len = varInt(in);
            byte[] b = new byte[len];
            in.readFully(b);
            DataInputStream p = new DataInputStream(new java.io.ByteArrayInputStream(b));
            if (varInt(p) != 0) return null;
            byte[] js = new byte[varInt(p)];
            p.readFully(js);
            return new String(js, StandardCharsets.UTF_8);
        }
    }

    static boolean legacyClosed(int port) throws IOException {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(5000);
            s.getOutputStream().write(new byte[] {(byte) 0xFE, 1});
            return s.getInputStream().read() == -1;
        }
    }

    static int varInt(DataInputStream d) throws IOException {
        int v = 0, sh = 0, b;
        do {
            b = d.readUnsignedByte();
            v |= (b & 0x7F) << sh;
            sh += 7;
        } while ((b & 0x80) != 0);
        return v;
    }

    static void waitHosts(Relay r, int n, int sec) throws InterruptedException {
        for (int i = 0; i < sec * 20 && r.hosts() != n; i++) Thread.sleep(50);
    }

    static int freePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static int closedPort() {
        return freePort();
    }

    static int freePortBlock() {
        for (int base = 41000; base < 60000; base += 50) {
            boolean ok = true;
            for (int p = base; p < base + 25 && ok; p++) {
                try {
                    new ServerSocket(p).close();
                } catch (IOException e) {
                    ok = false;
                }
            }
            if (ok) return base;
        }
        throw new IllegalStateException("нет свободных портов");
    }

    static final class Watcher implements TunnelClient.Listener {
        final BlockingQueue<Object[]> q = new LinkedBlockingQueue<>();

        public void onState(TunnelClient.State st, String text) {
            q.add(new Object[] {st, text == null ? "" : text});
        }

        String await(TunnelClient.State want, int sec) throws InterruptedException {
            long end = System.currentTimeMillis() + sec * 1000L;
            while (true) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) return null;
                Object[] e = q.poll(left, TimeUnit.MILLISECONDS);
                if (e == null) return null;
                if (e[0] == want) return (String) e[1];
            }
        }
    }

    // Поддельный сервер мира: читает приветствие, отдаёт его назад и дальше работает эхом.
    static final class EchoServer {
        final ServerSocket ss;

        EchoServer() throws IOException {
            ss = new ServerSocket(0);
            Thread t = new Thread(() -> {
                while (true) {
                    try {
                        Socket s = ss.accept();
                        Thread.ofVirtual().start(() -> echo(s));
                    } catch (IOException e) {
                        return;
                    }
                }
            });
            t.setDaemon(true);
            t.start();
        }

        int port() { return ss.getLocalPort(); }

        static void echo(Socket s) {
            try (s) {
                byte[] b = new byte[8192];
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream();
                int n;
                while ((n = in.read(b)) >= 0) out.write(b, 0, n);
            } catch (IOException ignored) {
            }
        }
    }
}
