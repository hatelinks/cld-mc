import dev.cld.tunnel.core.TunnelClient;
import dev.cld.tunnel.relay.McWire;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.Inflater;

// Проверка с настоящим сервером Minecraft: relay запущен отдельно из jar, хост это TunnelClient,
// как в моде. Игрок делает пинг списка серверов и входит без лицензии до фазы настройки.
// java RealServerTest <порт relay> <порт сервера> <ключ> <домен> <порт игроков>
public class RealServerTest {
    static int failed;

    public static void main(String[] a) throws Exception {
        int relayPort = Integer.parseInt(a[0]);
        int mcPort = Integer.parseInt(a[1]);
        String key = a[2];
        String domain = a[3];
        int pub = Integer.parseInt(a[4]);
        BlockingQueue<String> online = new ArrayBlockingQueue<>(4);
        TunnelClient c = new TunnelClient("127.0.0.1", relayPort, key, "0123456789abcdef0123456789abcdef", "127.0.0.1", mcPort,
            (st, t) -> {
                System.out.println("хост: " + st + (t == null ? "" : " " + t));
                if (st == TunnelClient.State.ONLINE) online.add(t);
            });
        c.start();
        String addr = online.poll(10, TimeUnit.SECONDS);
        check("адрес выдан", addr != null);
        String host = addr.split(" ")[0].split(":")[0];
        int worldPort = Integer.parseInt(addr.split(" ")[1].split(":")[1]);

        String motd = status(pub, host);
        System.out.println("ответ сервера: " + motd);
        check("пинг по имени дошёл до настоящего сервера", motd != null && motd.contains("CLD tunnel test"));
        check("пинг по порту мира", status(worldPort, "127.0.0.1").contains("CLD tunnel test"));
        check("вход по имени до фазы настройки", login(pub, host, "Tester1"));
        check("вход по порту до фазы настройки", login(worldPort, "127.0.0.1", "Tester2"));
        c.stop();
        Thread.sleep(500);
        String gone = status(pub, host);
        check("после закрытия relay отвечает сам", gone != null && gone.contains("не открыт"));
        System.out.println(failed == 0 ? "ВСЁ ПРОШЛО" : "УПАЛО: " + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    static void check(String w, boolean ok) {
        System.out.println((ok ? "OK   " : "FAIL ") + w);
        if (!ok) failed++;
    }

    static byte[] handshake(String host, int port, int next) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        McWire.writeVarInt(b, 0);
        McWire.writeVarInt(b, 767);
        McWire.writeString(b, host);
        b.write(port >> 8);
        b.write(port & 0xFF);
        McWire.writeVarInt(b, next);
        return frame(b.toByteArray());
    }

    static byte[] frame(byte[] body) throws IOException {
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        McWire.writeVarInt(p, body.length);
        p.write(body);
        return p.toByteArray();
    }

    static String status(int port, String host) throws IOException {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(10_000);
            OutputStream o = s.getOutputStream();
            o.write(handshake(host, 25565, 1));
            o.write(new byte[] {1, 0});
            DataInputStream in = new DataInputStream(s.getInputStream());
            DataInputStream p = new DataInputStream(new ByteArrayInputStream(readFrame(in)));
            if (varInt(p) != 0) return null;
            byte[] js = new byte[varInt(p)];
            p.readFully(js);
            o.write(new byte[] {9, 1, 0, 0, 0, 0, 0, 0, 0, 7});
            byte[] pong = readFrame(in);
            if (pong[0] != 1 || pong[8] != 7) return null;
            return new String(js, StandardCharsets.UTF_8);
        }
    }

    // Login Start, дальше Set Compression и Login Success, потом Login Acknowledged
    // и хотя бы один пакет фазы настройки от сервера.
    static boolean login(int port, String host, String name) throws Exception {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(15_000);
            OutputStream o = s.getOutputStream();
            o.write(handshake(host, 25565, 2));
            ByteArrayOutputStream ls = new ByteArrayOutputStream();
            McWire.writeVarInt(ls, 0);
            McWire.writeString(ls, name);
            UUID u = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            for (long v : new long[] {u.getMostSignificantBits(), u.getLeastSignificantBits()})
                for (int i = 7; i >= 0; i--) ls.write((int) (v >>> (i * 8)));
            o.write(frame(ls.toByteArray()));
            DataInputStream in = new DataInputStream(s.getInputStream());
            int threshold = -1;
            boolean success = false;
            for (int n = 0; n < 10; n++) {
                byte[] body = readPacket(in, threshold);
                DataInputStream p = new DataInputStream(new ByteArrayInputStream(body));
                int id = varInt(p);
                if (!success && id == 3) {
                    threshold = varInt(p);
                    System.out.println("  сжатие с " + threshold);
                } else if (!success && id == 2) {
                    p.skipBytes(16);
                    byte[] nm = new byte[varInt(p)];
                    p.readFully(nm);
                    System.out.println("  вход успешен: " + new String(nm, StandardCharsets.UTF_8));
                    success = true;
                    ByteArrayOutputStream ack = new ByteArrayOutputStream();
                    McWire.writeVarInt(ack, 3);
                    o.write(threshold >= 0 ? frame(withNoCompression(ack.toByteArray())) : frame(ack.toByteArray()));
                } else if (!success && id == 0) {
                    byte[] js = new byte[varInt(p)];
                    p.readFully(js);
                    System.out.println("  отказ: " + new String(js, StandardCharsets.UTF_8));
                    return false;
                } else if (success) {
                    System.out.println("  пакет настройки 0x" + Integer.toHexString(id) + ", " + body.length + " байт");
                    return true;
                }
            }
            return false;
        }
    }

    static byte[] withNoCompression(byte[] body) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        McWire.writeVarInt(b, 0);
        b.write(body);
        return b.toByteArray();
    }

    static byte[] readPacket(DataInputStream in, int threshold) throws Exception {
        byte[] f = readFrame(in);
        if (threshold < 0) return f;
        DataInputStream d = new DataInputStream(new ByteArrayInputStream(f));
        int dataLen = varInt(d);
        byte[] rest = d.readAllBytes();
        if (dataLen == 0) return rest;
        Inflater inf = new Inflater();
        inf.setInput(rest);
        byte[] out = new byte[dataLen];
        int n = inf.inflate(out);
        inf.end();
        if (n != dataLen) throw new IOException("сжатый пакет не тот");
        return out;
    }

    static byte[] readFrame(DataInputStream in) throws IOException {
        byte[] b = new byte[varInt(in)];
        in.readFully(b);
        return b;
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
}
