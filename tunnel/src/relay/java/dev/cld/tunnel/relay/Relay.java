package dev.cld.tunnel.relay;

import dev.cld.tunnel.core.Pipe;
import dev.cld.tunnel.core.Proto;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

// Relay: хосты держат с ним управляющее соединение, игроки заходят на общий порт по имени
// мира или на свой порт мира. На каждого игрока хост открывает отдельное соединение DATA.
public final class Relay {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RelayConfig cfg;
    private final Object lock = new Object();
    private final Map<String, Host> byWorld = new HashMap<>();
    private final Map<String, Host> byName = new HashMap<>();
    private final Map<Integer, Host> byPort = new HashMap<>();
    private final Map<String, Entry> saved = new HashMap<>();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "relay timer");
        t.setDaemon(true);
        return t;
    });
    private ServerSocket control;
    private ServerSocket pub;
    private volatile boolean running;

    private record Entry(String name, int port) {}

    private record Pending(Host host, CompletableFuture<Socket> data) {}

    final class Host {
        final String world;
        final Socket sock;
        final OutputStream out;
        final AtomicInteger players = new AtomicInteger();
        String name;
        int port;
        ServerSocket portSock;
        volatile boolean closed;

        Host(String world, Socket sock) throws IOException {
            this.world = world;
            this.sock = sock;
            this.out = sock.getOutputStream();
        }

        synchronized void send(String line) throws IOException {
            Proto.writeLine(out, line);
        }

        void close() {
            closed = true;
            Pipe.closeQuietly(portSock);
            Pipe.closeQuietly(sock);
        }

        String addresses() {
            List<String> a = new ArrayList<>();
            if (name != null) a.add(name + "." + cfg.domain + (cfg.publicPort == 25565 ? "" : ":" + cfg.publicPort));
            if (portSock != null) a.add(cfg.address + ":" + port);
            return String.join(" ", a);
        }
    }

    public Relay(RelayConfig cfg) {
        this.cfg = cfg;
    }

    public void start() throws IOException {
        loadState();
        running = true;
        control = bind(cfg.controlHost, cfg.controlPort);
        Pipe.start("relay control", () -> acceptLoop(control, this::onControl));
        if (cfg.namesOn()) {
            pub = bind(cfg.publicHost, cfg.publicPort);
            Pipe.start("relay public", () -> acceptLoop(pub, this::onPublic));
        }
        timer.scheduleAtFixedRate(this::pingAll, Proto.PING_MS, Proto.PING_MS, TimeUnit.MILLISECONDS);
        log("relay запущен: управление " + control.getLocalPort()
            + (pub != null ? ", игроки по имени *." + cfg.domain + " порт " + pub.getLocalPort() : "")
            + (cfg.portsOn() ? ", порты миров " + cfg.portMin + "-" + cfg.portMax + " на " + cfg.address : ""));
    }

    public void stop() {
        running = false;
        timer.shutdownNow();
        Pipe.closeQuietly(control);
        Pipe.closeQuietly(pub);
        synchronized (lock) {
            for (Host h : new ArrayList<>(byWorld.values())) h.close();
            byWorld.clear();
            byName.clear();
            byPort.clear();
        }
        for (Pending p : pending.values()) p.data.cancel(true);
        pending.clear();
    }

    public int controlPort() { return control.getLocalPort(); }
    public int publicPort() { return pub == null ? -1 : pub.getLocalPort(); }

    public int hosts() {
        synchronized (lock) { return byWorld.size(); }
    }

    private static ServerSocket bind(String host, int port) throws IOException {
        ServerSocket s = new ServerSocket();
        s.setReuseAddress(true);
        s.bind(new InetSocketAddress(InetAddress.getByName(host), port), 128);
        return s;
    }

    private interface Handler {
        void handle(Socket s) throws IOException;
    }

    private void acceptLoop(ServerSocket ss, Handler h) {
        while (running && !ss.isClosed()) {
            Socket s;
            try {
                s = ss.accept();
            } catch (IOException e) {
                if (!running || ss.isClosed()) return;
                continue;
            }
            Thread.ofVirtual().start(() -> {
                try {
                    s.setTcpNoDelay(true);
                    s.setKeepAlive(true);
                    h.handle(s);
                } catch (IOException | RuntimeException e) {
                    Pipe.closeQuietly(s);
                }
            });
        }
    }

    // Управляющее соединение хоста или соединение DATA для игрока.
    private void onControl(Socket s) throws IOException {
        s.setSoTimeout(10_000);
        InputStream in = s.getInputStream();
        OutputStream out = s.getOutputStream();
        String nonce = Proto.randomHex(16);
        Proto.writeLine(out, Proto.MAGIC + " " + Proto.VERSION + " " + nonce);
        String line = Proto.readLine(in);
        if (line == null) {
            s.close();
            return;
        }
        if (line.startsWith("DATA ")) {
            Pending p = pending.remove(line.substring(5).trim());
            if (p == null || !p.data.complete(s)) s.close();
            return;
        }
        String[] a = line.split(" ");
        if (a.length < 3 || !a[0].equals("AUTH") || !Proto.isHex(a[2], 32)) {
            Proto.writeLine(out, "ERR FATAL непонятный запрос, обновите мод");
            s.close();
            return;
        }
        if (!cfg.key.isEmpty() && !Proto.sameMac(Proto.mac(cfg.key, nonce), a[1])) {
            log("неверный ключ от " + s.getInetAddress().getHostAddress());
            sleep(1000);
            Proto.writeLine(out, "ERR FATAL неверный ключ relay, проверьте настройки мода");
            s.close();
            return;
        }
        String world = Proto.hex(Proto.sha256(a[2])).substring(0, 32);
        Host h = new Host(world, s);
        String err = register(h);
        if (err != null) {
            Proto.writeLine(out, "ERR RETRY " + err);
            s.close();
            return;
        }
        try {
            h.send("OK " + h.addresses());
            log("мир открыт: " + h.addresses());
            s.setSoTimeout(Proto.DEAD_MS);
            while (!h.closed) {
                String l = Proto.readLine(in);
                if (l == null || l.equals("BYE")) break;
                if (l.startsWith("FAIL ")) {
                    Pending p = pending.remove(l.substring(5).trim());
                    if (p != null && p.host == h) p.data.completeExceptionally(new IOException("нет сервера мира"));
                }
            }
        } catch (IOException e) {
            // обрыв или молчание дольше DEAD_MS
        } finally {
            unregister(h);
        }
    }

    private String register(Host h) throws IOException {
        Host old;
        synchronized (lock) {
            old = byWorld.get(h.world);
            if (old == null && byWorld.size() >= cfg.maxHosts) return "relay заполнен, попробуйте позже";
            if (old != null) {
                // тот же мир зашёл заново (старое соединение повисло): порт и имя отдаём новому
                drop(old);
                old.closed = true;
                Pipe.closeQuietly(old.portSock);
            }
            Entry e = saved.get(h.world);
            if (cfg.namesOn()) h.name = e != null && e.name != null ? e.name : newName(h.world);
            if (cfg.portsOn()) {
                int want = e != null ? e.port : 0;
                if (!openPort(h, want)) {
                    if (h.name == null) return "свободных портов нет";
                }
            }
            byWorld.put(h.world, h);
            if (h.name != null) byName.put(h.name, h);
            if (h.portSock != null) byPort.put(h.port, h);
            saved.put(h.world, new Entry(h.name, h.portSock != null ? h.port : (e != null ? e.port : 0)));
            saveState();
        }
        if (old != null) {
            // старому соединению говорим не переподключаться, иначе два хоста будут выбивать друг друга
            try { old.send("ERR FATAL этот мир открыли заново в другом месте"); } catch (IOException ignored) {}
            old.close();
        }
        if (h.portSock != null) {
            ServerSocket ps = h.portSock;
            Pipe.start("relay port " + h.port, () -> acceptLoop(ps, s -> onPlayer(h, s, new byte[0], null)));
        }
        return null;
    }

    private void unregister(Host h) {
        boolean was;
        synchronized (lock) {
            was = byWorld.get(h.world) == h;
            if (was) drop(h);
        }
        h.close();
        if (was) log("мир закрыт: " + h.addresses());
    }

    // вызывать под lock
    private void drop(Host h) {
        byWorld.remove(h.world);
        if (h.name != null) byName.remove(h.name, h);
        if (h.portSock != null) byPort.remove(h.port, h);
    }

    private String newName(String world) {
        Set<String> used = new HashSet<>();
        for (Entry e : saved.values()) if (e.name != null) used.add(e.name);
        byte[] hash = Proto.sha256("name:" + world);
        for (int i = 0; ; i++) {
            String n = Names.of(hash, i);
            if (!used.contains(n)) return n;
        }
    }

    // Сначала прежний порт мира, потом свободный, который никому не обещан, потом любой свободный.
    private boolean openPort(Host h, int want) {
        Set<Integer> promised = new HashSet<>();
        for (Map.Entry<String, Entry> e : saved.entrySet())
            if (!e.getKey().equals(h.world) && e.getValue().port > 0) promised.add(e.getValue().port);
        List<Integer> order = new ArrayList<>();
        if (want >= cfg.portMin && want <= cfg.portMax) order.add(want);
        for (int p = cfg.portMin; p <= cfg.portMax; p++) if (p != want && !promised.contains(p)) order.add(p);
        for (int p = cfg.portMin; p <= cfg.portMax; p++) if (p != want && promised.contains(p)) order.add(p);
        for (int p : order) {
            if (byPort.containsKey(p)) continue;
            try {
                h.portSock = bind(cfg.publicHost, p);
                h.port = h.portSock.getLocalPort();
                return true;
            } catch (IOException ignored) {
            }
        }
        return false;
    }

    // Игрок на общем порту: имя мира узнаём из пакета приветствия.
    private void onPublic(Socket s) throws IOException {
        s.setSoTimeout(5_000);
        McWire.Handshake hs = McWire.readHandshake(s.getInputStream());
        if (hs == null) {
            s.close();
            return;
        }
        String host = McWire.cleanHost(hs.host());
        String suffix = "." + cfg.domain;
        String name = host.endsWith(suffix) ? host.substring(0, host.length() - suffix.length()) : "";
        Host h;
        synchronized (lock) {
            h = byName.get(name);
        }
        if (h == null) {
            refuse(s, hs, name.isEmpty() ? "Это relay для миров по адресу имя." + cfg.domain
                : "Мир " + name + " сейчас не открыт. Хозяин мира должен зайти в него и открыть для сети.");
            return;
        }
        onPlayer(h, s, hs.raw(), hs);
    }

    private void onPlayer(Host h, Socket s, byte[] first, McWire.Handshake hs) throws IOException {
        if (h.closed) {
            refuse(s, hs, "Мир только что закрылся.");
            return;
        }
        if (h.players.incrementAndGet() > cfg.maxPlayersPerHost) {
            h.players.decrementAndGet();
            refuse(s, hs, "В мире уже слишком много игроков.");
            return;
        }
        boolean handed = false;
        String id = Proto.randomHex(16);
        Pending p = new Pending(h, new CompletableFuture<>());
        pending.put(id, p);
        try {
            h.send("OPEN " + id);
            Socket data = p.data.get(10, TimeUnit.SECONDS);
            data.setSoTimeout(0);
            s.setSoTimeout(0);
            if (first.length > 0) {
                data.getOutputStream().write(first);
                data.getOutputStream().flush();
            }
            handed = true;
            Pipe.run(s, data, "relay player", h.players::decrementAndGet);
        } catch (TimeoutException e) {
            refuse(s, hs, "Хост мира не отвечает.");
        } catch (java.util.concurrent.ExecutionException e) {
            refuse(s, hs, "Мир сейчас не принимает игроков. Попробуйте ещё раз.");
        } catch (Exception e) {
            Pipe.closeQuietly(s);
        } finally {
            pending.remove(id);
            if (!handed) {
                h.players.decrementAndGet();
                Socket late = p.data.getNow(null);
                Pipe.closeQuietly(late);
            }
        }
    }

    // Отвечаем игроку сами: в списке серверов текст, при входе экран отключения.
    private static void refuse(Socket s, McWire.Handshake hs, String text) {
        try {
            if (hs != null) {
                s.setSoTimeout(5_000);
                if (hs.next() == 1) McWire.answerStatus(s.getInputStream(), s.getOutputStream(), text, hs.protocol());
                else McWire.answerLogin(s.getOutputStream(), text);
            }
        } catch (IOException ignored) {
        } finally {
            Pipe.closeQuietly(s);
        }
    }

    private void pingAll() {
        List<Host> hs;
        synchronized (lock) {
            hs = new ArrayList<>(byWorld.values());
        }
        for (Host h : hs) {
            try {
                h.send("PING");
            } catch (IOException e) {
                h.close();
            }
        }
    }

    // Файл состояния: хэш id мира, имя, порт. Сами id миров тут не хранятся.
    private void loadState() throws IOException {
        if (!Files.exists(cfg.state)) return;
        for (String l : Files.readAllLines(cfg.state, StandardCharsets.UTF_8)) {
            String[] a = l.trim().split(" ");
            if (a.length != 3 || !Proto.isHex(a[0], 32)) continue;
            saved.put(a[0], new Entry(a[1].equals("-") ? null : a[1], Integer.parseInt(a[2])));
        }
    }

    private void saveState() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Entry> e : saved.entrySet())
            b.append(e.getKey()).append(' ').append(e.getValue().name == null ? "-" : e.getValue().name)
                .append(' ').append(e.getValue().port).append('\n');
        try {
            Path tmp = cfg.state.resolveSibling(cfg.state.getFileName() + ".tmp");
            Files.writeString(tmp, b, StandardCharsets.UTF_8);
            Files.move(tmp, cfg.state, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log("не смог сохранить " + cfg.state + ": " + e.getMessage());
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    static void log(String s) {
        System.out.println("[" + LocalDateTime.now().format(TIME) + "] " + s);
    }
}
