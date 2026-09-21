package com.example.examplemod;

import com.misanthropy.hit_indicator.client.CameraShake;
import com.misanthropy.hit_indicator.client.WindupTracker;
import com.misanthropy.hit_indicator.client.WindupTracker.Windup;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
    private static boolean freezeReleaseProbeLogged;
    private static int freezeProbeEntityId = -1;
    private static Field cameraTicksField;
    private static Method freezeInjectionMethod;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        String scenario = RuntimeTestMod.currentScenario();
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
                if ("FREEZE".equals(scenario) && !directFreezeProbeLogged) {
                    int before = living.tickCount;
                    living.tick();
                    int after = living.tickCount;
                    boolean callbackCanceled = probeFreezeCallback(living);
                    directFreezeProbeLogged = true;
                    freezeProbeEntityId = id;
                    LOGGER.info("[HI-MATRIX] CLIENT_MIXIN_DIRECT_PROBE before={} after={} tickCanceled={} callbackCanceled={}",
                            before, after, before == after, callbackCanceled);
                }

                String freezeKey = scenario + ":" + id;
                if (loggedFreeze.add(freezeKey)) {
                    LOGGER.info("[HI-MATRIX] CLIENT_FREEZE_TRUE scenario={} id={} kind={} tickCount={}",
                            scenario, id, w.kind, living.tickCount);
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

        if ("CANCEL".equals(scenario)
                && hadActiveInScenario
                && WindupTracker.active().isEmpty()
                && !cancelClearedLogged) {
            cancelClearedLogged = true;
            LOGGER.info("[HI-MATRIX] CLIENT_CANCEL_CLEARED");
        }

        if ("FREEZE".equals(scenario)
                && directFreezeProbeLogged
                && hadActiveInScenario
                && WindupTracker.active().isEmpty()
                && !freezeReleaseProbeLogged
                && freezeProbeEntityId >= 0) {
            Entity entity = mc.level.getEntity(freezeProbeEntityId);
            if (entity instanceof LivingEntity living) {
                boolean predicate = WindupTracker.shouldFreeze(living);
                boolean callbackCanceled = probeFreezeCallback(living);
                freezeReleaseProbeLogged = true;
                LOGGER.info("[HI-MATRIX] CLIENT_MIXIN_RELEASE_PROBE predicate={} callbackCanceled={} released={}",
                        predicate, callbackCanceled, !predicate && !callbackCanceled);
            }
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

    private static boolean probeFreezeCallback(LivingEntity living) {
        try {
            if (freezeInjectionMethod == null) {
                freezeInjectionMethod = living.getClass().getMethod("hit_indicator$freezeWindup", CallbackInfo.class);
                freezeInjectionMethod.setAccessible(true);
            }
            CallbackInfo ci = new CallbackInfo("tick", true);
            freezeInjectionMethod.invoke(living, ci);
            return ci.isCancelled();
        } catch (NoSuchMethodException missingPublic) {
            try {
                freezeInjectionMethod = LivingEntity.class.getDeclaredMethod(
                        "hit_indicator$freezeWindup", CallbackInfo.class);
                freezeInjectionMethod.setAccessible(true);
                CallbackInfo ci = new CallbackInfo("tick", true);
                freezeInjectionMethod.invoke(living, ci);
                return ci.isCancelled();
            } catch (ReflectiveOperationException error) {
                LOGGER.error("[HI-MATRIX] CLIENT_FREEZE_CALLBACK_PROBE_FAIL", error);
                return false;
            }
        } catch (ReflectiveOperationException error) {
            LOGGER.error("[HI-MATRIX] CLIENT_FREEZE_CALLBACK_PROBE_FAIL", error);
            return false;
        }
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
