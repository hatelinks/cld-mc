package dev.cld.tunnel;

import dev.cld.tunnel.core.Proto;
import dev.cld.tunnel.core.TunnelClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Связка туннеля с игрой: раз в секунду смотрим, открыт ли мир для сети, и включаем или выключаем туннель.
public final class HostTunnel {
    private static final Logger LOG = LoggerFactory.getLogger("cld_tunnel");
    private static TunnelClient client;
    private static IntegratedServer server;
    // сервер, на котором туннель выключили руками или он упал с ошибкой: сам больше не включаем
    private static IntegratedServer off;
    private static IntegratedServer warned;
    private static boolean lostShown;
    private static boolean wasOnline;
    private static int ticks;

    private HostTunnel() {}

    static void tick() {
        if (++ticks % 20 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer s = mc.getSingleplayerServer();
        boolean open = s != null && s.isPublished() && s.getPort() > 0 && !s.isStopped();
        if (client != null && (!open || s != server)) stop(false);
        if (!open) {
            if (s == null) {
                off = null;
                warned = null;
            }
            return;
        }
        if (client == null && TunnelConfig.AUTO.get() && off != s) start(s, false);
    }

    static synchronized boolean start(IntegratedServer s, boolean byHand) {
        if (client != null) return true;
        String[] relay = TunnelConfig.relay();
        if (relay == null) {
            if (byHand || warned != s) {
                warned = s;
                say(Component.translatable("cld_tunnel.no_relay").withStyle(ChatFormatting.YELLOW));
            }
            return false;
        }
        String id;
        try {
            id = worldId(s);
        } catch (IOException e) {
            LOG.warn("не смог прочитать id мира", e);
            say(Component.translatable("cld_tunnel.failed", e.getMessage()).withStyle(ChatFormatting.RED));
            return false;
        }
        if (TunnelConfig.OFFLINE_PLAYERS.get()) s.execute(() -> s.setUsesAuthentication(false));
        server = s;
        off = null;
        lostShown = false;
        wasOnline = false;
        int port = s.getPort();
        TunnelClient[] self = new TunnelClient[1];
        // события старого туннеля (уже остановленного) не должны трогать новый
        self[0] = new TunnelClient(relay[0], Integer.parseInt(relay[1]), TunnelConfig.KEY.get().trim(), id,
            "127.0.0.1", port, (st, text) -> onState(self[0], st, text));
        client = self[0];
        say(Component.translatable("cld_tunnel.connecting").withStyle(ChatFormatting.GRAY));
        client.start();
        LOG.info("туннель: relay {}:{}, локальный порт {}", relay[0], relay[1], port);
        return true;
    }

    static synchronized void stop(boolean byHand) {
        TunnelClient c = client;
        client = null;
        if (c == null) return;
        if (byHand) off = server;
        server = null;
        c.stop();
        if (byHand) say(Component.translatable("cld_tunnel.stopped").withStyle(ChatFormatting.GRAY));
    }

    static synchronized TunnelClient client() {
        return client;
    }

    static IntegratedServer offServer() {
        return off;
    }

    private static void onState(TunnelClient from, TunnelClient.State st, String text) {
        Minecraft.getInstance().execute(() -> {
            synchronized (HostTunnel.class) {
                if (from != client) return;
            }
            switch (st) {
                case ONLINE -> {
                    if (!wasOnline) say(addressMessage(text));
                    else say(Component.translatable("cld_tunnel.back").withStyle(ChatFormatting.GREEN));
                    wasOnline = true;
                    lostShown = false;
                }
                case RETRY -> {
                    LOG.info("туннель: {}", text);
                    if (!lostShown) {
                        lostShown = true;
                        say(Component.translatable(wasOnline ? "cld_tunnel.lost" : "cld_tunnel.retry", text)
                            .withStyle(ChatFormatting.YELLOW));
                    }
                }
                case FAILED -> {
                    LOG.warn("туннель: {}", text);
                    synchronized (HostTunnel.class) {
                        off = server;
                        client = null;
                        server = null;
                    }
                    say(Component.translatable("cld_tunnel.failed", text).withStyle(ChatFormatting.RED));
                }
                default -> {}
            }
        });
    }

    static MutableComponent addressMessage(String addresses) {
        MutableComponent m = Component.translatable("cld_tunnel.online").withStyle(ChatFormatting.GREEN);
        for (String a : addresses.split(" ")) {
            if (a.isEmpty()) continue;
            m.append(Component.literal(" "));
            m.append(Component.literal("[" + a + "]").withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, a))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("cld_tunnel.copy")))));
        }
        return m;
    }

    static void say(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) mc.gui.getChat().addMessage(Component.literal("[Сеть] ").withStyle(ChatFormatting.DARK_AQUA).append(c));
    }

    // id мира живёт в папке мира. Копия мира в другой папке получает свой id и свой адрес,
    // иначе две копии выбивали бы друг друга с relay.
    static String worldId(IntegratedServer s) throws IOException {
        Path root = s.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path file = root.resolve("cld_tunnel.txt");
        String place = Proto.hex(Proto.sha256(root.toString())).substring(0, 16);
        if (Files.exists(file)) {
            String id = null, at = null;
            for (String l : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (l.startsWith("id=")) id = l.substring(3).trim();
                if (l.startsWith("place=")) at = l.substring(6).trim();
            }
            if (Proto.isHex(id, 32) && place.equals(at)) return id;
        }
        String id = Proto.randomHex(16);
        Files.writeString(file, "# id мира для cld_tunnel, от него зависит адрес мира\nid=" + id + "\nplace=" + place + "\n",
            StandardCharsets.UTF_8);
        return id;
    }
}
