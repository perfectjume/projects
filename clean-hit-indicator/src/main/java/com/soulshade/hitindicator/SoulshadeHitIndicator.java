package com.soulshade.hitindicator;

import com.soulshade.hitindicator.network.Networking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(SoulshadeHitIndicator.MOD_ID)
public final class SoulshadeHitIndicator {
    public static final String MOD_ID = "soulshade_hit_indicator";

    public SoulshadeHitIndicator(IEventBus modBus, ModContainer container) {
        modBus.addListener(Networking::register);
    }
}
