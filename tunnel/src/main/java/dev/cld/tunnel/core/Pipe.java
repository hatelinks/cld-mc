package dev.cld.tunnel.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// Гоняет байты между двумя сокетами в обе стороны, пока обе стороны не закроются.
public final class Pipe {
    public static final AtomicLong BYTES = new AtomicLong();
    private static final int BUF = 32 * 1024;

    private Pipe() {}

    public static void run(Socket a, Socket b, String name, Runnable onClose) {
        AtomicInteger left = new AtomicInteger(2);
        Runnable done = () -> {
            if (left.decrementAndGet() == 0) {
                closeQuietly(a);
                closeQuietly(b);
                if (onClose != null) onClose.run();
            }
        };
        start(name + " >", () -> copy(a, b, done));
        start(name + " <", () -> copy(b, a, done));
    }

    private static void copy(Socket from, Socket to, Runnable done) {
        byte[] buf = new byte[BUF];
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                out.flush();
                BYTES.addAndGet(n);
            }
            // Сторона закончила писать: передаём это дальше, но читать в обратную сторону ещё можно.
            try { to.shutdownOutput(); } catch (IOException ignored) {}
        } catch (IOException e) {
            // Обрыв в одну сторону рвёт всё соединение, иначе вторая половина повиснет.
            closeQuietly(from);
            closeQuietly(to);
        } finally {
            done.run();
        }
    }

    public static Thread start(String name, Runnable r) {
        Thread t = new Thread(r, "cld_tunnel " + name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    public static void closeQuietly(AutoCloseable c) {
        if (c == null) return;
        try { c.close(); } catch (Exception ignored) {}
    }
}
