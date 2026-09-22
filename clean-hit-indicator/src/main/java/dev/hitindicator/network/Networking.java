package dev.hitindicator.network;

import dev.hitindicator.client.TelegraphState;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Networking {
    private Networking() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                TelegraphStartPayload.TYPE,
                TelegraphStartPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        TelegraphState.start(
                                payload.entityId(),
                                payload.durationTicks(),
                                context.player().level().getGameTime())));

        registrar.playToClient(
                TelegraphEndPayload.TYPE,
                TelegraphEndPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        TelegraphState.end(payload.entityId())));
    }
}
