package dev.cld.far;

import dev.cld.tcw.gen.Options;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Сид генератора рельефа и настройки мира TelipailoCraft. */
public record SeedPayload(long seed, int continents, int mountains, int height, int climate, int snow, int halls) implements CustomPacketPayload {
    public static final Type<SeedPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FarMod.NS, "seed"));
    public static final StreamCodec<FriendlyByteBuf, SeedPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeLong(p.seed);
                for (int v : new int[] { p.continents, p.mountains, p.height, p.climate, p.snow, p.halls }) buf.writeByte(v);
            },
            buf -> new SeedPayload(buf.readLong(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte()));

    public Options options() { return new Options(continents, mountains, height, climate, snow, halls); }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
