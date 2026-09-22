package com.soulshade.hitindicator.network;

import com.soulshade.hitindicator.SoulshadeHitIndicator;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TelegraphStartPayload(int entityId, int durationTicks) implements CustomPacketPayload {
    public static final Type<TelegraphStartPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoulshadeHitIndicator.MOD_ID, "telegraph_start"));

    public static final StreamCodec<ByteBuf, TelegraphStartPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TelegraphStartPayload::entityId,
            ByteBufCodecs.VAR_INT, TelegraphStartPayload::durationTicks,
            TelegraphStartPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
