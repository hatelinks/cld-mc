package dev.cld.tunnel.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

// Сторона хоста: держит управляющее соединение с relay и на каждую команду OPEN
// соединяет нового игрока с локальным сервером мира. От Minecraft не зависит.
public final class TunnelClient {
    public enum State { CONNECTING, ONLINE, RETRY, FAILED, STOPPED }

    public interface Listener {
        // text: для ONLINE адрес мира, для RETRY и FAILED причина
        void onState(State state, String text);
    }

    private final String relayHost;
    private final int relayPort;
    private final String key;
    private final String worldId;
    private final String localHost;
    private final int localPort;
    private final Listener listener;
    private final AtomicInteger players = new AtomicInteger();

    private volatile boolean running;
    private volatile Socket control;
    private volatile OutputStream controlOut;
    private volatile String address;
    private Thread thread;

    public TunnelClient(String relayHost, int relayPort, String key, String worldId,
                        String localHost, int localPort, Listener listener) {
        this.relayHost = relayHost;
        this.relayPort = relayPort;
        this.key = key == null ? "" : key;
        this.worldId = worldId;
        this.localHost = localHost;
        this.localPort = localPort;
        this.listener = listener;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = Pipe.start("control", this::loop);
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        Socket s = control;
        if (s != null) {
            try { Proto.writeLine(s.getOutputStream(), "BYE"); } catch (IOException ignored) {}
            Pipe.closeQuietly(s);
        }
        if (thread != null) thread.interrupt();
        fire(State.STOPPED, null);
    }

    public boolean isRunning() { return running; }
    public String address() { return address; }
    public int players() { return players.get(); }

    private void loop() {
        int delay = 2;
        while (running) {
            fire(State.CONNECTING, null);
            String fatal = null;
            try {
                fatal = session();
                delay = 2;
            } catch (IOException e) {
                if (!running) break;
                fire(State.RETRY, "нет связи с relay: " + e.getMessage());
            }
            address = null;
            if (!running) break;
            if (fatal != null) {
                running = false;
                fire(State.FAILED, fatal);
                break;
            }
            try {
                Thread.sleep(delay * 1000L);
            } catch (InterruptedException e) {
                break;
            }
            delay = Math.min(delay * 2, 60);
        }
    }

    // Возвращает текст ошибки, если пробовать снова бессмысленно (неверный ключ и т.п.).
    private String session() throws IOException {
        Socket s = new Socket();
        control = s;
        try {
            s.setTcpNoDelay(true);
            s.setKeepAlive(true);
            s.connect(new InetSocketAddress(relayHost, relayPort), 10_000);
            s.setSoTimeout(15_000);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            String nonce = greeting(in);
            String mac = key.isEmpty() ? "-" : Proto.mac(key, nonce);
            Proto.writeLine(out, "AUTH " + mac + " " + worldId);
            String reply = Proto.readLine(in);
            if (reply == null) throw new IOException("relay закрыл соединение");
            if (reply.startsWith("ERR ")) return error(reply);
            if (!reply.startsWith("OK ") || reply.length() < 4) throw new IOException("странный ответ relay: " + reply);
            // адресов может быть два: по имени и по порту, через пробел
            address = reply.substring(3).trim();
            controlOut = out;
            fire(State.ONLINE, address);
            s.setSoTimeout(Proto.DEAD_MS);
            while (running) {
                String line;
                try {
                    line = Proto.readLine(in);
                } catch (SocketTimeoutException e) {
                    throw new IOException("relay молчит");
                }
                if (line == null) throw new IOException("relay закрыл соединение");
                if (line.equals("PING")) {
                    synchronized (out) { Proto.writeLine(out, "PONG"); }
                } else if (line.startsWith("OPEN ")) {
                    String id = line.substring(5).trim();
                    if (Proto.isHex(id, 32)) Pipe.start("open", () -> open(id));
                } else if (line.startsWith("ERR ")) {
                    return error(line);
                }
            }
            return null;
        } finally {
            control = null;
            controlOut = null;
            Pipe.closeQuietly(s);
        }
    }

    // ERR FATAL текст: дальше не пробуем. ERR RETRY текст: пробуем позже.
    private String error(String line) throws IOException {
        String rest = line.substring(4);
        int sp = rest.indexOf(' ');
        String kind = sp < 0 ? rest : rest.substring(0, sp);
        String text = sp < 0 ? "" : rest.substring(sp + 1);
        if (kind.equals("RETRY")) throw new IOException(text);
        return text.isEmpty() ? rest : text;
    }

    private static String greeting(InputStream in) throws IOException {
        String g = Proto.readLine(in);
        if (g == null) throw new IOException("relay закрыл соединение");
        String[] p = g.split(" ");
        if (p.length != 3 || !p[0].equals(Proto.MAGIC)) throw new IOException("это не relay cld_tunnel");
        if (!p[1].equals(String.valueOf(Proto.VERSION))) throw new IOException("другая версия протокола relay: " + p[1]);
        return p[2];
    }

    // Новый игрок: одно соединение к локальному серверу и одно к relay с его id.
    private void open(String id) {
        Socket local = new Socket();
        Socket data = new Socket();
        try {
            local.setTcpNoDelay(true);
            local.connect(new InetSocketAddress(localHost, localPort), 5_000);
            data.setTcpNoDelay(true);
            data.setKeepAlive(true);
            data.connect(new InetSocketAddress(relayHost, relayPort), 10_000);
            data.setSoTimeout(10_000);
            greeting(data.getInputStream());
            Proto.writeLine(data.getOutputStream(), "DATA " + id);
            data.setSoTimeout(0);
        } catch (IOException e) {
            boolean noLocal = !local.isConnected();
            Pipe.closeQuietly(local);
            Pipe.closeQuietly(data);
            // сервер мира не ответил: скажем relay сразу, чтобы игрок не ждал
            OutputStream o = controlOut;
            if (noLocal && o != null) {
                try {
                    synchronized (o) { Proto.writeLine(o, "FAIL " + id); }
                } catch (IOException ignored) {
                }
            }
            return;
        }
        players.incrementAndGet();
        Pipe.run(data, local, "player", players::decrementAndGet);
    }

    private void fire(State st, String text) {
        try {
            if (listener != null) listener.onState(st, text);
        } catch (RuntimeException ignored) {
        }
    }
}
