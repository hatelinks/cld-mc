package dev.cld.tunnel;

import com.mojang.brigadier.CommandDispatcher;
import dev.cld.tunnel.core.TunnelClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

// Игра по сети: открытый для сети одиночный мир становится доступен друзьям через наш relay.
@Mod(value = "cld_tunnel", dist = Dist.CLIENT)
public final class TunnelMod {
    public TunnelMod(IEventBus modBus, ModContainer c) {
        c.registerConfig(ModConfig.Type.CLIENT, TunnelConfig.SPEC);
        c.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> HostTunnel.tick());
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> commands(e.getDispatcher()));
    }

    // /cldtunnel: адрес и состояние, /cldtunnel start и stop
    private static void commands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("cldtunnel")
            .executes(x -> status())
            .then(Commands.literal("start").executes(x -> start()))
            .then(Commands.literal("stop").executes(x -> {
                HostTunnel.stop(true);
                return 1;
            })));
    }

    private static int status() {
        TunnelClient c = HostTunnel.client();
        if (c == null) {
            IntegratedServer s = Minecraft.getInstance().getSingleplayerServer();
            String key = s == null ? "cld_tunnel.status.no_world"
                : !s.isPublished() ? "cld_tunnel.status.not_lan" : "cld_tunnel.status.off";
            HostTunnel.say(Component.translatable(key).withStyle(ChatFormatting.GRAY));
            return 0;
        }
        String a = c.address();
        if (a == null) HostTunnel.say(Component.translatable("cld_tunnel.status.connecting").withStyle(ChatFormatting.GRAY));
        else HostTunnel.say(HostTunnel.addressMessage(a).append(Component.translatable("cld_tunnel.status.players", c.players())
            .withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private static int start() {
        IntegratedServer s = Minecraft.getInstance().getSingleplayerServer();
        if (s == null || !s.isPublished()) {
            HostTunnel.say(Component.translatable(s == null ? "cld_tunnel.status.no_world" : "cld_tunnel.status.not_lan")
                .withStyle(ChatFormatting.GRAY));
            return 0;
        }
        if (HostTunnel.client() != null) return status();
        return HostTunnel.start(s, true) ? 1 : 0;
    }
}
