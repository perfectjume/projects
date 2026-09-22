package dev.hitindicator.network;

import dev.hitindicator.HitIndicator;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TelegraphEndPayload(int entityId) implements CustomPacketPayload {
    public static final Type<TelegraphEndPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HitIndicator.MOD_ID, "telegraph_end"));

    public static final StreamCodec<ByteBuf, TelegraphEndPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TelegraphEndPayload::entityId,
            TelegraphEndPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
