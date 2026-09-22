package dev.hitindicator;

import dev.hitindicator.network.Networking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(HitIndicator.MOD_ID)
public final class HitIndicator {
    public static final String MOD_ID = "hit_indicator";

    public HitIndicator(IEventBus modBus, ModContainer container) {
        modBus.addListener(Networking::register);
    }
}
