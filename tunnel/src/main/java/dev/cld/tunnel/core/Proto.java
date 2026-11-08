package dev.cld.tunnel.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// Общие куски протокола туннеля: строки управления, ключ, случайные id.
// Управление идёт текстовыми строками по TCP, данные игрока после строки DATA идут как есть.
public final class Proto {
    public static final int VERSION = 1;
    public static final String MAGIC = "CLDT";
    public static final int DEFAULT_PORT = 25580;
    public static final int MAX_LINE = 512;
    public static final int PING_MS = 20_000;
    public static final int DEAD_MS = 60_000;

    private static final SecureRandom RND = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private Proto() {}

    // Читаем строку побайтно, чтобы не съесть данные игрока, которые идут следом.
    public static String readLine(InputStream in) throws IOException {
        byte[] buf = new byte[MAX_LINE];
        int n = 0;
        while (true) {
            int b = in.read();
            if (b < 0) {
                if (n == 0) return null;
                throw new IOException("строка оборвана");
            }
            if (b == '\n') break;
            if (n >= MAX_LINE) throw new IOException("слишком длинная строка");
            buf[n++] = (byte) b;
        }
        if (n > 0 && buf[n - 1] == '\r') n--;
        return new String(buf, 0, n, StandardCharsets.UTF_8);
    }

    public static void writeLine(OutputStream out, String line) throws IOException {
        out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    public static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        RND.nextBytes(b);
        return HEX.formatHex(b);
    }

    public static boolean isHex(String s, int len) {
        if (s == null || s.length() != len) return false;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    // Сам ключ по сети не ходит: клиент отвечает HMAC от случайной строки relay.
    public static String mac(String key, String nonce) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HEX.formatHex(m.doFinal(nonce.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean sameMac(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hex(byte[] b) {
        return HEX.formatHex(b);
    }
}
