package dev.hitindicator.runtime;

import dev.hitindicator.client.StasisDesaturationRenderer;
import dev.hitindicator.client.TelegraphRenderer;
import dev.hitindicator.client.TelegraphState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
        modid = CleanRuntimeTestMod.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class CleanRuntimeTestClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("CleanHitIndicatorRuntimeClient");

    private static int activeFrames;
    private static int frozenTickCount;
    private static int trackedId = -1;
    private static boolean sawActive;
    private static boolean completed;

    private CleanRuntimeTestClient() {}

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        activeFrames = 0;
        trackedId = -1;
        sawActive = false;
        completed = false;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (completed) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        Zombie zombie = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Zombie z) {
                zombie = z;
                break;
            }
        }
        if (zombie == null) {
            return;
        }

        boolean frozen = TelegraphState.isFrozen(zombie.getId());
        if (frozen) {
            activeFrames++;
            if (!sawActive) {
                sawActive = true;
                trackedId = zombie.getId();
                frozenTickCount = zombie.tickCount;
                LOGGER.info("[CLEAN-HI] CLIENT_ACTIVE id={} tickCount={}", trackedId, frozenTickCount);
            }

            if (activeFrames >= 5 && zombie.tickCount != frozenTickCount) {
                fail("client tickCount advanced while frozen: start="
                        + frozenTickCount + " now=" + zombie.tickCount);
                return;
            }

            if (activeFrames == 8) {
                LOGGER.info(
                        "[CLEAN-HI] CLIENT_VISUAL_PROGRESS ringFrames={} grayscalePasses={}",
                        TelegraphRenderer.renderedFrames(),
                        StasisDesaturationRenderer.passCount());
            }
            return;
        }

        if (sawActive && zombie.getId() == trackedId) {
            if (TelegraphRenderer.renderedFrames() <= 0) {
                fail("procedural ring never rendered");
                return;
            }
            if (StasisDesaturationRenderer.passCount() <= 0) {
                fail("grayscale pass never rendered");
                return;
            }

            if (zombie.tickCount <= frozenTickCount) {
                return;
            }

            completed = true;
            LOGGER.info(
                    "[CLEAN-HI] CLIENT_CLEAN_PASS ringFrames={} grayscalePasses={} resumedTick={}",
                    TelegraphRenderer.renderedFrames(),
                    StasisDesaturationRenderer.passCount(),
                    zombie.tickCount);
        }
    }

    private static void fail(String reason) {
        completed = true;
        LOGGER.error("[CLEAN-HI] FAIL {}", reason);
    }
}
