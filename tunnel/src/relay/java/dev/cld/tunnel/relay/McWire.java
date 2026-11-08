package dev.cld.tunnel.relay;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

// Немного протокола Minecraft: прочитать пакет приветствия (handshake), чтобы узнать имя мира,
// и ответить игроку сами, если мира с таким именем нет.
public final class McWire {
    public static final int MAX_HANDSHAKE = 2048;

    public record Handshake(byte[] raw, int protocol, String host, int port, int next) {}

    private McWire() {}

    // Читает ровно один пакет приветствия. raw: все прочитанные байты, их надо отдать серверу как есть.
    // null: это старый пинг 1.6 (первый байт 0xFE), такое не поддерживаем.
    public static Handshake readHandshake(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(in);
        int first = d.read();
        if (first < 0) throw new EOFException();
        if (first == 0xFE) return null;
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        head.write(first);
        int len = first & 0x7F;
        int shift = 7;
        int b = first;
        while ((b & 0x80) != 0) {
            if (shift > 14) throw new IOException("длина пакета слишком большая");
            b = d.read();
            if (b < 0) throw new EOFException();
            head.write(b);
            len |= (b & 0x7F) << shift;
            shift += 7;
        }
        if (len <= 0 || len > MAX_HANDSHAKE) throw new IOException("плохая длина приветствия " + len);
        byte[] body = new byte[len];
        d.readFully(body);
        Reader r = new Reader(body);
        if (r.varInt() != 0) throw new IOException("первый пакет не приветствие");
        int protocol = r.varInt();
        String host = r.string(1024);
        int port = r.u16();
        int next = r.varInt();
        head.write(body);
        return new Handshake(head.toByteArray(), protocol, host, port, next);
    }

    // Из адреса в приветствии делаем имя мира: до первого \0 (так моды дописывают метки),
    // без точки в конце, маленькими буквами.
    public static String cleanHost(String host) {
        int z = host.indexOf('\0');
        if (z >= 0) host = host.substring(0, z);
        host = host.trim().toLowerCase(java.util.Locale.ROOT);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host;
    }

    // Ответ в списке серверов, когда мира нет: свой текст вместо описания сервера.
    public static void answerStatus(InputStream in, OutputStream out, String text, int protocol) throws IOException {
        DataInputStream d = new DataInputStream(in);
        readPacket(d);
        String json = "{\"version\":{\"name\":\"cld_tunnel\",\"protocol\":" + protocol + "},"
            + "\"players\":{\"max\":0,\"online\":0},\"description\":{\"text\":" + jsonString(text) + "}}";
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        writeVarInt(p, 0);
        writeString(p, json);
        writePacket(out, p.toByteArray());
        byte[] ping = readPacket(d);
        writePacket(out, ping);
    }

    // Отказ при входе: игрок увидит текст на экране отключения.
    public static void answerLogin(OutputStream out, String text) throws IOException {
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        writeVarInt(p, 0);
        writeString(p, "{\"text\":" + jsonString(text) + "}");
        writePacket(out, p.toByteArray());
    }

    static byte[] readPacket(DataInputStream d) throws IOException {
        int len = readVarInt(d);
        if (len < 0 || len > 64) throw new IOException("неожиданный пакет");
        byte[] b = new byte[len];
        d.readFully(b);
        return b;
    }

    static void writePacket(OutputStream out, byte[] body) throws IOException {
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        writeVarInt(p, body.length);
        p.write(body);
        out.write(p.toByteArray());
        out.flush();
    }

    static int readVarInt(DataInputStream d) throws IOException {
        int v = 0, shift = 0, b;
        do {
            b = d.read();
            if (b < 0) throw new EOFException();
            v |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) throw new IOException("varint слишком длинный");
        } while ((b & 0x80) != 0);
        return v;
    }

    public static void writeVarInt(OutputStream o, int v) throws IOException {
        while ((v & ~0x7F) != 0) {
            o.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        o.write(v);
    }

    public static void writeString(OutputStream o, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(o, b.length);
        o.write(b);
    }

    static String jsonString(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }

    private static final class Reader {
        final byte[] b;
        int i;

        Reader(byte[] b) { this.b = b; }

        int u8() throws IOException {
            if (i >= b.length) throw new IOException("пакет короче, чем нужно");
            return b[i++] & 0xFF;
        }

        int varInt() throws IOException {
            int v = 0, shift = 0, x;
            do {
                x = u8();
                v |= (x & 0x7F) << shift;
                shift += 7;
                if (shift > 35) throw new IOException("varint слишком длинный");
            } while ((x & 0x80) != 0);
            return v;
        }

        int u16() throws IOException {
            return (u8() << 8) | u8();
        }

        String string(int max) throws IOException {
            int n = varInt();
            if (n < 0 || n > max * 4 || i + n > b.length) throw new IOException("плохая строка в пакете");
            String s = new String(b, i, n, StandardCharsets.UTF_8);
            i += n;
            return s;
        }
    }
}
