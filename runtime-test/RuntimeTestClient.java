package com.example.examplemod;

import com.misanthropy.hit_indicator.client.CameraShake;
import com.misanthropy.hit_indicator.client.FrozenRenderClock;
import com.misanthropy.hit_indicator.client.StasisDesaturationRenderer;
import com.misanthropy.hit_indicator.client.WindupTracker;
import com.misanthropy.hit_indicator.client.WindupTracker.Windup;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = RuntimeTestMod.MODID, value = Dist.CLIENT)
public final class RuntimeTestClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<Integer> loggedWindups = new HashSet<>();
    private static final Set<String> loggedRenders = new HashSet<>();
    private static final Set<String> loggedFreeze = new HashSet<>();
    private static final Set<String> loggedFreezeMixin = new HashSet<>();
    private static final Set<String> loggedEnds = new HashSet<>();
    private static final Map<Integer, Integer> lastTickCount = new HashMap<>();
    private static final Map<Integer, Integer> sameTickCount = new HashMap<>();
    private static String lastScenario = "";
    private static boolean hadActiveInScenario;
    private static boolean cancelClearedLogged;
    private static boolean cameraStartedLogged;
    private static boolean cameraAppliedLogged;
    private static boolean directFreezeProbeLogged;
    private static boolean renderClockFreezeProbeLogged;
    private static boolean stasisGrayscaleLogged;
    private static boolean stasisDiagnosticLogged;
    private static boolean moddedStackLogged;
    private static boolean releasePredicateFalseLogged;
    private static boolean releaseBridgeObserved;
    private static boolean releaseResumeLogged;
    private static long releaseBridgeGameTime = Long.MIN_VALUE;
    private static int releaseBridgeTickCount = Integer.MIN_VALUE;
    private static int releaseBridgeExternalCount = Integer.MIN_VALUE;
    private static int freezeProbeEntityId = -1;
    private static Field cameraTicksField;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        String scenario = RuntimeTestMod.currentScenario();
        if (!moddedStackLogged) {
            moddedStackLogged = true;
            LOGGER.info("[HI-MATRIX] MODDED_STACK_LOADED bettercombat={} playeranimator={} betterMobCombat={}",
                    ModList.get().isLoaded("bettercombat"),
                    ModList.get().isLoaded("playeranimator"),
                    ModList.get().isLoaded("better_mob_combat_reimagined"));
        }
        if (!scenario.equals(lastScenario)) {
            lastScenario = scenario;
            hadActiveInScenario = false;
        }

        if (!WindupTracker.active().isEmpty()) hadActiveInScenario = true;

        for (Int2ObjectMap.Entry<Windup> entry : WindupTracker.active().int2ObjectEntrySet()) {
            int id = entry.getIntKey();
            Windup w = entry.getValue();
            if (loggedWindups.add(id)) {
                LOGGER.info("[HI-MATRIX] CLIENT_WINDUP scenario={} id={} kind={} duration={} move={} end={}",
                        scenario, id, w.kind, w.durationTicks, w.moveTicks, w.end);
            }

            String endKey = scenario + ":" + id + ":" + w.end;
            if (w.end != 0 && loggedEnds.add(endKey)) {
                LOGGER.info("[HI-MATRIX] CLIENT_END scenario={} id={} kind={} reason={}",
                        scenario, id, w.kind, w.end);
            }

            Entity entity = mc.level.getEntity(id);
            if (entity instanceof LivingEntity living) {
                aimAt(mc, living);
                if (WindupTracker.shouldFreeze(living)) {
                    long freezeElapsed = mc.level.getGameTime() - w.startGameTime;
                if ("FREEZE".equals(scenario) && !directFreezeProbeLogged) {
                    int before = living.tickCount;
                    int externalBefore = MobAnimationProbe.count();

                    double x = living.getX();
                    double y = living.getY();
                    double z = living.getZ();
                    float xRot = living.getXRot();
                    float yRot = living.getYRot();

                    living.xOld = x - 0.75D;
                    living.yOld = y - 0.50D;
                    living.zOld = z + 0.25D;
                    living.xo = x - 1.25D;
                    living.yo = y - 1.00D;
                    living.zo = z + 0.75D;
                    living.xRotO = xRot - 17.0F;
                    living.yRotO = yRot + 23.0F;
                    living.walkDistO = living.walkDist - 0.8F;
                    living.yBodyRotO = living.yBodyRot - 31.0F;
                    living.yHeadRotO = living.yHeadRot + 29.0F;
                    living.oAttackAnim = living.attackAnim - 0.35F;

                    mc.level.tickNonPassenger(living);

                    int after = living.tickCount;
                    int externalAfter = MobAnimationProbe.count();
                    boolean maintenanceStable =
                            living.xOld == x && living.yOld == y && living.zOld == z
                            && living.xo == x && living.yo == y && living.zo == z
                            && living.xRotO == xRot && living.yRotO == yRot
                            && living.walkDistO == living.walkDist
                            && living.yBodyRotO == living.yBodyRot
                            && living.yHeadRotO == living.yHeadRot
                            && living.oAttackAnim == living.attackAnim;

                    directFreezeProbeLogged = true;
                    freezeProbeEntityId = id;
                    LOGGER.info("[HI-MATRIX] CLIENT_TIME_STOP_DIRECT_PROBE before={} after={} tickCanceled={} maintenanceStable={} externalHeadBefore={} externalHeadAfter={} externalHeadBlocked={}",
                            before, after, before == after, maintenanceStable,
                            externalBefore, externalAfter, externalBefore == externalAfter);
                }

                if ("FREEZE".equals(scenario) && !renderClockFreezeProbeLogged) {
                    float first = FrozenRenderClock.partialFor(living, 0.73F);
                    float second = FrozenRenderClock.partialFor(living, 0.11F);
                    renderClockFreezeProbeLogged = true;
                    LOGGER.info("[HI-MATRIX] FROZEN_RENDER_CLOCK first={} second={} stable={}",
                            first, second, Math.abs(first - second) < 0.0001F);
                }

                String freezeKey = scenario + ":" + id;
                if (loggedFreeze.add(freezeKey)) {
                    LOGGER.info("[HI-MATRIX] CLIENT_FREEZE_TRUE scenario={} id={} kind={} tickCount={} elapsed={}",
                            scenario, id, w.kind, living.tickCount, freezeElapsed);
                }
                int old = lastTickCount.getOrDefault(id, Integer.MIN_VALUE);
                if (old == living.tickCount) {
                    int count = sameTickCount.getOrDefault(id, 0) + 1;
                    sameTickCount.put(id, count);
                    if (count >= 2 && loggedFreezeMixin.add(freezeKey)) {
                        LOGGER.info("[HI-MATRIX] CLIENT_MIXIN_FREEZE_CONFIRMED scenario={} id={} tickCount={}",
                                scenario, id, living.tickCount);
                    }
                } else {
                    sameTickCount.put(id, 0);
                }
                lastTickCount.put(id, living.tickCount);
                }


            }
        }

        if (directFreezeProbeLogged
                && !releaseResumeLogged
                && freezeProbeEntityId >= 0) {
            Entity frozenEntity = mc.level.getEntity(freezeProbeEntityId);
            if (frozenEntity instanceof LivingEntity living
                    && !WindupTracker.shouldFreeze(living)) {
                if (!releasePredicateFalseLogged) {
                    releasePredicateFalseLogged = true;
                    LOGGER.info("[HI-MATRIX] RELEASE_PREDICATE_FALSE id={} gameTime={} tickCount={} active={} frozenPartial={} bridge={}",
                            living.getId(),
                            mc.level.getGameTime(),
                            living.tickCount,
                            WindupTracker.active().containsKey(living.getId()),
                            FrozenRenderClock.frozenPartial(living),
                            FrozenRenderClock.isReleaseBridge(living));
                }

                if (!releaseBridgeObserved && FrozenRenderClock.isReleaseBridge(living)) {
                    float frozen = FrozenRenderClock.frozenPartial(living);
                    float earlyRequested = 0.05F;
                    float laterRequested = 0.40F;
                    float early = FrozenRenderClock.partialFor(living, earlyRequested);
                    float later = FrozenRenderClock.partialFor(living, laterRequested);
                    float expectedEarly = frozen + (1.0F - frozen) * earlyRequested;
                    float expectedLater = frozen + (1.0F - frozen) * laterRequested;
                    boolean continuous =
                            !Float.isNaN(frozen)
                            && Math.abs(early - expectedEarly) < 0.0001F
                            && Math.abs(later - expectedLater) < 0.0001F
                            && early + 0.0001F >= frozen
                            && later + 0.0001F >= early;

                    releaseBridgeObserved = true;
                    releaseBridgeGameTime = mc.level.getGameTime();
                    releaseBridgeTickCount = living.tickCount;
                    releaseBridgeExternalCount = MobAnimationProbe.count();

                    LOGGER.info("[HI-MATRIX] CONTINUOUS_RELEASE_BRIDGE frozen={} early={} later={} continuous={} tickHeld={} externalCount={}",
                            frozen, early, later, continuous, living.tickCount, releaseBridgeExternalCount);
                } else if (releaseBridgeObserved
                        && mc.level.getGameTime() > releaseBridgeGameTime) {
                    float requested = 0.31F;
                    float applied = FrozenRenderClock.partialFor(living, requested);
                    boolean bridgeCleared = !FrozenRenderClock.isReleaseBridge(living);
                    boolean tickResumed = living.tickCount > releaseBridgeTickCount;
                    boolean externalResumed = MobAnimationProbe.count() > releaseBridgeExternalCount;
                    boolean liveClock = Math.abs(applied - requested) < 0.0001F;

                    releaseResumeLogged = true;
                    LOGGER.info("[HI-MATRIX] CONTINUOUS_RELEASE_RESUME bridgeCleared={} tickResumed={} externalResumed={} requested={} applied={} liveClock={}",
                            bridgeCleared, tickResumed, externalResumed, requested, applied, liveClock);
                }
            }
        }

        if (!stasisDiagnosticLogged && StasisDesaturationRenderer.passCount() > 0) {
            stasisDiagnosticLogged = true;
            LOGGER.info("[HI-MATRIX] STASIS_PIXEL_DIAGNOSTIC scenario={} entityPixels={} beforeAlpha={} beforeSpread={} afterSpread={} rgb={}",
                    scenario,
                    StasisDesaturationRenderer.entityPixelCount(),
                    StasisDesaturationRenderer.diagnosticBeforeAlpha(),
                    StasisDesaturationRenderer.diagnosticBeforeSpread(),
                    StasisDesaturationRenderer.diagnosticAfterSpread(),
                    StasisDesaturationRenderer.diagnosticRgb());
        }

        if ("FREEZE".equals(scenario)
                && !stasisGrayscaleLogged
                && StasisDesaturationRenderer.passCount() > 0) {
            stasisGrayscaleLogged = true;
            int beforeSpread = StasisDesaturationRenderer.diagnosticBeforeSpread();
            int afterSpread = StasisDesaturationRenderer.diagnosticAfterSpread();
            boolean pixelDesaturated =
                    StasisDesaturationRenderer.entityPixelCount() > 0
                    && StasisDesaturationRenderer.diagnosticBeforeAlpha() >= 245
                    && beforeSpread > 10
                    && afterSpread >= 0
                    && afterSpread <= Math.max(4, beforeSpread / 4);
            LOGGER.info("[HI-MATRIX] STASIS_GRAYSCALE shaderReady={} targetReady={} beginCount={} passCount={} executed={} entityPixels={} beforeAlpha={} beforeSpread={} afterSpread={} pixelDesaturated={} rgb={}",
                    StasisDesaturationRenderer.shaderReady(),
                    StasisDesaturationRenderer.targetReady(),
                    StasisDesaturationRenderer.beginCount(),
                    StasisDesaturationRenderer.passCount(),
                    StasisDesaturationRenderer.passCount() > 0,
                    StasisDesaturationRenderer.entityPixelCount(),
                    StasisDesaturationRenderer.diagnosticBeforeAlpha(),
                    beforeSpread,
                    afterSpread,
                    pixelDesaturated,
                    StasisDesaturationRenderer.diagnosticRgb());
        }

        if ("CANCEL".equals(scenario)
                && hadActiveInScenario
                && WindupTracker.active().isEmpty()
                && !cancelClearedLogged) {
            cancelClearedLogged = true;
            LOGGER.info("[HI-MATRIX] CLIENT_CANCEL_CLEARED");
        }

        if ("SLAM".equals(scenario) && cameraTicksLeft() > 0 && !cameraStartedLogged) {
            cameraStartedLogged = true;
            LOGGER.info("[HI-MATRIX] CLIENT_CAMERA_SHAKE_STARTED ticksLeft={}", cameraTicksLeft());
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || WindupTracker.active().isEmpty()) return;
        String scenario = RuntimeTestMod.currentScenario();
        for (Windup w : WindupTracker.active().values()) {
            String key = scenario + ":" + w.kind;
            if (loggedRenders.add(key)) {
                LOGGER.info("[HI-MATRIX] CLIENT_RENDER scenario={} kind={}", scenario, w.kind);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!"SLAM".equals(RuntimeTestMod.currentScenario()) || cameraTicksLeft() <= 0 || cameraAppliedLogged) return;
        if (Math.abs(event.getRoll()) > 0.00001F) {
            cameraAppliedLogged = true;
            LOGGER.info("[HI-MATRIX] CLIENT_CAMERA_SHAKE_APPLIED roll={} pitch={} yaw={}",
                    event.getRoll(), event.getPitch(), event.getYaw());
        }
    }

    private static void aimAt(Minecraft mc, LivingEntity target) {
        if (mc.player == null) return;
        double dx = target.getX() - mc.player.getX();
        double dy = target.getEyeY() - mc.player.getEyeY();
        double dz = target.getZ() - mc.player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, horizontal)));
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
    }

    private static int cameraTicksLeft() {
        try {
            if (cameraTicksField == null) {
                cameraTicksField = CameraShake.class.getDeclaredField("ticksLeft");
                cameraTicksField.setAccessible(true);
            }
            return cameraTicksField.getInt(null);
        } catch (ReflectiveOperationException error) {
            LOGGER.error("[HI-MATRIX] CLIENT_REFLECTION_FAIL CameraShake.ticksLeft", error);
            return -1;
        }
    }
}
