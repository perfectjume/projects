package com.example.examplemod;

import com.misanthropy.hit_indicator.client.WindupTracker;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = RuntimeTestMod.MODID, value = Dist.CLIENT)
public final class RuntimeTestClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean activeLogged;
    private static boolean renderLogged;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!activeLogged && !WindupTracker.active().isEmpty()) {
            activeLogged = true;
            LOGGER.info("[HI-TEST] CLIENT_WINDUP_ACTIVE count={}", WindupTracker.active().size());
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (!renderLogged
                && event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES
                && !WindupTracker.active().isEmpty()) {
            renderLogged = true;
            LOGGER.info("[HI-TEST] RENDER_STAGE_WITH_ACTIVE count={}", WindupTracker.active().size());
        }
    }
}
